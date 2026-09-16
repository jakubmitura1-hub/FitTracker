package sk.fit.tracker;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Voľné textové poznámky ku cviku (napr. "robiť s podsadom, bolí zápästie pri nadhmate"),
 * uložené podľa presného názvu cviku — platí rovnako pre zabudované aj vlastné cviky,
 * nezávisle od toho, či je cvik práve default alebo custom. Nezávislé od histórie sérií.
 */
public class ExerciseNotesStore {

    private static final String PREFS_NAME = "FitTrackerExerciseNotes";

    private ExerciseNotesStore() {}

    public static String getNote(Context ctx, String exerciseName) {
        if (exerciseName == null) return "";
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(exerciseName, "");
    }

    public static void setNote(Context ctx, String exerciseName, String note) {
        if (exerciseName == null) return;
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (note == null || note.trim().isEmpty()) {
            prefs.edit().remove(exerciseName).commit();
        } else {
            prefs.edit().putString(exerciseName, note.trim()).commit();
        }
    }

    /** Presunie poznámku spod starého mena pod nové (napr. pri oprave preklepu v katalógu). */
    public static void rename(Context ctx, String oldName, String newName) {
        if (oldName == null || newName == null || oldName.equals(newName)) return;
        String note = getNote(ctx, oldName);
        if (!note.isEmpty()) {
            setNote(ctx, newName, note);
            setNote(ctx, oldName, null);
        }
    }
}
