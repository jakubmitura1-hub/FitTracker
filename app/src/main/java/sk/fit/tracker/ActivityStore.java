package sk.fit.tracker;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Ukladá pre každý dátum (yyyy-MM-dd) zoznam kategórií aktivít,
 * ktoré sa v ten deň udiali. Formát uloženia: "legs,back,intimacy"
 */
public class ActivityStore {

    private static final String PREFS_NAME = "FitTrackerData";
    private static final SimpleDateFormat KEY_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    public static String dateKey(Calendar cal) {
        return KEY_FORMAT.format(cal.getTime());
    }

    public static String todayKey() {
        return dateKey(Calendar.getInstance());
    }

    public static Set<String> getCategoriesForDate(Context ctx, String dateKey) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String raw = prefs.getString(dateKey, "");
        Set<String> set = new HashSet<>();
        if (!raw.isEmpty()) {
            for (String s : raw.split(",")) {
                if (!s.trim().isEmpty()) set.add(s.trim());
            }
        }
        return set;
    }

    public static void setCategoriesForDate(Context ctx, String dateKey, Set<String> categories) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        if (categories.isEmpty()) {
            editor.remove(dateKey);
        } else {
            editor.putString(dateKey, String.join(",", categories));
        }
        // commit() namiesto apply() — DayDetailActivity.saveAndClose() sa spolieha na synchrónny
        // zápis pred finish(), aby sa dáta pri páde appky hneď po zatvorení obrazovky nestratili.
        editor.commit();
    }

    /** Vráti všetky uložené záznamy zoradené podľa dátumu (najstarší prvý). */
    public static Map<String, Set<String>> getAllEntries(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Map<String, ?> all = prefs.getAll();
        TreeMap<String, Set<String>> result = new TreeMap<>();
        for (Map.Entry<String, ?> e : all.entrySet()) {
            String key = e.getKey();
            // Validuj formát dátumu yyyy-MM-dd
            if (key.length() == 10 && key.charAt(4) == '-' && key.charAt(7) == '-') {
                Object val = e.getValue();
                if (val instanceof String) {
                    Set<String> set = new HashSet<>();
                    for (String s : ((String) val).split(",")) {
                        if (!s.trim().isEmpty()) set.add(s.trim());
                    }
                    result.put(key, set);
                }
            }
        }
        return result;
    }

    /** Vráti dátum (yyyy-MM-dd) posledného výskytu danej kategórie, alebo null. */
    public static String lastDateForCategory(Context ctx, String categoryKey) {
        Map<String, Set<String>> all = getAllEntries(ctx);
        String last = null;
        for (Map.Entry<String, Set<String>> e : all.entrySet()) {
            if (e.getValue().contains(categoryKey)) {
                last = e.getKey(); // TreeMap je zoradený, posledný vyhovujúci = najnovší
            }
        }
        return last;
    }

    /** Počet dní od posledného výskytu kategórie (0 = dnes), alebo -1 ak nikdy. */
    public static int daysSince(String dateKey) {
        if (dateKey == null) return -1;
        try {
            java.util.Date d = KEY_FORMAT.parse(dateKey);
            if (d == null) return -1;
            long diffMs = System.currentTimeMillis() - d.getTime();
            // Normalizuj na celé dni (ignoruj čas, len kalendárny rozdiel)
            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);
            Calendar then = Calendar.getInstance();
            then.setTime(d);
            then.set(Calendar.HOUR_OF_DAY, 0);
            then.set(Calendar.MINUTE, 0);
            then.set(Calendar.SECOND, 0);
            then.set(Calendar.MILLISECOND, 0);
            long days = (today.getTimeInMillis() - then.getTimeInMillis()) / (24L * 60 * 60 * 1000);
            return (int) days;
        } catch (Exception e) {
            return -1;
        }
    }

    /** Počet výskytov kategórie za posledných N dní (vrátane dneška). */
    public static int countInLastDays(Context ctx, String categoryKey, int days) {
        Map<String, Set<String>> all = getAllEntries(ctx);
        int count = 0;
        for (Map.Entry<String, Set<String>> e : all.entrySet()) {
            if (e.getValue().contains(categoryKey)) {
                int ds = daysSince(e.getKey());
                if (ds >= 0 && ds < days) count++;
            }
        }
        return count;
    }

    /**
     * Počet tréningových dní danej kategórie v každom z posledných `weeks` týždňov
     * (7-dňové klopové okná, index 0 = tento týždeň, posledný index = najstarší).
     */
    public static List<Integer> weeklyCounts(Context ctx, String categoryKey, int weeks) {
        Integer[] counts = new Integer[weeks];
        java.util.Arrays.fill(counts, 0);

        Map<String, Set<String>> all = getAllEntries(ctx);
        for (Map.Entry<String, Set<String>> e : all.entrySet()) {
            if (!e.getValue().contains(categoryKey)) continue;
            int ds = daysSince(e.getKey());
            if (ds < 0) continue;
            int weekIdx = ds / 7;
            if (weekIdx < weeks) counts[weekIdx]++;
        }
        return new ArrayList<>(java.util.Arrays.asList(counts));
    }

    /** Či mal daný deň zaznamenanú aspoň jednu tréningovú (nie intímnu) kategóriu. */
    public static boolean hasWorkoutOnDate(Context ctx, String dateKey) {
        for (String key : getCategoriesForDate(ctx, dateKey)) {
            Category cat = Category.byKey(key);
            if (cat != null && Category.GROUP_WORKOUT.equals(cat.group)) return true;
        }
        return false;
    }

    /** Či je daný deň súčasťou súvislej tréningovej série (streak) — má tréning a susedný deň tiež. */
    public static boolean isWorkoutStreakDay(Context ctx, String dateKey) {
        if (!hasWorkoutOnDate(ctx, dateKey)) return false;
        return hasWorkoutOnDate(ctx, shiftDateKey(dateKey, -1)) || hasWorkoutOnDate(ctx, shiftDateKey(dateKey, 1));
    }

    private static String shiftDateKey(String dateKey, int days) {
        try {
            Calendar cal = Calendar.getInstance();
            cal.setTime(KEY_FORMAT.parse(dateKey));
            cal.add(Calendar.DAY_OF_MONTH, days);
            return dateKey(cal);
        } catch (Exception e) {
            return dateKey;
        }
    }

    /** Zoznam dátumov (yyyy-MM-dd) za posledných N dní kde sa kategória vyskytla, najnovšie prvé. */
    public static List<String> datesInLastDays(Context ctx, String categoryKey, int days) {
        Map<String, Set<String>> all = getAllEntries(ctx);
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, Set<String>> e : all.entrySet()) {
            if (e.getValue().contains(categoryKey)) {
                int ds = daysSince(e.getKey());
                if (ds >= 0 && ds < days) result.add(e.getKey());
            }
        }
        java.util.Collections.sort(result, java.util.Collections.reverseOrder());
        return result;
    }
}
