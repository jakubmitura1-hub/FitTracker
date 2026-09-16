package sk.fit.tracker;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.Iterator;
import java.util.Map;

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

    /** Obnoví dáta zo zálohy — PREPÍŠE aktuálne dáta appky obsahom súboru. */
    public static void restore(Context ctx, String json) throws Exception {
        JSONObject root = new JSONObject(json);
        for (String prefsName : PREFS_TO_BACKUP) {
            if (!root.has(prefsName)) continue;
            JSONObject prefsJson = root.getJSONObject(prefsName);
            SharedPreferences.Editor editor = ctx.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit();
            editor.clear();

            Iterator<String> keys = prefsJson.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject typed = prefsJson.getJSONObject(key);
                String type = typed.optString("t", "s");
                switch (type) {
                    case "f": editor.putFloat(key, (float) typed.getDouble("v")); break;
                    case "l": editor.putLong(key, typed.getLong("v")); break;
                    case "i": editor.putInt(key, typed.getInt("v")); break;
                    case "b": editor.putBoolean(key, typed.getBoolean("v")); break;
                    default:  editor.putString(key, typed.getString("v")); break;
                }
            }
            editor.commit();
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
        } else {
            return null; // neznámy typ (napr. StringSet) — appka ho nikde nepoužíva
        }
        return typed;
    }
}
