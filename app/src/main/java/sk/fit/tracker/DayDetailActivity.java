package sk.fit.tracker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public class DayDetailActivity extends Activity {

    private static final String CUSTOM_EXERCISE_SENTINEL = "+ Pridať vlastný cvik";
    private static final List<String> UNIT_LABELS = Arrays.asList("kg", "lbs", "tehličky");
    private static final List<String> UNIT_VALUES =
            Arrays.asList(SetEntry.UNIT_KG, SetEntry.UNIT_LBS, SetEntry.UNIT_PLATE);

    // Odporové gumy (expandery) pre asistovanú kalisteniku — ich pomocná hodnota sa uloží
    // k sérii ako záporná hmotnosť (odpočíta sa od vlastnej váhy). Index 0 = žiadny expander.
    private static final List<String> BAND_LABELS = Arrays.asList(
            "Bez expanderu (0 kg)",
            "Žltý expander (-5 kg)",
            "Červený expander (-12 kg)",
            "Čierny expander (-20 kg)",
            "Fialový expander (-28 kg)",
            "Zelený expander (-40 kg)",
            "Modrý expander (-55 kg)");
    private static final List<Float> BAND_VALUES =
            Arrays.asList(0f, -5f, -12f, -20f, -28f, -40f, -55f);

    private static final String CARDIO_KEY = "cardio";

    private String dateKey;
    private final List<CheckBox> checkBoxes = new ArrayList<>();
    private final Map<String, List<Exercise>> exercisesByCategory = new HashMap<>();
    private final Map<String, LinearLayout> exerciseListViews = new HashMap<>();
    private final List<CardioSession> cardioSessions = new ArrayList<>();
    private LinearLayout cardioListView;
    private LinearLayout strengthListContainer;

    // Meranie trvania silového tréningu — čas od prvej pridanej série v tejto relácii po zatvorenie.
    private Long sessionFirstSetAtMillis = null;

    // Otvorený dialóg cviku a funkcia, ktorá vráti jeho rozpísaný stav. Pri otočení telefónu alebo
    // keď Android zavrie appku na pozadí sa stav uloží (onSaveInstanceState) a dialóg sa po návrate
    // otvorí znova s tými istými, ešte neuloženými sériami. null = dialóg nie je otvorený.
    private static final String STATE_EXERCISE_DIALOG = "exercise_dialog";
    private AlertDialog exerciseDialog;
    private java.util.function.Supplier<Bundle> exerciseDialogState;

    @Override
    protected void onPause() {
        super.onPause();
        accumulateWorkoutTime();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (exerciseDialogState != null) outState.putBundle(STATE_EXERCISE_DIALOG, exerciseDialogState.get());
    }

    @Override
    protected void onDestroy() {
        // Stav je už uložený v onSaveInstanceState — dialóg treba zavrieť, inak by "visel" na zničenom okne
        if (exerciseDialog != null && exerciseDialog.isShowing()) exerciseDialog.dismiss();
        super.onDestroy();
    }

    /** Znovu otvorí dialóg cviku s rozpísanými sériami po otočení / obnovení obrazovky. */
    private void restoreExerciseDialog(Bundle state) {
        Category c = Category.byKey(state.getString("cat"));
        if (c == null) return;
        List<Exercise> list = exercisesByCategory.get(c.key);
        if (list == null) return;
        int editIndex = state.getInt("edit", -1);
        // Upravovaný cvik medzitým nemusí existovať (napr. obnova zo zálohy) — vtedy ako nový záznam
        Integer idx = (editIndex >= 0 && editIndex < list.size()) ? editIndex : null;
        showExerciseDialog(c, idx, state);
    }

    /** Pripočíta uplynutý čas tejto editačnej relácie k celkovému trvaniu tréningu za deň. */
    private void accumulateWorkoutTime() {
        if (sessionFirstSetAtMillis == null) return;
        float minutes = (System.currentTimeMillis() - sessionFirstSetAtMillis) / 60000f;
        WorkoutTimeStore.addMinutes(this, dateKey, minutes);
        sessionFirstSetAtMillis = null;
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(ThemeStore.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiKit.edgeToEdge(this); // odstup od stavového riadku, navigácie a klávesnice (Android 15+)

        dateKey = getIntent().getStringExtra("date");
        if (dateKey == null) dateKey = ActivityStore.todayKey();

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(getColor(R.color.bg_app));
        setContentView(outer);

        ScrollView scroll = new ScrollView(this);
        scroll.setId(R.id.day_scroll); // stabilné ID — Android si po otočení zapamätá pozíciu rolovania
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        outer.addView(scroll);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        root.setBackgroundColor(getColor(R.color.bg_app));
        scroll.addView(root);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(titleRow);

        TextView title = new TextView(this);
        title.setText(formatDateNice(dateKey));
        title.setTextColor(getColor(R.color.text_primary));
        title.setTextSize(19);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        titleRow.addView(title);

        TextView btnShareWorkout = new TextView(this);
        btnShareWorkout.setText("📤 Zdieľať");
        btnShareWorkout.setTextColor(getColor(R.color.accent_primary));
        btnShareWorkout.setTextSize(12);
        btnShareWorkout.setPadding(dp(8), dp(4), dp(4), dp(4));
        btnShareWorkout.setOnClickListener(v -> shareWorkoutImage(root));
        titleRow.addView(btnShareWorkout);

        boolean isToday = dateKey.equals(ActivityStore.todayKey());
        TextView subtitle = new TextView(this);
        subtitle.setText(isToday ? "Čo si dnes robil?" : "Záznam tohto dňa — môžeš upraviť");
        subtitle.setTextColor(getColor(R.color.text_muted));
        subtitle.setTextSize(12);
        subtitle.setPadding(0, dp(2), 0, dp(18));
        root.addView(subtitle);

        Set<String> existing = ActivityStore.getCategoriesForDate(this, dateKey);

        // Pass 1 — načítaj všetky dáta z disku (potrebné pre zhrnutie dňa ešte pred kartami)
        for (Category c : Category.all()) {
            if (!Category.GROUP_WORKOUT.equals(c.group)) continue;
            if (CARDIO_KEY.equals(c.key)) {
                cardioSessions.addAll(CardioStore.getSessions(this, dateKey));
            } else {
                exercisesByCategory.put(c.key,
                        new ArrayList<>(ExerciseStore.getExercises(this, dateKey, c.key)));
            }
        }
        // Legacy kombinované kategórie (spred rozdelenia na čisté partie) — ak má tento
        // konkrétny deň pod niektorou z nich už uložené cviky (staršie záznamy), načítaj
        // ich tiež, nech sa v dni nestratia a nech ich uloženie dňa neodznačí z kalendára.
        for (Category c : Category.legacyAll()) {
            List<Exercise> legacyExercises = ExerciseStore.getExercises(this, dateKey, c.key);
            if (!legacyExercises.isEmpty()) {
                exercisesByCategory.put(c.key, new ArrayList<>(legacyExercises));
            }
        }

        // Lokálne pravidlové zhrnutie dňa (bez AI/siete) — porovnáva dnešok s históriou
        LinearLayout summaryCard = buildDaySummaryCard();
        if (summaryCard != null) root.addView(summaryCard);

        // Subjektívne hodnotenie tréningu/kondície tohto dňa, ak už bolo zadané
        LinearLayout feelingCard = buildFeelingCard();
        if (feelingCard != null) root.addView(feelingCard);

        // Súhrn spálených kalórií (silový tréning + kardio)
        LinearLayout calorieCard = buildCalorieSummaryCard();
        if (calorieCard != null) root.addView(calorieCard);

        // Pass 2 — silový tréning (jedna zdieľaná knižnica partií) + kardio
        root.addView(UiKit.sectionHeader(this, "💪 Tréning"));
        root.addView(buildStrengthCard());
        for (Category c : Category.all()) {
            if (CARDIO_KEY.equals(c.key)) {
                root.addView(buildCardioCard(c, existing.contains(c.key)));
            }
        }

        // Odpočet pauzy medzi sériami — len pri dnešnom tréningu, pripnutý nad Uložiť
        if (isToday && FeatureStore.isEnabled(this, FeatureStore.REST_TIMER)) {
            RestTimerBar restBar = new RestTimerBar(this);
            LinearLayout.LayoutParams restLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            restLp.setMargins(dp(16), dp(8), dp(16), 0);
            restBar.setLayoutParams(restLp);
            outer.addView(restBar);
        }

        // Jedno tlačidlo Uložiť (kategórie) — mimo ScrollView, pripnuté k spodku obrazovky
        Button btnSave = new Button(this);
        btnSave.setText("✔ Uložiť a zavrieť");
        btnSave.setTextColor(getColor(R.color.accent_primary_dark));
        btnSave.setBackgroundResource(R.drawable.bg_button_primary);
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        saveLp.setMargins(dp(16), dp(10), dp(16), dp(16));
        btnSave.setLayoutParams(saveLp);
        btnSave.setOnClickListener(v -> saveAndClose());
        outer.addView(btnSave);

        // Obrazovka bola znovu vytvorená (otočenie, návrat po zavretí na pozadí) s otvoreným dialógom cviku
        if (savedInstanceState != null) {
            Bundle dialogState = savedInstanceState.getBundle(STATE_EXERCISE_DIALOG);
            if (dialogState != null) restoreExerciseDialog(dialogState);
        }
    }

    // ─── Zhrnutie dňa (lokálne pravidlá, bez AI/siete) ─────────────────────────

    private LinearLayout buildDaySummaryCard() {
        List<String> insights = DayInsights.evaluate(this, dateKey, exercisesByCategory, cardioSessions);
        if (insights.isEmpty()) return null;

        LinearLayout card = UiKit.card(this);

        TextView header = new TextView(this);
        header.setText("📋 Zhrnutie dňa");
        header.setTextColor(getColor(R.color.text_primary));
        header.setTextSize(14);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setPadding(0, 0, 0, dp(8));
        card.addView(header);

        for (String insight : insights) {
            TextView line = new TextView(this);
            line.setText(insight);
            line.setTextColor(getColor(R.color.text_secondary));
            line.setTextSize(12);
            line.setPadding(0, dp(3), 0, dp(3));
            card.addView(line);
        }

        return card;
    }

    /** Karta so zapísaným pocitom z tréningu ("Ako sa dnes cítim") — null, ak zatiaľ nič nezadané. */
    private LinearLayout buildFeelingCard() {
        int rating = WorkoutFeelingStore.getRating(this, dateKey);
        String note = WorkoutFeelingStore.getNote(this, dateKey);
        if (rating <= 0 && note.isEmpty()) return null;

        LinearLayout card = UiKit.card(this);

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView header = new TextView(this);
        header.setText("🩺 Ako si sa cítil(a)");
        header.setTextColor(getColor(R.color.text_primary));
        header.setTextSize(14);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        headerRow.addView(header);

        TextView btnEdit = new TextView(this);
        btnEdit.setText("✎ Upraviť");
        btnEdit.setTextColor(getColor(R.color.accent_primary));
        btnEdit.setTextSize(11);
        btnEdit.setOnClickListener(v -> showFeelingDialog(false));
        headerRow.addView(btnEdit);

        card.addView(headerRow);

        if (rating > 0) {
            TextView tvRating = new TextView(this);
            tvRating.setText(WorkoutFeelingStore.label(rating));
            tvRating.setTextColor(getColor(R.color.text_secondary));
            tvRating.setTextSize(12);
            tvRating.setPadding(0, dp(6), 0, 0);
            card.addView(tvRating);
        }
        if (!note.isEmpty()) {
            TextView tvNote = new TextView(this);
            tvNote.setText("„" + note + "“");
            tvNote.setTextColor(getColor(R.color.text_secondary));
            tvNote.setTextSize(12);
            tvNote.setPadding(0, dp(4), 0, 0);
            card.addView(tvNote);
        }

        return card;
    }

    private LinearLayout buildCalorieSummaryCard() {
        boolean hasStrength = false;
        for (List<Exercise> list : exercisesByCategory.values()) {
            if (!list.isEmpty()) { hasStrength = true; break; }
        }
        boolean hasCardio = !cardioSessions.isEmpty();
        if (!hasStrength && !hasCardio) return null;

        LinearLayout card = UiKit.card(this);

        TextView header = new TextView(this);
        header.setText("🔥 Spálené kalórie");
        header.setTextColor(getColor(R.color.text_primary));
        header.setTextSize(14);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setPadding(0, 0, 0, dp(8));
        card.addView(header);

        if (!ProfileStore.hasProfile(this)) {
            TextView warn = new TextView(this);
            warn.setText("⚠️ Nastav si váhu v profile pre výpočet kalórií.");
            warn.setTextColor(getColor(R.color.text_secondary));
            warn.setTextSize(12);
            card.addView(warn);

            TextView btnProfile = new TextView(this);
            btnProfile.setText("👤 Otvoriť profil");
            btnProfile.setTextColor(getColor(R.color.accent_primary));
            btnProfile.setTextSize(12);
            btnProfile.setPadding(0, dp(6), 0, 0);
            btnProfile.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
            card.addView(btnProfile);
            return card;
        }

        float weightKg = ProfileStore.getWeightKgOn(this, dateKey); // váha v ten deň, nie dnešná
        float trackedMinutes = WorkoutTimeStore.getMinutes(this, dateKey);
        float strengthMinutes = trackedMinutes;
        boolean estimatedTime = false;

        // Odhad trvania podľa počtu sérií (~2.5 min na sériu vrátane odpočinku) sa použije
        // vždy, keď je VYŠŠÍ než reálne nameraný čas — nie len keď je nameraný čas nulový.
        // Reálne nameraný čas totiž vie byť nerealisticky nízky (testovanie, pád appky počas
        // tréningu, spätné dopisovanie tréningu inokedy než sa reálne cvičilo) a v takom
        // prípade by kalórie boli podhodnotené; Math.max zaručuje bezpečnejší (vyšší) odhad.
        if (hasStrength) {
            int totalSets = 0;
            for (List<Exercise> list : exercisesByCategory.values()) {
                for (Exercise ex : list) totalSets += ex.sets.size();
            }
            float estimatedMinutes = totalSets * 2.5f;
            if (estimatedMinutes > trackedMinutes) {
                strengthMinutes = estimatedMinutes;
                estimatedTime = true;
            }
        }

        float strengthKcal = CalorieCalculator.strengthKcal(weightKg, strengthMinutes);

        float cardioKcal = 0;
        for (CardioSession cs : cardioSessions) cardioKcal += cs.calories;

        if (hasStrength) {
            String minutesStr = strengthMinutes > 0
                    ? " (" + Math.round(strengthMinutes) + " min" + (estimatedTime ? ", odhad podľa počtu sérií" : "") + ")"
                    : "";
            TextView line = new TextView(this);
            line.setText("💪 Silový tréning: " + Math.round(strengthKcal) + " kcal" + minutesStr);
            line.setTextColor(getColor(R.color.text_secondary));
            line.setTextSize(12);
            line.setPadding(0, dp(2), 0, dp(2));
            card.addView(line);
        }

        if (hasCardio) {
            TextView line = new TextView(this);
            line.setText("🏃 Kardio: " + Math.round(cardioKcal) + " kcal");
            line.setTextColor(getColor(R.color.text_secondary));
            line.setTextSize(12);
            line.setPadding(0, dp(2), 0, dp(2));
            card.addView(line);
        }

        TextView total = new TextView(this);
        total.setText("Spolu: " + Math.round(strengthKcal + cardioKcal) + " kcal");
        total.setTextColor(getColor(R.color.accent_highlight));
        total.setTextSize(13);
        total.setTypeface(null, android.graphics.Typeface.BOLD);
        total.setPadding(0, dp(8), 0, 0);
        card.addView(total);

        return card;
    }

    // ─── Karta silového tréningu (všetky svalové partie zdieľané v jednej knižnici) ────

    /**
     * Jedna spoločná karta pre všetky cviky dňa, zoskupené podľa čistej svalovej partie.
     * Žiadna partia nie je vopred vybraná/limitovaná — používateľ si cez "+ Pridať cvičenie"
     * (showMuscleGroupPicker) sám zvolí ľubovoľnú kombináciu partií pre tento deň.
     */
    private LinearLayout buildStrengthCard() {
        LinearLayout card = UiKit.card(this);

        strengthListContainer = new LinearLayout(this);
        strengthListContainer.setOrientation(LinearLayout.VERTICAL);
        card.addView(strengthListContainer);

        TextView btnAdd = new TextView(this);
        btnAdd.setText("+ Pridať cvičenie");
        btnAdd.setTextColor(getColor(R.color.accent_primary));
        btnAdd.setTextSize(12);
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        addLp.topMargin = dp(2);
        addLp.bottomMargin = dp(2);
        btnAdd.setLayoutParams(addLp);
        btnAdd.setOnClickListener(v -> showMuscleGroupPicker());
        card.addView(btnAdd);

        renderStrengthSection();
        return card;
    }

    /** Prekreslí zoznam cvikov dňa zoskupený podľa partie — len partie, ktoré dnes majú aspoň 1 cvik. */
    private void renderStrengthSection() {
        strengthListContainer.removeAllViews();
        exerciseListViews.clear();

        List<Category> renderableCategories = new ArrayList<>();
        for (Category c : Category.all()) {
            if (!Category.GROUP_WORKOUT.equals(c.group) || CARDIO_KEY.equals(c.key)) continue;
            renderableCategories.add(c);
        }
        // Legacy kombinované kategórie (Chrbát+Triceps, Prsia+Biceps, Nohy+Ramená) sa v pridávaní
        // nového cvičenia neponúkajú, ale ak tento deň má pod niektorou z nich staršie cviky,
        // nech sa naďalej zobrazia a dajú upraviť/zmazať — nesmú z pohľadu užívateľa zmiznúť.
        for (Category c : Category.legacyAll()) {
            List<Exercise> legacyList = exercisesByCategory.get(c.key);
            if (legacyList != null && !legacyList.isEmpty()) renderableCategories.add(c);
        }

        boolean anyExercise = false;
        for (Category c : renderableCategories) {
            List<Exercise> list = exercisesByCategory.get(c.key);
            if (list == null || list.isEmpty()) continue;
            anyExercise = true;

            LinearLayout header = new LinearLayout(this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            headerLp.topMargin = dp(6);
            headerLp.bottomMargin = dp(4);
            header.setLayoutParams(headerLp);

            View dot = colorDot(c.color, dp(8));
            ((LinearLayout.LayoutParams) dot.getLayoutParams()).rightMargin = dp(8);
            header.addView(dot);

            int muscleRes = Category.muscleDrawableFor(c.key);
            if (muscleRes != 0) {
                ImageView muscleIcon = new ImageView(this);
                muscleIcon.setImageDrawable(Category.muscleIcon(this, c.key));
                LinearLayout.LayoutParams muscleLp = new LinearLayout.LayoutParams(dp(13), dp(21));
                muscleLp.rightMargin = dp(6);
                muscleIcon.setLayoutParams(muscleLp);
                header.addView(muscleIcon);
            }

            TextView label = new TextView(this);
            label.setText(c.label);
            label.setTextColor(getColor(R.color.text_primary));
            label.setTextSize(13);
            label.setTypeface(null, android.graphics.Typeface.BOLD);
            header.addView(label);
            strengthListContainer.addView(header);

            LinearLayout groupList = new LinearLayout(this);
            groupList.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            listLp.bottomMargin = dp(6);
            groupList.setLayoutParams(listLp);
            strengthListContainer.addView(groupList);
            exerciseListViews.put(c.key, groupList);

            renderExerciseList(c.key, c);
        }

        if (!anyExercise) {
            TextView empty = new TextView(this);
            empty.setText("Zatiaľ žiadne cviky — pridaj prvý nižšie.");
            empty.setTextColor(getColor(R.color.text_muted));
            empty.setTextSize(12);
            empty.setPadding(0, dp(2), 0, dp(6));
            strengthListContainer.addView(empty);
        }
    }

    /** Dialóg výberu čistej svalovej partie — prvý krok pri pridávaní nového cviku. */
    private void showMuscleGroupPicker() {
        ScrollView dialogScroll = new ScrollView(this);
        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(dp(12), dp(8), dp(12), dp(8));
        dialogScroll.addView(dialogLayout);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Vyber svalovú partiu")
                .setView(dialogScroll)
                .setNegativeButton("Zrušiť", null)
                .create();

        for (Category c : Category.all()) {
            if (!Category.GROUP_WORKOUT.equals(c.group) || CARDIO_KEY.equals(c.key)) continue;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(UiKit.roundedBg(this, R.color.bg_card_alt, UiKit.dimenPx(this, R.dimen.radius_chip)));
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = dp(6);
            row.setLayoutParams(rowLp);

            View dot = colorDot(c.color, dp(12));
            ((LinearLayout.LayoutParams) dot.getLayoutParams()).rightMargin = dp(12);
            row.addView(dot);

            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView label = new TextView(this);
            label.setText(c.label);
            label.setTextColor(getColor(R.color.text_primary));
            label.setTextSize(14);
            label.setTypeface(null, android.graphics.Typeface.BOLD);
            textCol.addView(label);

            TextView hint = new TextView(this);
            hint.setText(lastTrainedHint(c.key));
            hint.setTextColor(getColor(R.color.text_muted));
            hint.setTextSize(11);
            textCol.addView(hint);

            row.addView(textCol);
            row.setOnClickListener(v -> {
                dialog.dismiss();
                showExerciseDialog(c, null);
            });
            dialogLayout.addView(row);
        }

        dialog.show();
    }

    /** Krátky text "Naposledy: pred N dňami" pre danú partiu, na použitie vo výbere partie. */
    private String lastTrainedHint(String categoryKey) {
        String lastDate = ActivityStore.lastDateForCategory(this, categoryKey);
        if (lastDate == null) return "Ešte necvičené";
        int days = ActivityStore.daysSince(lastDate);
        if (days == 0) return "Naposledy: dnes";
        if (days == 1) return "Naposledy: včera";
        return "Naposledy: pred " + days + " dňami";
    }

    // ─── Render zoznamu cvičení pre jednu partiu ──────────────────────────────

    private void renderExerciseList(String categoryKey, Category c) {
        LinearLayout container = exerciseListViews.get(categoryKey);
        if (container == null) return;
        container.removeAllViews();

        List<Exercise> list = exercisesByCategory.get(categoryKey);
        if (list == null) return;

        for (int i = 0; i < list.size(); i++) {
            Exercise ex = list.get(i);
            final int index = i;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(UiKit.roundedBg(this, R.color.bg_card_alt, UiKit.dimenPx(this, R.dimen.radius_chip)));
            row.setPadding(dp(8), dp(8), dp(8), dp(8));
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = dp(4);
            row.setLayoutParams(rowLp);

            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvName = new TextView(this);
            tvName.setText(ex.name);
            tvName.setTextColor(getColor(R.color.text_primary));
            tvName.setTextSize(13);
            tvName.setTypeface(null, android.graphics.Typeface.BOLD);
            textCol.addView(tvName);

            TextView tvSets = new TextView(this);
            tvSets.setText(ex.summary());
            tvSets.setTextColor(getColor(R.color.text_secondary));
            tvSets.setTextSize(12);
            textCol.addView(tvSets);

            row.addView(textCol);

            // Zmena poradia — prehodí cvičenie so susedom v List<Exercise> a hneď uloží na disk
            boolean canMoveUp = index > 0;
            boolean canMoveDown = index < list.size() - 1;

            TextView btnUp = new TextView(this);
            btnUp.setText("⬆️");
            btnUp.setTextSize(13);
            btnUp.setPadding(dp(6), 0, dp(4), 0);
            btnUp.setAlpha(canMoveUp ? 1f : 0.3f);
            btnUp.setOnClickListener(v -> {
                if (index > 0) {
                    Collections.swap(list, index, index - 1);
                    ExerciseStore.setExercises(this, dateKey, categoryKey, list);
                    renderExerciseList(categoryKey, c);
                }
            });
            row.addView(btnUp);

            TextView btnDown = new TextView(this);
            btnDown.setText("⬇️");
            btnDown.setTextSize(13);
            btnDown.setPadding(dp(4), 0, dp(6), 0);
            btnDown.setAlpha(canMoveDown ? 1f : 0.3f);
            btnDown.setOnClickListener(v -> {
                if (index < list.size() - 1) {
                    Collections.swap(list, index, index + 1);
                    ExerciseStore.setExercises(this, dateKey, categoryKey, list);
                    renderExerciseList(categoryKey, c);
                }
            });
            row.addView(btnDown);

            // Upraviť — zelené tlačidlo s vlastným pozadím (nie len text), aby malo poriadny dotykový cieľ
            TextView btnEdit = new TextView(this);
            btnEdit.setText("✎ Upraviť");
            btnEdit.setTextColor(getColor(R.color.accent_primary_dark));
            btnEdit.setTextSize(12);
            btnEdit.setTypeface(null, android.graphics.Typeface.BOLD);
            btnEdit.setGravity(Gravity.CENTER);
            btnEdit.setPadding(dp(12), dp(8), dp(12), dp(8));
            btnEdit.setBackgroundResource(R.drawable.bg_button_primary);
            LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            editLp.leftMargin = dp(10);
            btnEdit.setLayoutParams(editLp);
            btnEdit.setOnClickListener(v -> showExerciseDialog(c, index));
            row.addView(btnEdit);

            // Zmazať — zámerne oddelené od Upraviť (extra medzera) + výrazná červená + potvrdenie,
            // aby sa v posilňovni spotenou rukou nedalo omylom zmazať celé cvičenie.
            TextView btnDel = new TextView(this);
            btnDel.setText("✕ Zmazať");
            btnDel.setTextColor(getColor(R.color.text_white));
            btnDel.setTextSize(12);
            btnDel.setTypeface(null, android.graphics.Typeface.BOLD);
            btnDel.setGravity(Gravity.CENTER);
            btnDel.setPadding(dp(12), dp(8), dp(12), dp(8));
            btnDel.setBackground(UiKit.roundedBg(this, R.color.accent_danger_dark, UiKit.dimenPx(this, R.dimen.radius_button)));
            LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            delLp.leftMargin = dp(16);
            btnDel.setLayoutParams(delLp);
            btnDel.setOnClickListener(v -> confirmDeleteExercise(c, categoryKey, list, index, ex.name));
            row.addView(btnDel);

            container.addView(row);
        }
    }

    private void confirmDeleteExercise(Category c, String categoryKey, List<Exercise> list, int index, String exerciseName) {
        new AlertDialog.Builder(this)
                .setTitle("Zmazať cvičenie?")
                .setMessage("Naozaj chceš natrvalo zmazať „" + exerciseName + "“ a všetky jeho série z tohto dňa?")
                .setPositiveButton("Zmazať", (dialog, which) -> {
                    list.remove(index);
                    ExerciseStore.setExercises(this, dateKey, categoryKey, list);
                    saveCategoriesToDisk();
                    renderStrengthSection();
                })
                .setNegativeButton("Zrušiť", null)
                .show();
    }

    // ─── Karta pre kardio (bežecký pás, bicykel...) ────────────────────────────

    private LinearLayout buildCardioCard(Category c, boolean checked) {
        LinearLayout card = UiKit.card(this);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));

        View dot = colorDot(c.color, dp(10));
        LinearLayout.LayoutParams dotLp = (LinearLayout.LayoutParams) dot.getLayoutParams();
        dotLp.rightMargin = dp(12);
        dot.setLayoutParams(dotLp);
        row.addView(dot);

        TextView label = new TextView(this);
        label.setText("🏃 " + c.label);
        label.setTextColor(getColor(R.color.text_primary));
        label.setTextSize(14);
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        label.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(label);

        CheckBox cb = new CheckBox(this);
        cb.setChecked(checked);
        cb.setTag(c.key);
        checkBoxes.add(cb);
        cb.setOnCheckedChangeListener((btn, isChecked) -> saveCategoriesToDisk());
        row.addView(cb);
        row.setOnClickListener(v -> cb.setChecked(!cb.isChecked()));
        card.addView(row);

        // "Naposledy" hint — posledný kardio záznam pred týmto dňom
        List<CardioSession> lastSession = CardioStore.getLastSessionBefore(this, dateKey);
        if (!lastSession.isEmpty()) {
            LinearLayout hintBox = new LinearLayout(this);
            hintBox.setOrientation(LinearLayout.VERTICAL);
            hintBox.setBackground(UiKit.roundedBg(this, R.color.bg_hint, UiKit.dimenPx(this, R.dimen.radius_chip)));
            hintBox.setPadding(dp(10), dp(8), dp(10), dp(8));
            LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            hintLp.bottomMargin = dp(8);
            hintBox.setLayoutParams(hintLp);

            TextView hintTitle = new TextView(this);
            hintTitle.setText("📈 Naposledy kardio:");
            hintTitle.setTextColor(getColor(R.color.accent_primary));
            hintTitle.setTextSize(11);
            hintTitle.setTypeface(null, android.graphics.Typeface.BOLD);
            hintBox.addView(hintTitle);

            for (CardioSession cs : lastSession) {
                TextView line = new TextView(this);
                line.setText(cs.toString());
                line.setTextColor(getColor(R.color.text_secondary));
                line.setTextSize(11);
                line.setPadding(0, dp(2), 0, 0);
                hintBox.addView(line);
            }
            card.addView(hintBox);
        }

        cardioListView = new LinearLayout(this);
        cardioListView.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        listLp.bottomMargin = dp(4);
        cardioListView.setLayoutParams(listLp);
        card.addView(cardioListView);

        TextView btnAdd = new TextView(this);
        btnAdd.setText("+ Pridať kardio záznam");
        btnAdd.setTextColor(getColor(R.color.accent_primary));
        btnAdd.setTextSize(12);
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        addLp.topMargin = dp(2);
        addLp.bottomMargin = dp(6);
        btnAdd.setLayoutParams(addLp);
        btnAdd.setOnClickListener(v -> showCardioDialog(c, cb, null));
        card.addView(btnAdd);

        renderCardioList(c);
        return card;
    }

    private void renderCardioList(Category c) {
        if (cardioListView == null) return;
        cardioListView.removeAllViews();

        for (int i = 0; i < cardioSessions.size(); i++) {
            CardioSession cs = cardioSessions.get(i);
            final int index = i;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(UiKit.roundedBg(this, R.color.bg_card_alt, UiKit.dimenPx(this, R.dimen.radius_chip)));
            row.setPadding(dp(8), dp(8), dp(8), dp(8));
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = dp(4);
            row.setLayoutParams(rowLp);

            TextView tvSummary = new TextView(this);
            tvSummary.setText(cs.toString());
            tvSummary.setTextColor(getColor(R.color.text_primary));
            tvSummary.setTextSize(12);
            tvSummary.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(tvSummary);

            TextView btnEdit = new TextView(this);
            btnEdit.setText("✎ Upraviť");
            btnEdit.setTextColor(getColor(R.color.accent_primary_dark));
            btnEdit.setTextSize(12);
            btnEdit.setTypeface(null, android.graphics.Typeface.BOLD);
            btnEdit.setGravity(Gravity.CENTER);
            btnEdit.setPadding(dp(12), dp(8), dp(12), dp(8));
            btnEdit.setBackgroundResource(R.drawable.bg_button_primary);
            LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            editLp.leftMargin = dp(10);
            btnEdit.setLayoutParams(editLp);
            btnEdit.setOnClickListener(v -> showCardioDialog(c, null, index));
            row.addView(btnEdit);

            TextView btnDel = new TextView(this);
            btnDel.setText("✕ Zmazať");
            btnDel.setTextColor(getColor(R.color.text_white));
            btnDel.setTextSize(12);
            btnDel.setTypeface(null, android.graphics.Typeface.BOLD);
            btnDel.setGravity(Gravity.CENTER);
            btnDel.setPadding(dp(12), dp(8), dp(12), dp(8));
            btnDel.setBackground(UiKit.roundedBg(this, R.color.accent_danger_dark, UiKit.dimenPx(this, R.dimen.radius_button)));
            LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            delLp.leftMargin = dp(16);
            btnDel.setLayoutParams(delLp);
            btnDel.setOnClickListener(v -> confirmDeleteCardio(c, index));
            row.addView(btnDel);

            cardioListView.addView(row);
        }
    }

    private void confirmDeleteCardio(Category c, int index) {
        new AlertDialog.Builder(this)
                .setTitle("Zmazať kardio záznam?")
                .setMessage("Naozaj chceš natrvalo zmazať tento kardio záznam z tohto dňa?")
                .setPositiveButton("Zmazať", (dialog, which) -> {
                    cardioSessions.remove(index);
                    CardioStore.setSessions(this, dateKey, cardioSessions);
                    renderCardioList(c);
                })
                .setNegativeButton("Zrušiť", null)
                .show();
    }

    private void showCardioDialog(Category c, CheckBox cbToCheck, Integer editIndex) {
        CardioSession editing = (editIndex != null) ? cardioSessions.get(editIndex) : null;

        ScrollView dialogScroll = new ScrollView(this);
        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(dp(20), dp(16), dp(20), dp(16));
        dialogScroll.addView(dialogLayout);

        EditText etDuration = makeInput("Trvanie (min) — napr. 25.5",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText etDistance = makeInput("Vzdialenosť (km) — voliteľné",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText etSpeed = makeInput("Priemerná rýchlosť (kph) — voliteľné",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText etIncline = makeInput("Priemerné stúpanie (%) — voliteľné",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText etCalories = makeInput("Kalórie (kcal) — voliteľné", InputType.TYPE_CLASS_NUMBER);

        if (editing != null) {
            etDuration.setText(formatW(editing.durationMin));
            etDistance.setText(formatW(editing.distanceKm));
            etSpeed.setText(formatW(editing.avgSpeedKph));
            etIncline.setText(formatW(editing.avgInclinePercent));
            etCalories.setText(String.valueOf(editing.calories));
        }

        for (EditText et : new EditText[]{etDuration, etDistance, etSpeed, etIncline, etCalories}) {
            ((LinearLayout.LayoutParams) et.getLayoutParams()).topMargin = dp(8);
            dialogLayout.addView(et);
        }

        String positiveLabel = editing != null ? "Uložiť" : "Pridať";

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle((editing != null ? "Upraviť" : "Pridať") + " — " + c.label)
                .setView(dialogScroll)
                .setPositiveButton(positiveLabel, null) // listener nižšie, aby dialóg pri chybe nezmizol
                .setNegativeButton("Zrušiť", null)
                .setCancelable(false)
                .create();
        keepButtonsAboveKeyboard(dialog);

        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setOnClickListener(v -> {
                Float duration = parseWeightOrNull(etDuration.getText().toString());
                if (duration == null) {
                    Toast.makeText(this, "Zadaj trvanie tréningu (minúty).", Toast.LENGTH_SHORT).show();
                    return;
                }
                Float distance = parseOptionalFloat(etDistance.getText().toString());
                Float speed = parseOptionalFloat(etSpeed.getText().toString());
                Float incline = parseOptionalFloat(etIncline.getText().toString());
                Integer calories = parseOptionalInt(etCalories.getText().toString());
                if (distance == null || speed == null || incline == null || calories == null) {
                    Toast.makeText(this,
                            "Skontroluj zadané hodnoty — niektorá nie je platné číslo.",
                            Toast.LENGTH_LONG).show();
                    return; // dialóg ostáva otvorený, nič sa nestratí
                }

                // Ak používateľ nezadal kalórie ručne (zo stroja), odhadni ich cez MET vzorec.
                int finalCalories = calories;
                if (finalCalories == 0 && speed > 0 && ProfileStore.hasProfile(this)) {
                    finalCalories = Math.round(CalorieCalculator.cardioKcal(
                            ProfileStore.getWeightKgOn(this, dateKey), duration, speed, incline));
                }

                CardioSession session = new CardioSession(duration, distance, speed, incline, finalCalories);
                if (editIndex != null) cardioSessions.set(editIndex, session);
                else cardioSessions.add(session);

                CardioStore.setSessions(this, dateKey, cardioSessions);
                renderCardioList(c);
                if (cbToCheck != null) {
                    cbToCheck.setChecked(true);
                    saveCategoriesToDisk();
                }
                dialog.dismiss();
            });
        });

        dialog.show();
    }

    // ─── Dialóg na pridanie / úpravu cvičenia ─────────────────────────────────

    /** editIndex == null → nový cvik v partii c; inak úprava existujúceho na danom indexe. */
    private void showExerciseDialog(Category c, Integer editIndex) {
        showExerciseDialog(c, editIndex, null);
    }

    /** restore != null → dialóg sa otvára znova po otočení obrazovky s rozpísaným stavom z restore. */
    private void showExerciseDialog(Category c, Integer editIndex, Bundle restore) {
        Exercise editing = (editIndex != null)
                ? exercisesByCategory.get(c.key).get(editIndex) : null;
        // Pri úprave existujúceho cvičenia je typ merania natrvalo fixovaný na to, čo už má
        // uložené (nemení sa podľa prípadnej zmeny textu názvu) — pri novom zázname sa odvodí
        // od vybraného/zadaného cviku.
        final boolean locked = editing != null;

        ScrollView dialogScroll = new ScrollView(this);
        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(dp(20), dp(16), dp(20), dp(16));
        dialogScroll.addView(dialogLayout);

        // Odpočet pauzy aj priamo v dialógu — séria sa zapisuje tu, nech je odpočet na očiach
        final boolean isTodayDialog = dateKey.equals(ActivityStore.todayKey());
        if (isTodayDialog && FeatureStore.isEnabled(this, FeatureStore.REST_TIMER)) {
            RestTimerBar restBar = new RestTimerBar(this);
            LinearLayout.LayoutParams restLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            restLp.bottomMargin = dp(12);
            restBar.setLayoutParams(restLp);
            dialogLayout.addView(restBar);
        }

        // Meno cvičenia — dropdown/našepkávač z pevného zoznamu (SK + EN názvy) + vlastné cviky
        List<String> dropdownItems = new ArrayList<>(ExerciseCatalog.getAll(this, c.key));
        dropdownItems.add(CUSTOM_EXERCISE_SENTINEL);

        Map<String, ExerciseDefinition> exerciseDefsByName = new HashMap<>();
        for (ExerciseDefinition def : ExerciseCatalog.getDefinitionsFor(this, c.key)) {
            exerciseDefsByName.put(def.sk, def);
        }

        // Ilustračný obrázok cviku — predvolene silueta svalu podľa partie (Category.muscleDrawableFor);
        // ak má vybraný cvik v katalógu zadanú imageUrl, ExerciseImageCache ho na pozadí dohľadá
        // (z disku, alebo raz stiahne a natrvalo uloží) a obrázok potichu vymení za ten profesionálny.
        ImageView ivPreview = new ImageView(this);
        ivPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int muscleFallbackRes = Category.muscleDrawableFor(c.key);
        if (muscleFallbackRes != 0) ivPreview.setImageDrawable(Category.muscleIcon(this, c.key));
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(dp(120), dp(90));
        previewLp.gravity = Gravity.CENTER_HORIZONTAL;
        previewLp.bottomMargin = dp(10);
        ivPreview.setLayoutParams(previewLp);
        dialogLayout.addView(ivPreview);

        AutoCompleteTextView etName = new AutoCompleteTextView(this);
        etName.setHint("Názov cvičenia — vyber, alebo napíš SK/EN");
        etName.setInputType(InputType.TYPE_CLASS_TEXT);
        etName.setTextColor(getColor(R.color.text_primary));
        etName.setHintTextColor(getColor(R.color.text_muted));
        styleInputBackground(etName);
        etName.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        etName.setThreshold(1);
        ArrayAdapter<String> nameAdapter = buildExerciseNameAdapter(dropdownItems, exerciseDefsByName);
        etName.setAdapter(nameAdapter);
        if (editing != null) etName.setText(editing.name);
        etName.setOnClickListener(v -> etName.showDropDown());

        // "Copy Last" — vedľa názvu cviku; poslucháč sa pripojí nižšie, až keď existujú
        // setRows/addRow (potrebné na predvyplnenie sérií).
        Button btnCopyLast = new Button(this);
        btnCopyLast.setText("Minulý tréning");
        btnCopyLast.setTextSize(11);
        btnCopyLast.setAllCaps(false);
        btnCopyLast.setTextColor(getColor(R.color.accent_primary));
        btnCopyLast.setBackground(UiKit.roundedBgWithBorder(
                this, R.color.bg_input, R.color.input_border, dp(1), UiKit.dimenPx(this, R.dimen.radius_chip)));
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT);
        copyLp.leftMargin = dp(8);
        btnCopyLast.setLayoutParams(copyLp);

        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        nameRow.addView(etName);
        nameRow.addView(btnCopyLast);
        dialogLayout.addView(nameRow);

        // Hint — naposledy
        TextView tvHint = new TextView(this);
        tvHint.setTextColor(getColor(R.color.accent_primary));
        tvHint.setTextSize(11);
        tvHint.setPadding(0, dp(4), 0, dp(12));
        dialogLayout.addView(tvHint);

        TextView setsLabel = new TextView(this);
        setsLabel.setTextColor(getColor(R.color.text_primary));
        setsLabel.setTextSize(12);
        setsLabel.setPadding(0, 0, 0, dp(6));
        dialogLayout.addView(setsLabel);

        LinearLayout setsContainer = new LinearLayout(this);
        setsContainer.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.addView(setsContainer);

        // Typ merania tohto cvičenia — určuje, aké vstupné polia sa zobrazia v sériách nižšie.
        final MetricType[] currentType = { locked ? editing.metricType() : MetricType.WEIGHT_AND_REPS };

        Runnable updateSetsLabel = () -> {
            switch (currentType[0]) {
                case BODYWEIGHT_AND_REPS:
                    setsLabel.setText("Série  (opakovania  +  voliteľná pridaná záťaž v kg):");
                    break;
                case DURATION:
                    setsLabel.setText("Série  (trvanie  MM:SS):");
                    break;
                default:
                    setsLabel.setText("Série  (váha  ×  opakovania  ×  jednotka):");
            }
        };

        // Riadky sérií — obsah View[] závisí od currentType[0]:
        //  WEIGHT_AND_REPS      -> {tvNumber, etWeight, etReps, unitSpinner}
        //  BODYWEIGHT_AND_REPS  -> {tvNumber, etReps, cbBodyweightOnly, bandSpinner, etAddedWeight}
        //  DURATION             -> {tvNumber, etDuration}
        List<View[]> setRows = new ArrayList<>();

        Runnable renumberRows = () -> {
            for (int i = 0; i < setRows.size(); i++) {
                ((TextView) setRows.get(i)[0]).setText((i + 1) + ".");
            }
        };

        Runnable addRow = () -> {
            LinearLayout setRow = new LinearLayout(this);
            setRow.setOrientation(LinearLayout.HORIZONTAL);
            setRow.setGravity(Gravity.CENTER_VERTICAL);
            setRow.setPadding(0, dp(4), 0, dp(4));

            // Číslo série
            TextView tvNum = new TextView(this);
            tvNum.setTextColor(getColor(R.color.text_muted));
            tvNum.setTextSize(11);
            tvNum.setPadding(0, 0, dp(6), 0);
            tvNum.setLayoutParams(new LinearLayout.LayoutParams(dp(22), LinearLayout.LayoutParams.WRAP_CONTENT));
            setRow.addView(tvNum);

            final View[] rowData;

            if (currentType[0] == MetricType.DURATION) {
                EditText etDur = makeInputInline("MM:SS, napr. 1:30", false);
                etDur.setInputType(InputType.TYPE_CLASS_TEXT);
                etDur.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                setRow.addView(etDur);
                rowData = new View[]{tvNum, etDur};

            } else if (currentType[0] == MetricType.BODYWEIGHT_AND_REPS) {
                // Viacriadkový blok: opakovania hore; nižšie voľba medzi "Iba vlastná váha",
                // asistenčným expanderom (záporná záťaž) alebo pridanou záťažou (kladná, napr. opasok/vesta).
                LinearLayout column = new LinearLayout(this);
                column.setOrientation(LinearLayout.VERTICAL);
                column.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                LinearLayout repsLine = new LinearLayout(this);
                repsLine.setOrientation(LinearLayout.HORIZONTAL);
                repsLine.setGravity(Gravity.CENTER_VERTICAL);
                EditText etR = makeInputInline("opak.", false);
                etR.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                repsLine.addView(etR);
                column.addView(repsLine);

                CheckBox cbBodyweightOnly = new CheckBox(this);
                cbBodyweightOnly.setText("Iba vlastná váha");
                cbBodyweightOnly.setTextColor(getColor(R.color.text_secondary));
                cbBodyweightOnly.setTextSize(11);
                cbBodyweightOnly.setChecked(true); // predvolené — čistá vlastná váha, bez pomoci/záťaže
                LinearLayout.LayoutParams cbLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                cbLp.topMargin = dp(2);
                cbBodyweightOnly.setLayoutParams(cbLp);
                column.addView(cbBodyweightOnly);

                LinearLayout loadLine = new LinearLayout(this);
                loadLine.setOrientation(LinearLayout.HORIZONTAL);
                loadLine.setGravity(Gravity.CENTER_VERTICAL);
                loadLine.setPadding(0, dp(4), 0, 0);

                Spinner bandSpinner = new Spinner(this);
                ArrayAdapter<String> bandAdapter = new ArrayAdapter<>(
                        this, android.R.layout.simple_spinner_item, BAND_LABELS);
                bandAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                bandSpinner.setAdapter(bandAdapter);
                bandSpinner.setSelection(0);
                bandSpinner.setBackground(UiKit.roundedBgWithBorder(
                        this, R.color.bg_input, R.color.input_border, dp(1), UiKit.dimenPx(this, R.dimen.radius_chip)));
                bandSpinner.setPadding(dp(6), dp(4), dp(6), dp(4));
                bandSpinner.setLayoutParams(new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                loadLine.addView(bandSpinner);

                EditText etAdded = makeInputInline("+kg záťaž (voliteľné)", true);
                LinearLayout.LayoutParams addedLp = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                addedLp.leftMargin = dp(6);
                etAdded.setLayoutParams(addedLp);
                loadLine.addView(etAdded);
                column.addView(loadLine);

                cbBodyweightOnly.setOnCheckedChangeListener((btn, checked) ->
                        syncBodyweightRowEnabled(cbBodyweightOnly, bandSpinner, etAdded));
                bandSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                        syncBodyweightRowEnabled(cbBodyweightOnly, bandSpinner, etAdded);
                    }
                    @Override public void onNothingSelected(AdapterView<?> p) {}
                });
                syncBodyweightRowEnabled(cbBodyweightOnly, bandSpinner, etAdded);

                setRow.addView(column);
                rowData = new View[]{tvNum, etR, cbBodyweightOnly, bandSpinner, etAdded};

            } else {
                EditText etW = makeInputInline("váha", true);
                etW.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                setRow.addView(etW);

                TextView x = new TextView(this);
                x.setText("  ×  ");
                x.setTextColor(getColor(R.color.text_muted));
                setRow.addView(x);

                EditText etR = makeInputInline("opak.", false);
                etR.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                setRow.addView(etR);

                Spinner unitSpinner = new Spinner(this);
                ArrayAdapter<String> unitAdapter = new ArrayAdapter<>(
                        this, android.R.layout.simple_spinner_item, UNIT_LABELS);
                unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                unitSpinner.setAdapter(unitAdapter);
                unitSpinner.setSelection(0); // kg predvolené
                unitSpinner.setBackground(UiKit.roundedBgWithBorder(
                        this, R.color.bg_input, R.color.input_border, dp(1), UiKit.dimenPx(this, R.dimen.radius_chip)));
                unitSpinner.setPadding(dp(8), dp(4), dp(8), dp(4));
                unitSpinner.setLayoutParams(new LinearLayout.LayoutParams(
                        dp(76), LinearLayout.LayoutParams.WRAP_CONTENT));
                setRow.addView(unitSpinner);

                rowData = new View[]{tvNum, etW, etR, unitSpinner};
            }

            // RPE (subjektívna náročnosť) — voliteľné; čip je uložený ako tag čísla série (rowData[0])
            TextView rpeChip = makeRpeChip();
            tvNum.setTag(rpeChip);
            setRow.addView(rpeChip);

            TextView btnX = new TextView(this);
            btnX.setText("✕");
            btnX.setTextColor(getColor(R.color.text_white));
            btnX.setTextSize(14);
            btnX.setTypeface(null, android.graphics.Typeface.BOLD);
            btnX.setGravity(Gravity.CENTER);
            btnX.setBackground(UiKit.roundedBg(this, R.color.accent_danger_darker, UiKit.dimenPx(this, R.dimen.radius_chip)));
            LinearLayout.LayoutParams btnXLp = new LinearLayout.LayoutParams(dp(30), dp(30));
            btnXLp.leftMargin = dp(8);
            btnX.setLayoutParams(btnXLp);
            btnX.setOnClickListener(v2 -> {
                setsContainer.removeView(setRow);
                setRows.remove(rowData);
                renumberRows.run();
            });
            setRow.addView(btnX);

            setsContainer.addView(setRow);
            setRows.add(rowData);
            renumberRows.run();
        };

        Runnable clearRows = () -> {
            setsContainer.removeAllViews();
            setRows.clear();
        };

        // Prekreslí riadky podľa currentType[0] a naplní ich danými sériami (null/prázdne = jeden prázdny riadok).
        java.util.function.Consumer<List<SetEntry>> fillRows = sets -> {
            clearRows.run();
            if (sets == null || sets.isEmpty()) {
                addRow.run();
                return;
            }
            for (SetEntry se : sets) {
                addRow.run();
                View[] row = setRows.get(setRows.size() - 1);
                if (currentType[0] == MetricType.DURATION) {
                    ((EditText) row[1]).setText(SetEntry.formatDuration(se.durationSeconds));
                } else if (currentType[0] == MetricType.BODYWEIGHT_AND_REPS) {
                    ((EditText) row[1]).setText(String.valueOf(se.reps));
                    CheckBox cbBW = (CheckBox) row[2];
                    Spinner bandSp = (Spinner) row[3];
                    EditText etAdded = (EditText) row[4];
                    if (se.weight == 0) {
                        cbBW.setChecked(true);
                        bandSp.setSelection(0);
                    } else if (se.weight < 0) {
                        cbBW.setChecked(false);
                        int bandIdx = BAND_VALUES.indexOf(se.weight);
                        bandSp.setSelection(bandIdx >= 0 ? bandIdx : 0);
                        if (bandIdx < 0) etAdded.setText(formatW(se.weight)); // asistencia mimo bežných hodnôt gúm
                    } else {
                        cbBW.setChecked(false);
                        bandSp.setSelection(0);
                        etAdded.setText(formatW(se.weight));
                    }
                    syncBodyweightRowEnabled(cbBW, bandSp, etAdded);
                } else {
                    ((EditText) row[1]).setText(formatW(se.weight));
                    ((EditText) row[2]).setText(String.valueOf(se.reps));
                    int unitIdx = UNIT_VALUES.indexOf(se.unit);
                    ((Spinner) row[3]).setSelection(unitIdx < 0 ? 0 : unitIdx);
                }
                setRpe((TextView) row[0].getTag(), se.rpe);
            }
        };

        // Zmena typu merania (napr. výberom iného cviku z dropdownu) — prekreslí riadky nanovo.
        java.util.function.Consumer<MetricType> switchType = newType -> {
            if (locked || newType == null || newType == currentType[0]) return;
            currentType[0] = newType;
            updateSetsLabel.run();
            fillRows.accept(null);
        };

        // Vyplní riadky poslednou zapísanou sériou tohto cviku (Copy Last aj auto-fill nižšie
        // zdieľajú túto istú logiku — vrátane typu merania, ak ešte nie je fixovaný úpravou
        // existujúceho záznamu).
        java.util.function.Consumer<Exercise> applyHistorical = last -> {
            if (last == null || last.sets.isEmpty()) return;
            if (!locked) {
                currentType[0] = last.metricType();
                updateSetsLabel.run();
            }
            fillRows.accept(last.sets);
            // RPE sa hodnotí pri každej sérii nanovo — z minulého tréningu ho nepreberáme
            for (View[] row : setRows) setRpe((TextView) row[0].getTag(), 0f);
        };

        // One-Click Auto-Fill: keď appka práve rozpoznala cvik a používateľ ešte nič nezačal
        // vypĺňať (jediný, celkom prázdny riadok), rovno predvyplní hodnoty z posledného
        // odtrénovaného záznamu — nemusí sa čakať na ručné kliknutie "Minulý tréning".
        Runnable autoFillFirstRowFromHistory = () -> {
            if (locked || setRows.size() != 1) return;
            View[] r = setRows.get(0);
            boolean untouched;
            if (currentType[0] == MetricType.DURATION) {
                untouched = ((EditText) r[1]).getText().toString().trim().isEmpty();
            } else if (currentType[0] == MetricType.BODYWEIGHT_AND_REPS) {
                untouched = ((EditText) r[1]).getText().toString().trim().isEmpty();
            } else {
                untouched = ((EditText) r[1]).getText().toString().trim().isEmpty()
                        && ((EditText) r[2]).getText().toString().trim().isEmpty();
            }
            if (!untouched) return; // používateľ už niečo napísal — nič mu neprepisuj
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) return;
            applyHistorical.accept(ExerciseStore.getLastEntryForExercise(this, name));
        };

        etName.setOnItemClickListener((parent, view, position, id) -> {
            String picked = nameAdapter.getItem(position);
            if (CUSTOM_EXERCISE_SENTINEL.equals(picked)) {
                etName.setText(editing != null ? editing.name : "");
                promptCustomExerciseName(c.key, (newName, metricType) -> {
                    ExerciseCatalog.addCustom(this, c.key, newName, metricType);
                    dropdownItems.add(dropdownItems.size() - 1, newName); // pred sentinel
                    exerciseDefsByName.put(newName, new ExerciseDefinition(newName, null, null, metricType));
                    nameAdapter.notifyDataSetChanged();
                    etName.setText(newName);
                    etName.setSelection(newName.length());
                    switchType.accept(metricType);
                    refreshExercisePreview(ivPreview, c, newName);
                    // nový vlastný cvik — zatiaľ nemá žiadnu históriu, autoFill je no-op
                });
            } else {
                ExerciseDefinition def = exerciseDefsByName.get(picked);
                switchType.accept(def != null ? def.metricType : MetricType.WEIGHT_AND_REPS);
                refreshExercisePreview(ivPreview, c, picked);
                autoFillFirstRowFromHistory.run();
            }
        });

        etName.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String name = etName.getText().toString().trim();
                if (!name.isEmpty()) {
                    Exercise last = ExerciseStore.getLastEntryForExercise(this, name);
                    tvHint.setText(last != null
                            ? "📈 Naposledy: " + last.summary()
                            : "Nové cvičenie — zatiaľ žiadna história");
                    // Skutočný typ merania posledného záznamu (ak existuje) má prednosť pred
                    // katalógovým hintom — je to zdroj pravdy o tom, ako bol cvik naozaj zapísaný.
                    ExerciseDefinition def = exerciseDefsByName.get(name);
                    MetricType detected = last != null ? last.metricType() : (def != null ? def.metricType : null);
                    switchType.accept(detected);
                    refreshExercisePreview(ivPreview, c, name);
                    // Predvyplniť z minulého tréningu len prázdny riadok — nikdy neprepísať, čo už
                    // používateľ napísal (alebo čo sa obnovilo po otočení obrazovky)
                    autoFillFirstRowFromHistory.run();
                }
            }
        });

        // Naplň série
        updateSetsLabel.run();
        fillRows.accept(editing != null ? editing.sets : null);
        refreshExercisePreview(ivPreview, c, editing != null ? editing.name : "");

        // Obnova po otočení obrazovky — presne to, čo bolo rozpísané (aj čiastočne vyplnené riadky)
        if (restore != null) {
            String restoredName = restore.getString("name", "");
            etName.setText(restoredName);
            if (!locked) currentType[0] = MetricType.fromCode(restore.getString("type"));
            updateSetsLabel.run();
            clearRows.run();
            int rowCount = restore.getInt("rowCount", 0);
            for (int i = 0; i < rowCount; i++) {
                Bundle rowState = restore.getBundle("row" + i);
                if (rowState == null) continue;
                addRow.run();
                restoreRow(setRows.get(setRows.size() - 1), currentType[0], rowState);
            }
            if (setRows.isEmpty()) addRow.run();
            tvHint.setText(restore.getString("hint", ""));
            refreshExercisePreview(ivPreview, c, restoredName);
        }

        // "Minulý tréning" — to isté ako auto-fill vyššie, len na ručné vyžiadanie kedykoľvek počas rozrábania.
        btnCopyLast.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "Najprv zadaj názov cvičenia.", Toast.LENGTH_SHORT).show();
                return;
            }
            Exercise last = ExerciseStore.getLastEntryForExercise(this, name);
            if (last == null || last.sets.isEmpty()) {
                Toast.makeText(this, "Pre „" + name + "“ zatiaľ nemáme žiadnu históriu.", Toast.LENGTH_SHORT).show();
                return;
            }
            applyHistorical.accept(last);
            Toast.makeText(this, "Predvyplnené podľa posledného tréningu.", Toast.LENGTH_SHORT).show();
        });

        TextView btnAddSet = new TextView(this);
        btnAddSet.setText("+ Pridať sériu");
        btnAddSet.setTextColor(getColor(R.color.accent_primary));
        btnAddSet.setTextSize(12);
        btnAddSet.setPadding(0, dp(10), 0, dp(4));
        // Nová séria automaticky zrkadlí hodnoty poslednej rozrobenej série v tomto dialógu
        // (typicky sú ďalšie série s tou istou váhou/opakovaniami) — používateľ len prepíše, čo sa líši.
        btnAddSet.setOnClickListener(v -> {
            View[] prevRow = setRows.isEmpty() ? null : setRows.get(setRows.size() - 1);
            addRow.run();
            if (prevRow != null) copyRowValues(prevRow, setRows.get(setRows.size() - 1), currentType[0]);
            // Pridanie ďalšej série = predchádzajúca je odcvičená -> spusti pauzu (len dnes)
            if (isTodayDialog && RestTimer.isAutoStart(this)
                    && FeatureStore.isEnabled(this, FeatureStore.REST_TIMER)) {
                RestTimer.maybeAskNotificationPermission(this);
                RestTimer.startDefault(this);
            }
        });
        dialogLayout.addView(btnAddSet);

        String positiveLabel = editing != null ? "Uložiť" : "Pridať";

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle((editing != null ? "Upraviť" : "Pridať") + " — " + c.label)
                .setView(dialogScroll)
                .setPositiveButton(positiveLabel, null) // listener sa nastaví nižšie, aby dialóg pri chybe nezmizol
                .setNegativeButton("Zrušiť", null)
                .setCancelable(false) // náhodný ťuk mimo dialóg (spotené prsty) nesmie zahodiť rozrobené série
                .create();
        keepButtonsAboveKeyboard(dialog);

        // Rozpísaný stav dialógu pre onSaveInstanceState (otočenie / zavretie appky na pozadí)
        exerciseDialog = dialog;
        exerciseDialogState = () -> {
            Bundle state = new Bundle();
            state.putString("cat", c.key);
            state.putInt("edit", editIndex != null ? editIndex : -1);
            state.putString("name", etName.getText().toString());
            state.putString("type", currentType[0].code());
            state.putString("hint", tvHint.getText().toString());
            state.putInt("rowCount", setRows.size());
            for (int i = 0; i < setRows.size(); i++) {
                state.putBundle("row" + i, saveRow(setRows.get(i), currentType[0]));
            }
            return state;
        };
        dialog.setOnDismissListener(d -> {
            if (exerciseDialog == dialog) {
                exerciseDialog = null;
                exerciseDialogState = null;
            }
        });

        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setOnClickListener(v -> {
                // Znaky, ktoré appka používa ako oddeľovače pri ukladaní (Exercise.serialize ich aj tak
                // zahodí) — odstrániť už tu, aby sa meno v pamäti zhodovalo s uloženým a história sa našla
                String name = etName.getText().toString()
                        .replace("::", "").replace(";", "").replace(",", "").trim();
                if (name.isEmpty()) {
                    Toast.makeText(this, "Zadaj názov cvičenia.", Toast.LENGTH_SHORT).show();
                    return;
                }

                List<SetEntry> sets = new ArrayList<>();
                boolean invalidRow = false;

                if (currentType[0] == MetricType.DURATION) {
                    for (View[] r : setRows) {
                        String durStr = ((EditText) r[1]).getText().toString();
                        if (durStr.trim().isEmpty()) continue; // úplne prázdny riadok — v poriadku, preskoč
                        Integer seconds = SetEntry.parseDuration(durStr);
                        if (seconds == null || seconds <= 0) { invalidRow = true; break; } // 0:00 nie je séria
                        sets.add(withRpe(SetEntry.duration(seconds), r));
                    }
                } else if (currentType[0] == MetricType.BODYWEIGHT_AND_REPS) {
                    for (View[] r : setRows) {
                        String repsStr = ((EditText) r[1]).getText().toString();
                        if (repsStr.trim().isEmpty()) continue; // úplne prázdny riadok — v poriadku, preskoč

                        Integer reps = parseRepsOrNull(repsStr);
                        if (reps == null || reps <= 0) { invalidRow = true; break; } // 0 opakovaní nie je séria

                        boolean bodyweightOnly = ((CheckBox) r[2]).isChecked();
                        int bandIdx = ((Spinner) r[3]).getSelectedItemPosition();
                        float delta;
                        if (bodyweightOnly) {
                            delta = 0f;
                        } else if (bandIdx > 0) {
                            delta = BAND_VALUES.get(bandIdx); // expander — už záporná hodnota (asistencia)
                        } else {
                            String addedStr = ((EditText) r[4]).getText().toString();
                            Float added = addedStr.trim().isEmpty() ? 0f : parseWeightOrNull(addedStr);
                            if (added == null) { invalidRow = true; break; }
                            delta = added;
                        }
                        sets.add(withRpe(SetEntry.bodyweight(reps, delta), r));
                    }
                } else {
                    for (View[] r : setRows) {
                        EditText etW = (EditText) r[1];
                        EditText etR = (EditText) r[2];
                        String wStr = etW.getText().toString();
                        String rStr = etR.getText().toString();
                        boolean wEmpty = wStr.trim().isEmpty();
                        boolean rEmpty = rStr.trim().isEmpty();
                        if (wEmpty && rEmpty) continue; // úplne prázdny riadok — v poriadku, preskoč

                        Float w = parseWeightOrNull(wStr);
                        Integer reps = parseRepsOrNull(rStr);
                        // 0 opakovaní alebo záporná váha by pokazili 1RM, objem aj rekordy
                        if (w == null || reps == null || reps <= 0 || w < 0) {
                            invalidRow = true; // čiastočne vyplnená / nečitateľná séria — NIKDY ju nezahoď potichu
                            break;
                        }
                        int unitSel = ((Spinner) r[3]).getSelectedItemPosition();
                        String unit = (unitSel >= 0 && unitSel < UNIT_VALUES.size())
                                ? UNIT_VALUES.get(unitSel) : SetEntry.UNIT_KG;
                        sets.add(withRpe(new SetEntry(w, reps, unit), r));
                    }
                }

                if (invalidRow) {
                    Toast.makeText(this,
                            "Skontroluj série — niektorej chýba povinná hodnota alebo je neplatná.",
                            Toast.LENGTH_LONG).show();
                    return; // dialóg ostáva otvorený, nič sa nestratí ani neprepíše na 0
                }
                if (sets.isEmpty()) {
                    Toast.makeText(this, "Pridaj aspoň jednu sériu.", Toast.LENGTH_SHORT).show();
                    return;
                }

                List<Exercise> list = exercisesByCategory.get(c.key);
                if (editIndex != null) list.set(editIndex, new Exercise(name, sets));
                else list.add(new Exercise(name, sets));

                // *** Okamžite ulož na disk — každé pridanie/úprava ***
                ExerciseStore.setExercises(this, dateKey, c.key, list);
                // Čas tréningu merať len pri dnešnom dni — pri dopisovaní/oprave starého dňa by sa
                // namiesto tréningu meral čas úprav a pripočítal sa k minútam (a kalóriám) toho dňa
                if (sessionFirstSetAtMillis == null && dateKey.equals(ActivityStore.todayKey())) {
                    sessionFirstSetAtMillis = System.currentTimeMillis();
                }

                // Partia sa do kalendára/štatistík odvodí automaticky podľa toho, či má dnes
                // aspoň 1 cvik — žiadny manuálny checkbox, žiadne obmedzenie kombinácií.
                saveCategoriesToDisk();
                renderStrengthSection();
                dialog.dismiss();
            });
        });

        dialog.show();
    }

    /**
     * Adaptér pre AutoCompleteTextView, ktorý filtruje case-insensitive a bez diakritiky,
     * naprieč slovenským aj anglickým (+ alternatívnym) názvom naraz — napr. "calf" nájde
     * "Výpony na lýtka" aj keď sa v poli zobrazuje len slovenský názov.
     */
    private ArrayAdapter<String> buildExerciseNameAdapter(List<String> allItems, Map<String, ExerciseDefinition> bySkName) {
        return new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, new ArrayList<>(allItems)) {
            @Override
            public Filter getFilter() {
                return new Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence constraint) {
                        String query = (constraint == null) ? "" : ExerciseDefinition.normalize(constraint.toString());
                        List<String> filtered = new ArrayList<>();
                        for (String item : allItems) {
                            if (CUSTOM_EXERCISE_SENTINEL.equals(item) || query.isEmpty()) {
                                filtered.add(item);
                                continue;
                            }
                            ExerciseDefinition def = bySkName.get(item);
                            String haystack = (def != null) ? def.searchBlob : ExerciseDefinition.normalize(item);
                            if (haystack.contains(query)) filtered.add(item);
                        }
                        FilterResults results = new FilterResults();
                        results.values = filtered;
                        results.count = filtered.size();
                        return results;
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    protected void publishResults(CharSequence constraint, FilterResults results) {
                        clear();
                        if (results.values != null) addAll((List<String>) results.values);
                        notifyDataSetChanged();
                    }
                };
            }
        };
    }

    private void promptCustomExerciseName(String categoryKey, BiConsumer<String, MetricType> onAdded) {
        EditText input = makeInput("Napr. Cable crossover", InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this)
                .setTitle("Nový vlastný cvik")
                .setView(input)
                .setPositiveButton("Ďalej", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) promptMetricTypeThenAdd(name, onAdded);
                })
                .setNegativeButton("Zrušiť", null)
                .show();
    }

    /** Druhý krok pridania vlastného cviku — výber typu merania (ovplyvňuje, aké polia sa pri sériách zobrazia). */
    private void promptMetricTypeThenAdd(String name, BiConsumer<String, MetricType> onAdded) {
        String[] labels = {
                "Klasika — váha × opakovania",
                "Vlastná váha — opakovania (+ voliteľná záťaž)",
                "Časový cvik — trvanie (MM:SS)",
        };
        MetricType[] types = {MetricType.WEIGHT_AND_REPS, MetricType.BODYWEIGHT_AND_REPS, MetricType.DURATION};
        new AlertDialog.Builder(this)
                .setTitle("Ako sa má „" + name + "“ merať?")
                .setItems(labels, (dialog, which) -> onAdded.accept(name, types[which]))
                .show();
    }

    // ─── Ukladanie ────────────────────────────────────────────────────────────

    /**
     * Uloží stav aktívnych kategórií pre tento deň na disk okamžite. Kardio je stále
     * manuálny checkbox; svalové partie sa odvodia automaticky — partia je "aktívna" v
     * daný deň práve vtedy, keď má aspoň 1 uložený cvik.
     */
    private void saveCategoriesToDisk() {
        Set<String> selected = new HashSet<>();
        for (CheckBox cb : checkBoxes) {
            if (cb.isChecked()) selected.add((String) cb.getTag());
        }
        for (Map.Entry<String, List<Exercise>> entry : exercisesByCategory.entrySet()) {
            if (!entry.getValue().isEmpty()) selected.add(entry.getKey());
        }
        ActivityStore.setCategoriesForDate(this, dateKey, selected);
    }

    /**
     * Uloženie a zatvorenie — ak je za tento deň zaznamenaná nejaká aktivita a hodnotenie ešte
     * nie je zapísané, najprv sa opýta na subjektívny pocit z tréningu ("Ako sa dnes cítim");
     * na prázdny (odpočinkový) deň, alebo ak už hodnotenie existuje (staršie/už uložené
     * tréningy), sa pýtať nemá zmysel, tam sa rovno zavrie.
     */
    private void saveAndClose() {
        if (hasAnyActivityToday() && WorkoutFeelingStore.getRating(this, dateKey) <= 0) {
            showFeelingDialog(true);
        } else {
            finishSaveAndClose();
        }
    }

    private boolean hasAnyActivityToday() {
        if (!cardioSessions.isEmpty()) return true;
        for (List<Exercise> list : exercisesByCategory.values()) {
            if (!list.isEmpty()) return true;
        }
        return false;
    }

    /** Finálne uloženie a zatvorenie — kategórie + cvičenia + kardio (už uložené priebežne, tu len istota). */
    private void finishSaveAndClose() {
        saveCategoriesToDisk();
        for (Map.Entry<String, List<Exercise>> entry : exercisesByCategory.entrySet()) {
            ExerciseStore.setExercises(this, dateKey, entry.getKey(), entry.getValue());
        }
        CardioStore.setSessions(this, dateKey, cardioSessions);
        accumulateWorkoutTime();
        finish();
    }

    // ─── Zdieľanie tréningu ─────────────────────────────────────────────────

    /**
     * Vykreslí celý obsah dňa (nie len to, čo je práve vidieť na obrazovke — content view vnútri
     * ScrollView je vždy zmeraný na celú svoju výšku, aj mimo viditeľnej časti) do jedného
     * dlhého PNG obrázku a otvorí naň systémovú ponuku "Zdieľať". Rieši to, že bežný screenshot
     * zachytí len jednu obrazovku, kým tréning môže byť dlhší.
     */
    private void shareWorkoutImage(LinearLayout content) {
        try {
            int width = content.getWidth();
            int height = content.getHeight();
            if (width <= 0 || height <= 0) {
                Toast.makeText(this, "Obsah sa ešte nenačítal, skús to znova.", Toast.LENGTH_SHORT).show();
                return;
            }
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(getColor(R.color.bg_app));
            content.draw(canvas);

            File file = new File(getCacheDir(), "fittracker_trening_" + dateKey + ".png");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            }
            bitmap.recycle();

            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/png");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Zdieľať tréning do..."));
        } catch (Exception e) {
            Toast.makeText(this, "Zdieľanie zlyhalo: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ─── Pocit z tréningu ("Ako sa dnes cítim") ────────────────────────────────

    /**
     * Dialóg na zadanie subjektívneho hodnotenia (1-5) a voľnej poznámky ku kondícii pre tento
     * deň. Ak finalSave == true, ide o záverečný krok pred uložením a zatvorením obrazovky
     * (tlačidlo "Preskočiť" uloží deň bez hodnotenia); inak ide o dodatočnú úpravu už uloženého
     * hodnotenia z karty v zhrnutí dňa (bez zatvorenia obrazovky).
     */
    private void showFeelingDialog(boolean finalSave) {
        ScrollView dialogScroll = new ScrollView(this);
        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(dp(20), dp(16), dp(20), dp(16));
        dialogScroll.addView(dialogLayout);

        TextView label = new TextView(this);
        label.setText("Ako sa dnes cítiš (1 = kríza, 5 = top forma)?");
        label.setTextColor(getColor(R.color.text_primary));
        label.setTextSize(13);
        label.setPadding(0, 0, 0, dp(10));
        dialogLayout.addView(label);

        LinearLayout ratingRow = new LinearLayout(this);
        ratingRow.setOrientation(LinearLayout.HORIZONTAL);
        ratingRow.setGravity(Gravity.CENTER);
        dialogLayout.addView(ratingRow);

        int existingRating = WorkoutFeelingStore.getRating(this, dateKey);
        int[] selectedRating = {existingRating};
        List<TextView> ratingButtons = new ArrayList<>();
        String[] emojis = {"😣", "😕", "😐", "🙂", "💪"};

        Runnable refreshHighlight = () -> {
            for (int j = 0; j < ratingButtons.size(); j++) {
                boolean selected = (j + 1) == selectedRating[0];
                ratingButtons.get(j).setBackground(UiKit.roundedBg(
                        this, selected ? R.color.accent_primary : R.color.bg_input,
                        UiKit.dimenPx(this, R.dimen.radius_chip)));
            }
        };

        for (int i = 1; i <= 5; i++) {
            int ratingValue = i;
            TextView btn = new TextView(this);
            btn.setText(emojis[i - 1] + "\n" + i);
            btn.setTextSize(15);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(dp(6), dp(8), dp(6), dp(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(3), 0, dp(3), 0);
            btn.setLayoutParams(lp);
            btn.setOnClickListener(v -> {
                selectedRating[0] = ratingValue;
                refreshHighlight.run();
            });
            ratingButtons.add(btn);
            ratingRow.addView(btn);
        }
        refreshHighlight.run();

        TextView noteLabel = new TextView(this);
        noteLabel.setText("Poznámka ku kondícii (voliteľné)");
        noteLabel.setTextColor(getColor(R.color.text_secondary));
        noteLabel.setTextSize(12);
        noteLabel.setPadding(0, dp(14), 0, dp(4));
        dialogLayout.addView(noteLabel);

        EditText etNote = makeInput("napr. spal som len 5 hodín, bolí ľavé rameno...",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        etNote.setText(WorkoutFeelingStore.getNote(this, dateKey));
        etNote.setMinLines(2);
        dialogLayout.addView(etNote);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("🩺 Pocit z tréningu")
                .setView(dialogScroll)
                .setCancelable(false)
                .setPositiveButton(finalSave ? "Uložiť a zavrieť" : "Uložiť", null);
        if (finalSave) {
            builder.setNeutralButton("Preskočiť", (dialog, which) -> finishSaveAndClose());
        } else {
            builder.setNegativeButton("Zrušiť", null);
        }
        AlertDialog dialog = builder.create();
        keepButtonsAboveKeyboard(dialog);

        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setOnClickListener(v -> {
                WorkoutFeelingStore.setRating(this, dateKey, selectedRating[0]);
                WorkoutFeelingStore.setNote(this, dateKey, etNote.getText().toString());
                dialog.dismiss();
                if (finalSave) finishSaveAndClose();
                else recreate();
            });
        });

        dialog.show();
    }

    // ─── Helper metódy ────────────────────────────────────────────────────────

    /**
     * Pri otvorenej klávesnici sa dialóg zmenší a posunie nad ňu, takže tlačidlá Uložiť/Zrušiť
     * ostanú stále viditeľné (obsah sa roluje). Bez toho klávesnica tlačidlá prekryla a dalo sa
     * k nim dostať až po preklikaní Enterom cez všetky polia, kým sa klávesnica nezavrela.
     */
    private void keepButtonsAboveKeyboard(AlertDialog dialog) {
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    /**
     * Zosynchronizuje stav ovládacích prvkov jedného BODYWEIGHT_AND_REPS riadku série:
     * "Iba vlastná váha" vypína aj expander aj pridanú záťaž; vybraný expander (asistencia)
     * vypína pole pridanej záťaže, keďže naraz dáva zmysel len jedno z toho.
     */
    private void syncBodyweightRowEnabled(CheckBox cbBodyweightOnly, Spinner bandSpinner, EditText etAdded) {
        boolean bodyweightOnly = cbBodyweightOnly.isChecked();
        bandSpinner.setEnabled(!bodyweightOnly);
        bandSpinner.setAlpha(bodyweightOnly ? 0.4f : 1f);
        boolean bandSelected = bandSpinner.getSelectedItemPosition() > 0;
        boolean addedEnabled = !bodyweightOnly && !bandSelected;
        etAdded.setEnabled(addedEnabled);
        etAdded.setAlpha(addedEnabled ? 1f : 0.4f);
    }

    /**
     * Nastaví predvolený obrázok (silueta svalu danej partie); zabudované cviky ho potom potichu
     * vymenia za fotku z free-exercise-db (ExerciseMedia — z disku hneď, inak sa raz stiahne).
     * Vlastné cviky fotku nemajú, ostane silueta.
     */
    private void refreshExercisePreview(ImageView iv, Category c, String exerciseName) {
        int muscleRes = Category.muscleDrawableFor(c.key);
        if (muscleRes != 0) iv.setImageDrawable(Category.muscleIcon(this, c.key));
        iv.setTag(null); // zruší prípadné ešte bežiace načítanie fotky predošlého cviku
        if (exerciseName == null || exerciseName.trim().isEmpty()) return;
        ExerciseMedia.Info media = ExerciseMedia.get(this, exerciseName);
        if (media != null && media.imageCount > 0) {
            ExerciseImageCache.loadInto(this, media.imageCacheKey(0), media.imageUrl(0), iv);
        }
    }

    /**
     * Skopíruje hodnoty jedného riadku série do druhého (rovnaký MetricType) — použité pri
     * "+ Pridať sériu", aby nová séria rovno začala tam, kde skončila predchádzajúca.
     */
    // ─── RPE (subjektívna náročnosť série) ─────────────────────────────────────

    private TextView makeRpeChip() {
        TextView chip = new TextView(this);
        chip.setTextSize(12);
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setBackground(UiKit.roundedBgWithBorder(
                this, R.color.bg_input, R.color.input_border, dp(1), UiKit.dimenPx(this, R.dimen.radius_chip)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(36));
        lp.leftMargin = dp(6);
        chip.setLayoutParams(lp);
        chip.setContentDescription("RPE — náročnosť série");
        chip.setOnClickListener(v -> showRpePicker(chip));
        setRpe(chip, 0f);
        // Vypnuté RPE (režim Len zapisovanie) čip len skryje — už zadané RPE sa pri úprave zachová
        if (!FeatureStore.isEnabled(this, FeatureStore.RPE)) chip.setVisibility(View.GONE);
        return chip;
    }

    private void setRpe(TextView chip, float rpe) {
        if (chip == null) return;
        chip.setTag(rpe);
        if (rpe > 0) {
            chip.setText("@" + SetEntry.formatRpe(rpe));
            chip.setTextColor(getColor(R.color.accent_primary));
            chip.setTypeface(null, android.graphics.Typeface.BOLD);
        } else {
            chip.setText("RPE");
            chip.setTextColor(getColor(R.color.text_muted));
            chip.setTypeface(null, android.graphics.Typeface.NORMAL);
        }
    }

    private SetEntry withRpe(SetEntry se, View[] row) {
        Object chip = row[0].getTag();
        if (chip instanceof TextView && ((TextView) chip).getTag() instanceof Float) {
            se.rpe = (Float) ((TextView) chip).getTag();
        }
        return se;
    }

    /** Výber RPE s vysvetlením (RIR = koľko opakovaní ostalo v zásobe). */
    private void showRpePicker(TextView chip) {
        String[] labels = {
                "— bez RPE",
                "10 — do zlyhania, už ani jedno",
                "9,5 — možno by som dal ešte 1",
                "9 — ešte 1 opakovanie v zásobe",
                "8,5 — 1–2 v zásobe",
                "8 — 2 opakovania v zásobe",
                "7,5 — 2–3 v zásobe",
                "7 — 3 opakovania v zásobe",
                "6,5 — 3–4 v zásobe",
                "6 — ľahké, 4+ v zásobe",
        };
        float[] values = {0f, 10f, 9.5f, 9f, 8.5f, 8f, 7.5f, 7f, 6.5f, 6f};
        float current = chip.getTag() instanceof Float ? (Float) chip.getTag() : 0f;
        int checked = 0;
        for (int i = 0; i < values.length; i++) if (values[i] == current) checked = i;
        new AlertDialog.Builder(this)
                .setTitle("RPE — aká ťažká bola séria?")
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    setRpe(chip, values[which]);
                    d.dismiss();
                })
                .setNegativeButton("Zrušiť", null)
                .show();
    }

    /**
     * Surový obsah jedného riadku série (presne tak, ako je napísaný, aj neúplný) — na uloženie
     * rozpísaného dialógu pri otočení obrazovky. Rozloženie View[] pozri pri addRow v showExerciseDialog.
     */
    private Bundle saveRow(View[] r, MetricType type) {
        Bundle b = new Bundle();
        if (type == MetricType.DURATION) {
            b.putString("a", ((EditText) r[1]).getText().toString());
        } else if (type == MetricType.BODYWEIGHT_AND_REPS) {
            b.putString("a", ((EditText) r[1]).getText().toString());
            b.putBoolean("bw", ((CheckBox) r[2]).isChecked());
            b.putInt("band", ((Spinner) r[3]).getSelectedItemPosition());
            b.putString("b", ((EditText) r[4]).getText().toString());
        } else {
            b.putString("a", ((EditText) r[1]).getText().toString());
            b.putString("b", ((EditText) r[2]).getText().toString());
            b.putInt("unit", ((Spinner) r[3]).getSelectedItemPosition());
        }
        Object chip = r[0].getTag();
        if (chip instanceof TextView && ((TextView) chip).getTag() instanceof Float) {
            b.putFloat("rpe", (Float) ((TextView) chip).getTag());
        }
        return b;
    }

    /** Opak saveRow — vráti do riadku série presne to, čo v ňom bolo rozpísané. */
    private void restoreRow(View[] r, MetricType type, Bundle b) {
        if (type == MetricType.DURATION) {
            ((EditText) r[1]).setText(b.getString("a", ""));
        } else if (type == MetricType.BODYWEIGHT_AND_REPS) {
            ((EditText) r[1]).setText(b.getString("a", ""));
            CheckBox cb = (CheckBox) r[2];
            Spinner band = (Spinner) r[3];
            EditText added = (EditText) r[4];
            cb.setChecked(b.getBoolean("bw", true));
            int bandIdx = b.getInt("band", 0);
            band.setSelection(bandIdx >= 0 && bandIdx < BAND_LABELS.size() ? bandIdx : 0);
            added.setText(b.getString("b", ""));
            syncBodyweightRowEnabled(cb, band, added);
        } else {
            ((EditText) r[1]).setText(b.getString("a", ""));
            ((EditText) r[2]).setText(b.getString("b", ""));
            int unitIdx = b.getInt("unit", 0);
            ((Spinner) r[3]).setSelection(unitIdx >= 0 && unitIdx < UNIT_LABELS.size() ? unitIdx : 0);
        }
        setRpe((TextView) r[0].getTag(), b.getFloat("rpe", 0f));
    }

    private void copyRowValues(View[] from, View[] to, MetricType type) {
        if (type == MetricType.DURATION) {
            ((EditText) to[1]).setText(((EditText) from[1]).getText());
        } else if (type == MetricType.BODYWEIGHT_AND_REPS) {
            ((EditText) to[1]).setText(((EditText) from[1]).getText()); // opakovania
            CheckBox cbFrom = (CheckBox) from[2];
            Spinner bandFrom = (Spinner) from[3];
            CheckBox cbTo = (CheckBox) to[2];
            Spinner bandTo = (Spinner) to[3];
            EditText addedTo = (EditText) to[4];
            cbTo.setChecked(cbFrom.isChecked());
            bandTo.setSelection(bandFrom.getSelectedItemPosition());
            addedTo.setText(((EditText) from[4]).getText());
            syncBodyweightRowEnabled(cbTo, bandTo, addedTo);
        } else {
            ((EditText) to[1]).setText(((EditText) from[1]).getText()); // váha
            ((EditText) to[2]).setText(((EditText) from[2]).getText()); // opakovania
            ((Spinner) to[3]).setSelection(((Spinner) from[3]).getSelectedItemPosition());
        }
    }

    private View colorDot(int color, int size) {
        View dot = new View(this);
        dot.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(color);
        dot.setBackground(bg);
        return dot;
    }

    /** Tmavé, ohraničené pozadie pre textové polia v dialógu — default svetlý Android vzhľad trhá oči na tmavom pozadí. */
    private void styleInputBackground(View input) {
        input.setBackground(UiKit.roundedBgWithBorder(
                this, R.color.bg_input, R.color.input_border, dp(1), UiKit.dimenPx(this, R.dimen.radius_chip)));
        input.setPadding(dp(10), dp(8), dp(10), dp(8));
    }

    private EditText makeInput(String hint, int inputType) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setInputType(inputType);
        et.setTextColor(getColor(R.color.text_primary));
        et.setHintTextColor(getColor(R.color.text_muted));
        styleInputBackground(et);
        et.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return et;
    }

    private EditText makeInputInline(String hint, boolean decimal) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setInputType(decimal
                ? (InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL)
                : InputType.TYPE_CLASS_NUMBER);
        et.setTextColor(getColor(R.color.text_primary));
        et.setHintTextColor(getColor(R.color.text_muted));
        styleInputBackground(et);
        return et;
    }

    /** Váha ako float; toleruje desatinnú čiarku aj bodku (slovenská klávesnica píše ","). Null = prázdne/neplatné. */
    private Float parseWeightOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        try {
            return Float.parseFloat(t.replace(',', '.'));
        } catch (Exception e) {
            return null;
        }
    }

    /** Počet opakovaní; null = prázdne/neplatné. */
    private Integer parseRepsOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        try {
            return Integer.parseInt(t);
        } catch (Exception e) {
            return null;
        }
    }

    /** Voliteľná desatinná hodnota pre kardio: prázdne = 0, neplatný vstup = null (nikdy sa nezahodí potichu). */
    private Float parseOptionalFloat(String s) {
        if (s == null || s.trim().isEmpty()) return 0f;
        try {
            return Float.parseFloat(s.trim().replace(',', '.'));
        } catch (Exception e) {
            return null;
        }
    }

    /** Voliteľné celé číslo pre kardio (kalórie): prázdne = 0, neplatný vstup = null. */
    private Integer parseOptionalInt(String s) {
        if (s == null || s.trim().isEmpty()) return 0;
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Číslo do editovateľného poľa (váha, kardio) — celé bez desatinných miest, inak až na 2 desatinné
     * miesta bez koncových núl ("12,25", "22,5"). Predtým len 1 miesto, takže 12,25 kg sa pri úprave
     * a uložení potichu zmenilo na 12,3 kg.
     */
    private String formatW(float w) {
        if (w == Math.floor(w)) return String.format(Locale.getDefault(), "%.0f", w);
        String s = String.format(Locale.getDefault(), "%.2f", w);
        while (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        if (s.endsWith(",") || s.endsWith(".")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private String formatDateNice(String dateKeyStr) {
        try {
            SimpleDateFormat parser  = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            SimpleDateFormat display = new SimpleDateFormat("EEEE d. MMMM yyyy", new Locale("sk", "SK"));
            String s = display.format(parser.parse(dateKeyStr));
            return s.substring(0, 1).toUpperCase(Locale.getDefault()) + s.substring(1);
        } catch (Exception e) { return dateKeyStr; }
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}
