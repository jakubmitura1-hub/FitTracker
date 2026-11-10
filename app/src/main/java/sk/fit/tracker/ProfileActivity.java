package sk.fit.tracker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

public class ProfileActivity extends Activity {

    private static final int REQ_EXPORT = 1001;
    private static final int REQ_IMPORT = 1002;

    /** Jedno sledované telesné meranie (obvod pása/hrudníka/bicepsu/stehna...) — vstupné pole + jeho história. */
    private static class MeasurementField {
        final String inputLabel;
        final String inputHintExample;
        final String historyTitle;
        final String emptyHistoryText;
        final BodyMeasurementStore store;
        EditText input;
        LinearLayout historyContainer;

        MeasurementField(String inputLabel, String inputHintExample, String historyTitle,
                          String emptyHistoryText, BodyMeasurementStore store) {
            this.inputLabel = inputLabel;
            this.inputHintExample = inputHintExample;
            this.historyTitle = historyTitle;
            this.emptyHistoryText = emptyHistoryText;
            this.store = store;
        }
    }

    private EditText etHeight;
    private TextView tvFocus;
    private View focusCard;
    private TextView tvModeDesc;
    private final java.util.List<TextView> modeChips = new ArrayList<>();
    private final java.util.Map<String, android.widget.Switch> featureSwitches = new java.util.LinkedHashMap<>();
    private EditText etWeight;
    private TextView tvBackupStatus;
    private LinearLayout weightHistoryContainer;
    private final List<MeasurementField> measurementFields = new ArrayList<>();

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(ThemeStore.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiKit.edgeToEdge(this); // odstup od stavového riadku, navigácie a klávesnice (Android 15+)

        ScrollView scroll = new ScrollView(this);
        scroll.setId(R.id.profile_scroll); // stabilné ID — po otočení sa zachová pozícia rolovania
        scroll.setBackgroundColor(getColor(R.color.bg_app));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText("👤 Profil");
        title.setTextColor(getColor(R.color.text_primary));
        title.setTextSize(19);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Výška, váha a telesné obvody (pás, hrudník, biceps, stehno) — sledovanie postavy a vstup pre výpočet spálených kalórií z tréningu.");
        subtitle.setTextColor(getColor(R.color.text_muted));
        subtitle.setTextSize(12);
        subtitle.setPadding(0, dp(2), 0, dp(20));
        root.addView(subtitle);

        // ─── Fokus (partie z onboardingu) ────────────────────────────────────
        LinearLayout focusCard = UiKit.card(this);
        focusCard.setOrientation(LinearLayout.HORIZONTAL);
        focusCard.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout focusTexts = new LinearLayout(this);
        focusTexts.setOrientation(LinearLayout.VERTICAL);
        focusTexts.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView focusTitle = new TextView(this);
        focusTitle.setText("🎯 Môj fokus");
        focusTitle.setTextColor(getColor(R.color.text_primary));
        focusTitle.setTextSize(14);
        focusTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        focusTexts.addView(focusTitle);
        tvFocus = new TextView(this);
        tvFocus.setTextColor(getColor(R.color.text_secondary));
        tvFocus.setTextSize(12);
        tvFocus.setPadding(0, dp(2), 0, 0);
        focusTexts.addView(tvFocus);
        focusCard.addView(focusTexts);
        TextView focusChange = new TextView(this);
        focusChange.setText("Zmeniť ›");
        focusChange.setTextColor(getColor(R.color.accent_primary));
        focusChange.setTextSize(13);
        focusCard.addView(focusChange);
        focusCard.setOnClickListener(v -> startActivity(OnboardingActivity.intent(this, true)));
        root.addView(focusCard);
        this.focusCard = focusCard;

        root.addView(buildModeCard());

        LinearLayout card = UiKit.card(this);

        TextView heightLabel = new TextView(this);
        heightLabel.setText("Výška (cm)");
        heightLabel.setTextColor(getColor(R.color.text_secondary));
        heightLabel.setTextSize(12);
        heightLabel.setPadding(0, 0, 0, dp(4));
        card.addView(heightLabel);

        etHeight = makeInput("napr. 180");
        etHeight.setId(R.id.profile_height); // stabilné ID — rozpísaný text prežije otočenie telefónu
        card.addView(etHeight);

        TextView weightLabel = new TextView(this);
        weightLabel.setText("Váha dnes (kg)");
        weightLabel.setTextColor(getColor(R.color.text_secondary));
        weightLabel.setTextSize(12);
        weightLabel.setPadding(0, dp(14), 0, dp(4));
        card.addView(weightLabel);

        float latestWeight = ProfileStore.getWeightKg(this);
        etWeight = makeInput(latestWeight > 0 ? "posledná: " + formatNum(latestWeight) + " kg" : "napr. 82.5");
        etWeight.setId(R.id.profile_weight);
        card.addView(etWeight);

        // Sledované telesné obvody — obvod pása (hlavný ukazovateľ chudnutia) prvý, potom
        // hrudník, biceps, stehno. Rovnaký vzor pre všetky: karta vstupu + samostatná história.
        measurementFields.add(new MeasurementField(
                "Obvod pása dnes (cm, v pupku) — hlavný ukazovateľ chudnutia",
                "napr. 85", "📏 História obvodu pása", "Zatiaľ žiadny záznam obvodu pása.",
                new BodyMeasurementStore("FitTrackerWaist")));
        measurementFields.add(new MeasurementField(
                "Obvod hrudníka dnes (cm)",
                "napr. 100", "📏 História obvodu hrudníka", "Zatiaľ žiadny záznam obvodu hrudníka.",
                new BodyMeasurementStore("FitTrackerChest")));
        measurementFields.add(new MeasurementField(
                "Obvod bicepsu dnes (cm, zaťatý)",
                "napr. 38", "📏 História obvodu bicepsu", "Zatiaľ žiadny záznam obvodu bicepsu.",
                new BodyMeasurementStore("FitTrackerBiceps")));
        measurementFields.add(new MeasurementField(
                "Obvod stehna dnes (cm)",
                "napr. 58", "📏 História obvodu stehna", "Zatiaľ žiadny záznam obvodu stehna.",
                new BodyMeasurementStore("FitTrackerThigh")));

        // Stabilné ID polí obvodov v rovnakom poradí ako measurementFields (pás, hrudník, biceps, stehno)
        int[] measurementIds = {R.id.profile_waist, R.id.profile_chest, R.id.profile_biceps, R.id.profile_thigh};
        for (int i = 0; i < measurementFields.size(); i++) {
            MeasurementField f = measurementFields.get(i);
            TextView label = new TextView(this);
            label.setText(f.inputLabel);
            label.setTextColor(getColor(R.color.text_secondary));
            label.setTextSize(12);
            label.setPadding(0, dp(14), 0, dp(4));
            card.addView(label);

            float latest = f.store.getLatest(this);
            f.input = makeInput(latest > 0 ? "posledný: " + formatNum(latest) + " cm" : f.inputHintExample);
            if (i < measurementIds.length) f.input.setId(measurementIds[i]);
            card.addView(f.input);
        }

        float existingHeight = ProfileStore.getHeightCm(this);
        if (existingHeight > 0) etHeight.setText(formatNum(existingHeight));
        // Váha a telesné obvody sa NEPREDVYPĹŇAJÚ — sú to dátumové záznamy, prázdne pole = dnes sa nič nové nezaznamenáva.

        root.addView(card);

        Button btnSave = new Button(this);
        btnSave.setText("✔ Uložiť");
        btnSave.setTextColor(getColor(R.color.accent_primary_dark));
        btnSave.setBackgroundResource(R.drawable.bg_button_primary);
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        saveLp.topMargin = dp(16);
        btnSave.setLayoutParams(saveLp);
        btnSave.setOnClickListener(v -> saveProfile());
        root.addView(btnSave);

        // ─── História váhy ──────────────────────────────────────────────────
        root.addView(UiKit.sectionHeader(this, "📈 História váhy"));

        LinearLayout historyCard = UiKit.card(this);
        weightHistoryContainer = new LinearLayout(this);
        weightHistoryContainer.setOrientation(LinearLayout.VERTICAL);
        historyCard.addView(weightHistoryContainer);
        root.addView(historyCard);

        renderWeightHistory();

        // ─── História telesných obvodov (pás, hrudník, biceps, stehno) ──────
        for (MeasurementField f : measurementFields) {
            root.addView(UiKit.sectionHeader(this, f.historyTitle));

            LinearLayout measurementHistoryCard = UiKit.card(this);
            f.historyContainer = new LinearLayout(this);
            f.historyContainer.setOrientation(LinearLayout.VERTICAL);
            measurementHistoryCard.addView(f.historyContainer);
            root.addView(measurementHistoryCard);

            renderMeasurementHistory(f);
        }

        // ─── Záloha dát ─────────────────────────────────────────────────────
        TextView backupHeader = UiKit.sectionHeader(this, "💾 Záloha dát");
        root.addView(backupHeader);

        LinearLayout backupCard = UiKit.card(this);

        TextView backupDesc = new TextView(this);
        backupDesc.setText("Uloží tréningy, kardio, vlastné cviky aj profil do jedného súboru — "
                + "vyberieš si kam (napr. priamo do Google Drive, ak máš appku nainštalovanú).");
        backupDesc.setTextColor(getColor(R.color.text_secondary));
        backupDesc.setTextSize(12);
        backupDesc.setPadding(0, 0, 0, dp(10));
        backupCard.addView(backupDesc);

        tvBackupStatus = new TextView(this);
        tvBackupStatus.setTextColor(getColor(R.color.text_muted));
        tvBackupStatus.setTextSize(11);
        tvBackupStatus.setPadding(0, 0, 0, dp(10));
        updateBackupStatusLabel();
        backupCard.addView(tvBackupStatus);

        TextView btnBackup = new TextView(this);
        btnBackup.setText("💾 Zálohovať dáta");
        btnBackup.setTextColor(getColor(R.color.accent_primary_dark));
        btnBackup.setTextSize(13);
        btnBackup.setTypeface(null, android.graphics.Typeface.BOLD);
        btnBackup.setGravity(android.view.Gravity.CENTER);
        btnBackup.setPadding(0, dp(12), 0, dp(12));
        btnBackup.setBackgroundResource(R.drawable.bg_button_primary);
        LinearLayout.LayoutParams btnBackupLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnBackupLp.bottomMargin = dp(8);
        btnBackup.setLayoutParams(btnBackupLp);
        btnBackup.setOnClickListener(v -> startExport());
        backupCard.addView(btnBackup);

        TextView btnShare = new TextView(this);
        btnShare.setText("📤 Odoslať zálohu do Google Disku a pod.");
        btnShare.setTextColor(getColor(R.color.text_primary));
        btnShare.setTextSize(13);
        btnShare.setGravity(android.view.Gravity.CENTER);
        btnShare.setPadding(0, dp(12), 0, dp(12));
        btnShare.setBackground(UiKit.roundedBgWithBorder(
                this, R.color.bg_input, R.color.input_border, dp(1), UiKit.dimenPx(this, R.dimen.radius_button)));
        LinearLayout.LayoutParams btnShareLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnShareLp.bottomMargin = dp(8);
        btnShare.setLayoutParams(btnShareLp);
        btnShare.setOnClickListener(v -> shareBackup());
        backupCard.addView(btnShare);

        TextView btnShareApk = new TextView(this);
        btnShareApk.setText("📦 Odoslať appku (.apk) do Google Disku a pod.");
        btnShareApk.setTextColor(getColor(R.color.text_primary));
        btnShareApk.setTextSize(13);
        btnShareApk.setGravity(android.view.Gravity.CENTER);
        btnShareApk.setPadding(0, dp(12), 0, dp(12));
        btnShareApk.setBackground(UiKit.roundedBgWithBorder(
                this, R.color.bg_input, R.color.input_border, dp(1), UiKit.dimenPx(this, R.dimen.radius_button)));
        LinearLayout.LayoutParams btnShareApkLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnShareApkLp.bottomMargin = dp(8);
        btnShareApk.setLayoutParams(btnShareApkLp);
        btnShareApk.setOnClickListener(v -> shareApk());
        backupCard.addView(btnShareApk);

        TextView btnRestore = new TextView(this);
        btnRestore.setText("📥 Obnoviť zo zálohy");
        btnRestore.setTextColor(getColor(R.color.text_primary));
        btnRestore.setTextSize(13);
        btnRestore.setGravity(android.view.Gravity.CENTER);
        btnRestore.setPadding(0, dp(12), 0, dp(12));
        btnRestore.setBackground(UiKit.roundedBgWithBorder(
                this, R.color.bg_input, R.color.input_border, dp(1), UiKit.dimenPx(this, R.dimen.radius_button)));
        btnRestore.setOnClickListener(v -> startImport());
        backupCard.addView(btnRestore);

        // Návrat k dátam spred poslednej obnovy — len ak záchranná kópia existuje
        if (BackupManager.hasSafetyCopy(this)) {
            SimpleDateFormat fmt = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
            TextView btnUndo = new TextView(this);
            btnUndo.setText("↩ Vrátiť dáta spred obnovy ("
                    + fmt.format(new Date(BackupManager.safetyCopyTime(this))) + ")");
            btnUndo.setTextColor(getColor(R.color.text_secondary));
            btnUndo.setTextSize(12);
            btnUndo.setGravity(android.view.Gravity.CENTER);
            btnUndo.setPadding(0, dp(12), 0, dp(4));
            btnUndo.setOnClickListener(v -> confirmUndoRestore());
            backupCard.addView(btnUndo);
        }

        root.addView(backupCard);

        Button btnBack = new Button(this);
        btnBack.setText("← Späť");
        btnBack.setTextColor(getColor(R.color.text_primary));
        btnBack.setBackgroundColor(getColor(R.color.bg_button_neutral));
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        backLp.topMargin = dp(8);
        btnBack.setLayoutParams(backLp);
        btnBack.setOnClickListener(v -> finish());
        root.addView(btnBack);
    }

    @Override
    protected void onResume() {
        super.onResume();
        java.util.Set<String> focus = FocusStore.getMuscles(this);
        tvFocus.setText(focus.isEmpty() ? "Nevybraté — ťukni a vyber partie" : FocusStore.describe(focus));
        refreshMode();
    }

    // ─── Režim appky (Sprievodca / Len zapisovanie / vlastné prepínače) ──────────

    private View buildModeCard() {
        LinearLayout card = UiKit.card(this);

        TextView title = new TextView(this);
        title.setText("🎛️ Režim appky");
        title.setTextColor(getColor(R.color.text_primary));
        title.setTextSize(14);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(title);

        tvModeDesc = new TextView(this);
        tvModeDesc.setTextColor(getColor(R.color.text_secondary));
        tvModeDesc.setTextSize(12);
        tvModeDesc.setPadding(0, dp(2), 0, dp(10));
        card.addView(tvModeDesc);

        LinearLayout chipsRow = new LinearLayout(this);
        chipsRow.setOrientation(LinearLayout.HORIZONTAL);
        String[][] modes = {{FeatureStore.MODE_GUIDE, "🧭 Sprievodca"}, {FeatureStore.MODE_PRO, "⚡ Len zapisovanie"}};
        for (int i = 0; i < modes.length; i++) {
            String mode = modes[i][0];
            TextView chip = new TextView(this);
            chip.setText(modes[i][1]);
            chip.setTag(mode);
            chip.setTextSize(13);
            chip.setGravity(android.view.Gravity.CENTER);
            chip.setPadding(dp(6), dp(10), dp(6), dp(10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) lp.leftMargin = dp(8);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> {
                FeatureStore.applyMode(this, mode);
                refreshMode();
            });
            modeChips.add(chip);
            chipsRow.addView(chip);
        }
        card.addView(chipsRow);

        TextView customHint = new TextView(this);
        customHint.setText("Alebo si zapni len to, čo chceš:");
        customHint.setTextColor(getColor(R.color.text_muted));
        customHint.setTextSize(11);
        customHint.setPadding(0, dp(12), 0, dp(2));
        card.addView(customHint);

        addFeatureSwitch(card, FeatureStore.REST_TIMER, "⏱ Odpočet pauzy medzi sériami",
                "Spustí sa po pridaní série, na konci zavibruje");
        addFeatureSwitch(card, FeatureStore.RPE, "📊 RPE pri sériách",
                "Hodnotenie, aká ťažká bola séria (6–10)");
        addFeatureSwitch(card, FeatureStore.GUIDES, "📖 Postupy cvikov",
                "Krok za krokom v detaile cviku");
        addFeatureSwitch(card, FeatureStore.FOCUS, "🎯 Fokus a pripomienky",
                "Partie, na ktoré sa sústredíš — v Štatistikách a katalógu");
        return card;
    }

    private void addFeatureSwitch(LinearLayout card, String feature, String label, String desc) {
        android.widget.Switch sw = new android.widget.Switch(this);
        android.text.SpannableStringBuilder text = new android.text.SpannableStringBuilder(label + "\n" + desc);
        text.setSpan(new android.text.style.ForegroundColorSpan(getColor(R.color.text_muted)),
                label.length() + 1, text.length(), 0);
        text.setSpan(new android.text.style.RelativeSizeSpan(0.85f), label.length() + 1, text.length(), 0);
        sw.setText(text);
        sw.setTextColor(getColor(R.color.text_primary));
        sw.setTextSize(13);
        sw.setPadding(0, dp(8), 0, dp(8));
        sw.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        featureSwitches.put(feature, sw);
        card.addView(sw);
    }

    /** Prekreslí výber režimu a prepínače podľa FeatureStore (bez spúšťania poslucháčov). */
    private void refreshMode() {
        if (tvModeDesc == null) return;
        String mode = FeatureStore.getMode(this);
        tvModeDesc.setText(FeatureStore.MODE_GUIDE.equals(mode)
                ? "Sprievodca — appka ťa vedie: postupy, odpočet pauzy, RPE aj pripomienky."
                : FeatureStore.MODE_PRO.equals(mode)
                ? "Len zapisovanie — rýchle zapisovanie bez sprievodcu a vysvetliviek."
                : "Vlastné nastavenie — zapnuté sú len vybrané pomôcky.");
        int radius = UiKit.dimenPx(this, R.dimen.radius_chip);
        for (TextView chip : modeChips) {
            boolean on = mode.equals(chip.getTag());
            chip.setBackground(on
                    ? UiKit.roundedBgWithBorder(this, R.color.accent_primary_soft, R.color.accent_primary, dp(1), radius)
                    : UiKit.roundedBgWithBorder(this, R.color.bg_chip, R.color.separator, dp(1), radius));
            chip.setTextColor(getColor(on ? R.color.text_on_accent : R.color.text_secondary));
            chip.setTypeface(null, on ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
        for (java.util.Map.Entry<String, android.widget.Switch> e : featureSwitches.entrySet()) {
            android.widget.Switch sw = e.getValue();
            String feature = e.getKey();
            sw.setOnCheckedChangeListener(null);
            sw.setChecked(FeatureStore.isEnabled(this, feature));
            sw.setOnCheckedChangeListener((b, checked) -> {
                FeatureStore.setEnabled(this, feature, checked);
                refreshMode();
            });
        }
        if (focusCard != null) {
            focusCard.setVisibility(FeatureStore.isEnabled(this, FeatureStore.FOCUS) ? View.VISIBLE : View.GONE);
        }
    }

    private void saveProfile() {
        Float height = parseOrNull(etHeight.getText().toString());
        Float weight = parseOrNull(etWeight.getText().toString());

        boolean anyMeasurement = false;
        for (MeasurementField f : measurementFields) {
            Float v = parseOrNull(f.input.getText().toString());
            if (v != null && v > 0) { anyMeasurement = true; break; }
        }

        if (height == null && (weight == null || weight <= 0) && !anyMeasurement) {
            Toast.makeText(this, "Zadaj aspoň jeden údaj (výška, váha alebo niektorý obvod).", Toast.LENGTH_SHORT).show();
            return;
        }

        if (height != null) ProfileStore.setHeightCm(this, height);
        if (weight != null && weight > 0) {
            WeightStore.setWeight(this, ActivityStore.todayKey(), weight);
            etWeight.setText("");
            etWeight.setHint("posledná: " + formatNum(weight) + " kg");
            renderWeightHistory();
        }
        for (MeasurementField f : measurementFields) {
            Float v = parseOrNull(f.input.getText().toString());
            if (v != null && v > 0) {
                f.store.setCm(this, ActivityStore.todayKey(), v);
                f.input.setText("");
                f.input.setHint("posledný: " + formatNum(v) + " cm");
                renderMeasurementHistory(f);
            }
        }

        Toast.makeText(this, "Uložené.", Toast.LENGTH_SHORT).show();
    }

    // ─── História váhy ──────────────────────────────────────────────────────

    private void renderWeightHistory() {
        weightHistoryContainer.removeAllViews();

        TreeMap<String, Float> all = WeightStore.getAllSorted(this);
        if (all.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Zatiaľ žiadny záznam váhy.");
            empty.setTextColor(getColor(R.color.text_muted));
            empty.setTextSize(12);
            weightHistoryContainer.addView(empty);
            return;
        }

        List<String> dates = new ArrayList<>(all.keySet());
        Collections.reverse(dates); // najnovšie hore

        for (int i = 0; i < dates.size(); i++) {
            String dateKey = dates.get(i);
            float weight = all.get(dateKey);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(6), 0, dp(6));

            TextView tvDate = new TextView(this);
            tvDate.setText(formatDateNice(dateKey));
            tvDate.setTextColor(getColor(R.color.text_secondary));
            tvDate.setTextSize(12);
            tvDate.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(tvDate);

            TextView tvWeight = new TextView(this);
            tvWeight.setText(formatNum(weight) + " kg");
            tvWeight.setTextColor(getColor(R.color.text_primary));
            tvWeight.setTextSize(12);
            tvWeight.setTypeface(null, android.graphics.Typeface.BOLD);
            row.addView(tvWeight);

            TextView btnDel = new TextView(this);
            btnDel.setText(" ✕ ");
            btnDel.setTextColor(getColor(R.color.accent_danger));
            btnDel.setTextSize(15);
            btnDel.setPadding(dp(12), dp(4), dp(2), dp(4));
            btnDel.setOnClickListener(v -> confirmDeleteWeight(dateKey));
            row.addView(btnDel);

            weightHistoryContainer.addView(row);

            if (i < dates.size() - 1) {
                View sep = new View(this);
                sep.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
                sep.setBackgroundColor(getColor(R.color.separator));
                weightHistoryContainer.addView(sep);
            }
        }
    }

    private void confirmDeleteWeight(String dateKey) {
        new AlertDialog.Builder(this)
                .setTitle("Zmazať záznam váhy?")
                .setMessage("Naozaj chceš zmazať záznam váhy z " + formatDateNice(dateKey) + "?")
                .setPositiveButton("Zmazať", (dialog, which) -> {
                    WeightStore.removeWeight(this, dateKey);
                    renderWeightHistory();
                })
                .setNegativeButton("Zrušiť", null)
                .show();
    }

    // ─── História telesných obvodov (pás, hrudník, biceps, stehno) ─────────────

    private void renderMeasurementHistory(MeasurementField f) {
        f.historyContainer.removeAllViews();

        TreeMap<String, Float> all = f.store.getAllSorted(this);
        if (all.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(f.emptyHistoryText);
            empty.setTextColor(getColor(R.color.text_muted));
            empty.setTextSize(12);
            f.historyContainer.addView(empty);
            return;
        }

        List<String> dates = new ArrayList<>(all.keySet());
        Collections.reverse(dates); // najnovšie hore

        for (int i = 0; i < dates.size(); i++) {
            String dateKey = dates.get(i);
            float value = all.get(dateKey);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(6), 0, dp(6));

            TextView tvDate = new TextView(this);
            tvDate.setText(formatDateNice(dateKey));
            tvDate.setTextColor(getColor(R.color.text_secondary));
            tvDate.setTextSize(12);
            tvDate.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(tvDate);

            TextView tvValue = new TextView(this);
            tvValue.setText(formatNum(value) + " cm");
            tvValue.setTextColor(getColor(R.color.text_primary));
            tvValue.setTextSize(12);
            tvValue.setTypeface(null, android.graphics.Typeface.BOLD);
            row.addView(tvValue);

            TextView btnDel = new TextView(this);
            btnDel.setText(" ✕ ");
            btnDel.setTextColor(getColor(R.color.accent_danger));
            btnDel.setTextSize(15);
            btnDel.setPadding(dp(12), dp(4), dp(2), dp(4));
            btnDel.setOnClickListener(v -> confirmDeleteMeasurement(f, dateKey));
            row.addView(btnDel);

            f.historyContainer.addView(row);

            if (i < dates.size() - 1) {
                View sep = new View(this);
                sep.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
                sep.setBackgroundColor(getColor(R.color.separator));
                f.historyContainer.addView(sep);
            }
        }
    }

    private void confirmDeleteMeasurement(MeasurementField f, String dateKey) {
        new AlertDialog.Builder(this)
                .setTitle("Zmazať záznam?")
                .setMessage("Naozaj chceš zmazať záznam z " + formatDateNice(dateKey) + "?")
                .setPositiveButton("Zmazať", (dialog, which) -> {
                    f.store.remove(this, dateKey);
                    renderMeasurementHistory(f);
                })
                .setNegativeButton("Zrušiť", null)
                .show();
    }

    private String formatDateNice(String dateKeyStr) {
        try {
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            SimpleDateFormat display = new SimpleDateFormat("dd.MM.yyyy", Locale.US);
            return display.format(parser.parse(dateKeyStr));
        } catch (Exception e) {
            return dateKeyStr;
        }
    }

    // ─── Záloha / obnova ────────────────────────────────────────────────────

    private void startExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        String filename = "fittracker_zaloha_" + ActivityStore.todayKey() + ".json";
        intent.putExtra(Intent.EXTRA_TITLE, filename);
        startActivityForResult(intent, REQ_EXPORT);
    }

    private void startImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, REQ_IMPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();

        if (requestCode == REQ_EXPORT) {
            doExport(uri);
        } else if (requestCode == REQ_IMPORT) {
            confirmAndImport(uri);
        }
    }

    private void doExport(Uri uri) {
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            String json = BackupManager.export(this);
            if (out == null) throw new IllegalStateException("Nepodarilo sa otvoriť súbor na zápis.");
            out.write(json.getBytes(StandardCharsets.UTF_8));
            ProfileStore.setLastBackupAt(this, System.currentTimeMillis());
            updateBackupStatusLabel();
            Toast.makeText(this, "Záloha uložená.", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Zálohovanie zlyhalo: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** Vygeneruje zálohu a otvorí systémovú ponuku "Zdieľať" — Google Disk sa v nej ponúkne priamo, ak je nainštalovaný. */
    private void shareBackup() {
        try {
            String json = BackupManager.export(this);
            File file = new File(getCacheDir(), "fittracker_zaloha.json");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(json.getBytes(StandardCharsets.UTF_8));
            }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/json");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            ProfileStore.setLastBackupAt(this, System.currentTimeMillis());
            updateBackupStatusLabel();

            startActivity(Intent.createChooser(shareIntent, "Odoslať zálohu do..."));
        } catch (Exception e) {
            Toast.makeText(this, "Zdieľanie zlyhalo: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** Skopíruje nainštalované .apk do cache a otvorí systémovú ponuku "Zdieľať" (napr. do Google Disku). */
    private void shareApk() {
        try {
            File sourceApk = new File(getApplicationInfo().sourceDir);
            File destApk = new File(getCacheDir(), "FitTracker.apk");
            copyFile(sourceApk, destApk);

            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", destApk);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/vnd.android.package-archive");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, "Odoslať appku (.apk) do..."));
        } catch (Exception e) {
            Toast.makeText(this, "Odoslanie appky zlyhalo: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void copyFile(File src, File dst) throws Exception {
        try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) out.write(buffer, 0, len);
        }
    }

    private void confirmAndImport(Uri uri) {
        new AlertDialog.Builder(this)
                .setTitle("Obnoviť zo zálohy?")
                .setMessage("Týmto prepíšeš VŠETKY aktuálne dáta v appke obsahom zvoleného súboru. "
                        + "Táto akcia sa nedá vrátiť späť.")
                .setPositiveButton("Obnoviť", (dialog, which) -> doImport(uri))
                .setNegativeButton("Zrušiť", null)
                .show();
    }

    private void doImport(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IllegalStateException("Nepodarilo sa otvoriť zvolený súbor.");
            String json = readAll(in);
            BackupManager.restore(this, json);
            Toast.makeText(this, "Dáta obnovené zo zálohy. Predchádzajúce dáta sú odložené — "
                    + "vrátiš ich tlačidlom „Vrátiť dáta spred obnovy“.", Toast.LENGTH_LONG).show();
            recreate(); // prekreslí históriu váhy, obvodov a ostatné údaje podľa obnovených dát
        } catch (BackupManager.InvalidBackupException e) {
            Toast.makeText(this, e.getMessage() + " Tvoje dáta ostali nezmenené.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Obnovenie zlyhalo: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void confirmUndoRestore() {
        new AlertDialog.Builder(this)
                .setTitle("Vrátiť dáta spred obnovy?")
                .setMessage("Appka sa vráti do stavu tesne pred poslednou obnovou zo zálohy. "
                        + "Dáta z obnovenej zálohy sa tým prepíšu.")
                .setPositiveButton("Vrátiť", (dialog, which) -> {
                    try {
                        BackupManager.undoLastRestore(this);
                        Toast.makeText(this, "Dáta vrátené do stavu pred obnovou.", Toast.LENGTH_LONG).show();
                        recreate();
                    } catch (Exception e) {
                        Toast.makeText(this, "Vrátenie zlyhalo: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Zrušiť", null)
                .show();
    }

    private String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = in.read(chunk)) != -1) buffer.write(chunk, 0, read);
        return buffer.toString("UTF-8");
    }

    private void updateBackupStatusLabel() {
        long lastBackup = ProfileStore.getLastBackupAt(this);
        if (lastBackup <= 0) {
            tvBackupStatus.setText("Zatiaľ žiadna záloha.");
        } else {
            SimpleDateFormat fmt = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
            tvBackupStatus.setText("Posledná záloha: " + fmt.format(new Date(lastBackup)));
        }
    }

    private Float parseOrNull(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try {
            return Float.parseFloat(s.trim().replace(',', '.'));
        } catch (Exception e) {
            return null;
        }
    }

    private String formatNum(float v) {
        return (v == Math.floor(v))
                ? String.format(Locale.getDefault(), "%.0f", v)
                : String.format(Locale.getDefault(), "%.1f", v);
    }

    private EditText makeInput(String hint) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        et.setTextColor(getColor(R.color.text_primary));
        et.setHintTextColor(getColor(R.color.text_muted));
        et.setBackground(UiKit.roundedBgWithBorder(
                this, R.color.bg_input, R.color.input_border, dp(1), UiKit.dimenPx(this, R.dimen.radius_chip)));
        et.setPadding(dp(10), dp(8), dp(10), dp(8));
        et.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return et;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
