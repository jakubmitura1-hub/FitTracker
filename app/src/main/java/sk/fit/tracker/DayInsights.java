package sk.fit.tracker;

import android.content.Context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lokálne, pravidlové "zhrnutie dňa" — žiadne AI ani sieť, len porovnanie dnešných
 * záznamov s históriou (posledný tréning tej istej partie/cviku, streak, kardio tempo).
 */
public class DayInsights {

    private DayInsights() {}

    public static List<String> evaluate(Context ctx, String dateKey,
                                          Map<String, List<Exercise>> exercisesByCategory,
                                          List<CardioSession> cardioSessions) {
        List<String> insights = new ArrayList<>();

        boolean hasAnyActivity = !cardioSessions.isEmpty();
        for (List<Exercise> list : exercisesByCategory.values()) {
            if (!list.isEmpty()) { hasAnyActivity = true; break; }
        }
        if (!hasAnyActivity) return insights;

        if (ActivityStore.isWorkoutStreakDay(ctx, dateKey)) {
            insights.add("🔥 Si v tréningovej sérii — pokračuj!");
        }

        for (Map.Entry<String, List<Exercise>> entry : exercisesByCategory.entrySet()) {
            String categoryKey = entry.getKey();
            List<Exercise> todayList = entry.getValue();
            if (todayList.isEmpty()) continue;

            List<Exercise> lastSession = ExerciseStore.getLastSessionForCategory(ctx, categoryKey, dateKey);

            Map<String, Exercise> lastByName = new HashMap<>();
            float lastVolume = 0;
            for (Exercise ex : lastSession) {
                lastByName.put(ex.name, ex);
                lastVolume += ex.totalVolume();
            }

            float todayVolume = 0;
            for (Exercise ex : todayList) {
                todayVolume += ex.totalVolume();
                Exercise prev = lastByName.get(ex.name);
                if (prev == null) continue;
                float todayMax = ex.maxWeight();
                float prevMax = prev.maxWeight();
                if (prevMax > 0 && todayMax > prevMax) {
                    insights.add("💪 " + ex.name + ": nové maximum " + formatNum(todayMax)
                            + "kg (predtým " + formatNum(prevMax) + "kg)");
                }
            }

            if (lastVolume > 0 && todayVolume > 0) {
                float diffPct = ((todayVolume - lastVolume) / lastVolume) * 100f;
                if (Math.abs(diffPct) >= 5) {
                    Category cat = Category.byKey(categoryKey);
                    String label = cat != null ? cat.label : categoryKey;
                    insights.add((diffPct > 0 ? "📈 " : "📉 ") + label + ": objem "
                            + (diffPct > 0 ? "+" : "") + Math.round(diffPct) + "% oproti minulému tréningu");
                }
            }
        }

        if (!cardioSessions.isEmpty()) {
            List<CardioSession> lastCardio = CardioStore.getLastSessionBefore(ctx, dateKey);
            if (!lastCardio.isEmpty()) {
                CardioSession today = cardioSessions.get(cardioSessions.size() - 1);
                CardioSession prev = lastCardio.get(lastCardio.size() - 1);
                if (today.avgSpeedKph > 0 && prev.avgSpeedKph > 0 && today.avgSpeedKph > prev.avgSpeedKph) {
                    insights.add("🏃 Rýchlejšie tempo ako minule: " + formatNum(today.avgSpeedKph)
                            + " kph (predtým " + formatNum(prev.avgSpeedKph) + " kph)");
                }
                if (today.distanceKm > 0 && prev.distanceKm > 0 && today.distanceKm > prev.distanceKm) {
                    insights.add("🏃 Dlhšia vzdialenosť ako minule: " + formatNum(today.distanceKm)
                            + " km (predtým " + formatNum(prev.distanceKm) + " km)");
                }
            }
        }

        if (insights.isEmpty()) {
            insights.add("👍 Slušný tréning — pokračuj v tom!");
        }

        return insights;
    }

    private static String formatNum(float v) {
        return (v == Math.floor(v))
                ? String.format(Locale.getDefault(), "%.0f", v)
                : String.format(Locale.getDefault(), "%.1f", v);
    }
}
