package sk.fit.tracker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Vlastný čiarový graf (žiadna externá knižnica) na vývoj hodnoty v čase, napr. maximálna váha
 * cvičenia naprieč tréningami. Vykresľuje zjemnenú krivku so zlatým prechodom pod čiarou,
 * zvýrazneným aktuálnym (posledným) bodom a odznačeným historickým maximom.
 */
public class LineChartView extends View {

    private List<Float> values = new ArrayList<>();
    private List<String> labels = new ArrayList<>();

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointHaloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dimPointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valueLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int accentColor;
    private int highlightColor;

    public LineChartView(Context ctx) {
        super(ctx);
        init(ctx);
    }

    public LineChartView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        init(ctx);
    }

    private void init(Context ctx) {
        accentColor = ctx.getColor(R.color.accent_primary);
        highlightColor = ctx.getColor(R.color.accent_highlight);
        int muted = ctx.getColor(R.color.text_muted);

        linePaint.setColor(accentColor);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(UiKit.dp(ctx, 2));
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setStrokeCap(Paint.Cap.ROUND);

        fillPaint.setStyle(Paint.Style.FILL);

        pointPaint.setStyle(Paint.Style.FILL);

        pointHaloPaint.setStyle(Paint.Style.FILL);
        pointHaloPaint.setColor(accentColor);
        pointHaloPaint.setAlpha(45);

        dimPointPaint.setStyle(Paint.Style.FILL);
        dimPointPaint.setColor(withAlpha(accentColor, 130));

        gridPaint.setColor(muted);
        gridPaint.setAlpha(50);
        gridPaint.setStrokeWidth(1);

        labelPaint.setColor(muted);
        labelPaint.setTextSize(UiKit.dp(ctx, 10));

        valueLabelPaint.setTextSize(UiKit.dp(ctx, 11));
        valueLabelPaint.setFakeBoldText(true);
        valueLabelPaint.setTextAlign(Paint.Align.CENTER);
    }

    /** Hodnoty a im zodpovedajúce popisky (napr. dátumy) — musia mať rovnakú dĺžku. */
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
        if (values.size() < 2 || w == 0 || h == 0) return;

        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        int peakIndex = 0;
        for (int i = 0; i < values.size(); i++) {
            float v = values.get(i);
            if (v < min) min = v;
            if (v > max) {
                max = v;
                peakIndex = i;
            }
        }
        if (max == min) {
            max += 1;
            min -= 1;
        }

        float hPad = UiKit.dp(getContext(), 8);
        float labelSpace = UiKit.dp(getContext(), 16);
        float topPad = UiKit.dp(getContext(), 20);
        float chartW = w - hPad * 2;
        float chartH = h - labelSpace - topPad;
        float baseline = topPad + chartH;
        float stepX = chartW / (values.size() - 1);

        float[] xs = new float[values.size()];
        float[] ys = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            xs[i] = hPad + i * stepX;
            float normalized = (values.get(i) - min) / (max - min);
            ys[i] = topPad + chartH - normalized * chartH;
        }

        // Zjemnená krivka (kvadratické segmenty cez medzibody susedných dát) — presné hodnoty
        // ostávajú vyznačené bodkami navrch, krivka len vizuálne zjemňuje zalomenia.
        Path linePath = new Path();
        linePath.moveTo(xs[0], ys[0]);
        for (int i = 0; i < xs.length - 1; i++) {
            float midX = (xs[i] + xs[i + 1]) / 2f;
            float midY = (ys[i] + ys[i + 1]) / 2f;
            linePath.quadTo(xs[i], ys[i], midX, midY);
        }
        linePath.lineTo(xs[xs.length - 1], ys[ys.length - 1]);

        Path fillPath = new Path(linePath);
        fillPath.lineTo(xs[xs.length - 1], baseline);
        fillPath.lineTo(xs[0], baseline);
        fillPath.close();

        fillPaint.setShader(new LinearGradient(
                0, topPad, 0, baseline,
                withAlpha(accentColor, 75), withAlpha(accentColor, 0),
                Shader.TileMode.CLAMP));
        canvas.drawPath(fillPath, fillPaint);

        canvas.drawLine(hPad, baseline, hPad + chartW, baseline, gridPaint);
        canvas.drawPath(linePath, linePaint);

        int lastIndex = xs.length - 1;
        for (int i = 0; i < xs.length; i++) {
            boolean isLast = (i == lastIndex);
            boolean isPeak = (i == peakIndex && !isLast);
            if (isLast) {
                canvas.drawCircle(xs[i], ys[i], UiKit.dp(getContext(), 7), pointHaloPaint);
                pointPaint.setColor(accentColor);
                canvas.drawCircle(xs[i], ys[i], UiKit.dp(getContext(), 4), pointPaint);
                valueLabelPaint.setColor(accentColor);
                canvas.drawText(formatValue(values.get(i)), xs[i], ys[i] - UiKit.dp(getContext(), 10), valueLabelPaint);
            } else if (isPeak) {
                pointPaint.setColor(highlightColor);
                canvas.drawCircle(xs[i], ys[i], UiKit.dp(getContext(), 3), pointPaint);
                valueLabelPaint.setColor(highlightColor);
                canvas.drawText(formatValue(values.get(i)), xs[i], ys[i] - UiKit.dp(getContext(), 8), valueLabelPaint);
            } else {
                canvas.drawCircle(xs[i], ys[i], UiKit.dp(getContext(), 2), dimPointPaint);
            }
        }

        if (!labels.isEmpty()) {
            String first = labels.get(0);
            String last = labels.get(labels.size() - 1);
            labelPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(first, hPad, h - 2, labelPaint);
            labelPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(last, hPad + chartW, h - 2, labelPaint);
        }
    }

    private int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private String formatValue(float v) {
        if (v == Math.round(v)) return String.valueOf(Math.round(v));
        return String.format(Locale.getDefault(), "%.1f", v);
    }
}
