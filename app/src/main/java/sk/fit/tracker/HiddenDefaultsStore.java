package sk.fit.tracker;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * Zoznam "skrytých" zabudovaných (default) cvikov podľa partie — používa sa, keď si používateľ
 * v katalógovom editore premenuje, prekategorizuje alebo zmaže zabudovaný cvik. Zabudované cviky
 * sú napevno v Java kóde (ExerciseCatalog), takže sa nedajú reálne zmazať/premenovať — namiesto
 * toho sa ich pôvodné meno pridá sem, aby sa v danej partii prestalo ponúkať ako návrh. Samotná
 * história odtrénovaných sérií pod starým menom nie je týmto nijako dotknutá.
 */
public class HiddenDefaultsStore {

    private static final String PREFS_NAME = "FitTrackerHiddenDefaults";

    private HiddenDefaultsStore() {}

    public static boolean isHidden(Context ctx, String categoryKey, String name) {
        if (name == null) return false;
        for (String hidden : getHidden(ctx, categoryKey)) {
            if (hidden.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    public static List<String> getHidden(Context ctx, String categoryKey) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String raw = prefs.getString(categoryKey, "");
        List<String> list = new ArrayList<>();
        if (!raw.isEmpty()) {
            for (String s : raw.split("\\|")) {
                String t = s.trim();
                if (!t.isEmpty()) list.add(t);
            }
        }
        return list;
    }

    public static void hide(Context ctx, String categoryKey, String name) {
        if (name == null || name.trim().isEmpty()) return;
        List<String> hidden = getHidden(ctx, categoryKey);
        for (String h : hidden) if (h.equalsIgnoreCase(name)) return; // už skryté
        hidden.add(name.trim());
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(categoryKey, String.join("|", hidden)).apply();
    }
}
