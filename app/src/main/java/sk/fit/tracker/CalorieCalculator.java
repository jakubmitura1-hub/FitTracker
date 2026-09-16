package sk.fit.tracker;

/**
 * Odhad spálených kalórií pomocou MET (Metabolic Equivalent of Task).
 * Spoločný vzorec: kcal = MET × 3.5 × váha(kg) / 200 × minúty.
 */
public class CalorieCalculator {

    /** MET pre intenzívny silový tréning. */
    private static final float STRENGTH_MET = 6.0f;

    private CalorieCalculator() {}

    public static float metToKcal(float met, float weightKg, float minutes) {
        if (weightKg <= 0 || minutes <= 0 || met <= 0) return 0f;
        return met * 3.5f * weightKg / 200f * minutes;
    }

    public static float strengthKcal(float weightKg, float minutes) {
        return metToKcal(STRENGTH_MET, weightKg, minutes);
    }

    /**
     * Odhad MET pre kardio podľa rýchlosti a sklonu — ACSM vzorec pre chôdzu/beh:
     * VO2 (ml/kg/min) = a × rýchlosť(m/min) + b × rýchlosť(m/min) × sklon(zlomok) + 3.5 ; MET = VO2 / 3.5.
     * Nad 8 kph sa použijú koeficienty pre beh, inak pre chôdzu do kopca.
     * (Pri 5.3 kph a 3% sklone vychádza MET ≈ 4.9 — presne v rozsahu, ktorý ukazujú bežecké pásy.)
     */
    public static float estimateCardioMet(float speedKph, float inclinePercent) {
        if (speedKph <= 0) return 0f;
        float speedMPerMin = speedKph * 1000f / 60f;
        float grade = inclinePercent / 100f;
        boolean running = speedKph > 8f;
        float a = running ? 0.2f : 0.1f;
        float b = running ? 0.9f : 1.8f;
        float vo2 = a * speedMPerMin + b * speedMPerMin * grade + 3.5f;
        return vo2 / 3.5f;
    }

    public static float cardioKcal(float weightKg, float durationMin, float speedKph, float inclinePercent) {
        return metToKcal(estimateCardioMet(speedKph, inclinePercent), weightKg, durationMin);
    }
}
