package sk.fit.tracker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.Locale;

/**
 * Lišta odpočtu pauzy (RestTimer) — v obrazovke dňa aj v dialógu cviku. Keď nebeží, ponúka
 * "▶ Pauza" a voľbu dĺžky; počas odpočtu ukazuje zostávajúci čas, priebeh a −15 / +15 / ✕.
 * Stav číta z RestTimer, takže viac líšt naraz (obrazovka + dialóg) ukazuje vždy to isté.
 */
public class RestTimerBar extends LinearLayout {

    private static final long TICK_MS = 250;
    private static final long DONE_BANNER_MS = 10_000;

    private final TextView tvTime;
    private final TextView tvLabel;
    private final TextView btnPreset;
    private final TextView btnStart;
    private final TextView btnMinus;
    private final TextView btnPlus;
    private final TextView btnStop;
    private final ProgressBar progress;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            postDelayed(this, TICK_MS);
        }
    };

    public RestTimerBar(Context ctx) {
        super(ctx);
        setOrientation(VERTICAL);
        int r = UiKit.dimenPx(ctx, R.dimen.radius_chip);
        setBackground(UiKit.roundedBgWithBorder(ctx, R.color.bg_card, R.color.separator, dp(1), r));
        setPadding(dp(12), dp(8), dp(8), dp(8));

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        addView(row);

        tvTime = new TextView(ctx);
        tvTime.setTextColor(getColor(R.color.accent_primary_soft));
        tvTime.setTextSize(22);
        tvTime.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        row.addView(tvTime);

        tvLabel = new TextView(ctx);
        tvLabel.setTextColor(getColor(R.color.text_secondary));
        tvLabel.setTextSize(13);
        tvLabel.setPadding(dp(8), 0, 0, 0);
        tvLabel.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        row.addView(tvLabel);

        btnPreset = pill("1:30 ▾", R.color.text_secondary);
        btnPreset.setOnClickListener(v -> showSettings());
        row.addView(btnPreset);

        btnStart = pill("▶ Pauza", R.color.text_on_accent);
        btnStart.setBackground(UiKit.roundedBg(ctx, R.color.accent_primary_soft, r));
        btnStart.setTypeface(null, Typeface.BOLD);
        btnStart.setOnClickListener(v -> {
            if (ctx instanceof Activity) RestTimer.maybeAskNotificationPermission((Activity) ctx);
            RestTimer.startDefault(ctx);
            refresh();
        });
        row.addView(btnStart);

        btnMinus = pill("−15", R.color.text_secondary);
        btnMinus.setOnClickListener(v -> { RestTimer.addSeconds(ctx, -15); refresh(); });
        row.addView(btnMinus);

        btnPlus = pill("+15", R.color.text_secondary);
        btnPlus.setOnClickListener(v -> { RestTimer.addSeconds(ctx, 15); refresh(); });
        row.addView(btnPlus);

        btnStop = pill("✕", R.color.text_secondary);
        btnStop.setContentDescription("Zrušiť pauzu");
        btnStop.setOnClickListener(v -> { RestTimer.stop(ctx); refresh(); });
        row.addView(btnStop);

        progress = new ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.accent_primary)));
        LayoutParams pLp = new LayoutParams(LayoutParams.MATCH_PARENT, dp(4));
        pLp.topMargin = dp(6);
        pLp.rightMargin = dp(4);
        progress.setLayoutParams(pLp);
        addView(progress);

        refresh();
    }

    private TextView pill(String text, int colorRes) {
        TextView t = new TextView(getContext());
        t.setText(text);
        t.setTextColor(getColor(colorRes));
        t.setTextSize(13);
        t.setGravity(Gravity.CENTER);
        t.setMinWidth(dp(40));
        t.setPadding(dp(10), dp(8), dp(10), dp(8));
        t.setBackground(UiKit.roundedBg(getContext(), R.color.bg_chip, UiKit.dimenPx(getContext(), R.dimen.radius_chip)));
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(6);
        t.setLayoutParams(lp);
        return t;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        removeCallbacks(tick);
        post(tick);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    private void refresh() {
        Context ctx = getContext();
        long remaining = RestTimer.remainingMillis(ctx);
        boolean running = remaining > 0;
        long now = System.currentTimeMillis();
        // Krátky banner po prirodzenom dobehnutí (zrušenie cez ✕ ho nespustí — ani v druhej lište)
        long finishedAt = RestTimer.lastFinishedAt(ctx);
        boolean showDone = !running && finishedAt > 0 && now - finishedAt < DONE_BANNER_MS;

        int def = RestTimer.getDefaultSeconds(ctx);
        btnPreset.setText(format(def * 1000L) + " ▾");

        if (running) {
            tvTime.setText(format(remaining + 999)); // zaokrúhli nahor — 0:00 sa ukáže až na konci
            tvTime.setTextColor(getColor(remaining < 10_000 ? R.color.accent_danger : R.color.accent_primary_soft));
            tvLabel.setText("pauza");
            long total = Math.max(1, RestTimer.getTotalMillis(ctx));
            progress.setProgress((int) (1000 * (total - remaining) / total));
        } else if (showDone) {
            tvTime.setText("✅");
            tvTime.setTextColor(getColor(R.color.accent_primary_soft));
            tvLabel.setText("Pauza skončila — ďalšia séria!");
        } else {
            tvTime.setText("⏱");
            tvTime.setTextColor(getColor(R.color.text_secondary));
            tvLabel.setText(RestTimer.isAutoStart(ctx) ? "Pauza sa spustí po pridaní série" : "Pauza medzi sériami");
        }
        progress.setVisibility(running ? VISIBLE : GONE);
        btnPreset.setVisibility(running ? GONE : VISIBLE);
        btnStart.setVisibility(running ? GONE : VISIBLE);
        btnMinus.setVisibility(running ? VISIBLE : GONE);
        btnPlus.setVisibility(running ? VISIBLE : GONE);
        btnStop.setVisibility(running ? VISIBLE : GONE);
    }

    /** Dĺžka pauzy a automatické spúšťanie. */
    private void showSettings() {
        Context ctx = getContext();
        String[] labels = new String[RestTimer.PRESETS_S.length];
        int checked = -1;
        int def = RestTimer.getDefaultSeconds(ctx);
        for (int i = 0; i < labels.length; i++) {
            int s = RestTimer.PRESETS_S[i];
            labels[i] = format(s * 1000L) + (s == 60 ? "  (ľahšie cviky)" : s == 90 ? "  (bežne)" : s == 180 ? "  (ťažké základné cviky)" : "");
            if (s == def) checked = i;
        }
        CheckBox cbAuto = new CheckBox(ctx);
        cbAuto.setText("Spustiť automaticky po „+ Pridať sériu“");
        cbAuto.setTextColor(getColor(R.color.text_secondary));
        cbAuto.setChecked(RestTimer.isAutoStart(ctx));
        cbAuto.setPadding(dp(4), 0, 0, 0);
        LinearLayout wrap = new LinearLayout(ctx);
        wrap.setPadding(dp(20), dp(4), dp(20), 0);
        wrap.addView(cbAuto);

        new AlertDialog.Builder(ctx)
                .setTitle("Dĺžka pauzy")
                .setSingleChoiceItems(labels, checked, (d, which) -> RestTimer.setDefaultSeconds(ctx, RestTimer.PRESETS_S[which]))
                .setView(wrap)
                .setPositiveButton("OK", (d, w) -> {
                    RestTimer.setAutoStart(ctx, cbAuto.isChecked());
                    refresh();
                })
                .setOnDismissListener(d -> refresh())
                .show();
    }

    private static String format(long ms) {
        long s = ms / 1000;
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    private int getColor(int res) {
        return getContext().getColor(res);
    }

    private int dp(int v) {
        return UiKit.dp(getContext(), v);
    }
}
