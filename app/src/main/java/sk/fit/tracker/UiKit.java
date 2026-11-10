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

    /**
     * Obsah od okraja po okraj (Android 15+ to vynucuje pri targetSdk 35): appka kreslí aj pod
     * stavový riadok a navigačnú lištu, preto obsah obrazovky dostane odstup presne o ich výšku
     * (a o výrez kamery). Spodný odstup zároveň rastie s klávesnicou, takže polia a tlačidlá dole
     * ostanú nad ňou. Rovnaké správanie na všetkých verziách Androidu (aj starších ako 15).
     * Volať v onCreate každej aktivity hneď po super.onCreate().
     */
    public static void edgeToEdge(android.app.Activity activity) {
        android.view.Window window = activity.getWindow();
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        // Bez polopriehľadného závoja za 3-tlačidlovou navigáciou — lišta má farbu pozadia appky
        if (android.os.Build.VERSION.SDK_INT >= 29) window.setNavigationBarContrastEnforced(false);

        android.view.View content = activity.findViewById(android.R.id.content);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars()
                            | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
            androidx.core.graphics.Insets ime = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            return androidx.core.view.WindowInsetsCompat.CONSUMED;
        });
    }

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
        card.setBackground(roundedBgWithBorder(
                ctx, R.color.bg_card, R.color.separator,
                dimenPx(ctx, R.dimen.stroke_hairline),
                dimenPx(ctx, R.dimen.radius_card)));
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
        tv.setTextColor(ctx.getColor(R.color.text_secondary));
        tv.setTextSize(10);
        tv.setTypeface(ctx.getResources().getFont(R.font.lora));
        tv.setLetterSpacing(0.14f);
        tv.setAllCaps(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(ctx, 10);
        lp.bottomMargin = dp(ctx, 8);
        tv.setLayoutParams(lp);
        return tv;
    }
}
