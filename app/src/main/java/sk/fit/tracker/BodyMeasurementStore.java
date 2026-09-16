package sk.fit.tracker;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Map;
import java.util.TreeMap;

/**
 * Generická história telesného obvodu (v cm) podľa dňa — spoločný mechanizmus pre pás, hrudník,
 * biceps, stehno a pod. Každá miera má vlastný oddelený súbor SharedPreferences (podľa prefsName),
 * aby sa navzájom nemiešali a dali sa jednotlivo zálohovať/obnoviť (pozri BackupManager).
 */
public class BodyMeasurementStore {

    private final String prefsName;

    public BodyMeasurementStore(String prefsName) {
        this.prefsName = prefsName;
    }

    public void setCm(Context ctx, String dateKey, float cm) {
        ctx.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                .edit().putFloat(dateKey, cm).commit();
    }

    public void remove(Context ctx, String dateKey) {
        ctx.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                .edit().remove(dateKey).commit();
    }

    /** Všetky záznamy chronologicky zoradené (najstarší prvý). */
    public TreeMap<String, Float> getAllSorted(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
        TreeMap<String, Float> sorted = new TreeMap<>();
        for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
            if (e.getValue() instanceof Float) sorted.put(e.getKey(), (Float) e.getValue());
        }
        return sorted;
    }

    /** Najnovšie zaznamenaná hodnota, alebo 0 ak zatiaľ žiadna. */
    public float getLatest(Context ctx) {
        TreeMap<String, Float> all = getAllSorted(ctx);
        return all.isEmpty() ? 0f : all.lastEntry().getValue();
    }
}
