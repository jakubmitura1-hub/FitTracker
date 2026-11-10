package sk.fit.tracker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class ExerciseHistoryActivity extends Activity {

    /** Názov cviku -> partia, načítané z katalógu raz pre celú obrazovku (nie pre každú kartu zvlášť). */
    private Map<String, String> categoryKeyByName;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(ThemeStore.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiKit.edgeToEdge(this); // odstup od stavového riadku, navigácie a klávesnice (Android 15+)

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
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("🏋️ Progres v cvičeniach");
        title.setTextColor(getColor(R.color.text_primary));
        title.setTextSize(19);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Vývoj váh — najnovšie hore");
        subtitle.setTextColor(getColor(R.color.text_muted));
        subtitle.setTextSize(12);
        subtitle.setPadding(0, dp(2), 0, dp(18));
        root.addView(subtitle);

        // Kluc = nazov cvicenia, hodnota = zoznam [dateKey, Exercise] chrono
        Map<String, List<Object[]>> history = ExerciseStore.getHistoryGroupedByExerciseName(this);

        if (history.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Ešte žiadne zaznamenané cvičenia.\nPridaj ich pri tréningu v kalendári.");
            empty.setTextColor(getColor(R.color.text_muted));
            empty.setTextSize(13);
            root.addView(empty);
        } else {
            for (Map.Entry<String, List<Object[]>> entry : history.entrySet()) {
                root.addView(buildExerciseCard(entry.getKey(), entry.getValue()));
            }

            Map<String, Object[]> personalBests = ExerciseStore.getPersonalBestsByExercise(history);
            if (!personalBests.isEmpty()) {
                root.addView(buildPersonalBestsCard(personalBests));
            }
        }

        Button btnBack = new Button(this);
        btnBack.setText("← Späť");
        btnBack.setTextColor(getColor(R.color.text_primary));
        btnBack.setBackgroundColor(getColor(R.color.bg_button_neutral));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        lp.setMargins(dp(16), dp(8), dp(16), dp(16));
        btnBack.setLayoutParams(lp);
        btnBack.setOnClickListener(v -> finish());
        outer.addView(btnBack);
    }

    // ─── Osobné rekordy ──────────────────────────────────────────────────────

    private LinearLayout buildPersonalBestsCard(Map<String, Object[]> personalBests) {
        LinearLayout card = UiKit.card(this);

        TextView header = new TextView(this);
        header.setText("🏆 Osobné rekordy");
        header.setTextColor(getColor(R.color.text_primary));
        header.setTextSize(15);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setPadding(0, 0, 0, dp(10));
        card.addView(header);

        boolean first = true;
        for (Map.Entry<String, Object[]> e : personalBests.entrySet()) {
            if (!first) {
                android.view.View sep = new android.view.View(this);
                LinearLayout.LayoutParams sepLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
                sep.setLayoutParams(sepLp);
                sep.setBackgroundColor(getColor(R.color.separator));
                card.addView(sep);
            }
            first = false;

            String name = e.getKey();
            float weight = (Float) e.getValue()[0];
            String date = (String) e.getValue()[1];

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(6), 0, dp(6));

            TextView tvName = new TextView(this);
            tvName.setText(name);
            tvName.setTextColor(getColor(R.color.text_primary));
            tvName.setTextSize(12);
            tvName.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(tvName);

            TextView tvPb = new TextView(this);
            tvPb.setText(ProgressHero.formatKg(weight) + "kg  ·  " + ProgressHero.formatNice(date));
            tvPb.setTextColor(getColor(R.color.accent_highlight));
            tvPb.setTextSize(12);
            tvPb.setTypeface(null, android.graphics.Typeface.BOLD);
            row.addView(tvPb);

            card.addView(row);
        }

        return card;
    }

    // ─── Karta jedného cvičenia ─────────────────────────────────────────────

    private LinearLayout buildExerciseCard(String name, List<Object[]> entriesChrono) {
        LinearLayout card = UiKit.card(this);

        Exercise latestEx = (Exercise) entriesChrono.get(entriesChrono.size() - 1)[1];

        // Hlavička karty — ikona partie (sval na postave) + názov cvičenia.
        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        nameRow.setPadding(0, 0, 0, dp(8));

        if (categoryKeyByName == null) categoryKeyByName = ExerciseCatalog.categoryKeysByExerciseName(this);
        String categoryKey = categoryKeyByName.get(name);
        int muscleRes = categoryKey != null ? Category.muscleDrawableFor(categoryKey) : 0;
        if (muscleRes != 0) {
            android.widget.ImageView muscleIcon = new android.widget.ImageView(this);
            muscleIcon.setImageDrawable(Category.muscleIcon(this, categoryKey));
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(18), dp(30));
            iconLp.rightMargin = dp(10);
            muscleIcon.setLayoutParams(iconLp);
            nameRow.addView(muscleIcon);
        }

        TextView tvName = new TextView(this);
        tvName.setText(name);
        tvName.setTextColor(getColor(R.color.text_primary));
        tvName.setTextSize(15);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        tvName.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        nameRow.addView(tvName);

        TextView tvDetail = new TextView(this);
        tvDetail.setText("Detail ›");
        tvDetail.setTextColor(getColor(R.color.accent_primary));
        tvDetail.setTextSize(12);
        nameRow.addView(tvDetail);
        nameRow.setOnClickListener(v -> startActivity(ExerciseDetailActivity.intent(this, name, false)));
        card.addView(nameRow);

        // Typ merania tohto cvičenia — podľa najnovšieho záznamu (chronologicky posledný v zozname).
        MetricType metricType = latestEx.metricType();

        // Hero riadok — aktuálna hodnota (posledný tréning) veľkým písmom + farebný trend
        // chip oproti predchádzajúcemu tréningu, nech je progres čitateľný na prvý pohľad
        // bez nutnosti prechádzať celú históriu sérií.
        LinearLayout heroLeft = new LinearLayout(this);
        heroLeft.setOrientation(LinearLayout.VERTICAL);
        heroLeft.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView heroValue = new TextView(this);
        heroValue.setTextColor(getColor(R.color.accent_primary_soft));
        heroValue.setTextSize(23);
        heroValue.setTypeface(null, android.graphics.Typeface.BOLD);
        heroLeft.addView(heroValue);

        TextView heroLabel = new TextView(this);
        heroLabel.setTextColor(getColor(R.color.text_muted));
        heroLabel.setTextSize(11);
        heroLabel.setPadding(0, dp(2), 0, 0);
        heroLeft.addView(heroLabel);

        TextView trendChip = new TextView(this);
        trendChip.setTextSize(12);
        trendChip.setTypeface(null, android.graphics.Typeface.BOLD);
        trendChip.setPadding(dp(10), dp(5), dp(10), dp(5));

        LinearLayout heroRow = new LinearLayout(this);
        heroRow.setOrientation(LinearLayout.HORIZONTAL);
        heroRow.setGravity(Gravity.CENTER_VERTICAL);
        heroRow.setPadding(0, dp(2), 0, dp(10));
        heroRow.addView(heroLeft);
        heroRow.addView(trendChip);
        card.addView(heroRow);

        // Graf vývoja (potrebuje aspoň 2 záznamy) — pre klasické cviky prepínateľný medzi max.
        // váhou a odhadovaným 1RM; pre vlastnú váhu graf max. opakovaní; pre časové cviky graf
        // najdlhšej výdrže.
        if (entriesChrono.size() >= 2) {
            List<Float> primaryValues = new ArrayList<>();
            List<Float> oneRmValues = new ArrayList<>();
            List<String> chartLabels = new ArrayList<>();
            for (Object[] o : entriesChrono) {
                Exercise ex = (Exercise) o[1];
                chartLabels.add(ProgressHero.formatNice((String) o[0]));
                if (metricType == MetricType.DURATION) {
                    primaryValues.add((float) ex.maxDurationSeconds());
                } else if (metricType == MetricType.BODYWEIGHT_AND_REPS) {
                    primaryValues.add((float) ex.maxReps());
                } else {
                    primaryValues.add(ex.maxWeight());
                    oneRmValues.add(ex.getMaxEstimated1RM());
                }
            }

            TextView chartToggle = new TextView(this);
            chartToggle.setText(metricType == MetricType.DURATION ? "📈 Najdlhšia výdrž"
                    : metricType == MetricType.BODYWEIGHT_AND_REPS ? "📈 Max opakovania"
                    : "📈 Max váha  (klikni pre odhad. 1RM)");
            chartToggle.setTextColor(getColor(R.color.accent_primary));
            chartToggle.setTextSize(11);
            chartToggle.setPadding(0, 0, 0, dp(4));
            card.addView(chartToggle);

            LineChartView chart = new LineChartView(this);
            LinearLayout.LayoutParams chartLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(96));
            chartLp.bottomMargin = dp(12);
            chart.setLayoutParams(chartLp);
            chart.setData(primaryValues, chartLabels);
            card.addView(chart);

            ProgressHero.apply(this, heroValue, heroLabel, trendChip, metricType, false, primaryValues);

            if (metricType == MetricType.WEIGHT_AND_REPS) {
                boolean[] showingOneRm = {false};
                chartToggle.setOnClickListener(v -> {
                    showingOneRm[0] = !showingOneRm[0];
                    if (showingOneRm[0]) {
                        chart.setData(oneRmValues, chartLabels);
                        chartToggle.setText("📈 Odhad. 1RM  (klikni pre max váhu)");
                    } else {
                        chart.setData(primaryValues, chartLabels);
                        chartToggle.setText("📈 Max váha  (klikni pre odhad. 1RM)");
                    }
                    ProgressHero.apply(this, heroValue, heroLabel, trendChip, metricType, showingOneRm[0],
                            showingOneRm[0] ? oneRmValues : primaryValues);
                });
            }
        } else {
            List<Float> single = new ArrayList<>();
            if (metricType == MetricType.DURATION) single.add((float) latestEx.maxDurationSeconds());
            else if (metricType == MetricType.BODYWEIGHT_AND_REPS) single.add((float) latestEx.maxReps());
            else single.add(latestEx.maxWeight());
            ProgressHero.apply(this, heroValue, heroLabel, trendChip, metricType, false, single);
        }

        // Osobný rekord = PRVÝ tréning (chronologicky), kde padla najvyššia váha — trofej dostane
        // len ten, nie každý ďalší tréning s rovnakou váhou
        float pb = 0;
        int pbChronoIndex = -1;
        for (int k = 0; k < entriesChrono.size(); k++) {
            float w = ((Exercise) entriesChrono.get(k)[1]).maxWeight();
            if (w > pb) {
                pb = w;
                pbChronoIndex = k;
            }
        }

        // Zobraz od najnovsieho — staršie tréningy (i > 0) idú do skladateľného historyContainer,
        // nech karta pri dlhej histórii nezaberá celú obrazovku a najdôležitejšie (posledný
        // tréning + graf) ostáva hneď viditeľné.
        List<Object[]> reversed = new ArrayList<>(entriesChrono);
        Collections.reverse(reversed);

        LinearLayout historyContainer = new LinearLayout(this);
        historyContainer.setOrientation(LinearLayout.VERTICAL);
        historyContainer.setVisibility(View.GONE);

        // Posledný tréning hneď; staršie sa vytvoria až pri prvom rozbalení (pri dlhej histórii
        // by inak otvorenie Progresu vytváralo stovky skrytých riadkov naraz)
        final int pbIndex = pbChronoIndex; // pre lambdu nižšie
        addHistoryEntry(card, reversed, 0, pbIndex);

        int olderCount = reversed.size() - 1;
        if (olderCount > 0) {
            TextView btnToggleHistory = new TextView(this);
            btnToggleHistory.setText("▾ Zobraziť históriu (" + olderCount + ")");
            btnToggleHistory.setTextColor(getColor(R.color.accent_primary));
            btnToggleHistory.setTextSize(11);
            btnToggleHistory.setPadding(0, dp(10), 0, dp(4));
            btnToggleHistory.setOnClickListener(v -> {
                if (historyContainer.getChildCount() == 0) {
                    for (int i = 1; i < reversed.size(); i++) {
                        addHistoryEntry(historyContainer, reversed, i, pbIndex);
                    }
                }
                boolean showing = historyContainer.getVisibility() == View.VISIBLE;
                historyContainer.setVisibility(showing ? View.GONE : View.VISIBLE);
                btnToggleHistory.setText((showing ? "▾ Zobraziť históriu (" : "▴ Skryť históriu (")
                        + olderCount + ")");
            });
            card.addView(btnToggleHistory);
            card.addView(historyContainer);
        }

        // Manuálne zlúčenie histórie (KROK 3) — napr. premenovanie starého neodborného
        // názvu ("žehlička") na nový oficiálny cvik z katalógu, bez straty odtrénovaných sérií.
        TextView btnMerge = new TextView(this);
        btnMerge.setText("🔗 Zlúčiť s iným cvikom");
        btnMerge.setTextColor(getColor(R.color.accent_primary));
        btnMerge.setTextSize(11);
        btnMerge.setPadding(0, dp(10), 0, 0);
        btnMerge.setOnClickListener(v -> showMergeDialog(name));
        card.addView(btnMerge);

        return card;
    }

    /**
     * Jeden tréning v karte cviku: dátum (+ 🏆 PB a šípka trendu) a jeho série.
     * reversed = najnovší prvý; i = index v ňom; pbChronoIndex = index rekordu v chronologickom poradí.
     */
    private void addHistoryEntry(LinearLayout target, List<Object[]> reversed, int i, int pbChronoIndex) {
        Object[] o = reversed.get(i);
        String dateKey = (String) o[0];
        Exercise ex = (Exercise) o[1];
        boolean isLatest = (i == 0);
        boolean isPB = (reversed.size() - 1 - i) == pbChronoIndex; // reversed[i] = entriesChrono[size-1-i]

        // Datum riadok
        LinearLayout rowHeader = new LinearLayout(this);
        rowHeader.setOrientation(LinearLayout.HORIZONTAL);
        rowHeader.setGravity(Gravity.CENTER_VERTICAL);
        rowHeader.setPadding(0, dp(6), 0, dp(2));

        TextView tvDate = new TextView(this);
        tvDate.setText(ProgressHero.formatNice(dateKey));
        tvDate.setTextColor(isLatest ? getColor(R.color.accent_primary_soft) : getColor(R.color.text_muted));
        tvDate.setTextSize(11);
        tvDate.setTypeface(null, isLatest ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        tvDate.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        rowHeader.addView(tvDate);

        if (isPB) {
            TextView tvPB = new TextView(this);
            tvPB.setText("🏆 PB");
            tvPB.setTextColor(getColor(R.color.accent_highlight));
            tvPB.setTextSize(11);
            rowHeader.addView(tvPB);
        }

        // Sipka trendu (porovnaj max weight s predchadzajucim zaznamom)
        if (i < reversed.size() - 1) {
            float prevMax = ((Exercise) reversed.get(i + 1)[1]).maxWeight();
            float curMax = ex.maxWeight();
            String arrow = curMax > prevMax ? " ↑" : curMax < prevMax ? " ↓" : "";
            if (!arrow.isEmpty()) {
                TextView tvArrow = new TextView(this);
                tvArrow.setText(arrow);
                tvArrow.setTextColor(curMax > prevMax ?
                        getColor(R.color.accent_primary_soft) : getColor(R.color.accent_danger));
                tvArrow.setTextSize(13);
                rowHeader.addView(tvArrow);
            }
        }

        target.addView(rowHeader);

        // Riadky serii
        for (int s = 0; s < ex.sets.size(); s++) {
            SetEntry se = ex.sets.get(s);
            TextView tvSet = new TextView(this);
            tvSet.setText("  Séria " + (s + 1) + ": " + se.toString());
            tvSet.setTextColor(isLatest ?
                    getColor(R.color.text_primary) : getColor(R.color.text_secondary));
            tvSet.setTextSize(12);
            tvSet.setTypeface(android.graphics.Typeface.MONOSPACE);
            target.addView(tvSet);
        }

        // Separator (nie za poslednym)
        if (i < reversed.size() - 1) {
            android.view.View sep = new android.view.View(this);
            LinearLayout.LayoutParams sepLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
            sepLp.topMargin = dp(6);
            sep.setLayoutParams(sepLp);
            sep.setBackgroundColor(getColor(R.color.separator));
            target.addView(sep);
        }
    }

    // ─── Manuálne zlúčenie histórie dvoch cvikov ──────────────────────────────

    /** Ponúkne výber cieľového (nového) cvičenia a po potvrdení zlúči doň históriu "oldName". */
    private void showMergeDialog(String oldName) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        for (Category c : Category.all()) candidates.addAll(ExerciseCatalog.getAll(this, c.key));
        for (Category c : Category.legacyAll()) candidates.addAll(ExerciseCatalog.getAll(this, c.key));
        candidates.addAll(ExerciseStore.getHistoryGroupedByExerciseName(this).keySet());
        candidates.remove(oldName);

        ScrollView dialogScroll = new ScrollView(this);
        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(dp(20), dp(16), dp(20), dp(16));
        dialogScroll.addView(dialogLayout);

        TextView info = new TextView(this);
        info.setText("Vyber nový (oficiálny) cvik — všetky doterajšie odtrénované série z „"
                + oldName + "“ sa prepíšu naň vo všetkých dňoch. Žiadna história sa nestratí.");
        info.setTextColor(getColor(R.color.text_secondary));
        info.setTextSize(12);
        info.setPadding(0, 0, 0, dp(10));
        dialogLayout.addView(info);

        AutoCompleteTextView etTarget = new AutoCompleteTextView(this);
        etTarget.setHint("Nový názov cvičenia");
        etTarget.setInputType(InputType.TYPE_CLASS_TEXT);
        etTarget.setTextColor(getColor(R.color.text_primary));
        etTarget.setHintTextColor(getColor(R.color.text_muted));
        etTarget.setBackground(UiKit.roundedBgWithBorder(
                this, R.color.bg_input, R.color.input_border, dp(1), UiKit.dimenPx(this, R.dimen.radius_chip)));
        etTarget.setPadding(dp(10), dp(8), dp(10), dp(8));
        etTarget.setThreshold(1);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, new ArrayList<>(candidates));
        etTarget.setAdapter(adapter);
        etTarget.setOnClickListener(v -> etTarget.showDropDown());
        dialogLayout.addView(etTarget);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Zlúčiť „" + oldName + "“ s iným cvikom")
                .setView(dialogScroll)
                .setPositiveButton("Zlúčiť", null) // listener nižšie, aby dialóg pri chybe nezmizol
                .setNegativeButton("Zrušiť", null)
                .setCancelable(false)
                .create();

        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setOnClickListener(v -> {
                String newName = etTarget.getText().toString().trim();
                if (newName.isEmpty()) {
                    Toast.makeText(this, "Vyber alebo napíš nový názov cvičenia.", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (newName.equals(oldName)) {
                    Toast.makeText(this, "Nový názov musí byť iný ako pôvodný.", Toast.LENGTH_SHORT).show();
                    return;
                }

                new AlertDialog.Builder(this)
                        .setTitle("Naozaj zlúčiť?")
                        .setMessage("„" + oldName + "“ → „" + newName + "“\n\nVšetky doterajšie série sa prepíšu pod nový názov. História (grafy, maximálky) sa zachová.")
                        .setPositiveButton("Zlúčiť", (confirmDialog, which) -> {
                            int changedDays = ExerciseMerger.merge(this, oldName, newName);
                            Toast.makeText(this,
                                    changedDays > 0
                                            ? "Zlúčené — upravených dní: " + changedDays
                                            : "Cvik „" + oldName + "“ sa v histórii nenašiel.",
                                    Toast.LENGTH_LONG).show();
                            dialog.dismiss();
                            recreate();
                        })
                        .setNegativeButton("Zrušiť", null)
                        .show();
            });
        });

        dialog.show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
