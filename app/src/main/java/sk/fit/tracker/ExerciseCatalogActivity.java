package sk.fit.tracker;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Prehľad, vyhľadávanie/filtrovanie, úprava a mazanie VŠETKÝCH cvikov v katalógu — zabudovaných
 * (natvrdo v ExerciseCatalog) aj vlastných. Úprava/mazanie nemá žiadny vplyv na už odtrénované
 * série v histórii (tie sú viazané len na text názvu v ExerciseStore, nie na katalógový záznam) —
 * pri oprave preklepu v názve sa ale história prepíše na nové meno (cez ExerciseMerger/
 * ExerciseStore.renameExerciseEverywhere), aby sa progres cviku nerozdelil na dve mená.
 */
public class ExerciseCatalogActivity extends Activity {

    private static final String ALL_CATEGORIES = "__all__";

    private LinearLayout listContainer;
    private String searchQuery = "";
    private String selectedCategoryKey = ALL_CATEGORIES;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(getColor(R.color.bg_app));
        setContentView(outer);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(16), dp(16), dp(16), dp(8));
        outer.addView(header);

        TextView title = new TextView(this);
        title.setText("📖 Katalóg cvikov");
        title.setTextColor(getColor(R.color.text_white));
        title.setTextSize(19);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Vyhľadaj, filtruj podľa partie, uprav alebo zmaž cvik z ponuky. História odtrénovaných sérií sa tým nestratí.");
        subtitle.setTextColor(getColor(R.color.text_muted));
        subtitle.setTextSize(12);
        subtitle.setPadding(0, dp(2), 0, dp(12));
        header.addView(subtitle);

        EditText etSearch = new EditText(this);
        etSearch.setHint("Hľadať cvik...");
        etSearch.setInputType(InputType.TYPE_CLASS_TEXT);
        etSearch.setTextColor(getColor(R.color.text_primary));
        etSearch.setHintTextColor(getColor(R.color.text_muted));
        etSearch.setBackground(UiKit.roundedBgWithBorder(
                this, R.color.bg_input, R.color.input_border, dp(1), UiKit.dimenPx(this, R.dimen.radius_chip)));
        etSearch.setPadding(dp(10), dp(8), dp(10), dp(8));
        etSearch.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        header.addView(etSearch);

        List<String> categoryFilterKeys = new ArrayList<>();
        List<String> categoryFilterLabels = new ArrayList<>();
        categoryFilterKeys.add(ALL_CATEGORIES);
        categoryFilterLabels.add("Všetky partie");
        for (Category c : Category.all()) {
            if (!Category.GROUP_WORKOUT.equals(c.group) || "cardio".equals(c.key)) continue;
            categoryFilterKeys.add(c.key);
            categoryFilterLabels.add(c.label);
        }

        Spinner categorySpinner = new Spinner(this);
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, categoryFilterLabels);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(categoryAdapter);
        categorySpinner.setBackground(UiKit.roundedBgWithBorder(
                this, R.color.bg_input, R.color.input_border, dp(1), UiKit.dimenPx(this, R.dimen.radius_chip)));
        categorySpinner.setPadding(dp(8), dp(4), dp(8), dp(4));
        LinearLayout.LayoutParams spinnerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        spinnerLp.topMargin = dp(8);
        categorySpinner.setLayoutParams(spinnerLp);
        header.addView(categorySpinner);

        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        outer.addView(scroll);

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(dp(16), dp(4), dp(16), dp(16));
        scroll.addView(listContainer);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString();
                renderList();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        categorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedCategoryKey = categoryFilterKeys.get(position);
                renderList();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        Button btnBack = new Button(this);
        btnBack.setText("← Späť");
        btnBack.setTextColor(getColor(R.color.text_white));
        btnBack.setBackgroundColor(getColor(R.color.bg_button_neutral));
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        backLp.setMargins(dp(16), dp(0), dp(16), dp(16));
        btnBack.setLayoutParams(backLp);
        btnBack.setOnClickListener(v -> finish());
        outer.addView(btnBack);

        renderList();
    }

    private void renderList() {
        listContainer.removeAllViews();

        String normalizedQuery = ExerciseDefinition.normalize(searchQuery);
        List<ExerciseCatalog.CatalogEntry> all = ExerciseCatalog.getAllEntries(this);
        List<ExerciseCatalog.CatalogEntry> filtered = new ArrayList<>();
        for (ExerciseCatalog.CatalogEntry e : all) {
            if (!ALL_CATEGORIES.equals(selectedCategoryKey) && !e.categoryKey.equals(selectedCategoryKey)) continue;
            if (!normalizedQuery.isEmpty() && !ExerciseDefinition.normalize(e.name).contains(normalizedQuery)) continue;
            filtered.add(e);
        }

        if (filtered.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Žiadny cvik nezodpovedá filtru.");
            empty.setTextColor(getColor(R.color.text_muted));
            empty.setTextSize(13);
            empty.setPadding(0, dp(8), 0, dp(8));
            listContainer.addView(empty);
            return;
        }

        for (ExerciseCatalog.CatalogEntry entry : filtered) {
            listContainer.addView(buildEntryRow(entry));
        }
    }

    private LinearLayout buildEntryRow(ExerciseCatalog.CatalogEntry entry) {
        LinearLayout card = UiKit.card(this);

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        int muscleRes = Category.muscleDrawableFor(entry.categoryKey);
        if (muscleRes != 0) {
            ImageView icon = new ImageView(this);
            icon.setImageResource(muscleRes);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(16), dp(26));
            iconLp.rightMargin = dp(10);
            icon.setLayoutParams(iconLp);
            topRow.addView(icon);
        }

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvName = new TextView(this);
        tvName.setText(entry.name);
        tvName.setTextColor(getColor(R.color.text_primary));
        tvName.setTextSize(14);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        textCol.addView(tvName);

        Category cat = Category.byKey(entry.categoryKey);
        TextView tvMeta = new TextView(this);
        tvMeta.setText((cat != null ? cat.label : entry.categoryKey) + " · " + metricTypeLabel(entry.metricType)
                + (entry.isDefault ? " · predvolený" : " · vlastný"));
        tvMeta.setTextColor(getColor(R.color.text_muted));
        tvMeta.setTextSize(11);
        textCol.addView(tvMeta);

        if (entry.notes != null && !entry.notes.isEmpty()) {
            TextView tvNotes = new TextView(this);
            tvNotes.setText("📝 " + entry.notes);
            tvNotes.setTextColor(getColor(R.color.text_secondary));
            tvNotes.setTextSize(11);
            tvNotes.setPadding(0, dp(3), 0, 0);
            textCol.addView(tvNotes);
        }

        topRow.addView(textCol);
        card.addView(topRow);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, dp(8), 0, 0);

        TextView btnEdit = new TextView(this);
        btnEdit.setText("✎ Upraviť");
        btnEdit.setTextColor(getColor(R.color.text_on_accent));
        btnEdit.setTextSize(12);
        btnEdit.setTypeface(null, android.graphics.Typeface.BOLD);
        btnEdit.setGravity(Gravity.CENTER);
        btnEdit.setPadding(dp(12), dp(8), dp(12), dp(8));
        btnEdit.setBackground(UiKit.roundedBg(this, R.color.accent_primary, UiKit.dimenPx(this, R.dimen.radius_button)));
        btnEdit.setOnClickListener(v -> showEditDialog(entry));
        btnRow.addView(btnEdit);

        TextView btnDelete = new TextView(this);
        btnDelete.setText("✕ Zmazať");
        btnDelete.setTextColor(getColor(R.color.text_white));
        btnDelete.setTextSize(12);
        btnDelete.setTypeface(null, android.graphics.Typeface.BOLD);
        btnDelete.setGravity(Gravity.CENTER);
        btnDelete.setPadding(dp(12), dp(8), dp(12), dp(8));
        btnDelete.setBackground(UiKit.roundedBg(this, R.color.accent_danger_dark, UiKit.dimenPx(this, R.dimen.radius_button)));
        LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        delLp.leftMargin = dp(10);
        btnDelete.setLayoutParams(delLp);
        btnDelete.setOnClickListener(v -> confirmDelete(entry));
        btnRow.addView(btnDelete);

        card.addView(btnRow);
        return card;
    }

    private String metricTypeLabel(MetricType type) {
        switch (type) {
            case BODYWEIGHT_AND_REPS: return "vlastná váha";
            case DURATION: return "časový";
            default: return "váha × opak.";
        }
    }

    private void showEditDialog(ExerciseCatalog.CatalogEntry entry) {
        ScrollView dialogScroll = new ScrollView(this);
        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(dp(20), dp(16), dp(20), dp(16));
        dialogScroll.addView(dialogLayout);

        TextView nameLabel = new TextView(this);
        nameLabel.setText("Názov cvičenia");
        nameLabel.setTextColor(getColor(R.color.text_secondary));
        nameLabel.setTextSize(12);
        nameLabel.setPadding(0, 0, 0, dp(4));
        dialogLayout.addView(nameLabel);

        EditText etName = makeInput(entry.name);
        etName.setText(entry.name);
        dialogLayout.addView(etName);

        TextView catLabel = new TextView(this);
        catLabel.setText("Svalová partia");
        catLabel.setTextColor(getColor(R.color.text_secondary));
        catLabel.setTextSize(12);
        catLabel.setPadding(0, dp(12), 0, dp(4));
        dialogLayout.addView(catLabel);

        List<String> categoryKeys = new ArrayList<>();
        List<String> categoryLabels = new ArrayList<>();
        int selectedIndex = 0;
        for (Category c : Category.all()) {
            if (!Category.GROUP_WORKOUT.equals(c.group) || "cardio".equals(c.key)) continue;
            if (c.key.equals(entry.categoryKey)) selectedIndex = categoryKeys.size();
            categoryKeys.add(c.key);
            categoryLabels.add(c.label);
        }

        Spinner categorySpinner = new Spinner(this);
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, categoryLabels);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(categoryAdapter);
        categorySpinner.setSelection(selectedIndex);
        categorySpinner.setBackground(UiKit.roundedBgWithBorder(
                this, R.color.bg_input, R.color.input_border, dp(1), UiKit.dimenPx(this, R.dimen.radius_chip)));
        categorySpinner.setPadding(dp(8), dp(4), dp(8), dp(4));
        dialogLayout.addView(categorySpinner);

        TextView notesLabel = new TextView(this);
        notesLabel.setText("Poznámka (voliteľné)");
        notesLabel.setTextColor(getColor(R.color.text_secondary));
        notesLabel.setTextSize(12);
        notesLabel.setPadding(0, dp(12), 0, dp(4));
        dialogLayout.addView(notesLabel);

        EditText etNotes = makeInput("napr. robiť s podsadom, bolí zápästie...");
        etNotes.setText(entry.notes);
        etNotes.setMinLines(2);
        etNotes.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        dialogLayout.addView(etNotes);

        if (entry.isDefault) {
            TextView hint = new TextView(this);
            hint.setText("Toto je predvolený cvik z katalógu — úpravou vznikne tvoja vlastná verzia, pôvodný sa prestane ponúkať.");
            hint.setTextColor(getColor(R.color.text_muted));
            hint.setTextSize(11);
            hint.setPadding(0, dp(10), 0, 0);
            dialogLayout.addView(hint);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Upraviť cvik")
                .setView(dialogScroll)
                .setPositiveButton("Uložiť", null)
                .setNegativeButton("Zrušiť", null)
                .setCancelable(false)
                .create();

        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setOnClickListener(v -> {
                String newName = etName.getText().toString().trim();
                if (newName.isEmpty()) {
                    Toast.makeText(this, "Názov cvičenia nesmie byť prázdny.", Toast.LENGTH_SHORT).show();
                    return;
                }
                String newCategoryKey = categoryKeys.get(categorySpinner.getSelectedItemPosition());
                String newNotes = etNotes.getText().toString().trim();

                boolean ok = ExerciseCatalog.editEntry(this, entry, newName, newCategoryKey, newNotes);
                if (!ok) {
                    Toast.makeText(this, "Cvik s týmto názvom už v cieľovej partii existuje.", Toast.LENGTH_LONG).show();
                    return;
                }
                Toast.makeText(this, "Uložené.", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                renderList();
            });
        });

        dialog.show();
    }

    private void confirmDelete(ExerciseCatalog.CatalogEntry entry) {
        new AlertDialog.Builder(this)
                .setTitle("Zmazať cvik z katalógu?")
                .setMessage("„" + entry.name + "“ sa prestane ponúkať pri pridávaní nového cvičenia. "
                        + "Doterajšie odtrénované série v histórii/Progrese sa NEZMAŽÚ a ostanú viditeľné pod týmto názvom.")
                .setPositiveButton("Zmazať", (dialog, which) -> {
                    ExerciseCatalog.deleteEntry(this, entry);
                    Toast.makeText(this, "Cvik odstránený z katalógu.", Toast.LENGTH_SHORT).show();
                    renderList();
                })
                .setNegativeButton("Zrušiť", null)
                .show();
    }

    private EditText makeInput(String hint) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
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
