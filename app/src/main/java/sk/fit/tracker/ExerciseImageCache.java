package sk.fit.tracker;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Trvalé lokálne úložisko ilustračných obrázkov cvikov (context.getFilesDir()) — offline-first.
 * Ak appka obrázok cviku raz stiahne, natrvalo si ho uloží na disk a odvtedy ho vždy načíta
 * lokálne (bez opätovného sťahovania), aj keď telefón nemá pripojenie na internet.
 *
 * Zámerne bez Glide/Picasso — obyčajný HttpURLConnection na pozadovom vlákne (appka doteraz
 * nemala žiadnu sieťovú/obrázkovú knižnicu; toto je jediné miesto, kde appka vôbec pristupuje
 * na sieť). Ak appka v budúcnosti pridá skutočne veľké/časté sťahovanie obrázkov, oplatí sa
 * na Glide prejsť (memory cache, resize, zrušenie requestov pri recyklovaní View) — pre jeden
 * malý obrázok na cvik v dialógu to zatiaľ nie je potrebné.
 */
public class ExerciseImageCache {

    /**
     * Najdlhšia strana dekódovaného obrázka v px — katalóg zobrazuje desiatky kariet naraz,
     * plné rozlíšenie by zbytočne zaberalo pamäť.
     */
    private static final int MAX_DECODE_PX = 480;

    /** Obmedzený počet vlákien — mriežka katalógu inak spustí desiatky sťahovaní naraz. */
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** Pamäťová cache dekódovaných bitmáp (1/8 heapu) — rolovanie mriežky tak nečíta stále z disku. */
    private static final LruCache<String, Bitmap> MEMORY = new LruCache<String, Bitmap>(
            (int) (Runtime.getRuntime().maxMemory() / 1024 / 8)) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount() / 1024;
        }
    };

    private ExerciseImageCache() {}

    /** Súbor v trvalom internom úložisku appky pre daný cvik (existuje nezávisle od siete/pripojenia). */
    public static File cacheFileFor(Context ctx, String exerciseName) {
        return new File(ctx.getFilesDir(), "exercise_" + sanitize(exerciseName) + ".jpg");
    }

    public static boolean isCached(Context ctx, String exerciseName) {
        return cacheFileFor(ctx, exerciseName).exists();
    }

    /**
     * Nastaví obrázok cviku do daného ImageView, len ak má naozaj čo zobraziť:
     *  1) ak je obrázok už natrvalo uložený na disku, načíta ho hneď (offline, bez siete),
     *  2) inak, ak je zadaná imageUrl, stiahne ho na pozadí, natrvalo uloží pre budúce
     *     offline použitie a potom ho zobrazí,
     *  3) inak nerobí vôbec nič — volajúci si má PRED zavolaním tejto metódy sám nastaviť
     *     predvolený obrázok (napr. Category.muscleDrawableFor), ktorý v tomto prípade
     *     ostane zobrazený.
     * Bezpečné voči medzičasu zmeny cviku v dialógu (napr. používateľ medzitým vybral iný
     * cvik) — pred aktualizáciou na hlavnom vlákne sa overí, že tag ImageView sa stále
     * zhoduje s cvikom, pre ktorý sa obrázok sťahoval/načítaval.
     */
    public static void loadInto(Context ctx, String exerciseName, String imageUrl, ImageView target) {
        loadInto(ctx, exerciseName, imageUrl, target, null);
    }

    /**
     * Ako loadInto vyššie; onLoaded (môže byť null) sa zavolá na hlavnom vlákne hneď po tom,
     * ako sa do ImageView naozaj nastavil obrázok cviku — napr. na prepnutie scaleType zo
     * siluety partie na fotku cez celú plochu.
     */
    public static void loadInto(Context ctx, String exerciseName, String imageUrl, ImageView target, Runnable onLoaded) {
        Context appCtx = ctx.getApplicationContext();
        target.setTag(exerciseName);

        Bitmap inMemory = MEMORY.get(exerciseName);
        if (inMemory != null) {
            target.setImageBitmap(inMemory);
            if (onLoaded != null) onLoaded.run();
            return;
        }

        File cached = cacheFileFor(appCtx, exerciseName);
        if (cached.exists()) {
            EXECUTOR.execute(() -> {
                Bitmap bmp = decodeSampled(cached);
                postIfStillRelevant(target, exerciseName, bmp, onLoaded);
            });
            return;
        }

        if (imageUrl == null || imageUrl.trim().isEmpty()) return; // nič na stiahnutie — ostáva predvolený obrázok

        EXECUTOR.execute(() -> {
            Bitmap bmp = downloadAndCache(appCtx, exerciseName, imageUrl);
            postIfStillRelevant(target, exerciseName, bmp, onLoaded);
        });
    }

    private static void postIfStillRelevant(ImageView target, String exerciseName, Bitmap bmp, Runnable onLoaded) {
        if (bmp == null) return; // chyba/žiadne dáta — necháme predvolený obrázok, appka kvôli tomu nespadne
        MEMORY.put(exerciseName, bmp);
        MAIN.post(() -> {
            if (exerciseName.equals(target.getTag())) {
                target.setImageBitmap(bmp);
                if (onLoaded != null) onLoaded.run();
            }
        });
    }

    /** Dekóduje obrázok zmenšený tak, aby dlhšia strana nebola väčšia ako MAX_DECODE_PX. */
    private static Bitmap decodeSampled(File file) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_DECODE_PX) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
    }

    /**
     * Stiahne obrázok cez HttpURLConnection, natrvalo ho uloží do interného úložiska appky
     * a vráti ho na zobrazenie. Sťahuje sa najprv do dočasného súboru a až po úspešnom
     * dokončení sa premenuje na finálny názov — čiastočne stiahnutý súbor sa tak nikdy
     * neuloží pod menom, ktoré appka považuje za "už stiahnuté".
     * Vráti null pri akejkoľvek chybe (žiadny internet, zlá URL, timeout...) — appka na to
     * nesmie spadnúť, len ostane pri predvolenom obrázku.
     */
    private static Bitmap downloadAndCache(Context appCtx, String exerciseName, String imageUrl) {
        File finalFile = cacheFileFor(appCtx, exerciseName);
        // Unikátny dočasný súbor — viac cvikov zdieľa ten istý obrázok a môžu sa sťahovať súbežne
        File tmpFile = new File(appCtx.getFilesDir(), finalFile.getName() + "." + System.nanoTime() + ".tmp");
        HttpURLConnection conn = null;
        try {
            URL url = new URL(imageUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(15_000);
            conn.setInstanceFollowRedirects(true);

            try (InputStream in = conn.getInputStream();
                 OutputStream out = new FileOutputStream(tmpFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            }

            if (!tmpFile.renameTo(finalFile)) return null;
            return decodeSampled(finalFile);
        } catch (IOException | RuntimeException e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
            if (tmpFile.exists()) tmpFile.delete(); // po úspešnom rename už neexistuje; po chybe upracce zvyšky
        }
    }

    /** Bezpečný, čitateľný názov súboru odvodený z názvu cviku (bez diakritiky/medzier/špeciálnych znakov). */
    private static String sanitize(String exerciseName) {
        String normalized = ExerciseDefinition.normalize(exerciseName); // malé písmená, bez diakritiky
        StringBuilder sb = new StringBuilder();
        for (char ch : normalized.toCharArray()) {
            if (Character.isLetterOrDigit(ch)) {
                sb.append(ch);
            } else if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '_') {
                sb.append('_');
            }
        }
        String result = sb.toString();
        while (result.endsWith("_")) result = result.substring(0, result.length() - 1);
        return result.isEmpty() ? "unknown" : result;
    }
}
