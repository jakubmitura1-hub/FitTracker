package sk.fit.tracker;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

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
        Context appCtx = ctx.getApplicationContext();
        target.setTag(exerciseName);

        File cached = cacheFileFor(appCtx, exerciseName);
        if (cached.exists()) {
            new Thread(() -> {
                Bitmap bmp = BitmapFactory.decodeFile(cached.getAbsolutePath());
                postIfStillRelevant(target, exerciseName, bmp);
            }).start();
            return;
        }

        if (imageUrl == null || imageUrl.trim().isEmpty()) return; // nič na stiahnutie — ostáva predvolený obrázok

        new Thread(() -> {
            Bitmap bmp = downloadAndCache(appCtx, exerciseName, imageUrl);
            postIfStillRelevant(target, exerciseName, bmp);
        }).start();
    }

    private static void postIfStillRelevant(ImageView target, String exerciseName, Bitmap bmp) {
        if (bmp == null) return; // chyba/žiadne dáta — necháme predvolený obrázok, appka kvôli tomu nespadne
        new Handler(Looper.getMainLooper()).post(() -> {
            if (exerciseName.equals(target.getTag())) {
                target.setImageBitmap(bmp);
            }
        });
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
        File tmpFile = new File(appCtx.getFilesDir(), finalFile.getName() + ".tmp");
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
            return BitmapFactory.decodeFile(finalFile.getAbsolutePath());
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
