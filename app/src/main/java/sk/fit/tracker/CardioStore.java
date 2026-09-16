package sk.fit.tracker;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Ukladá kardio záznamy (bežecký pás, bicykel...) pre daný deň.
 * Kľúč: "yyyy-MM-dd" -> "25.5|2.24|5.3|3|159;30|3.1|6.2|0|210" (viac záznamov oddelených ";")
 */
public class CardioStore {

    private static final String PREFS_NAME = "FitTrackerCardio";

    public static List<CardioSession> getSessions(Context ctx, String dateKey) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String raw = prefs.getString(dateKey, "");
        List<CardioSession> list = new ArrayList<>();
        if (!raw.isEmpty()) {
            for (String s : raw.split(";")) {
                CardioSession cs = CardioSession.parse(s);
                if (cs != null) list.add(cs);
            }
        }
        return list;
    }

    public static void setSessions(Context ctx, String dateKey, List<CardioSession> sessions) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        if (sessions.isEmpty()) {
            editor.remove(dateKey);
        } else {
            StringBuilder sb = new StringBuilder();
            for (CardioSession cs : sessions) {
                if (sb.length() > 0) sb.append(";");
                sb.append(cs.serialize());
            }
            editor.putString(dateKey, sb.toString());
        }
        // commit() namiesto apply() — konzistentné s ExerciseStore/ActivityStore, synchrónny zápis.
        editor.commit();
    }

    /** Kardio záznamy z posledného dňa PRED daným dátumom, kde sa kardio robilo (pre "naposledy" hint). */
    public static List<CardioSession> getLastSessionBefore(Context ctx, String beforeDateExclusive) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        TreeMap<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
            if (e.getValue() instanceof String) sorted.put(e.getKey(), (String) e.getValue());
        }

        String bestRaw = null;
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            if (beforeDateExclusive != null && e.getKey().compareTo(beforeDateExclusive) >= 0) continue;
            bestRaw = e.getValue(); // chronologicky zoradené — posledný vyhovujúci je najnovší
        }

        List<CardioSession> list = new ArrayList<>();
        if (bestRaw != null) {
            for (String s : bestRaw.split(";")) {
                CardioSession cs = CardioSession.parse(s);
                if (cs != null) list.add(cs);
            }
        }
        return list;
    }
}
