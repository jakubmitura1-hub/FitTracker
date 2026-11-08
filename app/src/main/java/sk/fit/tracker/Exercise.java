package sk.fit.tracker;

import java.util.ArrayList;
import java.util.List;

/**
 * Jedno cvičenie (napr. "Bicepsový zdvih") s viacerými sériami,
 * pričom každá séria môže mať inú váhu a počet opakovaní.
 */
public class Exercise {
    public String name;
    public List<SetEntry> sets = new ArrayList<>();

    /**
     * URL profesionálneho detailného anatomického vizuálu (3D sval + konkrétny stroj, napr.
     * v štýle WeightTraining.Guide / Hammer Strength) pre tento konkrétny cvik. Zatiaľ vždy
     * null — appka nemá zavedené žiadne sieťové načítavanie obrázkov. Nie je súčasťou
     * serialize()/parse() (nič sa neukladá), je to len logický háčik pripravený pre budúcnosť:
     * keď sa sem raz doplní URL (napr. z katalógu podľa presného stroja), UI si ho vyžiada cez
     * getDetailedVisualUrl() a namiesto lokálneho obrázku svalu (Category.muscleDrawableFor)
     * načíta tento — vtedy bude treba pridať knižnicu ako Glide/Picasso, aby sa veľké obrázky
     * sťahovali/cachovali bez zahltenia pamäte telefónu; NEriešiť to ručným Bitmap/URLConnection.
     */
    private String detailedVisualUrl = null;

    public Exercise(String name) {
        this.name = name;
    }

    public Exercise(String name, List<SetEntry> sets) {
        this.name = name;
        this.sets = sets;
    }

    /** Null, kým appka nemá zdroj profesionálnych vizuálov — UI má v tom prípade zobraziť lokálny obrázok svalu. */
    public String getDetailedVisualUrl() {
        return detailedVisualUrl;
    }

    public void setDetailedVisualUrl(String detailedVisualUrl) {
        this.detailedVisualUrl = detailedVisualUrl;
    }

    /** Formát: "Názov::12x10,14x8,16x6" (váhaXopak, oddelené čiarkou) */
    public String serialize() {
        String safeName = name.replace("::", "").replace(";", "").replace(",", "");
        StringBuilder sb = new StringBuilder();
        for (SetEntry s : sets) {
            if (sb.length() > 0) sb.append(",");
            sb.append(s.serialize());
        }
        return safeName + "::" + sb;
    }

    public static Exercise parse(String s) {
        String[] parts = s.split("::", 2);
        if (parts.length != 2) return null;
        Exercise ex = new Exercise(parts[0]);
        if (!parts[1].isEmpty()) {
            for (String setStr : parts[1].split(",")) {
                SetEntry se = SetEntry.parse(setStr);
                if (se != null) ex.sets.add(se);
            }
        }
        return ex;
    }

    /** Zhrnutie napr. "12kg×10, 14kg×8, 16kg×6" */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sets.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(sets.get(i).toString());
        }
        return sb.toString();
    }

    /** Typ merania tohto cvičenia — odvodený z prvej série (predvolené WEIGHT_AND_REPS, ak zatiaľ nemá žiadnu). */
    public MetricType metricType() {
        return sets.isEmpty() ? MetricType.WEIGHT_AND_REPS : sets.get(0).metricType;
    }

    /** Najvyššia váha (prepočítaná na kg) spomedzi všetkých sérií tohto cvičenia (jeden záznam). Časové série neprispievajú. */
    public float maxWeight() {
        float max = 0;
        for (SetEntry s : sets) {
            if (s.metricType == MetricType.DURATION) continue;
            float kg = s.weightInKgEquivalent();
            if (kg > max) max = kg;
        }
        return max;
    }

    /** Celkový objem (váha v kg × opakovania, súčet cez všetky série) tohto cvičenia. Časové série neprispievajú. */
    public float totalVolume() {
        float volume = 0;
        for (SetEntry s : sets) {
            if (s.metricType == MetricType.DURATION) continue;
            volume += s.weightInKgEquivalent() * s.reps;
        }
        return volume;
    }

    /**
     * Odhadovaná maximálka na jedno opakovanie (Brzyckiho vzorec), najvyššia hodnota
     * spomedzi všetkých sérií tohto cvičenia. Umožňuje sledovať silový progres aj
     * naprieč tréningami s rôznym počtom opakovaní. Časové série neprispievajú.
     */
    public float getMaxEstimated1RM() {
        float best1RM = 0;
        for (SetEntry s : sets) {
            if (s.metricType == MetricType.DURATION) continue;
            float weightKg = s.weightInKgEquivalent();
            float denom = 1.0278f - 0.0278f * s.reps;
            float oneRM = denom > 0 ? (weightKg / denom) : weightKg; // pri extrémnych opakovaniach sa vzorec rozpadá
            if (oneRM > best1RM) best1RM = oneRM;
        }
        return best1RM;
    }

    /** Priemerné RPE zo sérií, kde je zadané; 0 = žiadna séria nemá RPE. */
    public float averageRpe() {
        float sum = 0;
        int n = 0;
        for (SetEntry s : sets) {
            if (s.rpe > 0) {
                sum += s.rpe;
                n++;
            }
        }
        return n == 0 ? 0 : sum / n;
    }

    /** Najvyšší počet opakovaní spomedzi všetkých sérií — použité pre grafy cvikov s vlastnou váhou. */
    public int maxReps() {
        int max = 0;
        for (SetEntry s : sets) {
            if (s.reps > max) max = s.reps;
        }
        return max;
    }

    /** Najdlhšie trvanie (v sekundách) spomedzi všetkých sérií — použité pre grafy časových cvikov. */
    public int maxDurationSeconds() {
        int max = 0;
        for (SetEntry s : sets) {
            if (s.durationSeconds > max) max = s.durationSeconds;
        }
        return max;
    }
}
