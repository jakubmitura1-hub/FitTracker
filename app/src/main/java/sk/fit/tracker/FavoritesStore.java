package sk.fit.tracker;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/** Obľúbené cviky (záložka v katalógu) — množina slovenských názvov cvikov v SharedPreferences. */
public class FavoritesStore {

    private static final String PREFS_NAME = "FitTrackerFavorites";
    private static final String KEY = "favorites";

    private FavoritesStore() {}

    public static boolean isFavorite(Context ctx, String exerciseName) {
        return getAll(ctx).contains(exerciseName);
    }

    /** Prepne stav a vráti nový (true = je obľúbený). */
    public static boolean toggle(Context ctx, String exerciseName) {
        Set<String> set = getAll(ctx);
        boolean nowFavorite = !set.remove(exerciseName);
        if (nowFavorite) set.add(exerciseName);
        prefs(ctx).edit().putStringSet(KEY, set).apply();
        return nowFavorite;
    }

    /** Pri premenovaní cviku v katalógu prenesie aj jeho záložku (volá ExerciseCatalog.editEntry). */
    public static void rename(Context ctx, String oldName, String newName) {
        Set<String> set = getAll(ctx);
        if (set.remove(oldName)) {
            set.add(newName);
            prefs(ctx).edit().putStringSet(KEY, set).apply();
        }
    }

    /** Vždy nová (meniteľná) kópia — množinu vrátenú zo SharedPreferences sa nesmie meniť priamo. */
    public static Set<String> getAll(Context ctx) {
        return new HashSet<>(prefs(ctx).getStringSet(KEY, new HashSet<>()));
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
