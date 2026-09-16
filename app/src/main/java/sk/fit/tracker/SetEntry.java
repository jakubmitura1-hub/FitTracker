package sk.fit.tracker;

import java.util.Locale;

/**
 * Jedna séria. Sémantika polí závisí od metricType:
 *  - WEIGHT_AND_REPS: weight × reps × unit (klasika).
 *  - BODYWEIGHT_AND_REPS: reps opakovaní vlastnou váhou, weight = voliteľná PRIDANÁ záťaž v kg (0 = bez záťaže).
 *  - DURATION: durationSeconds trvania; weight/reps sa nepoužívajú.
 */
public class SetEntry {

    public static final String UNIT_KG = "kg";
    public static final String UNIT_LBS = "lbs";
    public static final String UNIT_PLATE = "plate";
    public static final String DEFAULT_UNIT = UNIT_KG;

    public float weight;
    public int reps;
    public String unit;
    public MetricType metricType;
    public int durationSeconds;

    public SetEntry(float weight, int reps) {
        this(weight, reps, DEFAULT_UNIT);
    }

    public SetEntry(float weight, int reps, String unit) {
        this(weight, reps, unit, MetricType.WEIGHT_AND_REPS, 0);
    }

    public SetEntry(float weight, int reps, String unit, MetricType metricType, int durationSeconds) {
        this.weight = weight;
        this.reps = reps;
        this.unit = (unit == null || unit.isEmpty()) ? DEFAULT_UNIT : unit;
        this.metricType = (metricType == null) ? MetricType.WEIGHT_AND_REPS : metricType;
        this.durationSeconds = durationSeconds;
    }

    /** Vytvorí sériu s vlastnou váhou (voliteľná pridaná záťaž v kg, 0 = bez záťaže). */
    public static SetEntry bodyweight(int reps, float addedWeightKg) {
        return new SetEntry(addedWeightKg, reps, UNIT_KG, MetricType.BODYWEIGHT_AND_REPS, 0);
    }

    /** Vytvorí časovú sériu (trvanie v sekundách). */
    public static SetEntry duration(int seconds) {
        return new SetEntry(0, 0, UNIT_KG, MetricType.DURATION, seconds);
    }

    /**
     * Formát "váhaXopak:jednotka:metricType:trvanieSekund". Posledné dva segmenty sú
     * rozšírenie — staré dáta (bez nich, prípadne aj bez jednotky) sa naďalej parsujú identicky.
     */
    public String serialize() {
        return weight + "x" + reps + ":" + unit + ":" + metricType.code() + ":" + durationSeconds;
    }

    public static SetEntry parse(String s) {
        // limit -1 zachová prípadné prázdne koncové segmenty (napr. chýbajúce durationSeconds)
        String[] segments = s.split(":", -1);
        String core = segments[0];
        String unit = (segments.length > 1 && !segments[1].isEmpty()) ? segments[1] : DEFAULT_UNIT;
        MetricType metricType = (segments.length > 2) ? MetricType.fromCode(segments[2]) : MetricType.WEIGHT_AND_REPS;
        int durationSeconds = 0;
        if (segments.length > 3 && !segments[3].isEmpty()) {
            try {
                durationSeconds = Integer.parseInt(segments[3]);
            } catch (Exception ignored) {
                durationSeconds = 0;
            }
        }

        String[] parts = core.split("x");
        if (parts.length != 2) return null;
        try {
            float weight = Float.parseFloat(parts[0]);
            int reps = Integer.parseInt(parts[1]);
            return new SetEntry(weight, reps, unit, metricType, durationSeconds);
        } catch (Exception e) {
            return null;
        }
    }

    /** Krátka značka jednotky pre zobrazenie, napr. "kg", "lbs", "ks (tehličky)". */
    public String unitLabel() {
        switch (unit) {
            case UNIT_LBS: return "lbs";
            case UNIT_PLATE: return "ks tehličiek";
            default: return "kg";
        }
    }

    /**
     * Váha prepočítaná na kg pre porovnávanie PB medzi rôznymi jednotkami.
     * Tehličky nemajú jednoznačný prevod na kg, preto sa z porovnávania vynechávajú (vráti 0).
     */
    public float weightInKgEquivalent() {
        switch (unit) {
            case UNIT_LBS: return weight * 0.45359237f;
            case UNIT_PLATE: return 0f;
            default: return weight;
        }
    }

    /** Trvanie v sekundách naformátované ako "MM:SS". */
    public static String formatDuration(int totalSeconds) {
        int m = totalSeconds / 60;
        int s = totalSeconds % 60;
        return String.format(Locale.US, "%d:%02d", m, s);
    }

    /** Parsuje "MM:SS" (alebo len sekundy, napr. "45") na celkový počet sekúnd. Null = neplatný vstup. */
    public static Integer parseDuration(String mmss) {
        if (mmss == null) return null;
        String t = mmss.trim();
        if (t.isEmpty()) return null;
        try {
            if (t.contains(":")) {
                String[] parts = t.split(":");
                if (parts.length != 2) return null;
                int minutes = Integer.parseInt(parts[0].trim());
                int seconds = Integer.parseInt(parts[1].trim());
                if (minutes < 0 || seconds < 0 || seconds > 59) return null;
                return minutes * 60 + seconds;
            }
            int seconds = Integer.parseInt(t);
            return seconds >= 0 ? seconds : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String toString() {
        if (metricType == MetricType.DURATION) {
            return formatDuration(durationSeconds);
        }
        if (metricType == MetricType.BODYWEIGHT_AND_REPS) {
            String load = weight > 0 ? ("+" + formatW(weight) + "kg")
                    : weight < 0 ? (formatW(weight) + "kg asist.")
                    : "VV";
            return load + "×" + reps;
        }
        String w = formatW(weight);
        String label = UNIT_PLATE.equals(unit) ? " " + unitLabel() : unitLabel();
        return w + label + "×" + reps;
    }

    private static String formatW(float w) {
        return (w == Math.floor(w)) ?
                String.format(Locale.getDefault(), "%.0f", w) :
                String.format(Locale.getDefault(), "%.1f", w);
    }
}
