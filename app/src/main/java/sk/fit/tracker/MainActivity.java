package sk.fit.tracker;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {

    private LinearLayout calendarContainer;
    private LinearLayout legendContainer;
    private TextView tvMonthLabel;
    private TextView tvMonthMotivation;
    private Calendar displayedMonth; // day = 1

    private static final String[] WEEKDAYS = {"Po", "Ut", "St", "Št", "Pi", "So", "Ne"};
    private static final SimpleDateFormat MONTH_FMT =
            new SimpleDateFormat("LLLL yyyy", new Locale("sk", "SK"));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        calendarContainer = findViewById(R.id.calendar_container);
        legendContainer = findViewById(R.id.legend_container);
        tvMonthLabel = findViewById(R.id.tv_month_label);
        tvMonthMotivation = findViewById(R.id.tv_month_motivation);

        displayedMonth = Calendar.getInstance();
        displayedMonth.set(Calendar.DAY_OF_MONTH, 1);

        findViewById(R.id.btn_prev_month).setOnClickListener(v -> {
            displayedMonth.add(Calendar.MONTH, -1);
            renderCalendar();
        });
        findViewById(R.id.btn_next_month).setOnClickListener(v -> {
            displayedMonth.add(Calendar.MONTH, 1);
            renderCalendar();
        });
        findViewById(R.id.btn_stats).setOnClickListener(v ->
                startActivity(new Intent(this, StatsActivity.class)));
        findViewById(R.id.btn_progress).setOnClickListener(v ->
                startActivity(new Intent(this, ExerciseHistoryActivity.class)));
        findViewById(R.id.btn_catalog).setOnClickListener(v ->
                startActivity(new Intent(this, ExerciseCatalogActivity.class)));
        findViewById(R.id.btn_profile).setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));

        buildLegend();
        renderCalendar();
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderCalendar();
    }

    private void renderCalendar() {
        String label = MONTH_FMT.format(displayedMonth.getTime());
        tvMonthLabel.setText(capitalize(label));
        updateMonthMotivation();

        calendarContainer.removeAllViews();

        // Weekday header row
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        for (String wd : WEEKDAYS) {
            TextView tv = new TextView(this);
            tv.setText(wd);
            tv.setTextColor(getColor(R.color.text_muted));
            tv.setTextSize(10);
            tv.setGravity(Gravity.CENTER);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            header.addView(tv);
        }
        calendarContainer.addView(header);

        Calendar cal = (Calendar) displayedMonth.clone();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        // Monday = 0 ... Sunday = 6
        int dow = cal.get(Calendar.DAY_OF_WEEK); // SUNDAY=1...SATURDAY=7
        int offset = (dow + 5) % 7;

        String todayKey = ActivityStore.todayKey();

        LinearLayout week = newWeekRow();
        for (int i = 0; i < offset; i++) {
            week.addView(emptyCell());
        }

        for (int day = 1; day <= daysInMonth; day++) {
            Calendar dayCal = (Calendar) cal.clone();
            dayCal.set(Calendar.DAY_OF_MONTH, day);
            String dateKey = ActivityStore.dateKey(dayCal);
            Set<String> categories = ActivityStore.getCategoriesForDate(this, dateKey);

            boolean isToday = dateKey.equals(todayKey);
            week.addView(dayCell(day, dateKey, categories, isToday));

            int filled = offset + day;
            if (filled % 7 == 0 && day != daysInMonth) {
                calendarContainer.addView(week);
                week = newWeekRow();
            }
        }
        // Doplň posledný riadok prázdnymi bunkami
        int remaining = (offset + daysInMonth) % 7;
        if (remaining != 0) {
            for (int i = remaining; i < 7; i++) week.addView(emptyCell());
        }
        calendarContainer.addView(week);
    }

    private LinearLayout newWeekRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(2);
        row.setLayoutParams(lp);
        return row;
    }

    private View emptyCell() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, dp(52), 1f));
        return v;
    }

    private View dayCell(int day, String dateKey, Set<String> categories, boolean isToday) {
        FrameLayout cell = new FrameLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(52), 1f);
        lp.setMargins(dp(2), 0, dp(2), 0);
        cell.setLayoutParams(lp);

        // Zaoblené rohy — oreže farebné pruhy pozadia na tvar bunky
        final int radius = dp(10);
        cell.setClipToOutline(true);
        cell.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });

        // Farebné pozadie dňa namiesto bodiek — pruh pre každú aktívnu kategóriu
        List<Category> activeCats = new ArrayList<>();
        for (String catKey : categories) {
            Category c = Category.byKey(catKey);
            if (c != null) activeCats.add(c);
        }

        if (activeCats.isEmpty()) {
            cell.setBackgroundColor(isToday ? getColor(R.color.today_bg) : getColor(R.color.day_cell_bg));
        } else {
            LinearLayout stripes = new LinearLayout(this);
            stripes.setOrientation(LinearLayout.HORIZONTAL);
            stripes.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            for (Category c : activeCats) {
                View stripe = new View(this);
                stripe.setLayoutParams(new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
                stripe.setBackgroundColor(withAlpha(c.color, isToday ? 190 : 140));
                stripes.addView(stripe);
            }
            cell.addView(stripes);
        }

        // Streak — dni, ktoré sú súčasťou súvislej tréningovej série (2+ dni po sebe)
        boolean streak = ActivityStore.isWorkoutStreakDay(this, dateKey);

        TextView tvDay = new TextView(this);
        tvDay.setText(String.valueOf(day));
        tvDay.setTextColor(isToday ? getColor(R.color.accent_primary_soft) : getColor(R.color.text_primary));
        tvDay.setTextSize(13);
        tvDay.setShadowLayer(dp(2), 0, 0, 0xCC000000); // čitateľnosť čísla nad farebným pozadím
        FrameLayout.LayoutParams dayLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        dayLp.gravity = Gravity.CENTER;
        tvDay.setLayoutParams(dayLp);
        cell.addView(tvDay);

        if (streak) {
            TextView flame = new TextView(this);
            flame.setText("🔥");
            flame.setTextSize(9);
            FrameLayout.LayoutParams flameLp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
            flameLp.gravity = Gravity.TOP | Gravity.END;
            flameLp.setMargins(0, dp(2), dp(2), 0);
            flame.setLayoutParams(flameLp);
            cell.addView(flame);
        }

        // Ikonka precvičovanej partie priamo v bunke — rovnaké diagramy ako v legende
        if (!activeCats.isEmpty()) {
            LinearLayout iconRow = new LinearLayout(this);
            iconRow.setOrientation(LinearLayout.HORIZONTAL);
            iconRow.setGravity(Gravity.CENTER_VERTICAL);
            FrameLayout.LayoutParams iconRowLp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
            iconRowLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            iconRowLp.bottomMargin = dp(3);
            iconRow.setLayoutParams(iconRowLp);

            int shown = 0;
            for (Category c : activeCats) {
                if (shown >= 2) break; // obmedzené miesto v bunke
                int muscleRes = Category.muscleDrawableFor(c.key);
                if (muscleRes == 0) continue;
                android.widget.ImageView icon = new android.widget.ImageView(this);
                icon.setImageResource(muscleRes);
                LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(9), dp(15));
                if (shown > 0) iconLp.leftMargin = dp(3);
                icon.setLayoutParams(iconLp);
                iconRow.addView(icon);
                shown++;
            }
            if (shown > 0) cell.addView(iconRow);
        }

        if (isToday) {
            View border = new View(this);
            GradientDrawable strokeBg = new GradientDrawable();
            strokeBg.setCornerRadius(radius);
            strokeBg.setStroke(dp(2), getColor(R.color.today_stroke));
            border.setBackground(strokeBg);
            border.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            cell.addView(border);
        }

        cell.setOnClickListener(v -> {
            Intent intent = new Intent(this, DayDetailActivity.class);
            intent.putExtra("date", dateKey);
            startActivity(intent);
        });

        return cell;
    }

    /** Nahradí alfa kanál farby (0-255) — na priehľadné farebné pruhy pozadia dňa. */
    private int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    /** Spočíta tréningové dni v aktuálne zobrazenom mesiaci a nastaví motivačný text nad kalendárom. */
    private void updateMonthMotivation() {
        int year = displayedMonth.get(Calendar.YEAR);
        int month = displayedMonth.get(Calendar.MONTH); // 0-based

        int count = 0;
        for (Map.Entry<String, Set<String>> entry : ActivityStore.getAllEntries(this).entrySet()) {
            String dateKey = entry.getKey(); // "yyyy-MM-dd"
            int y = Integer.parseInt(dateKey.substring(0, 4));
            int m = Integer.parseInt(dateKey.substring(5, 7)) - 1; // 0-based
            if (y != year || m != month) continue;

            for (String catKey : entry.getValue()) {
                Category cat = Category.byKey(catKey);
                if (cat != null && Category.GROUP_WORKOUT.equals(cat.group)) {
                    count++;
                    break;
                }
            }
        }

        tvMonthMotivation.setText(count > 0
                ? "Tento mesiac si odmakal " + count + " " + pluralTraining(count) + "! 🔥"
                : "Tento mesiac zatiaľ žiadny tréning — poď na to! 💪");
    }

    /** Slovenská pluralizácia slova "tréning" (1 / 2-4 / 0,5+). */
    private String pluralTraining(int count) {
        if (count == 1) return "tréning";
        if (count >= 2 && count <= 4) return "tréningy";
        return "tréningov";
    }

    private void buildLegend() {
        legendContainer.removeAllViews();

        TextView title = new TextView(this);
        title.setText("Legenda");
        title.setTextColor(getColor(R.color.text_muted));
        title.setTextSize(10);
        title.setPadding(0, 0, 0, dp(10));
        legendContainer.addView(title);

        List<Category> cats = Category.all();
        for (int i = 0; i < cats.size(); i++) {
            Category c = cats.get(i);

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i < cats.size() - 1) itemLp.bottomMargin = dp(10);
            item.setLayoutParams(itemLp);

            int muscleRes = Category.muscleDrawableFor(c.key);
            if (muscleRes != 0) {
                android.widget.ImageView diagram = new android.widget.ImageView(this);
                diagram.setImageResource(muscleRes);
                LinearLayout.LayoutParams diagramLp = new LinearLayout.LayoutParams(dp(22), dp(36));
                diagramLp.rightMargin = dp(10);
                diagram.setLayoutParams(diagramLp);
                item.addView(diagram);
            } else if ("cardio".equals(c.key)) {
                TextView emoji = new TextView(this);
                emoji.setText("🏃");
                emoji.setTextSize(18);
                LinearLayout.LayoutParams emojiLp = new LinearLayout.LayoutParams(dp(22), dp(36));
                emojiLp.rightMargin = dp(10);
                emojiLp.gravity = Gravity.CENTER;
                emoji.setLayoutParams(emojiLp);
                emoji.setGravity(Gravity.CENTER);
                item.addView(emoji);
            } else {
                View dot = new View(this);
                int size = dp(8);
                LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(size, size);
                dotLp.setMargins(0, 0, dp(6), 0);
                dot.setLayoutParams(dotLp);
                GradientDrawable dotBg = new GradientDrawable();
                dotBg.setShape(GradientDrawable.OVAL);
                dotBg.setColor(c.color);
                dot.setBackground(dotBg);
                item.addView(dot);
            }

            TextView label = new TextView(this);
            label.setText(c.label);
            label.setTextColor(getColor(R.color.text_secondary));
            label.setTextSize(12);
            item.addView(label);

            legendContainer.addView(item);
        }
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase(Locale.getDefault()) + s.substring(1);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
