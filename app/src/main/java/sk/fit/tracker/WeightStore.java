package sk.fit.tracker;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Map;
import java.util.TreeMap;

/** História váhy používateľa — jeden záznam na deň ("yyyy-MM-dd" -> kg), keďže sa časom mení. */
public class WeightStore {

    private static final String PREFS_NAME = "FitTrackerWeight";

    public static void setWeight(Context ctx, String dateKey, float weightKg) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putFloat(dateKey, weightKg).commit();
    }

    public static void removeWeight(Context ctx, String dateKey) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().remove(dateKey).commit();
    }

    /** Všetky záznamy chronologicky zoradené (najstarší prvý). */
    public static TreeMap<String, Float> getAllSorted(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        TreeMap<String, Float> sorted = new TreeMap<>();
        for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
            if (e.getValue() instanceof Float) sorted.put(e.getKey(), (Float) e.getValue());
        }
        return sorted;
    }

    /** Najnovšia zaznamenaná váha (napr. pre výpočet kalórií), alebo 0 ak zatiaľ žiadna. */
    public static float getLatestWeight(Context ctx) {
        TreeMap<String, Float> all = getAllSorted(ctx);
        return all.isEmpty() ? 0f : all.lastEntry().getValue();
    }
}
