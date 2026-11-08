package sk.fit.tracker;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Svaly, na ktoré sa chce používateľ sústrediť (výber v OnboardingActivity), a či už onboarding
 * prebehol. Kľúče svalov sú rovnaké ako v MuscleMapView / free-exercise-db.
 */
public final class FocusStore {

    private static final String PREFS_NAME = "FitTrackerFocus";
    private static final String KEY_MUSCLES = "muscles";
    private static final String KEY_DONE = "onboarding_done";

    /** Partia na výber v onboardingu: popis + svaly, ktoré pokrýva. */
    public static final class Area {
        public final String label;
        public final List<String> muscles;

        Area(String label, String... muscles) {
            this.label = label;
            this.muscles = Arrays.asList(muscles);
        }
    }

    public static final List<Area> AREAS = Arrays.asList(
            new Area("Hrudník", MuscleMapView.CHEST),
            new Area("Chrbát", MuscleMapView.LATS, MuscleMapView.MIDDLE_BACK, MuscleMapView.LOWER_BACK, MuscleMapView.TRAPS),
            new Area("Ramená", MuscleMapView.SHOULDERS),
            new Area("Ruky", MuscleMapView.BICEPS, MuscleMapView.TRICEPS, MuscleMapView.FOREARMS),
            new Area("Brucho", MuscleMapView.ABDOMINALS),
            new Area("Nohy", MuscleMapView.QUADRICEPS, MuscleMapView.HAMSTRINGS, MuscleMapView.CALVES,
                    MuscleMapView.ADDUCTORS, MuscleMapView.ABDUCTORS),
            new Area("Zadok", MuscleMapView.GLUTES)
    );

    private FocusStore() {}

    /** Všetky svaly, ktoré sa dajú vybrať (zjednotenie AREAS + krk). */
    public static List<String> allMuscles() {
        Set<String> all = new LinkedHashSet<>();
        for (Area a : AREAS) all.addAll(a.muscles);
        all.add(MuscleMapView.NECK);
        return new ArrayList<>(all);
    }

    public static Set<String> getMuscles(Context ctx) {
        return new HashSet<>(prefs(ctx).getStringSet(KEY_MUSCLES, new HashSet<>()));
    }

    /** Uloží výber a označí onboarding za hotový. */
    public static void save(Context ctx, Set<String> muscles) {
        prefs(ctx).edit()
                .putStringSet(KEY_MUSCLES, new HashSet<>(muscles))
                .putBoolean(KEY_DONE, true)
                .apply();
    }

    /** Onboarding preskočený — výber ostane prázdny, ale znova sa už neponúkne. */
    public static void markDone(Context ctx) {
        prefs(ctx).edit().putBoolean(KEY_DONE, true).apply();
    }

    public static boolean isOnboardingDone(Context ctx) {
        return prefs(ctx).getBoolean(KEY_DONE, false);
    }

    /** Krátky popis výberu: celé partie ich názvom, zvyšné svaly samostatne (napr. "Hrudník, Ruky, Lýtka"). */
    public static String describe(Set<String> muscles) {
        if (muscles.isEmpty()) return "nič nevybraté";
        if (muscles.containsAll(allMuscles())) return "Celé telo";
        Set<String> rest = new LinkedHashSet<>(muscles);
        List<String> parts = new ArrayList<>();
        for (Area a : AREAS) {
            if (muscles.containsAll(a.muscles)) {
                parts.add(a.label);
                rest.removeAll(a.muscles);
            }
        }
        for (String m : rest) parts.add(MuscleMapView.label(m));
        return String.join(", ", parts);
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
