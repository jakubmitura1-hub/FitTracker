package sk.fit.tracker;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Jeden cvik v katalógu — primárny slovenský názov (ten sa aj reálne ukladá do dát),
 * anglický názov a voliteľný alternatívny/slangový názov, obidva len na vyhľadávanie.
 */
public class ExerciseDefinition {

    public final String sk;
    public final String en;
    public final String alt; // môže byť null
    public final MetricType metricType;

    // Fotky zabudovaných cvikov rieši ExerciseMedia (free-exercise-db), nie táto trieda.

    /** Normalizovaný text (bez diakritiky, malé písmená) so všetkými názvami — na rýchle vyhľadávanie. */
    public final String searchBlob;

    /** Klasický typ merania (váha × opakovania). */
    public ExerciseDefinition(String sk, String en, String alt) {
        this(sk, en, alt, MetricType.WEIGHT_AND_REPS);
    }

    public ExerciseDefinition(String sk, String en, String alt, MetricType metricType) {
        this.sk = sk;
        this.en = en;
        this.alt = alt;
        this.metricType = metricType;

        StringBuilder sb = new StringBuilder(normalize(sk));
        if (en != null) sb.append(' ').append(normalize(en));
        if (alt != null) sb.append(' ').append(normalize(alt));
        this.searchBlob = sb.toString();
    }

    /** Odstráni diakritiku a prevedie na malé písmená (case-insensitive + diacritic-insensitive porovnávanie). */
    public static String normalize(String s) {
        if (s == null) return "";
        String decomposed = Normalizer.normalize(s, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
