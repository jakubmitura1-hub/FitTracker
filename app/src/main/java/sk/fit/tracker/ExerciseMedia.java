package sk.fit.tracker;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.ImageView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Obrázky, cieľové svaly a postup pre zabudované cviky — z voľnej databázy free-exercise-db
 * (github.com/yuhonas/free-exercise-db, licencia The Unlicense = public domain).
 *
 * Do appky je pribalený len malý textový súbor assets/exercise_media.json (slovenský názov
 * cviku -> id v databáze + svaly + postup). Samotné fotky sa sťahujú až pri prvom zobrazení
 * a natrvalo ukladajú cez ExerciseImageCache — inštalačný balík tak ostáva malý.
 *
 * Kľúče svalov sú zhodné s MuscleMapView (ten ich preberá priamo z tejto databázy).
 */
public class ExerciseMedia {

    private static final String ASSET_FILE = "exercise_media.json";
    private static final String IMAGE_BASE_URL =
            "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/";

    /** Premenované cviky: nové meno -> pôvodné meno, pod ktorým je cvik v exercise_media.json. */
    private static final String ALIAS_PREFS = "FitTrackerMediaAliases";

    public static class Info {
        public final String id;
        public final int imageCount;
        public final List<String> primaryMuscles;
        public final List<String> secondaryMuscles;
        public final String equipment; // môže byť null
        public final List<String> instructions; // anglicky, tak ako sú v databáze
        public final List<String> instructionsSk; // slovenský preklad (môže byť prázdny)

        Info(String id, int imageCount, List<String> primaryMuscles, List<String> secondaryMuscles,
             String equipment, List<String> instructions, List<String> instructionsSk) {
            this.id = id;
            this.imageCount = imageCount;
            this.primaryMuscles = primaryMuscles;
            this.secondaryMuscles = secondaryMuscles;
            this.equipment = equipment;
            this.instructions = instructions;
            this.instructionsSk = instructionsSk;
        }

        /** URL obrázku (0 = východisková poloha, 1 = koncová poloha), alebo null ak neexistuje. */
        public String imageUrl(int index) {
            if (index < 0 || index >= imageCount) return null;
            return IMAGE_BASE_URL + id + "/" + index + ".jpg";
        }

        /** Kľúč pre ExerciseImageCache — viac cvikov môže zdieľať ten istý obrázok, stiahne sa raz. */
        public String imageCacheKey(int index) {
            return "fedb_" + id + "_" + index;
        }
    }

    private static Map<String, Info> byName;

    private ExerciseMedia() {}

    /** Info pre cvik podľa slovenského názvu (aj po premenovaní v katalógu), alebo null. */
    public static Info get(Context ctx, String exerciseName) {
        if (exerciseName == null) return null;
        Map<String, Info> all = load(ctx);
        Info info = all.get(exerciseName);
        if (info != null) return info;
        String original = aliases(ctx).getString(exerciseName, null);
        return original != null ? all.get(original) : null;
    }

    /**
     * Nastaví do ImageView siluetu svalovej partie a spustí načítanie fotky cviku (stiahne sa raz,
     * potom offline). Keď fotka dorazí, vyplní celú plochu (CENTER_CROP). Cviky bez fotky
     * (vlastné) ostanú pri siluete. imageIndex: 0 = východisková, 1 = koncová poloha.
     */
    public static void bindPhoto(Context ctx, ImageView iv, String exerciseName, String categoryKey, int imageIndex) {
        int muscleRes = categoryKey != null ? Category.muscleDrawableFor(categoryKey) : 0;
        int pad = UiKit.dp(ctx, 20);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setPadding(pad, pad, pad, pad);
        if (muscleRes != 0) iv.setImageDrawable(Category.muscleIcon(ctx, categoryKey));
        else iv.setImageDrawable(null);

        Info media = get(ctx, exerciseName);
        String url = media != null ? media.imageUrl(imageIndex) : null;
        if (url == null) {
            iv.setTag(null); // zruší prípadné ešte bežiace načítanie pre recyklovanú kartu
            return;
        }
        // ImageView sa odovzdá až pri použití — callback tak nedrží v pamäti zatvorenú obrazovku
        ExerciseImageCache.loadInto(ctx, media.imageCacheKey(imageIndex), url, iv, view -> {
            view.setScaleType(ImageView.ScaleType.CENTER_CROP);
            view.setPadding(0, 0, 0, 0);
        });
    }

    /** Slovenský názov náradia z free-exercise-db. */
    public static String equipmentLabel(String equipment) {
        if (equipment == null) return "—";
        switch (equipment) {
            case "barbell": return "veľká činka";
            case "dumbbell": return "jednoručky";
            case "machine": return "stroj";
            case "cable": return "kladka";
            case "body only": return "vlastná váha";
            case "e-z curl bar": return "EZ činka";
            case "kettlebells": return "kettlebell";
            case "bands": return "gumy";
            case "exercise ball": return "fitlopta";
            default: return "iné";
        }
    }

    /**
     * Zapamätá si, že cvik bol v katalógu premenovaný — obrázky a svaly sa tak nestratia.
     * Volá sa z ExerciseCatalog.editEntry.
     */
    public static void rename(Context ctx, String oldName, String newName) {
        if (oldName == null || newName == null || oldName.equals(newName)) return;
        SharedPreferences prefs = aliases(ctx);
        String original = load(ctx).containsKey(oldName) ? oldName : prefs.getString(oldName, null);
        if (original == null) return;
        prefs.edit().remove(oldName).putString(newName, original).apply();
    }

    /**
     * Načíta databázu cvikov (assets JSON, ~160 KB) vopred na pozadí — inak by sa parsovala na hlavnom
     * vlákne pri prvom otvorení katalógu/detailu a obrazovka by sa na chvíľu zasekla. Volá MainActivity.
     */
    public static void preloadInBackground(Context ctx) {
        Context appCtx = ctx.getApplicationContext();
        new Thread(() -> load(appCtx), "preload-exercise-media").start();
    }

    private static SharedPreferences aliases(Context ctx) {
        return ctx.getSharedPreferences(ALIAS_PREFS, Context.MODE_PRIVATE);
    }

    private static synchronized Map<String, Info> load(Context ctx) {
        if (byName != null) return byName;
        Map<String, Info> map = new HashMap<>();
        try (InputStream in = ctx.getAssets().open(ASSET_FILE)) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) != -1) buf.write(chunk, 0, n);
            JSONObject root = new JSONObject(new String(buf.toByteArray(), StandardCharsets.UTF_8));

            JSONArray names = root.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String name = names.getString(i);
                    JSONObject o = root.getJSONObject(name);
                    map.put(name, new Info(
                            o.getString("id"),
                            o.optInt("images", 0),
                            strings(o.optJSONArray("primary")),
                            strings(o.optJSONArray("secondary")),
                            o.isNull("equipment") ? null : o.optString("equipment", null),
                            strings(o.optJSONArray("instructions")),
                            strings(o.optJSONArray("instructions_sk"))));
                }
            }
        } catch (Exception e) {
            // Poškodený/chýbajúci súbor nesmie zhodiť appku — cviky sa len zobrazia bez obrázkov
        }
        byName = Collections.unmodifiableMap(map);
        return byName;
    }

    private static List<String> strings(JSONArray arr) {
        List<String> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) list.add(arr.optString(i));
        return list;
    }
}
