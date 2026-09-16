package sk.fit.tracker;

import java.util.Locale;

/** Jeden kardio záznam (bežecký pás, bicykel...): trvanie, vzdialenosť, rýchlosť, stúpanie, kalórie. */
public class CardioSession {

    public float durationMin;
    public float distanceKm;
    public float avgSpeedKph;
    public float avgInclinePercent;
    public int calories;

    public CardioSession(float durationMin, float distanceKm, float avgSpeedKph,
                          float avgInclinePercent, int calories) {
        this.durationMin = durationMin;
        this.distanceKm = distanceKm;
        this.avgSpeedKph = avgSpeedKph;
        this.avgInclinePercent = avgInclinePercent;
        this.calories = calories;
    }

    /** Formát: "durationMin|distanceKm|avgSpeedKph|avgInclinePercent|calories", napr. "25.5|2.24|5.3|3|159" */
    public String serialize() {
        return durationMin + "|" + distanceKm + "|" + avgSpeedKph + "|" + avgInclinePercent + "|" + calories;
    }

    public static CardioSession parse(String s) {
        String[] parts = s.split("\\|");
        if (parts.length != 5) return null;
        try {
            return new CardioSession(
                    Float.parseFloat(parts[0]),
                    Float.parseFloat(parts[1]),
                    Float.parseFloat(parts[2]),
                    Float.parseFloat(parts[3]),
                    Integer.parseInt(parts[4]));
        } catch (Exception e) {
            return null;
        }
    }

    /** Zhrnutie napr. "25.5 min · 2.24 km · 5.3 kph · 3% stúpanie · 159 kcal" */
    @Override
    public String toString() {
        return formatNum(durationMin) + " min · " + formatNum(distanceKm) + " km · "
                + formatNum(avgSpeedKph) + " kph · " + formatNum(avgInclinePercent) + "% stúpanie · "
                + calories + " kcal";
    }

    private static String formatNum(float v) {
        return (v == Math.floor(v))
                ? String.format(Locale.getDefault(), "%.0f", v)
                : String.format(Locale.getDefault(), "%.1f", v);
    }
}
