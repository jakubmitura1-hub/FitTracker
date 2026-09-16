package sk.fit.tracker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Jednoduchý vlastný stĺpcový graf (žiadna externá knižnica), napr. na tréningovú
 * frekvenciu po týždňoch.
 */
public class BarChartView extends View {

    private List<Float> values = new ArrayList<>();
    private List<String> labels = new ArrayList<>();

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public BarChartView(Context ctx) {
        super(ctx);
        init(ctx);
    }

    public BarChartView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        init(ctx);
    }

    private void init(Context ctx) {
        barPaint.setColor(ctx.getColor(R.color.accent_primary));
        barPaint.setStyle(Paint.Style.FILL);

        labelPaint.setColor(ctx.getColor(R.color.text_muted));
        labelPaint.setTextSize(UiKit.dp(ctx, 9));
        labelPaint.setTextAlign(Paint.Align.CENTER);
    }

    /** Hodnoty stĺpcov a im zodpovedajúce popisky — musia mať rovnakú dĺžku. */
    public void setData(List<Float> values, List<String> labels) {
        this.values = values != null ? values : new ArrayList<>();
        this.labels = labels != null ? labels : new ArrayList<>();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (values.isEmpty() || w == 0 || h == 0) return;

        float max = 0;
        for (float v : values) if (v > max) max = v;
        if (max <= 0) max = 1;

        float labelSpace = UiKit.dp(getContext(), 14);
        float chartH = h - labelSpace;
        float slot = (float) w / values.size();
        float barWidth = slot * 0.5f;
        float corner = UiKit.dp(getContext(), 3);

        for (int i = 0; i < values.size(); i++) {
            float value = values.get(i);
            float barH = chartH * (value / max);
            float left = i * slot + (slot - barWidth) / 2f;
            float right = left + barWidth;
            float top = chartH - barH;
            float bottom = chartH;
            // minimálna viditeľná výška, aj keď je hodnota 0, nech je vidno, že stĺpec existuje
            if (bottom - top < 2) top = bottom - 2;
            canvas.drawRoundRect(left, top, right, bottom, corner, corner, barPaint);

            if (i < labels.size()) {
                canvas.drawText(labels.get(i), left + barWidth / 2f, h - 2, labelPaint);
            }
        }
    }
}
