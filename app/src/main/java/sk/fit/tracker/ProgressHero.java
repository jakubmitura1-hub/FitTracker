package sk.fit.tracker;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/**
 * Spoločné formátovanie progresu cviku — "hero" hodnota posledného tréningu s farebným trend
 * chipom a formáty kg / dátumu. Používa ExerciseHistoryActivity (karty) aj ExerciseDetailActivity.
 */
public final class ProgressHero {

    private ProgressHero() {}

    /**
     * Nastaví hero hodnotu (posledný tréning) a farebný trend chip oproti predchádzajúcemu
     * tréningu (↑ zlepšenie, ↓ zhoršenie, → rovnako). "series" je chronologická (najnovšie
     * posledné) a zodpovedá metrike, ktorá je práve zobrazená v grafe (max váha/1RM/opak./výdrž).
     */
    public static void apply(Context ctx, TextView heroValue, TextView heroLabel, TextView trendChip,
                             MetricType metricType, boolean oneRm, List<Float> series) {
        float current = series.get(series.size() - 1);

        String valueText;
        String labelText;
        if (metricType == MetricType.DURATION) {
            valueText = SetEntry.formatDuration(Math.round(current));
            labelText = "Najdlhšia výdrž · posledný tréning";
        } else if (metricType == MetricType.BODYWEIGHT_AND_REPS) {
            valueText = Math.round(current) + " opak.";
            labelText = "Max opakovania · posledný tréning";
        } else {
            valueText = formatKg(current) + " kg";
            labelText = (oneRm ? "Odhad. 1RM" : "Max váha") + " · posledný tréning";
        }
        heroValue.setText(valueText);
        heroLabel.setText(labelText);

        if (series.size() < 2) {
            trendChip.setVisibility(View.GONE);
            return;
        }
        trendChip.setVisibility(View.VISIBLE);
        float delta = current - series.get(series.size() - 2);
        if (Math.abs(delta) < 0.01f) {
            trendChip.setText("→ rovnako");
            trendChip.setTextColor(ctx.getColor(R.color.text_secondary));
        } else if (delta > 0) {
            trendChip.setText("↑ +" + formatDelta(metricType, delta));
            trendChip.setTextColor(ctx.getColor(R.color.accent_primary_soft));
        } else {
            trendChip.setText("↓ -" + formatDelta(metricType, -delta));
            trendChip.setTextColor(ctx.getColor(R.color.accent_danger));
        }
        trendChip.setBackground(UiKit.roundedBg(ctx, R.color.bg_chip, UiKit.dimenPx(ctx, R.dimen.radius_chip)));
    }

    public static String formatDelta(MetricType metricType, float absDelta) {
        if (metricType == MetricType.DURATION) return SetEntry.formatDuration(Math.round(absDelta));
        if (metricType == MetricType.BODYWEIGHT_AND_REPS) return Math.round(absDelta) + " opak.";
        return formatKg(absDelta) + " kg";
    }

    public static String formatKg(float w) {
        return (w == Math.floor(w))
                ? String.format(Locale.getDefault(), "%.0f", w)
                : String.format(Locale.getDefault(), "%.1f", w);
    }

    /** "2026-11-08" -> "08.11.2026 (ne)"; pri chybe vráti vstup bez zmeny. */
    public static String formatNice(String dateKeyStr) {
        try {
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            SimpleDateFormat display = new SimpleDateFormat("dd.MM.yyyy (EEE)", new Locale("sk", "SK"));
            return display.format(parser.parse(dateKeyStr));
        } catch (Exception e) {
            return dateKeyStr;
        }
    }
}
