package sk.fit.tracker;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Ukladá cvičenia (zoznam Exercise s viacerými sériami) pre konkrétny dátum + kategóriu.
 * Kľúč: "yyyy-MM-dd#categoryKey" -> "Názov1::12x10,14x8;Názov2::20x12"
 */
public class ExerciseStore {

    private static final String PREFS_NAME = "FitTrackerExercises";

    private static String storeKey(String dateKey, String categoryKey) {
        return dateKey + "#" + categoryKey;
    }

    public static List<Exercise> getExercises(Context ctx, String dateKey, String categoryKey) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String raw = prefs.getString(storeKey(dateKey, categoryKey), "");
        List<Exercise> list = new ArrayList<>();
        if (!raw.isEmpty()) {
            for (String s : raw.split(";")) {
                Exercise e = Exercise.parse(s);
                if (e != null) list.add(e);
            }
        }
        return list;
    }

    public static void setExercises(Context ctx, String dateKey, String categoryKey, List<Exercise> exercises) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        String k = storeKey(dateKey, categoryKey);
        // Ulož len cvičenia ktoré majú aspoň jednu sériu
        List<Exercise> valid = new ArrayList<>();
        for (Exercise e : exercises) if (!e.sets.isEmpty()) valid.add(e);

        if (valid.isEmpty()) {
            editor.remove(k);
        } else {
            StringBuilder sb = new StringBuilder();
            for (Exercise e : valid) {
                if (sb.length() > 0) sb.append(";");
                sb.append(e.serialize());
            }
            editor.putString(k, sb.toString());
        }
        // commit() namiesto apply() — synchrónny zápis, aby tréning neprišiel nazmar
        // pri páde appky tesne po uložení (apply() zapisuje na disk asynchrónne na pozadí).
        editor.commit();
    }

    /**
     * Vráti všetky uložené záznamy zoradené chronologicky (kľúč "date#cat" -> raw string).
     */
    private static TreeMap<String, String> getAllRawSorted(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Map<String, ?> all = prefs.getAll();
        TreeMap<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, ?> e : all.entrySet()) {
            if (e.getValue() instanceof String) sorted.put(e.getKey(), (String) e.getValue());
        }
        return sorted;
    }

    /**
     * Vráti históriu pre každé cvičenie (podľa názvu), zoradenú od najstaršieho po najnovší záznam.
     * Mapa: názov cvičenia -> zoznam [dateKey, Exercise]
     */
    public static Map<String, List<Object[]>> getHistoryGroupedByExerciseName(Context ctx) {
        TreeMap<String, String> sortedRaw = getAllRawSorted(ctx);

        Map<String, List<Object[]>> result = new TreeMap<>();
        for (Map.Entry<String, String> e : sortedRaw.entrySet()) {
            String dateKey = e.getKey().split("#")[0];

            for (String s : e.getValue().split(";")) {
                Exercise ex = Exercise.parse(s);
                if (ex == null || ex.sets.isEmpty()) continue;
                List<Object[]> list = result.computeIfAbsent(ex.name, k -> new ArrayList<>());
                list.add(new Object[]{dateKey, ex});
            }
        }
        return result;
    }

    /** Objem (kg × opakovania, súčet všetkých cvikov danej kategórie) za každý deň, kde sa kategória cvičila. */
    public static TreeMap<String, Float> getVolumeByDate(Context ctx, String categoryKey) {
        TreeMap<String, String> sortedRaw = getAllRawSorted(ctx);
        TreeMap<String, Float> result = new TreeMap<>();
        for (Map.Entry<String, String> e : sortedRaw.entrySet()) {
            String[] keyParts = e.getKey().split("#");
            if (keyParts.length < 2) continue;
            String dateKey = keyParts[0];
            String catKey = keyParts[1];
            if (!catKey.equals(categoryKey)) continue;

            float dayVolume = 0;
            for (String s : e.getValue().split(";")) {
                Exercise ex = Exercise.parse(s);
                if (ex != null) dayVolume += ex.totalVolume();
            }
            if (dayVolume > 0) result.put(dateKey, dayVolume);
        }
        return result;
    }

    /**
     * Osobný rekord (najvyššia váha v kg + dátum) pre každý klasický (WEIGHT_AND_REPS) cvik,
     * ktorý bol niekedy zaznamenaný. Cviky s vlastnou váhou/časové sa sem zámerne nezarátavajú —
     * "0kg PB" pri kliku alebo Planku by bolo mätúce; ich vlastný progres (max. opakovania/výdrž)
     * má samostatný graf priamo na karte cviku (ExerciseHistoryActivity).
     */
    public static Map<String, Object[]> getPersonalBestsByExercise(Context ctx) {
        return getPersonalBestsByExercise(getHistoryGroupedByExerciseName(ctx));
    }

    /** To isté z už načítanej histórie (getHistoryGroupedByExerciseName) — bez opätovného čítania všetkých dát. */
    public static Map<String, Object[]> getPersonalBestsByExercise(Map<String, List<Object[]>> history) {
        Map<String, Object[]> bests = new TreeMap<>();
        for (Map.Entry<String, List<Object[]>> entry : history.entrySet()) {
            String name = entry.getKey();
            float bestWeight = 0;
            String bestDate = null;
            // História je chronologická; ostrá nerovnosť (>) ponechá PRVÝ deň, keď rekord padol,
            // a nulová váha (napr. tehličky bez prevodu na kg) rekord nikdy nevytvorí ("0 kg PB").
            for (Object[] o : entry.getValue()) {
                String dateKey = (String) o[0];
                Exercise ex = (Exercise) o[1];
                if (ex.metricType() != MetricType.WEIGHT_AND_REPS) continue;
                float w = ex.maxWeight();
                if (w > bestWeight) {
                    bestWeight = w;
                    bestDate = dateKey;
                }
            }
            if (bestDate != null) bests.put(name, new Object[]{bestWeight, bestDate});
        }
        return bests;
    }

    /**
     * Premenuje cvik (podľa presného mena) na nový názov naprieč úplne všetkými uloženými dňami
     * a partiami — použité pri manuálnom zlúčení histórie (ExerciseMerger), keď používateľ
     * premenuje starý neodborný názov cviku na nový oficiálny. Všetky doterajšie série ostávajú
     * bezo zmeny, len sa prepíšu pod nové meno — žiadna história sa nemaže.
     * Vráti počet dní (raw prefs záznamov), v ktorých došlo k premenovaniu.
     */
    public static int renameExerciseEverywhere(Context ctx, String oldName, String newName) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Map<String, ?> all = prefs.getAll();
        SharedPreferences.Editor editor = prefs.edit();
        int changedDays = 0;

        for (Map.Entry<String, ?> e : all.entrySet()) {
            if (!(e.getValue() instanceof String)) continue;
            String raw = (String) e.getValue();
            if (raw.isEmpty()) continue;

            boolean changed = false;
            List<Exercise> list = new ArrayList<>();
            for (String s : raw.split(";")) {
                Exercise ex = Exercise.parse(s);
                if (ex == null) continue;
                if (ex.name.equals(oldName)) {
                    ex.name = newName;
                    changed = true;
                }
                list.add(ex);
            }

            if (changed) {
                changedDays++;
                StringBuilder sb = new StringBuilder();
                for (Exercise ex : list) {
                    if (sb.length() > 0) sb.append(";");
                    sb.append(ex.serialize());
                }
                editor.putString(e.getKey(), sb.toString());
            }
        }

        if (changedDays > 0) editor.commit();
        return changedDays;
    }

    /** Vráti posledný zaznamenaný Exercise pre dané meno, alebo null. */
    public static Exercise getLastEntryForExercise(Context ctx, String exerciseName) {
        Map<String, List<Object[]>> all = getHistoryGroupedByExerciseName(ctx);
        List<Object[]> list = all.get(exerciseName);
        if (list == null || list.isEmpty()) return null;
        return (Exercise) list.get(list.size() - 1)[1];
    }

    /**
     * Vráti zoznam cvičení z posledného tréningu danej kategórie PRED daným dátumom
     * (alebo úplne posledný, ak sa pozeráme na dnešok). Použité na "Naposledy si robil..." prehľad.
     */
    public static List<Exercise> getLastSessionForCategory(Context ctx, String categoryKey, String beforeDateKeyExclusive) {
        TreeMap<String, String> sortedRaw = getAllRawSorted(ctx);
        String bestDate = null;
        String bestRaw = null;
        for (Map.Entry<String, String> e : sortedRaw.entrySet()) {
            String[] keyParts = e.getKey().split("#");
            if (keyParts.length < 2) continue;
            String dateKey = keyParts[0];
            String catKey = keyParts[1];
            if (!catKey.equals(categoryKey)) continue;
            if (beforeDateKeyExclusive != null && dateKey.compareTo(beforeDateKeyExclusive) >= 0) continue;
            // sortedRaw je chronologicky zoradený podľa kľúča, takže posledný vyhovujúci je najnovší
            bestDate = dateKey;
            bestRaw = e.getValue();
        }
        List<Exercise> list = new ArrayList<>();
        if (bestRaw != null) {
            for (String s : bestRaw.split(";")) {
                Exercise ex = Exercise.parse(s);
                if (ex != null && !ex.sets.isEmpty()) list.add(ex);
            }
        }
        return list;
    }
}
