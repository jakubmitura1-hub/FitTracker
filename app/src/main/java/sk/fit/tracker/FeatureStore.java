package sk.fit.tracker;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Režim appky a zapnuté pomocné funkcie. Začiatočník (Sprievodca) má všetko, skúsený
 * (Len zapisovanie) má appku bez sprievodcu a vysvetliviek — rýchle zapisovanie sérií.
 * Jednotlivé funkcie sa dajú prepnúť aj samostatne v Profile (režim je potom "Vlastné").
 *
 * Predvolene (aj pre existujúcich používateľov) je všetko zapnuté = Sprievodca.
 */
public final class FeatureStore {

    public static final String MODE_GUIDE = "guide";
    public static final String MODE_PRO = "pro";
    public static final String MODE_CUSTOM = "custom";

    /** Odpočet pauzy medzi sériami (RestTimerBar + auto-štart). */
    public static final String REST_TIMER = "rest_timer";
    /** RPE (náročnosť) pri každej sérii. */
    public static final String RPE = "rpe";
    /** Postupy cvikov a vysvetlivky v detaile cviku. */
    public static final String GUIDES = "guides";
    /** Fokus partií: pripomienka v Štatistikách, filter v katalógu, karta v Profile. */
    public static final String FOCUS = "focus";

    public static final String[] ALL = {REST_TIMER, RPE, GUIDES, FOCUS};

    private static final String PREFS = "FitTrackerFeatures";
    private static final String KEY_MODE = "mode";

    private FeatureStore() {}

    public static boolean isEnabled(Context ctx, String feature) {
        return prefs(ctx).getBoolean(feature, true);
    }

    /** Ručné prepnutie jednej funkcie — režim sa tým stáva "Vlastné" (ak nesedí s predvoľbou). */
    public static void setEnabled(Context ctx, String feature, boolean enabled) {
        prefs(ctx).edit().putBoolean(feature, enabled).apply();
        prefs(ctx).edit().putString(KEY_MODE, detectMode(ctx)).apply();
    }

    /** Nastaví predvoľbu: Sprievodca = všetko zapnuté, Len zapisovanie = všetko vypnuté. */
    public static void applyMode(Context ctx, String mode) {
        boolean on = MODE_GUIDE.equals(mode);
        SharedPreferences.Editor e = prefs(ctx).edit();
        for (String f : ALL) e.putBoolean(f, on);
        e.putString(KEY_MODE, on ? MODE_GUIDE : MODE_PRO).apply();
    }

    public static String getMode(Context ctx) {
        return prefs(ctx).getString(KEY_MODE, MODE_GUIDE);
    }

    /** Či si používateľ režim už vybral (pri prvom spustení sa pýta). */
    public static boolean isModeChosen(Context ctx) {
        return prefs(ctx).contains(KEY_MODE);
    }

    private static String detectMode(Context ctx) {
        boolean allOn = true, allOff = true;
        for (String f : ALL) {
            boolean v = isEnabled(ctx, f);
            allOn &= v;
            allOff &= !v;
        }
        return allOn ? MODE_GUIDE : allOff ? MODE_PRO : MODE_CUSTOM;
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
