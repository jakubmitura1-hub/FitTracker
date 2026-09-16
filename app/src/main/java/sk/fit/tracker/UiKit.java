package sk.fit.tracker;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Zdieľané UI pomôcky (karty, farby, rozmery) tak, aby sa vzhľad appky dal
 * meniť na jednom mieste — res/values/colors.xml a res/values/dimens.xml.
 */
public class UiKit {

    private UiKit() {}

    public static int dp(Context ctx, int v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density);
    }

    public static int dimenPx(Context ctx, int dimenRes) {
        return ctx.getResources().getDimensionPixelSize(dimenRes);
    }

    /** Zaoblená karta s jednotným pozadím/paddingom, používaná naprieč obrazovkami. */
    public static LinearLayout card(Context ctx) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundedBg(ctx, R.color.bg_card, dimenPx(ctx, R.dimen.radius_card)));
        int padH = dp(ctx, 14);
        int padV = dp(ctx, 12);
        card.setPadding(padH, padV, padH, padV);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(ctx, 10);
        card.setLayoutParams(lp);
        return card;
    }

    public static GradientDrawable roundedBg(Context ctx, int colorRes, int radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(ctx.getColor(colorRes));
        d.setCornerRadius(radiusPx);
        return d;
    }

    public static GradientDrawable roundedBgWithBorder(Context ctx, int colorRes, int borderColorRes, int borderWidthPx, int radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(ctx.getColor(colorRes));
        d.setStroke(borderWidthPx, ctx.getColor(borderColorRes));
        d.setCornerRadius(radiusPx);
        return d;
    }

    public static TextView sectionHeader(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(ctx.getColor(R.color.text_primary));
        tv.setTextSize(13);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(ctx, 10);
        lp.bottomMargin = dp(ctx, 8);
        tv.setLayoutParams(lp);
        return tv;
    }
}
