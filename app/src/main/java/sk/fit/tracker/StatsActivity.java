package sk.fit.tracker;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

public class StatsActivity extends Activity {

    private static final int WEEKS_SHOWN = 8;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(getColor(R.color.bg_app));
        setContentView(outer);

        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        outer.addView(scroll);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("📊 Štatistiky");
        title.setTextColor(getColor(R.color.text_white));
        title.setTextSize(19);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Posledný krát, frekvencia a objem");
        subtitle.setTextColor(getColor(R.color.text_muted));
        subtitle.setTextSize(12);
        subtitle.setPadding(0, dp(2), 0, dp(18));
        root.addView(subtitle);

        List<Category> cats = Category.all();

        // Sekcia: tréning
        root.addView(UiKit.sectionHeader(this, "💪 Tréning"));
        for (Category c : cats) {
            if (Category.GROUP_WORKOUT.equals(c.group)) {
                root.addView(buildCategoryCard(c));
            }
        }

        Button btnBack = new Button(this);
        btnBack.setText("← Späť");
        btnBack.setTextColor(getColor(R.color.text_white));
        btnBack.setBackgroundColor(getColor(R.color.bg_button_neutral));
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        btnLp.setMargins(dp(16), dp(8), dp(16), dp(16));
        btnBack.setLayoutParams(btnLp);
        btnBack.setOnClickListener(v -> finish());
        outer.addView(btnBack);
    }

    private View buildCategoryCard(Category c) {
        LinearLayout card = UiKit.card(this);

        // Header row: dot + label
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        View dot = new View(this);
        int size = dp(10);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(size, size);
        dotLp.rightMargin = dp(10);
        dot.setLayoutParams(dotLp);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(c.color);
        dot.setBackground(dotBg);
        header.addView(dot);

        TextView label = new TextView(this);
        label.setText(c.label);
        label.setTextColor(getColor(R.color.text_white));
        label.setTextSize(14);
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        label.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(label);

        card.addView(header);

        // Last time text
        String lastDate = ActivityStore.lastDateForCategory(this, c.key);
        TextView tvLast = new TextView(this);
        tvLast.setTextSize(12);
        tvLast.setPadding(0, dp(8), 0, dp(8));
        if (lastDate == null) {
            tvLast.setText("Ešte nikdy zaznamenané");
            tvLast.setTextColor(getColor(R.color.text_muted));
        } else {
            int days = ActivityStore.daysSince(lastDate);
            String when;
            if (days == 0) when = "dnes";
            else if (days == 1) when = "včera";
            else when = "pred " + days + " dňami";
            String niceDate = formatNice(lastDate);
            tvLast.setText("Posledný krát: " + when + " (" + niceDate + ")");
            tvLast.setTextColor(days <= 2 ? getColor(R.color.accent_primary_soft) : getColor(R.color.text_primary));
        }
        card.addView(tvLast);

        // Frequency chips row
        int count7 = ActivityStore.countInLastDays(this, c.key, 7);
        int count30 = ActivityStore.countInLastDays(this, c.key, 30);

        LinearLayout chipsRow = new LinearLayout(this);
        chipsRow.setOrientation(LinearLayout.HORIZONTAL);

        chipsRow.addView(buildChip("7 dní", count7 + "×"));
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(dp(8), 1));
        chipsRow.addView(spacer);
        chipsRow.addView(buildChip("30 dní", count30 + "×"));

        card.addView(chipsRow);

        // Objem (len pre tréningové kategórie — má zmysel len tam, kde sa zapisujú série)
        if (Category.GROUP_WORKOUT.equals(c.group)) {
            float volume30 = sumRecentVolume(c.key, 30);
            if (volume30 > 0) {
                TextView tvVolume = new TextView(this);
                tvVolume.setText("Objem za 30 dní: " + formatVolume(volume30) + " kg");
                tvVolume.setTextColor(getColor(R.color.text_secondary));
                tvVolume.setTextSize(12);
                tvVolume.setPadding(0, dp(10), 0, dp(4));
                card.addView(tvVolume);
            }

            // Frekvenčný graf — počet tréningov za posledných WEEKS_SHOWN týždňov
            List<Integer> weekly = ActivityStore.weeklyCounts(this, c.key, WEEKS_SHOWN);
            boolean anyActivity = weekly.stream().anyMatch(v -> v > 0);
            if (anyActivity) {
                List<Float> chartValues = new ArrayList<>();
                List<String> chartLabels = new ArrayList<>();
                // weekly[0] = tento týždeň (najnovší) -> otoč na chronologické poradie zľava doprava
                for (int i = weekly.size() - 1; i >= 0; i--) {
                    chartValues.add((float) weekly.get(i));
                    chartLabels.add(i == 0 ? "teraz" : "-" + i + "t");
                }

                TextView chartLabel = new TextView(this);
                chartLabel.setText("Frekvencia (posledných " + WEEKS_SHOWN + " týž.):");
                chartLabel.setTextColor(getColor(R.color.text_secondary));
                chartLabel.setTextSize(11);
                chartLabel.setPadding(0, dp(6), 0, dp(4));
                card.addView(chartLabel);

                BarChartView chart = new BarChartView(this);
                LinearLayout.LayoutParams chartLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
                chart.setLayoutParams(chartLp);
                chart.setData(chartValues, chartLabels);
                card.addView(chart);
            }
        }

        return card;
    }

    private float sumRecentVolume(String categoryKey, int days) {
        TreeMap<String, Float> byDate = ExerciseStore.getVolumeByDate(this, categoryKey);
        float total = 0;
        for (java.util.Map.Entry<String, Float> e : byDate.entrySet()) {
            int ds = ActivityStore.daysSince(e.getKey());
            if (ds >= 0 && ds < days) total += e.getValue();
        }
        return total;
    }

    private String formatVolume(float v) {
        return String.format(Locale.getDefault(), "%,.0f", v);
    }

    private View buildChip(String label, String value) {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setBackground(UiKit.roundedBg(this, R.color.bg_chip, UiKit.dimenPx(this, R.dimen.radius_chip)));
        chip.setPadding(dp(10), dp(5), dp(10), dp(5));

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label + ": ");
        tvLabel.setTextColor(getColor(R.color.text_muted));
        tvLabel.setTextSize(11);
        chip.addView(tvLabel);

        TextView tvValue = new TextView(this);
        tvValue.setText(value);
        tvValue.setTextColor(getColor(R.color.text_primary));
        tvValue.setTextSize(11);
        tvValue.setTypeface(null, android.graphics.Typeface.BOLD);
        chip.addView(tvValue);

        return chip;
    }

    private String formatNice(String dateKeyStr) {
        try {
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            SimpleDateFormat display = new SimpleDateFormat("dd.MM.yyyy", Locale.US);
            return display.format(parser.parse(dateKeyStr));
        } catch (Exception e) {
            return dateKeyStr;
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
