package sk.fit.tracker;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Subjektívne hodnotenie tréningového dňa ("Ako sa dnes cítim") — číselná škála 1 (kríza/choroba)
 * až 5 (top forma) plus voľná textová poznámka ku kondícii (napr. "spal som len 5 hodín").
 * Jeden záznam na deň, rovnaký vzor ako WeightStore/BodyMeasurementStore — nezávislé od odtrénovaných sérií,
 * len dopĺňa kontext k tomu, prečo v daný deň klesol/stúpol výkon.
 */
public class WorkoutFeelingStore {

    private static final String PREFS_NAME = "FitTrackerWorkoutFeeling";
    private static final String NOTE_SUFFIX = "_note";

    private WorkoutFeelingStore() {}

    /** 1 (kríza) — 5 (top forma), alebo 0 ak pre daný deň zatiaľ nie je zaznamenané. */
    public static int getRating(Context ctx, String dateKey) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(dateKey, 0);
    }

    public static void setRating(Context ctx, String dateKey, int rating) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (rating <= 0) {
            prefs.edit().remove(dateKey).commit();
        } else {
            prefs.edit().putInt(dateKey, Math.min(rating, 5)).commit();
        }
    }

    /** Voľná poznámka ku kondícii pre daný deň, alebo "" ak žiadna. */
    public static String getNote(Context ctx, String dateKey) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(dateKey + NOTE_SUFFIX, "");
    }

    public static void setNote(Context ctx, String dateKey, String note) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (note == null || note.trim().isEmpty()) {
            prefs.edit().remove(dateKey + NOTE_SUFFIX).commit();
        } else {
            prefs.edit().putString(dateKey + NOTE_SUFFIX, note.trim()).commit();
        }
    }

    /** Krátky popisok škály 1-5 pre zobrazenie (emoji + slovo). */
    public static String label(int rating) {
        switch (rating) {
            case 1: return "😣 Kríza";
            case 2: return "😕 Slabý deň";
            case 3: return "😐 Priemer";
            case 4: return "🙂 Dobrý";
            case 5: return "💪 Top forma";
            default: return "";
        }
    }
}
