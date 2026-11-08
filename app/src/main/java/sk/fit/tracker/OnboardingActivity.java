package sk.fit.tracker;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Výber partií, na ktoré sa chce používateľ sústrediť — pri prvom spustení (z MainActivity)
 * a neskôr cez Profil ("Zmeniť"). Partie sa vyberajú chipmi alebo ťukom priamo na sval na
 * postave (MuscleMapView v režime setSelectable); oba spôsoby sa navzájom synchronizujú.
 * Výsledok ukladá FocusStore.
 */
public class OnboardingActivity extends Activity {

    private static final String EXTRA_EDIT = "edit";
    private static final String STATE_SELECTED = "selected";
    private static final String ALL_BODY = "Celé telo";

    private final Set<String> selected = new HashSet<>();
    private final List<TextView> chips = new ArrayList<>();
    private MuscleMapView map;
    private TextView summary;
    private TextView btnContinue;
    private boolean editMode;

    public static Intent intent(Context ctx, boolean edit) {
        return new Intent(ctx, OnboardingActivity.class).putExtra(EXTRA_EDIT, edit);
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(ThemeStore.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        editMode = getIntent().getBooleanExtra(EXTRA_EDIT, false);
        if (savedInstanceState != null && savedInstanceState.getStringArrayList(STATE_SELECTED) != null) {
            selected.addAll(savedInstanceState.getStringArrayList(STATE_SELECTED));
        } else {
            selected.addAll(FocusStore.getMuscles(this));
        }

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(getColor(R.color.bg_app));
        setContentView(outer);

        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(8));
        scroll.addView(root);
        outer.addView(scroll);

        TextView title = new TextView(this);
        title.setText(editMode ? "🎯 Môj fokus" : "🎯 Na čo sa chceš sústrediť?");
        title.setTextColor(getColor(R.color.text_primary));
        title.setTextSize(22);
        title.setTypeface(null, Typeface.BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Vyber partie chipmi alebo ťukni priamo na sval na postave. "
                + "Appka ti ich potom pripomenie v Štatistikách a v katalógu. Zmeniť ich môžeš kedykoľvek v Profile.");
        subtitle.setTextColor(getColor(R.color.text_muted));
        subtitle.setTextSize(13);
        subtitle.setPadding(0, dp(6), 0, dp(14));
        root.addView(subtitle);

        // Chipy: 7 partií + "Celé telo" v dvoch riadkoch po štyroch
        List<String> labels = new ArrayList<>();
        for (FocusStore.Area a : FocusStore.AREAS) labels.add(a.label);
        labels.add(ALL_BODY);
        for (int row = 0; row < 2; row++) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (row > 0) lineLp.topMargin = dp(8);
            line.setLayoutParams(lineLp);
            for (int col = 0; col < 4; col++) {
                int idx = row * 4 + col;
                TextView chip = new TextView(this);
                chip.setText(labels.get(idx));
                chip.setTag(labels.get(idx));
                chip.setTextSize(13);
                chip.setGravity(Gravity.CENTER);
                chip.setSingleLine(true);
                chip.setPadding(dp(4), dp(10), dp(4), dp(10));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                if (col > 0) lp.leftMargin = dp(8);
                chip.setLayoutParams(lp);
                chip.setOnClickListener(v -> toggleArea((String) v.getTag()));
                chips.add(chip);
                line.addView(chip);
            }
            root.addView(line);
        }

        map = new MuscleMapView(this);
        LinearLayout.LayoutParams mapLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        mapLp.topMargin = dp(18);
        map.setLayoutParams(mapLp);
        map.setSelectable(true);
        map.setOnMuscleToggleListener((muscle, isSelected) -> {
            if (isSelected) selected.add(muscle);
            else selected.remove(muscle);
            refresh(false);
        });
        root.addView(map);

        summary = new TextView(this);
        summary.setTextColor(getColor(R.color.text_secondary));
        summary.setTextSize(13);
        summary.setGravity(Gravity.CENTER_HORIZONTAL);
        summary.setPadding(0, dp(10), 0, 0);
        root.addView(summary);

        // Spodná lišta: Preskočiť / Zrušiť + Pokračovať / Uložiť
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(8), dp(16), dp(16));

        TextView btnSkip = new TextView(this);
        btnSkip.setText(editMode ? "Zrušiť" : "Preskočiť");
        btnSkip.setTextColor(getColor(R.color.text_muted));
        btnSkip.setTextSize(14);
        btnSkip.setGravity(Gravity.CENTER);
        btnSkip.setPadding(dp(16), dp(14), dp(16), dp(14));
        btnSkip.setOnClickListener(v -> {
            if (!editMode) FocusStore.markDone(this);
            finish();
        });
        bar.addView(btnSkip);

        btnContinue = new TextView(this);
        btnContinue.setText(editMode ? "Uložiť" : "Pokračovať");
        btnContinue.setTextSize(15);
        btnContinue.setTypeface(null, Typeface.BOLD);
        btnContinue.setGravity(Gravity.CENTER);
        btnContinue.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams contLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        contLp.leftMargin = dp(8);
        btnContinue.setLayoutParams(contLp);
        btnContinue.setOnClickListener(v -> {
            if (selected.isEmpty()) return;
            FocusStore.save(this, selected);
            setResult(RESULT_OK);
            finish();
        });
        bar.addView(btnContinue);
        outer.addView(bar);

        refresh(true);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putStringArrayList(STATE_SELECTED, new ArrayList<>(selected));
    }

    @Override
    public void onBackPressed() {
        // Systémové Späť pri prvom spustení = Preskočiť (inak by sa onboarding ukazoval stále dokola)
        if (!editMode) FocusStore.markDone(this);
        super.onBackPressed();
    }

    /** Chip partie: ak je celá vybratá, odznačí ju, inak doplní všetky jej svaly. */
    private void toggleArea(String label) {
        List<String> muscles = musclesOf(label);
        if (selected.containsAll(muscles)) selected.removeAll(muscles);
        else selected.addAll(muscles);
        refresh(true);
    }

    private List<String> musclesOf(String label) {
        if (ALL_BODY.equals(label)) return FocusStore.allMuscles();
        for (FocusStore.Area a : FocusStore.AREAS) {
            if (a.label.equals(label)) return a.muscles;
        }
        return new ArrayList<>();
    }

    /** Prekreslí chipy, súhrn a tlačidlo; updateMap = false keď zmena prišla z ťuku na postavu. */
    private void refresh(boolean updateMap) {
        if (updateMap) map.setMuscles(selected, null);
        int radius = UiKit.dimenPx(this, R.dimen.radius_chip);
        for (TextView chip : chips) {
            boolean on = selected.containsAll(musclesOf((String) chip.getTag()));
            chip.setBackground(on
                    ? UiKit.roundedBgWithBorder(this, R.color.accent_primary_soft, R.color.accent_primary, dp(1), radius)
                    : UiKit.roundedBgWithBorder(this, R.color.bg_chip, R.color.separator, dp(1), radius));
            chip.setTextColor(getColor(on ? R.color.text_on_accent : R.color.text_secondary));
            chip.setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);
        }
        summary.setText(selected.isEmpty() ? "Zatiaľ nič nevybraté" : "Vybraté: " + FocusStore.describe(selected));

        boolean enabled = !selected.isEmpty();
        btnContinue.setEnabled(enabled);
        btnContinue.setAlpha(enabled ? 1f : 0.45f);
        btnContinue.setBackground(UiKit.roundedBg(this, R.color.accent_primary_soft, radius));
        btnContinue.setTextColor(getColor(R.color.text_on_accent));
    }

    private int dp(int v) {
        return UiKit.dp(this, v);
    }
}
