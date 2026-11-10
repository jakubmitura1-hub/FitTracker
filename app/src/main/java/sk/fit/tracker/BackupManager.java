package sk.fit.tracker;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Export/import všetkých dát appky (kategórie po dňoch, cviky, kardio, vlastné cviky,
 * profil) do jedného JSON súboru. Používateľ si súbor uloží/otvorí cez systémový výber
 * súboru (Storage Access Framework) — žiadne Google API, žiadny internet.
 */
public class BackupManager {

    private static final String[] PREFS_TO_BACKUP = {
            "FitTrackerData",            // ActivityStore
            "FitTrackerExercises",       // ExerciseStore
            "FitTrackerCardio",          // CardioStore
            "FitTrackerCustomExercises", // ExerciseCatalog
            "FitTrackerProfile",         // ProfileStore
            "FitTrackerWorkoutTime",     // WorkoutTimeStore
            "FitTrackerWeight",          // WeightStore
            "FitTrackerWaist",           // BodyMeasurementStore — obvod pása
            "FitTrackerChest",           // BodyMeasurementStore — obvod hrudníka
            "FitTrackerBiceps",          // BodyMeasurementStore — obvod bicepsu
            "FitTrackerThigh",           // BodyMeasurementStore — obvod stehna
            "FitTrackerExerciseNotes",   // ExerciseNotesStore
            "FitTrackerHiddenDefaults",  // HiddenDefaultsStore
            "FitTrackerWorkoutFeeling",  // WorkoutFeelingStore
            "FitTrackerFavorites",       // FavoritesStore — obľúbené cviky
            "FitTrackerMediaAliases",    // ExerciseMedia — premenované cviky -> pôvodný záznam s fotkou
            "FitTrackerFocus",           // FocusStore — partie z onboardingu
            "FitTrackerFeatures",        // FeatureStore — režim appky a zapnuté pomôcky
    };

    private BackupManager() {}

    public static String export(Context ctx) throws Exception {
        JSONObject root = new JSONObject();
        root.put("app", "FitTracker");
        root.put("version", 1);
        root.put("exportedAt", System.currentTimeMillis());

        for (String prefsName : PREFS_TO_BACKUP) {
            SharedPreferences prefs = ctx.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
            JSONObject prefsJson = new JSONObject();
            for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                JSONObject typed = typedValue(entry.getValue());
                if (typed != null) prefsJson.put(entry.getKey(), typed);
            }
            root.put(prefsName, prefsJson);
        }
        return root.toString(2);
    }

    /** Najvyššia verzia formátu zálohy, ktorú táto verzia appky vie obnoviť. */
    private static final int SUPPORTED_VERSION = 1;

    /** Záchranná kópia dát spred poslednej obnovy (interné úložisko appky, nie cache — nezmaže sa sama). */
    private static final String SAFETY_COPY_FILE = "fittracker_pred_obnovou.json";

    /** Súbor nie je platná záloha FitTrackeru — nič sa nezapísalo, dáta appky ostali nezmenené. */
    public static class InvalidBackupException extends Exception {
        InvalidBackupException(String message) {
            super(message);
        }
    }

    /**
     * Obnoví dáta zo zálohy — PREPÍŠE aktuálne dáta appky obsahom súboru. Postup "všetko alebo nič":
     *  1) celý súbor sa najprv načíta a overí (InvalidBackupException = nič sa nezmenilo),
     *  2) aktuálne dáta sa uložia ako záchranná kópia (dá sa k nim vrátiť cez undoLastRestore),
     *  3) až potom sa zapisuje; ak by zápis zlyhal v polovici, pôvodné dáta sa vrátia zo záchrannej kópie.
     * Časti, ktoré záloha neobsahuje (napr. staršia záloha bez novších nastavení), ostanú nedotknuté.
     */
    public static void restore(Context ctx, String json) throws Exception {
        Map<String, Map<String, Object>> data = parseAndValidate(json);

        String current = export(ctx);
        writeSafetyCopy(ctx, current);

        try {
            writeAll(ctx, data);
        } catch (Exception e) {
            writeAll(ctx, parseAndValidate(current));
            throw new java.io.IOException("Zápis dát zlyhal, pôvodné dáta boli vrátené.", e);
        }
    }

    /** Či existuje záchranná kópia dát spred poslednej obnovy. */
    public static boolean hasSafetyCopy(Context ctx) {
        return safetyCopyFile(ctx).exists();
    }

    /** Kedy vznikla záchranná kópia (millis), 0 ak neexistuje. */
    public static long safetyCopyTime(Context ctx) {
        File f = safetyCopyFile(ctx);
        return f.exists() ? f.lastModified() : 0L;
    }

    /** Vráti dáta do stavu pred poslednou obnovou zo zálohy a záchrannú kópiu potom zmaže. */
    public static void undoLastRestore(Context ctx) throws Exception {
        File f = safetyCopyFile(ctx);
        if (!f.exists()) throw new InvalidBackupException("Záchranná kópia neexistuje.");
        String json;
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) != -1) buf.write(chunk, 0, n);
            json = new String(buf.toByteArray(), StandardCharsets.UTF_8);
        }
        writeAll(ctx, parseAndValidate(json));
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    private static File safetyCopyFile(Context ctx) {
        return new File(ctx.getFilesDir(), SAFETY_COPY_FILE);
    }

    private static void writeSafetyCopy(Context ctx, String json) throws Exception {
        try (OutputStream out = new FileOutputStream(safetyCopyFile(ctx))) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Načíta a overí celý obsah zálohy bez akéhokoľvek zápisu. Vráti dáta pripravené na zápis
     * (názov SharedPreferences -> kľúč -> hodnota v pôvodnom type), alebo vyhodí
     * InvalidBackupException so zrozumiteľným popisom, čo je so súborom zle.
     */
    private static Map<String, Map<String, Object>> parseAndValidate(String json) throws InvalidBackupException {
        JSONObject root;
        try {
            root = new JSONObject(json);
        } catch (Exception e) {
            throw new InvalidBackupException("Súbor sa nedá prečítať — nie je to záloha FitTrackeru.");
        }
        if (!"FitTracker".equals(root.optString("app"))) {
            throw new InvalidBackupException("Tento súbor nie je záloha FitTrackeru.");
        }
        int version = root.optInt("version", -1);
        if (version < 1) {
            throw new InvalidBackupException("Záloha nemá platnú verziu formátu.");
        }
        if (version > SUPPORTED_VERSION) {
            throw new InvalidBackupException("Záloha je z novšej verzie appky — najprv appku aktualizuj.");
        }

        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (String prefsName : PREFS_TO_BACKUP) {
            if (!root.has(prefsName)) continue;
            JSONObject prefsJson = root.optJSONObject(prefsName);
            if (prefsJson == null) throw corrupted(prefsName);

            Map<String, Object> values = new LinkedHashMap<>();
            Iterator<String> keys = prefsJson.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject typed = prefsJson.optJSONObject(key);
                if (typed == null || !typed.has("v")) throw corrupted(prefsName);
                Object value = parseTypedValue(typed);
                if (value == null) throw corrupted(prefsName);
                // Pokazené dátumové kľúče v kalendári nevadia — ActivityStore.getAllEntries ich pri čítaní preskočí
                values.put(key, value);
            }
            result.put(prefsName, values);
        }
        if (result.isEmpty()) {
            throw new InvalidBackupException("Záloha neobsahuje žiadne dáta.");
        }
        return result;
    }

    private static InvalidBackupException corrupted(String prefsName) {
        return new InvalidBackupException("Záloha je poškodená (časť " + prefsName + ").");
    }

    /** Hodnota podľa typu "t" (rovnaké kódy ako typedValue), null ak typ alebo hodnota nesedí. */
    private static Object parseTypedValue(JSONObject typed) {
        Object v = typed.opt("v");
        switch (typed.optString("t", "s")) {
            case "f": return v instanceof Number ? (Object) ((Number) v).floatValue() : null;
            case "l": return v instanceof Number ? (Object) ((Number) v).longValue() : null;
            case "i": return v instanceof Number ? (Object) ((Number) v).intValue() : null;
            case "b": return v instanceof Boolean ? v : null;
            case "s": return v instanceof String ? v : null;
            case "ss": {
                if (!(v instanceof JSONArray)) return null;
                JSONArray arr = (JSONArray) v;
                Set<String> set = new HashSet<>();
                for (int i = 0; i < arr.length(); i++) {
                    Object item = arr.opt(i);
                    if (!(item instanceof String)) return null;
                    set.add((String) item);
                }
                return set;
            }
            default: return null;
        }
    }

    /** Zapíše už overené dáta; každá časť zálohy nahradí celý príslušný súbor SharedPreferences. */
    @SuppressWarnings("unchecked")
    private static void writeAll(Context ctx, Map<String, Map<String, Object>> data) throws java.io.IOException {
        for (Map.Entry<String, Map<String, Object>> section : data.entrySet()) {
            SharedPreferences.Editor editor = ctx.getSharedPreferences(section.getKey(), Context.MODE_PRIVATE).edit();
            editor.clear();
            for (Map.Entry<String, Object> e : section.getValue().entrySet()) {
                String key = e.getKey();
                Object v = e.getValue();
                if (v instanceof Float) editor.putFloat(key, (Float) v);
                else if (v instanceof Long) editor.putLong(key, (Long) v);
                else if (v instanceof Integer) editor.putInt(key, (Integer) v);
                else if (v instanceof Boolean) editor.putBoolean(key, (Boolean) v);
                else if (v instanceof Set) editor.putStringSet(key, (Set<String>) v);
                else editor.putString(key, (String) v);
            }
            if (!editor.commit()) {
                throw new java.io.IOException("Nepodarilo sa zapísať " + section.getKey());
            }
        }
    }

    /** Zapíše hodnotu spolu s jej typom ("t"), aby sa pri obnove vrátila do SharedPreferences ako pôvodný typ. */
    private static JSONObject typedValue(Object value) throws Exception {
        JSONObject typed = new JSONObject();
        if (value instanceof Float) {
            typed.put("t", "f");
            typed.put("v", ((Float) value).doubleValue());
        } else if (value instanceof Long) {
            typed.put("t", "l");
            typed.put("v", (long) (Long) value);
        } else if (value instanceof Integer) {
            typed.put("t", "i");
            typed.put("v", (int) (Integer) value);
        } else if (value instanceof Boolean) {
            typed.put("t", "b");
            typed.put("v", (boolean) (Boolean) value);
        } else if (value instanceof String) {
            typed.put("t", "s");
            typed.put("v", value);
        } else if (value instanceof java.util.Set) {
            typed.put("t", "ss");
            org.json.JSONArray arr = new org.json.JSONArray();
            for (Object o : (java.util.Set<?>) value) arr.put(String.valueOf(o));
            typed.put("v", arr);
        } else {
            return null; // neznámy typ
        }
        return typed;
    }
}
