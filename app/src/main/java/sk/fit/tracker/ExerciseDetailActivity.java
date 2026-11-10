package sk.fit.tracker;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Detail jedného cviku so záložkami (inšpirované detailom cviku v Lyfte):
 *  - O cviku: fotky začiatku/konca pohybu, cieľové svaly na postave (MuscleMapView), náradie,
 *    poznámka, postup z free-exercise-db,
 *  - História: všetky odtrénované tréningy tohto cviku so sériami, objemom a označením rekordu,
 *  - Progres: posledná hodnota s trendom, graf (max váha / odhad 1RM / opakovania / výdrž) a rekordy.
 *
 * Otvára sa z katalógu (s tlačidlami Upraviť/Zmazať — tie vykoná katalóg, detail mu len vráti
 * EXTRA_ACTION) a z Progresu (ExerciseHistoryActivity).
 */
public class ExerciseDetailActivity extends Activity {

    public static final String EXTRA_NAME = "exercise_name";
    public static final String EXTRA_ACTION = "action";
    public static final String ACTION_EDIT = "edit";
    public static final String ACTION_DELETE = "delete";
    private static final String EXTRA_FROM_CATALOG = "from_catalog";
    private static final String STATE_TAB = "tab";

    private static final int TAB_ABOUT = 0;
    private static final int TAB_HISTORY = 1;
    private static final int TAB_PROGRESS = 2;
    private static final String[] TAB_LABELS = {"O cviku", "História", "Progres"};

    private String name;
    private String categoryKey;
    private boolean fromCatalog;
    /** Chronologicky (najstarší prvý): [dateKey, Exercise]. */
    private List<Object[]> history = new ArrayList<>();

    private LinearLayout tabRow;
    private LinearLayout content;
    private ScrollView scroll;
    private int currentTab = TAB_ABOUT;

    public static Intent intent(Context ctx, String exerciseName, boolean fromCatalog) {
        return new Intent(ctx, ExerciseDetailActivity.class)
                .putExtra(EXTRA_NAME, exerciseName)
                .putExtra(EXTRA_FROM_CATALOG, fromCatalog);
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(ThemeStore.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiKit.edgeToEdge(this); // odstup od stavového riadku, navigácie a klávesnice (Android 15+)
        name = getIntent().getStringExtra(EXTRA_NAME);
        if (name == null) {
            finish();
            return;
        }
        fromCatalog = getIntent().getBooleanExtra(EXTRA_FROM_CATALOG, false);
        categoryKey = ExerciseCatalog.findCategoryKeyForExercise(this, name);
        List<Object[]> h = ExerciseStore.getHistoryGroupedByExerciseName(this).get(name);
        if (h != null) history = h;

        // Bez histórie začni na "O cviku", inak na Progrese (ak prišiel používateľ z Progresu)
        currentTab = savedInstanceState != null ? savedInstanceState.getInt(STATE_TAB, TAB_ABOUT)
                : (!fromCatalog && !history.isEmpty() ? TAB_PROGRESS : TAB_ABOUT);

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(getColor(R.color.bg_app));
        setContentView(outer);

        outer.addView(buildHeader());

        tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        tabRow.setPadding(dp(16), dp(4), dp(16), dp(8));
        for (int i = 0; i < TAB_LABELS.length; i++) {
            final int tab = i;
            TextView t = new TextView(this);
            t.setText(TAB_LABELS[i]);
            t.setTextSize(13);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(9), 0, dp(9));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) lp.leftMargin = dp(6);
            t.setLayoutParams(lp);
            t.setOnClickListener(v -> selectTab(tab));
            tabRow.addView(t);
        }
        outer.addView(tabRow);

        scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(4), dp(16), dp(16));
        scroll.addView(content);
        outer.addView(scroll);

        Button btnBack = new Button(this);
        btnBack.setText("← Späť");
        btnBack.setTextColor(getColor(R.color.text_primary));
        btnBack.setBackgroundColor(getColor(R.color.bg_button_neutral));
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        backLp.setMargins(dp(16), dp(8), dp(16), dp(16));
        btnBack.setLayoutParams(backLp);
        btnBack.setOnClickListener(v -> finish());
        outer.addView(btnBack);

        selectTab(currentTab);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, currentTab);
    }

    // ---------------------------------------------------------------- hlavička & záložky

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(16), dp(16), dp(6));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(texts);

        TextView title = new TextView(this);
        title.setText(name);
        title.setTextColor(getColor(R.color.text_primary));
        title.setTextSize(19);
        title.setTypeface(null, Typeface.BOLD);
        texts.addView(title);

        Category cat = categoryKey != null ? Category.byKey(categoryKey) : null;
        TextView subtitle = new TextView(this);
        String sub = cat != null ? cat.label : "Vlastný cvik";
        if (!history.isEmpty()) {
            sub += " · " + history.size() + (history.size() == 1 ? " tréning" : history.size() < 5 ? " tréningy" : " tréningov");
        }
        subtitle.setText(sub);
        subtitle.setTextColor(getColor(R.color.text_muted));
        subtitle.setTextSize(12);
        subtitle.setPadding(0, dp(2), 0, 0);
        texts.addView(subtitle);

        TextView star = new TextView(this);
        star.setTextSize(22);
        star.setGravity(Gravity.CENTER);
        star.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(44)));
        star.setBackground(UiKit.roundedBg(this, R.color.bg_chip, dp(22)));
        bindStar(star);
        star.setOnClickListener(v -> {
            FavoritesStore.toggle(this, name);
            bindStar(star);
        });
        header.addView(star);
        return header;
    }

    private void bindStar(TextView star) {
        boolean fav = FavoritesStore.isFavorite(this, name);
        star.setText(fav ? "★" : "☆");
        star.setTextColor(getColor(fav ? R.color.accent_primary : R.color.text_muted));
        star.setContentDescription(fav ? "Odobrať z obľúbených" : "Pridať medzi obľúbené");
    }

    private void selectTab(int tab) {
        currentTab = tab;
        int radius = UiKit.dimenPx(this, R.dimen.radius_chip);
        for (int i = 0; i < tabRow.getChildCount(); i++) {
            TextView t = (TextView) tabRow.getChildAt(i);
            boolean sel = i == tab;
            t.setBackground(sel
                    ? UiKit.roundedBgWithBorder(this, R.color.accent_primary_soft, R.color.accent_primary, dp(1), radius)
                    : UiKit.roundedBgWithBorder(this, R.color.bg_chip, R.color.separator, dp(1), radius));
            t.setTextColor(getColor(sel ? R.color.text_on_accent : R.color.text_secondary));
            t.setTypeface(null, sel ? Typeface.BOLD : Typeface.NORMAL);
        }
        content.removeAllViews();
        switch (tab) {
            case TAB_HISTORY: buildHistory(); break;
            case TAB_PROGRESS: buildProgress(); break;
            default: buildAbout();
        }
        scroll.scrollTo(0, 0);
    }

    // ---------------------------------------------------------------- O cviku

    private void buildAbout() {
        ExerciseMedia.Info media = ExerciseMedia.get(this, name);

        LinearLayout photos = new LinearLayout(this);
        photos.setOrientation(LinearLayout.HORIZONTAL);
        int photoCount = media != null ? Math.max(1, Math.min(2, media.imageCount)) : 1;
        for (int i = 0; i < photoCount; i++) {
            ImageView iv = new ImageView(this);
            iv.setBackground(UiKit.roundedBg(this, R.color.bg_card_alt, UiKit.dimenPx(this, R.dimen.radius_chip)));
            iv.setClipToOutline(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(130), 1f);
            if (i > 0) lp.leftMargin = dp(8);
            iv.setLayoutParams(lp);
            ExerciseMedia.bindPhoto(this, iv, name, categoryKey, i);
            photos.addView(iv);
        }
        content.addView(photos);

        List<String> primary = media != null ? media.primaryMuscles : MuscleMapView.musclesForCategory(categoryKey);
        List<String> secondary = media != null ? media.secondaryMuscles : new ArrayList<>();

        LinearLayout musclesCard = UiKit.card(this);
        musclesCard.addView(cardTitle("Cieľové svaly"));
        MuscleMapView map = new MuscleMapView(this);
        map.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        map.setPadding(dp(8), dp(4), dp(8), 0);
        map.setMuscles(primary, secondary);
        musclesCard.addView(map);
        musclesCard.addView(dotLine("Hlavné", muscleLabels(primary), R.color.muscle_highlight));
        if (!secondary.isEmpty()) {
            musclesCard.addView(dotLine("Pomocné", muscleLabels(secondary), R.color.muscle_secondary));
        }
        TextView credit = new TextView(this);
        credit.setText("Anatómia: Z-Anatomy, CC BY-SA 4.0");
        credit.setTextColor(getColor(R.color.text_muted));
        credit.setTextSize(9);
        credit.setPadding(0, dp(8), 0, 0);
        musclesCard.addView(credit);
        content.addView(musclesCard, topMargin(12));

        LinearLayout infoCard = UiKit.card(this);
        infoCard.addView(cardTitle("Informácie"));
        if (media != null && media.equipment != null) {
            infoCard.addView(plainLine("Náradie", ExerciseMedia.equipmentLabel(media.equipment)));
        }
        // Poznámka priamo podľa mena (predtým sa kvôli nej prechádzal celý katalóg)
        String note = ExerciseNotesStore.getNote(this, name);
        if (!note.isEmpty()) {
            infoCard.addView(plainLine("Poznámka", note));
        }
        // Posledný tréning z už načítanej histórie (predtým sa kvôli nemu znova čítali všetky dáta)
        if (!history.isEmpty()) {
            String lastDate = (String) history.get(history.size() - 1)[0];
            infoCard.addView(plainLine("Naposledy", ProgressHero.formatNice(lastDate) + " — " + latest().summary()));
        }
        if (infoCard.getChildCount() > 1) content.addView(infoCard, topMargin(12));

        // Slovenský preklad postupu, anglický originál z databázy len ako záloha
        boolean slovak = media != null && !media.instructionsSk.isEmpty();
        List<String> steps = media == null ? new ArrayList<>() : slovak ? media.instructionsSk : media.instructions;
        if (!steps.isEmpty() && FeatureStore.isEnabled(this, FeatureStore.GUIDES)) {
            LinearLayout howCard = UiKit.card(this);
            howCard.addView(cardTitle("Postup"));
            TextView lang = new TextView(this);
            lang.setText(slovak ? "Podľa databázy free-exercise-db" : "Z databázy free-exercise-db (v angličtine)");
            lang.setTextColor(getColor(R.color.text_muted));
            lang.setTextSize(11);
            lang.setPadding(0, 0, 0, dp(6));
            howCard.addView(lang);
            for (int i = 0; i < steps.size(); i++) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(0, dp(5), 0, dp(5));
                TextView num = new TextView(this);
                num.setText(String.valueOf(i + 1));
                num.setTextColor(getColor(R.color.text_on_accent));
                num.setTextSize(11);
                num.setTypeface(null, Typeface.BOLD);
                num.setGravity(Gravity.CENTER);
                num.setBackground(UiKit.roundedBg(this, R.color.accent_primary_soft, dp(11)));
                LinearLayout.LayoutParams numLp = new LinearLayout.LayoutParams(dp(22), dp(22));
                numLp.rightMargin = dp(10);
                num.setLayoutParams(numLp);
                row.addView(num);
                TextView step = new TextView(this);
                step.setText(steps.get(i));
                step.setTextColor(getColor(R.color.text_secondary));
                step.setTextSize(13);
                step.setLineSpacing(0, 1.15f);
                step.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                row.addView(step);
                howCard.addView(row);
            }
            content.addView(howCard, topMargin(12));
        }

        if (fromCatalog) {
            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.addView(actionButton("✏️ Upraviť", ACTION_EDIT, R.color.text_primary), weighted(0));
            actions.addView(actionButton("🗑 Zmazať z katalógu", ACTION_DELETE, R.color.accent_danger), weighted(8));
            content.addView(actions, topMargin(14));
        }
    }

    private TextView actionButton(String text, String action, int colorRes) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextColor(getColor(colorRes));
        b.setTextSize(13);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(8), dp(11), dp(8), dp(11));
        b.setBackground(UiKit.roundedBgWithBorder(this, R.color.bg_chip, R.color.separator, dp(1),
                UiKit.dimenPx(this, R.dimen.radius_chip)));
        b.setOnClickListener(v -> {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_NAME, name).putExtra(EXTRA_ACTION, action));
            finish();
        });
        return b;
    }

    // ---------------------------------------------------------------- História

    private void buildHistory() {
        if (history.isEmpty()) {
            content.addView(emptyText("Tento cvik si ešte necvičil. Keď ho zapíšeš pri tréningu v kalendári, "
                    + "uvidíš tu všetky série."));
            return;
        }
        MetricType type = latest().metricType();
        // Trofej len pre PRVÝ tréning (chronologicky), kde padla najvyššia váha
        float pb = 0;
        int pbChronoIndex = -1;
        for (int k = 0; k < history.size(); k++) {
            float w = ((Exercise) history.get(k)[1]).maxWeight();
            if (w > pb) {
                pb = w;
                pbChronoIndex = k;
            }
        }

        List<Object[]> reversed = new ArrayList<>(history);
        Collections.reverse(reversed);
        for (int i = 0; i < reversed.size(); i++) {
            String dateKey = (String) reversed.get(i)[0];
            Exercise ex = (Exercise) reversed.get(i)[1];
            LinearLayout card = UiKit.card(this);

            LinearLayout head = new LinearLayout(this);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            TextView date = new TextView(this);
            date.setText(ProgressHero.formatNice(dateKey));
            date.setTextColor(getColor(i == 0 ? R.color.accent_primary_soft : R.color.text_primary));
            date.setTextSize(14);
            date.setTypeface(null, Typeface.BOLD);
            date.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            head.addView(date);
            if (type == MetricType.WEIGHT_AND_REPS && (reversed.size() - 1 - i) == pbChronoIndex) {
                TextView badge = new TextView(this);
                badge.setText("🏆 PB");
                badge.setTextColor(getColor(R.color.accent_highlight));
                badge.setTextSize(12);
                head.addView(badge);
            }
            card.addView(head);

            for (int s = 0; s < ex.sets.size(); s++) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(0, dp(6), 0, 0);
                TextView idx = new TextView(this);
                idx.setText(String.valueOf(s + 1));
                idx.setTextColor(getColor(R.color.text_muted));
                idx.setTextSize(12);
                idx.setLayoutParams(new LinearLayout.LayoutParams(dp(24), LinearLayout.LayoutParams.WRAP_CONTENT));
                row.addView(idx);
                TextView set = new TextView(this);
                set.setText(ex.sets.get(s).toString());
                set.setTextColor(getColor(R.color.text_secondary));
                set.setTextSize(13);
                set.setTypeface(Typeface.MONOSPACE);
                row.addView(set);
                card.addView(row);
            }

            String footer = ex.sets.size() + (ex.sets.size() == 1 ? " séria" : ex.sets.size() < 5 ? " série" : " sérií");
            if (type == MetricType.WEIGHT_AND_REPS) {
                footer += " · objem " + ProgressHero.formatKg(ex.totalVolume()) + " kg"
                        + " · 1RM ≈ " + ProgressHero.formatKg(ex.getMaxEstimated1RM()) + " kg";
            }
            float avgRpe = ex.averageRpe();
            if (avgRpe > 0) {
                footer += " · RPE ⌀ " + SetEntry.formatRpe(Math.round(avgRpe * 2f) / 2f);
            }
            TextView foot = new TextView(this);
            foot.setText(footer);
            foot.setTextColor(getColor(R.color.text_muted));
            foot.setTextSize(11);
            foot.setPadding(0, dp(8), 0, 0);
            card.addView(foot);

            content.addView(card, topMargin(i == 0 ? 0 : 10));
        }
    }

    // ---------------------------------------------------------------- Progres

    private void buildProgress() {
        if (history.isEmpty()) {
            content.addView(emptyText("Zatiaľ žiadny progres — zapíš prvý tréning tohto cviku a tu uvidíš "
                    + "graf, osobné rekordy a odhad 1RM."));
            return;
        }
        MetricType type = latest().metricType();

        List<Float> primaryValues = new ArrayList<>();
        List<Float> oneRmValues = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (Object[] o : history) {
            Exercise ex = (Exercise) o[1];
            labels.add(ProgressHero.formatNice((String) o[0]));
            if (type == MetricType.DURATION) primaryValues.add((float) ex.maxDurationSeconds());
            else if (type == MetricType.BODYWEIGHT_AND_REPS) primaryValues.add((float) ex.maxReps());
            else {
                primaryValues.add(ex.maxWeight());
                oneRmValues.add(ex.getMaxEstimated1RM());
            }
        }

        LinearLayout card = UiKit.card(this);
        LinearLayout heroRow = new LinearLayout(this);
        heroRow.setOrientation(LinearLayout.HORIZONTAL);
        heroRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout heroLeft = new LinearLayout(this);
        heroLeft.setOrientation(LinearLayout.VERTICAL);
        heroLeft.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView heroValue = new TextView(this);
        heroValue.setTextColor(getColor(R.color.accent_primary_soft));
        heroValue.setTextSize(26);
        heroValue.setTypeface(null, Typeface.BOLD);
        heroLeft.addView(heroValue);
        TextView heroLabel = new TextView(this);
        heroLabel.setTextColor(getColor(R.color.text_muted));
        heroLabel.setTextSize(11);
        heroLeft.addView(heroLabel);
        heroRow.addView(heroLeft);
        TextView trend = new TextView(this);
        trend.setTextSize(12);
        trend.setTypeface(null, Typeface.BOLD);
        trend.setPadding(dp(10), dp(5), dp(10), dp(5));
        heroRow.addView(trend);
        card.addView(heroRow);
        ProgressHero.apply(this, heroValue, heroLabel, trend, type, false, primaryValues);

        if (primaryValues.size() >= 2) {
            LinearLayout toggle = new LinearLayout(this);
            toggle.setOrientation(LinearLayout.HORIZONTAL);
            toggle.setPadding(0, dp(12), 0, dp(6));
            LineChartView chart = new LineChartView(this);
            LinearLayout.LayoutParams chartLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(150));
            chart.setLayoutParams(chartLp);
            chart.setData(primaryValues, labels);

            if (type == MetricType.WEIGHT_AND_REPS) {
                TextView a = smallChip("Max váha");
                TextView b = smallChip("Odhad 1RM");
                Runnable[] select = new Runnable[2];
                select[0] = () -> {
                    styleSmallChip(a, true);
                    styleSmallChip(b, false);
                    chart.setData(primaryValues, labels);
                    ProgressHero.apply(this, heroValue, heroLabel, trend, type, false, primaryValues);
                };
                select[1] = () -> {
                    styleSmallChip(a, false);
                    styleSmallChip(b, true);
                    chart.setData(oneRmValues, labels);
                    ProgressHero.apply(this, heroValue, heroLabel, trend, type, true, oneRmValues);
                };
                a.setOnClickListener(v -> select[0].run());
                b.setOnClickListener(v -> select[1].run());
                toggle.addView(a);
                toggle.addView(b);
                select[0].run();
            } else {
                TextView only = smallChip(type == MetricType.DURATION ? "Najdlhšia výdrž" : "Max opakovania");
                styleSmallChip(only, true);
                toggle.addView(only);
            }
            card.addView(toggle);
            card.addView(chart);
        } else {
            TextView hint = new TextView(this);
            hint.setText("Graf sa zobrazí od druhého tréningu.");
            hint.setTextColor(getColor(R.color.text_muted));
            hint.setTextSize(11);
            hint.setPadding(0, dp(10), 0, 0);
            card.addView(hint);
        }
        content.addView(card);

        // Rekordy a súhrn
        LinearLayout records = UiKit.card(this);
        records.addView(cardTitle("🏆 Rekordy"));
        if (type == MetricType.WEIGHT_AND_REPS) {
            Object[] maxW = best(o -> ((Exercise) o[1]).maxWeight());
            Object[] max1rm = best(o -> ((Exercise) o[1]).getMaxEstimated1RM());
            Object[] maxVol = best(o -> ((Exercise) o[1]).totalVolume());
            records.addView(recordRow("Max váha", ProgressHero.formatKg((Float) maxW[1]) + " kg", (String) maxW[0]));
            records.addView(recordRow("Odhad 1RM", ProgressHero.formatKg((Float) max1rm[1]) + " kg", (String) max1rm[0]));
            records.addView(recordRow("Objem na tréning", ProgressHero.formatKg((Float) maxVol[1]) + " kg", (String) maxVol[0]));
            float total = 0;
            for (Object[] o : history) total += ((Exercise) o[1]).totalVolume();
            records.addView(recordRow("Celkový objem", ProgressHero.formatKg(total) + " kg", null));
        } else if (type == MetricType.BODYWEIGHT_AND_REPS) {
            Object[] maxR = best(o -> (float) ((Exercise) o[1]).maxReps());
            records.addView(recordRow("Max opakovaní v sérii", Math.round((Float) maxR[1]) + "", (String) maxR[0]));
        } else {
            Object[] maxD = best(o -> (float) ((Exercise) o[1]).maxDurationSeconds());
            records.addView(recordRow("Najdlhšia výdrž", SetEntry.formatDuration(Math.round((Float) maxD[1])), (String) maxD[0]));
        }
        int sets = 0;
        for (Object[] o : history) sets += ((Exercise) o[1]).sets.size();
        records.addView(recordRow("Tréningov / sérií", history.size() + " / " + sets, null));
        content.addView(records, topMargin(12));
    }

    private interface Metric {
        float of(Object[] entry);
    }

    /** {dateKey, Float} tréningu s najvyššou hodnotou metriky (pri zhode ten skorší — vtedy padol rekord). */
    private Object[] best(Metric m) {
        Object[] best = null;
        float bestV = -1;
        for (Object[] o : history) {
            float v = m.of(o);
            if (v > bestV) {
                bestV = v;
                best = o;
            }
        }
        return new Object[]{best != null ? best[0] : null, Math.max(0f, bestV)};
    }

    private View recordRow(String label, String value, String dateKey) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(7), 0, dp(7));
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(getColor(R.color.text_secondary));
        l.setTextSize(13);
        left.addView(l);
        if (dateKey != null) {
            TextView d = new TextView(this);
            d.setText(ProgressHero.formatNice(dateKey));
            d.setTextColor(getColor(R.color.text_muted));
            d.setTextSize(11);
            left.addView(d);
        }
        row.addView(left);
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(getColor(R.color.accent_highlight));
        v.setTextSize(15);
        v.setTypeface(null, Typeface.BOLD);
        row.addView(v);
        return row;
    }

    private TextView smallChip(String text) {
        TextView c = new TextView(this);
        c.setText(text);
        c.setTextSize(12);
        c.setPadding(dp(12), dp(6), dp(12), dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(6);
        c.setLayoutParams(lp);
        return c;
    }

    private void styleSmallChip(TextView c, boolean selected) {
        int radius = UiKit.dimenPx(this, R.dimen.radius_chip);
        c.setBackground(selected
                ? UiKit.roundedBgWithBorder(this, R.color.accent_primary_soft, R.color.accent_primary, dp(1), radius)
                : UiKit.roundedBgWithBorder(this, R.color.bg_chip, R.color.separator, dp(1), radius));
        c.setTextColor(getColor(selected ? R.color.text_on_accent : R.color.text_secondary));
    }

    // ---------------------------------------------------------------- pomocné

    private Exercise latest() {
        return (Exercise) history.get(history.size() - 1)[1];
    }

    private TextView cardTitle(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(getColor(R.color.text_primary));
        t.setTextSize(15);
        t.setTypeface(null, Typeface.BOLD);
        t.setPadding(0, 0, 0, dp(6));
        return t;
    }

    private TextView dotLine(String label, String value, int dotColorRes) {
        TextView tv = new TextView(this);
        tv.setTextColor(getColor(R.color.text_secondary));
        tv.setTextSize(13);
        tv.setPadding(0, dp(6), 0, 0);
        SpannableString span = new SpannableString("● " + label + ": " + value);
        span.setSpan(new ForegroundColorSpan(getColor(dotColorRes)), 0, 1, 0);
        tv.setText(span);
        return tv;
    }

    private TextView plainLine(String label, String value) {
        TextView tv = new TextView(this);
        tv.setText(label + ": " + value);
        tv.setTextColor(getColor(R.color.text_secondary));
        tv.setTextSize(13);
        tv.setPadding(0, dp(4), 0, dp(2));
        return tv;
    }

    private TextView emptyText(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(getColor(R.color.text_muted));
        tv.setTextSize(13);
        tv.setPadding(0, dp(12), 0, 0);
        return tv;
    }

    private static String muscleLabels(List<String> muscles) {
        List<String> labels = new ArrayList<>();
        for (String m : muscles) labels.add(MuscleMapView.label(m));
        return labels.isEmpty() ? "—" : String.join(", ", labels);
    }

    private LinearLayout.LayoutParams topMargin(int dpTop) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(dpTop);
        return lp;
    }

    private LinearLayout.LayoutParams weighted(int dpLeft) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = dp(dpLeft);
        return lp;
    }

    private int dp(int v) {
        return UiKit.dp(this, v);
    }
}
