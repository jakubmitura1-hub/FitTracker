package sk.fit.tracker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Prehľad, vyhľadávanie/filtrovanie, úprava a mazanie VŠETKÝCH cvikov v katalógu — zabudovaných
 * (natvrdo v ExerciseCatalog) aj vlastných. Úprava/mazanie nemá žiadny vplyv na už odtrénované
 * série v histórii (tie sú viazané len na text názvu v ExerciseStore, nie na katalógový záznam) —
 * pri oprave preklepu v názve sa ale história prepíše na nové meno (cez ExerciseMerger/
 * ExerciseStore.renameExerciseEverywhere), aby sa progres cviku nerozdelil na dve mená.
 *
 * Cviky sú zobrazené ako mriežka kariet s fotkou (ExerciseMedia / free-exercise-db), filtrom
 * podľa partie a obľúbenými (FavoritesStore). Ťuk na kartu otvorí ExerciseDetailActivity.
 */
public class ExerciseCatalogActivity extends Activity {

    private static final String ALL_CATEGORIES = "__all__";
    private static final String FAVORITES = "__favorites__";
    private static final String FOCUS = "__focus__";

    private final List<ExerciseCatalog.CatalogEntry> visibleEntries = new ArrayList<>();
    private final CatalogAdapter adapter = new CatalogAdapter();
    private LinearLayout chipRow;
    private TextView emptyView;
    private String searchQuery = "";
    private String selectedFilterKey = ALL_CATEGORIES;

    private static final int REQ_DETAIL = 1;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(ThemeStore.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(getColor(R.color.bg_app));
        setContentView(outer);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(16), dp(16), dp(16), dp(4));
        outer.addView(header);

        TextView title = new TextView(this);
        title.setText("📖 Katalóg cvikov");
        title.setTextColor(getColor(R.color.text_primary));
        title.setTextSize(19);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Ťukni na cvik pre detail, svaly, históriu a progres. ☆ ho pridá medzi obľúbené.");
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

        // Filter partií ako chipy vo vodorovne rolovateľnom riadku
        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams chipScrollLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        chipScrollLp.topMargin = dp(10);
        chipScroll.setLayoutParams(chipScrollLp);
        chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipScroll.addView(chipRow);
        header.addView(chipScroll);

        addFilterChip(ALL_CATEGORIES, "Všetky");
        addFilterChip(FAVORITES, "★ Obľúbené");
        if (!FocusStore.getMuscles(this).isEmpty() && FeatureStore.isEnabled(this, FeatureStore.FOCUS)) {
            addFilterChip(FOCUS, "🎯 Fokus");
        }
        for (Category c : Category.all()) {
            if (!Category.GROUP_WORKOUT.equals(c.group) || "cardio".equals(c.key)) continue;
            addFilterChip(c.key, c.label);
        }
        refreshChipStyles();

        emptyView = new TextView(this);
        emptyView.setTextColor(getColor(R.color.text_muted));
        emptyView.setTextSize(13);
        emptyView.setPadding(dp(16), dp(12), dp(16), dp(8));
        emptyView.setVisibility(View.GONE);
        outer.addView(emptyView);

        // GridView recykluje karty — pri ~100 cvikoch s fotkami by obyčajný ScrollView držal všetky naraz
        GridView grid = new GridView(this);
        grid.setNumColumns(2);
        grid.setHorizontalSpacing(dp(10));
        grid.setVerticalSpacing(dp(10));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setPadding(dp(16), dp(8), dp(16), dp(16));
        grid.setClipToPadding(false);
        grid.setSelector(android.R.color.transparent);
        grid.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> startActivityForResult(
                ExerciseDetailActivity.intent(this, visibleEntries.get(position).name, true), REQ_DETAIL));
        outer.addView(grid);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString();
                renderList();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        Button btnBack = new Button(this);
        btnBack.setText("← Späť");
        btnBack.setTextColor(getColor(R.color.text_primary));
        btnBack.setBackgroundColor(getColor(R.color.bg_button_neutral));
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        backLp.setMargins(dp(16), dp(0), dp(16), dp(16));
        btnBack.setLayoutParams(backLp);
        btnBack.setOnClickListener(v -> finish());
        outer.addView(btnBack);

        renderList();
    }

    /** Návrat z detailu cviku: obnoví hviezdičky a vykoná prípadnú úpravu/zmazanie vyžiadanú z detailu. */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_DETAIL) return;
        renderList();
        if (resultCode != RESULT_OK || data == null) return;
        String name = data.getStringExtra(ExerciseDetailActivity.EXTRA_NAME);
        String action = data.getStringExtra(ExerciseDetailActivity.EXTRA_ACTION);
        for (ExerciseCatalog.CatalogEntry e : ExerciseCatalog.getAllEntries(this)) {
            if (!e.name.equals(name)) continue;
            if (ExerciseDetailActivity.ACTION_EDIT.equals(action)) showEditDialog(e);
            else if (ExerciseDetailActivity.ACTION_DELETE.equals(action)) confirmDelete(e);
            return;
        }
    }

    private void addFilterChip(String key, String label) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTag(key);
        chip.setTextSize(12);
        chip.setPadding(dp(12), dp(7), dp(12), dp(7));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(6);
        chip.setLayoutParams(lp);
        chip.setOnClickListener(v -> {
            selectedFilterKey = key;
            refreshChipStyles();
            renderList();
        });
        chipRow.addView(chip);
    }

    private void refreshChipStyles() {
        int radius = UiKit.dimenPx(this, R.dimen.radius_chip);
        for (int i = 0; i < chipRow.getChildCount(); i++) {
            TextView chip = (TextView) chipRow.getChildAt(i);
            boolean selected = selectedFilterKey.equals(chip.getTag());
            chip.setBackground(selected
                    ? UiKit.roundedBgWithBorder(this, R.color.accent_primary_soft, R.color.accent_primary, dp(1), radius)
                    : UiKit.roundedBgWithBorder(this, R.color.bg_chip, R.color.separator, dp(1), radius));
            chip.setTextColor(getColor(selected ? R.color.text_on_accent : R.color.text_secondary));
            chip.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
    }

    private void renderList() {
        String normalizedQuery = ExerciseDefinition.normalize(searchQuery);
        Set<String> favorites = FavoritesStore.getAll(this);
        Set<String> focus = FocusStore.getMuscles(this);
        visibleEntries.clear();
        for (ExerciseCatalog.CatalogEntry e : ExerciseCatalog.getAllEntries(this)) {
            if (FAVORITES.equals(selectedFilterKey)) {
                if (!favorites.contains(e.name)) continue;
            } else if (FOCUS.equals(selectedFilterKey)) {
                if (!targetsFocus(e, focus)) continue;
            } else if (!ALL_CATEGORIES.equals(selectedFilterKey) && !e.categoryKey.equals(selectedFilterKey)) {
                continue;
            }
            if (!normalizedQuery.isEmpty() && !ExerciseDefinition.normalize(e.name).contains(normalizedQuery)) continue;
            visibleEntries.add(e);
        }
        adapter.notifyDataSetChanged();

        if (visibleEntries.isEmpty()) {
            emptyView.setText(FAVORITES.equals(selectedFilterKey) && normalizedQuery.isEmpty()
                    ? "Zatiaľ nemáš obľúbené cviky — označ ich hviezdičkou ☆ na karte."
                    : "Žiadny cvik nezodpovedá filtru.");
            emptyView.setVisibility(View.VISIBLE);
        } else {
            emptyView.setVisibility(View.GONE);
        }
    }

    /** Cvik patrí do fokusu, ak jeho hlavný sval (z ExerciseMedia, inak podľa partie) je medzi vybranými. */
    private boolean targetsFocus(ExerciseCatalog.CatalogEntry e, Set<String> focus) {
        ExerciseMedia.Info media = ExerciseMedia.get(this, e.name);
        List<String> primary = media != null && !media.primaryMuscles.isEmpty()
                ? media.primaryMuscles : MuscleMapView.musclesForCategory(e.categoryKey);
        for (String m : primary) {
            if (focus.contains(m)) return true;
        }
        return false;
    }

    /** Karta v mriežke: fotka cviku (alebo silueta partie), hviezdička, názov a partia. */
    private class CatalogAdapter extends BaseAdapter {

        private class Holder {
            ImageView image;
            TextView star;
            TextView name;
            TextView meta;
        }

        @Override public int getCount() { return visibleEntries.size(); }
        @Override public Object getItem(int position) { return visibleEntries.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View card = convertView;
            Holder h;
            if (card == null) {
                h = new Holder();
                card = buildCardView(h);
                card.setTag(h);
            } else {
                h = (Holder) card.getTag();
            }
            bindCard(h, visibleEntries.get(position));
            return card;
        }

        private View buildCardView(Holder h) {
            Context ctx = ExerciseCatalogActivity.this;
            LinearLayout card = new LinearLayout(ctx);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(UiKit.roundedBgWithBorder(ctx, R.color.bg_card, R.color.separator,
                    dp(1), UiKit.dimenPx(ctx, R.dimen.radius_card)));

            FrameLayout imageBox = new FrameLayout(ctx);
            imageBox.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(118)));
            card.addView(imageBox);

            h.image = new ImageView(ctx);
            h.image.setBackgroundColor(getColor(R.color.bg_card_alt));
            h.image.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            imageBox.addView(h.image);

            h.star = new TextView(ctx);
            h.star.setTextSize(17);
            h.star.setGravity(Gravity.CENTER);
            h.star.setBackground(UiKit.roundedBg(ctx, R.color.bg_card, dp(15)));
            FrameLayout.LayoutParams starLp = new FrameLayout.LayoutParams(dp(30), dp(30), Gravity.TOP | Gravity.END);
            starLp.setMargins(0, dp(6), dp(6), 0);
            h.star.setLayoutParams(starLp);
            imageBox.addView(h.star);

            h.name = new TextView(ctx);
            h.name.setTextColor(getColor(R.color.text_primary));
            h.name.setTextSize(13);
            h.name.setTypeface(null, android.graphics.Typeface.BOLD);
            h.name.setMaxLines(2);
            h.name.setMinLines(2);
            h.name.setEllipsize(TextUtils.TruncateAt.END);
            h.name.setPadding(dp(10), dp(8), dp(10), 0);
            card.addView(h.name);

            h.meta = new TextView(ctx);
            h.meta.setTextColor(getColor(R.color.text_muted));
            h.meta.setTextSize(11);
            h.meta.setSingleLine(true);
            h.meta.setEllipsize(TextUtils.TruncateAt.END);
            h.meta.setPadding(dp(10), dp(2), dp(10), dp(10));
            card.addView(h.meta);
            return card;
        }

        private void bindCard(Holder h, ExerciseCatalog.CatalogEntry entry) {
            ExerciseMedia.bindPhoto(ExerciseCatalogActivity.this, h.image, entry.name, entry.categoryKey, 0);

            boolean fav = FavoritesStore.isFavorite(ExerciseCatalogActivity.this, entry.name);
            h.star.setText(fav ? "★" : "☆");
            h.star.setTextColor(getColor(fav ? R.color.accent_primary : R.color.text_muted));
            h.star.setOnClickListener(v -> {
                FavoritesStore.toggle(ExerciseCatalogActivity.this, entry.name);
                if (FAVORITES.equals(selectedFilterKey)) renderList();
                else notifyDataSetChanged();
            });

            h.name.setText(entry.name);
            Category cat = Category.byKey(entry.categoryKey);
            String meta = cat != null ? cat.label : entry.categoryKey;
            if (!entry.isDefault) meta += " · vlastný";
            if (entry.notes != null && !entry.notes.isEmpty()) meta += " · 📝";
            h.meta.setText(meta);
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
