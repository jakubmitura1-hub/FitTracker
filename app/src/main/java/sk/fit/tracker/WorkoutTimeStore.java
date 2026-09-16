package sk.fit.tracker;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Odhadované trvanie silového tréningu (v minútach) za daný deň — vstup pre výpočet kalórií.
 * Meria sa od pridania prvej série v danej editačnej relácii po uloženie/zatvorenie obrazovky
 * a priebežne sa pripočítava (deň sa môže upravovať vo viacerých samostatných reláciách).
 */
public class WorkoutTimeStore {

    private static final String PREFS_NAME = "FitTrackerWorkoutTime";

    public static float getMinutes(Context ctx, String dateKey) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getFloat(dateKey, 0f);
    }

    public static void addMinutes(Context ctx, String dateKey, float minutesToAdd) {
        if (minutesToAdd <= 0) return;
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        float total = prefs.getFloat(dateKey, 0f) + minutesToAdd;
        prefs.edit().putFloat(dateKey, total).commit();
    }
}
