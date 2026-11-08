package sk.fit.tracker;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Anatomická mapa tela — realistická postava spredu a zozadu vedľa seba (render 3D modelu
 * Z-Anatomy, CC BY-SA 4.0, pozri assets/muscle_map/LICENSE.txt). Svaly sa prefarbujú:
 *  - primárne svaly (hlavné precvičované) -> muscle_highlight (červená),
 *  - sekundárne svaly (pomocné) -> muscle_secondary (modrá),
 *  - ostatné zostanú v neutrálnej farbe renderu.
 * Farba sa násobí jasom renderu, takže tieňovanie, vlákna aj šľachy zostanú viditeľné.
 *
 * Assets (assets/muscle_map/):
 *  - body_front.webp / body_back.webp — podklad (RGBA),
 *  - muscles_*.bin — mapa svalov rovnakého rozmeru: "FTML", int w, int h, potom w*h bajtov,
 *    0 = žiadny sval, 1..17 = index do GROUPS + 1,
 *  - muscles_back_deep.bin — hlboká vrstva chrbta (romboidy, vzpriamovače), ktoré v modeli
 *    prekrýva trapéz a široký chrbtový; presvitá cez ne, ak má vyššiu prioritu.
 *
 * Kľúče svalov zámerne kopírujú názvy z free-exercise-db (primaryMuscles/secondaryMuscles),
 * aby sa dáta cvikov z tej databázy dali na mapu posielať priamo, bez prekladovej tabuľky.
 *
 * V režime setSelectable(true) sa sval ťukom zapne/vypne (onboarding — výber partií).
 */
public class MuscleMapView extends View {

    private static final String TAG = "MuscleMapView";

    public static final String ABDOMINALS = "abdominals";
    public static final String ABDUCTORS = "abductors";
    public static final String ADDUCTORS = "adductors";
    public static final String BICEPS = "biceps";
    public static final String CALVES = "calves";
    public static final String CHEST = "chest";
    public static final String FOREARMS = "forearms";
    public static final String GLUTES = "glutes";
    public static final String HAMSTRINGS = "hamstrings";
    public static final String LATS = "lats";
    public static final String LOWER_BACK = "lower back";
    public static final String MIDDLE_BACK = "middle back";
    public static final String NECK = "neck";
    public static final String QUADRICEPS = "quadriceps";
    public static final String SHOULDERS = "shoulders";
    public static final String TRAPS = "traps";
    public static final String TRICEPS = "triceps";

    /** Poradie musí sedieť s GROUPS v renderovacom skripte (index v .bin = poradie + 1). */
    private static final String[] GROUPS = {ABDOMINALS, ABDUCTORS, ADDUCTORS, BICEPS, CALVES, CHEST,
            FOREARMS, GLUTES, HAMSTRINGS, LATS, LOWER_BACK, MIDDLE_BACK, NECK, QUADRICEPS,
            SHOULDERS, TRAPS, TRICEPS};
    private static final int IDX_TRAPS = indexOf(TRAPS) + 1;
    private static final int IDX_LATS = indexOf(LATS) + 1;

    /** Medzera medzi postavami ako podiel šírky jednej postavy. */
    private static final float GAP_RATIO = 0.06f;
    /** Polomer (v pixeloch renderu), v ktorom sa pri ťuknutí vedľa svalu hľadá najbližší sval. */
    private static final int TOUCH_SLOP_PX = 10;

    public interface OnMuscleToggleListener {
        void onMuscleToggled(String muscle, boolean selected);
    }

    // ---------------------------------------------------------------- zdieľané dáta z assets

    /** Jedna postava: podklad + mapa svalov (+ voliteľná hlboká vrstva). */
    private static final class Figure {
        final int w, h;
        final int[] base;
        final byte[] top;
        final byte[] deep;

        Figure(int w, int h, int[] base, byte[] top, byte[] deep) {
            this.w = w;
            this.h = h;
            this.base = base;
            this.top = top;
            this.deep = deep;
        }
    }

    private static Figure sFront;
    private static Figure sBack;

    private static synchronized boolean ensureLoaded(Context ctx) {
        if (sFront != null && sBack != null) return true;
        AssetManager am = ctx.getApplicationContext().getAssets();
        try {
            sFront = loadFigure(am, "front", false);
            sBack = loadFigure(am, "back", true);
            return true;
        } catch (IOException | RuntimeException e) {
            Log.e(TAG, "Nepodarilo sa načítať anatomickú mapu", e);
            sFront = null;
            sBack = null;
            return false;
        }
    }

    private static Figure loadFigure(AssetManager am, String view, boolean withDeep) throws IOException {
        Bitmap bmp;
        try (InputStream in = am.open("muscle_map/body_" + view + ".webp")) {
            bmp = BitmapFactory.decodeStream(in);
        }
        if (bmp == null) throw new IOException("body_" + view + ".webp sa nedá dekódovať");
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        int[] base = new int[w * h];
        bmp.getPixels(base, 0, w, 0, 0, w, h);
        bmp.recycle();
        byte[] top = loadLabels(am, "muscle_map/muscles_" + view + ".bin", w, h);
        byte[] deep = withDeep ? loadLabels(am, "muscle_map/muscles_" + view + "_deep.bin", w, h) : null;
        return new Figure(w, h, base, top, deep);
    }

    private static byte[] loadLabels(AssetManager am, String path, int w, int h) throws IOException {
        try (DataInputStream in = new DataInputStream(am.open(path))) {
            byte[] magic = new byte[4];
            in.readFully(magic);
            if (magic[0] != 'F' || magic[1] != 'T' || magic[2] != 'M' || magic[3] != 'L') {
                throw new IOException(path + ": neplatná hlavička");
            }
            int lw = in.readInt();
            int lh = in.readInt();
            if (lw != w || lh != h) {
                throw new IOException(path + ": rozmer " + lw + "x" + lh + " nesedí s podkladom " + w + "x" + h);
            }
            byte[] data = new byte[w * h];
            in.readFully(data);
            return data;
        }
    }

    // ---------------------------------------------------------------- stav inštancie

    private final Set<String> primary = new HashSet<>();
    private final Set<String> secondary = new HashSet<>();

    private boolean selectable;
    private boolean showLabels = true;
    private OnMuscleToggleListener toggleListener;

    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int colorPrimary;
    private int colorSecondary;

    private boolean loaded;
    private Bitmap frontBmp;
    private Bitmap backBmp;
    private boolean dirty = true;

    // Umiestnenie postáv v pixeloch View, prepočítané v onSizeChanged
    private final RectF frontDst = new RectF();
    private final RectF backDst = new RectF();

    public MuscleMapView(Context ctx) {
        super(ctx);
        init(ctx);
    }

    public MuscleMapView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        init(ctx);
    }

    private void init(Context ctx) {
        colorPrimary = ctx.getColor(R.color.muscle_highlight);
        colorSecondary = ctx.getColor(R.color.muscle_secondary);

        labelPaint.setColor(ctx.getColor(R.color.text_muted));
        labelPaint.setTextSize(UiKit.dp(ctx, 10));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setLetterSpacing(0.14f);

        loaded = ensureLoaded(ctx);
    }

    // ---------------------------------------------------------------- API

    /** Nastaví zvýraznené svaly. Sval, ktorý je v oboch množinách, sa kreslí ako primárny. */
    public void setMuscles(Collection<String> primaryMuscles, Collection<String> secondaryMuscles) {
        primary.clear();
        secondary.clear();
        if (primaryMuscles != null) primary.addAll(primaryMuscles);
        if (secondaryMuscles != null) secondary.addAll(secondaryMuscles);
        dirty = true;
        invalidate();
    }

    /** Aktuálne vybrané (primárne) svaly — v režime setSelectable je to výber používateľa. */
    public Set<String> getSelectedMuscles() {
        return new HashSet<>(primary);
    }

    public void setSelectable(boolean selectable) {
        this.selectable = selectable;
        setClickable(selectable);
    }

    public void setOnMuscleToggleListener(OnMuscleToggleListener l) {
        this.toggleListener = l;
    }

    /** Popisky "SPREDU" / "ZOZADU" pod postavami (predvolene zapnuté). */
    public void setShowLabels(boolean show) {
        this.showLabels = show;
        requestLayout();
        invalidate();
    }

    /** Slovenský názov svalu pre popisky a legendy. */
    public static String label(String muscle) {
        if (muscle == null) return "";
        switch (muscle) {
            case ABDOMINALS: return "Brucho";
            case ABDUCTORS: return "Abduktory";
            case ADDUCTORS: return "Adduktory";
            case BICEPS: return "Biceps";
            case CALVES: return "Lýtka";
            case CHEST: return "Hrudník";
            case FOREARMS: return "Predlaktia";
            case GLUTES: return "Zadok";
            case HAMSTRINGS: return "Hamstringy";
            case LATS: return "Široký chrbtový";
            case LOWER_BACK: return "Spodný chrbát";
            case MIDDLE_BACK: return "Stred chrbta";
            case NECK: return "Krk";
            case QUADRICEPS: return "Stehná";
            case SHOULDERS: return "Ramená";
            case TRAPS: return "Trapézy";
            case TRICEPS: return "Triceps";
            default: return muscle;
        }
    }

    /**
     * Svaly, ktoré pokrýva partia z Category (vrátane legacy kombinovaných kľúčov).
     * Pre "cardio" a neznáme kľúče vráti prázdny zoznam.
     */
    public static List<String> musclesForCategory(String categoryKey) {
        if (categoryKey == null) return new ArrayList<>();
        switch (categoryKey) {
            case "chest": return list(CHEST);
            case "back": return list(LATS, MIDDLE_BACK, LOWER_BACK);
            case "shoulders": return list(SHOULDERS);
            case "biceps": return list(BICEPS);
            case "triceps": return list(TRICEPS);
            case "trapezius": return list(TRAPS);
            case "leg": return list(QUADRICEPS, HAMSTRINGS, GLUTES, ADDUCTORS, ABDUCTORS);
            case "calves": return list(CALVES);
            case "abs": return list(ABDOMINALS);
            // Legacy kombinované kategórie — pozri Category.legacyAll()
            case "back_triceps": return list(LATS, MIDDLE_BACK, LOWER_BACK, TRICEPS);
            case "chest_biceps": return list(CHEST, BICEPS);
            case "legs": return list(QUADRICEPS, HAMSTRINGS, GLUTES, ADDUCTORS, ABDUCTORS, SHOULDERS);
            default: return new ArrayList<>();
        }
    }

    private static List<String> list(String... muscles) {
        return new ArrayList<>(Arrays.asList(muscles));
    }

    private static int indexOf(String muscle) {
        for (int i = 0; i < GROUPS.length; i++) {
            if (GROUPS[i].equals(muscle)) return i;
        }
        return -1;
    }

    // ---------------------------------------------------------------- skladanie obrázka

    /** Priorita skupiny: 2 = primárna, 1 = sekundárna, 0 = nezvýraznená. Index 0 = žiadny sval. */
    private int[] groupStates() {
        int[] state = new int[GROUPS.length + 1];
        for (int i = 0; i < GROUPS.length; i++) {
            String g = GROUPS[i];
            state[i + 1] = primary.contains(g) ? 2 : secondary.contains(g) ? 1 : 0;
        }
        return state;
    }

    private Bitmap compose(Figure f, Bitmap reuse, int[] state) {
        int[] out = composePixels(f, state, colorPrimary, colorSecondary);
        Bitmap bmp = reuse;
        if (bmp == null || bmp.getWidth() != f.w || bmp.getHeight() != f.h) {
            bmp = Bitmap.createBitmap(f.w, f.h, Bitmap.Config.ARGB_8888);
        }
        bmp.setPixels(out, 0, f.w, 0, 0, f.w, f.h);
        return bmp;
    }

    /** Podklad s prefarbenými svalmi podľa state (2 = primárny, 1 = sekundárny). */
    private static int[] composePixels(Figure f, int[] state, int colorPrimary, int colorSecondary) {
        int n = f.w * f.h;
        int[] out = new int[n];
        System.arraycopy(f.base, 0, out, 0, n);
        boolean any = false;
        for (int s : state) any |= s != 0;
        if (!any) return out;
        int[] tint = {0, colorSecondary, colorPrimary};
        for (int i = 0; i < n; i++) {
            int t = f.top[i];
            if (t == 0) continue;
            int s = state[t];
            // Hlboký chrbtový sval presvitá cez trapéz / široký chrbtový, ak má vyššiu prioritu
            if (f.deep != null && (t == IDX_TRAPS || t == IDX_LATS)) {
                int d = f.deep[i];
                if (d != 0 && state[d] > s) s = state[d];
            }
            if (s == 0) continue;
            out[i] = shade(out[i], tint[s]);
        }
        return out;
    }

    // ---------------------------------------------------------------- malé ikony partií

    private static final java.util.Map<String, Bitmap> ICON_CACHE = new java.util.HashMap<>();
    /** Výška ikony v pixeloch — ostrá aj v legende (80 dp), v kalendári sa len zmenší. */
    private static final int ICON_HEIGHT_PX = 280;

    /**
     * Malá ikona partie (legenda, kalendár, hlavičky): realistická postava s partiou načerveno,
     * z toho pohľadu (spredu/zozadu), kde je partia viac vidieť. Null, ak partia nemá svaly (kardio)
     * alebo sa assets nepodarilo načítať. Výsledok je cachovaný.
     */
    public static synchronized Bitmap categoryIcon(Context ctx, String categoryKey) {
        if (categoryKey == null) return null;
        if (ICON_CACHE.containsKey(categoryKey)) return ICON_CACHE.get(categoryKey);
        List<String> muscles = musclesForCategory(categoryKey);
        Bitmap icon = null;
        if (!muscles.isEmpty() && ensureLoaded(ctx)) {
            int[] state = new int[GROUPS.length + 1];
            for (String m : muscles) {
                int idx = indexOf(m);
                if (idx >= 0) state[idx + 1] = 2;
            }
            Figure f = coverage(sBack, state) > coverage(sFront, state) ? sBack : sFront;
            int[] px = composePixels(f, state, ctx.getColor(R.color.muscle_highlight), ctx.getColor(R.color.muscle_secondary));
            // Priblíženie na polovicu tela, kde partia je: horná (hlava–boky) alebo dolná (boky–chodidlá)
            boolean lower = centroidY(f, state) > f.h * 0.5f;
            int y0 = lower ? Math.round(f.h * 0.42f) : 0;
            int y1 = lower ? f.h : Math.round(f.h * 0.56f);
            Bitmap full = Bitmap.createBitmap(px, f.w, f.h, Bitmap.Config.ARGB_8888);
            Bitmap half = Bitmap.createBitmap(full, 0, y0, f.w, y1 - y0);
            int w = Math.max(1, Math.round(f.w * (ICON_HEIGHT_PX / (float) (y1 - y0))));
            icon = Bitmap.createScaledBitmap(half, w, ICON_HEIGHT_PX, true);
            if (half != full && half != icon) half.recycle();
            if (icon != full) full.recycle();
        }
        ICON_CACHE.put(categoryKey, icon);
        return icon;
    }

    /** Priemerná výška (y) zvýraznených pixelov — rozhoduje, či ikona ukáže hornú alebo dolnú polovicu tela. */
    private static float centroidY(Figure f, int[] state) {
        long sum = 0;
        int count = 0;
        for (int i = 0; i < f.top.length; i++) {
            int t = f.top[i];
            if (t == 0) continue;
            if (state[t] > 0 || (f.deep != null && f.deep[i] != 0 && state[f.deep[i]] > 0)) {
                sum += i / f.w;
                count++;
            }
        }
        return count == 0 ? 0 : sum / (float) count;
    }

    /** Počet pixelov zvýraznených svalov na postave (vrátane hlbokej vrstvy chrbta). */
    private static int coverage(Figure f, int[] state) {
        int count = 0;
        for (int i = 0; i < f.top.length; i++) {
            int t = f.top[i];
            if (t == 0) continue;
            if (state[t] > 0 || (f.deep != null && f.deep[i] != 0 && state[f.deep[i]] > 0)) count++;
        }
        return count;
    }

    /** Farba svalu modulovaná jasom renderu (tiene, vlákna a šľachy zostanú čitateľné). */
    private static int shade(int px, int color) {
        int a = px >>> 24;
        float lum = (((px >> 16) & 0xFF) + ((px >> 8) & 0xFF) + (px & 0xFF)) / (3f * 255f);
        float k = Math.min(1.2f, 0.35f + 0.95f * lum);
        int r = Math.min(255, (int) (Color.red(color) * k));
        int g = Math.min(255, (int) (Color.green(color) * k));
        int b = Math.min(255, (int) (Color.blue(color) * k));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // ---------------------------------------------------------------- layout & kreslenie

    private float labelSpacePx() {
        return showLabels ? labelPaint.getTextSize() * 2.2f : 0f;
    }

    /** Šírka/výška oboch postáv vedľa seba (vrátane medzery). */
    private float mapAspect() {
        if (!loaded) return 2.2f / 2.5f;
        return (sFront.w * (2f + GAP_RATIO)) / sFront.h;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        float availW = w - getPaddingLeft() - getPaddingRight();
        int desiredH = (int) (availW / mapAspect() + labelSpacePx()) + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(w, resolveSize(desiredH, heightMeasureSpec));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (!loaded) return;
        float availW = w - getPaddingLeft() - getPaddingRight();
        float availH = h - getPaddingTop() - getPaddingBottom() - labelSpacePx();
        float scale = Math.max(0.001f, Math.min(availW / (sFront.w * (2f + GAP_RATIO)), availH / sFront.h));
        float figW = sFront.w * scale;
        float figH = sFront.h * scale;
        float gap = figW * GAP_RATIO;
        float left = getPaddingLeft() + (availW - (2 * figW + gap)) / 2f;
        float top = getPaddingTop() + Math.max(0f, (availH - figH) / 2f);
        frontDst.set(left, top, left + figW, top + figH);
        backDst.set(left + figW + gap, top, left + 2 * figW + gap, top + figH);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!loaded || getWidth() == 0) return;

        if (dirty) {
            int[] state = groupStates();
            frontBmp = compose(sFront, frontBmp, state);
            backBmp = compose(sBack, backBmp, state);
            dirty = false;
        }
        canvas.drawBitmap(frontBmp, null, frontDst, bitmapPaint);
        canvas.drawBitmap(backBmp, null, backDst, bitmapPaint);

        if (showLabels) {
            float y = frontDst.bottom + labelPaint.getTextSize() * 1.6f;
            canvas.drawText("SPREDU", frontDst.centerX(), y, labelPaint);
            canvas.drawText("ZOZADU", backDst.centerX(), y, labelPaint);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // Zložené bitmapy (~2 MB na postavu) uvoľníme; pri ďalšom kreslení sa zložia znova
        frontBmp = null;
        backBmp = null;
        dirty = true;
    }

    // ---------------------------------------------------------------- výber ťukom

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!selectable) return super.onTouchEvent(event);
        if (event.getAction() == MotionEvent.ACTION_DOWN) return true;
        if (event.getAction() != MotionEvent.ACTION_UP) return true;

        String hit = muscleAt(event.getX(), event.getY());
        if (hit != null) {
            boolean nowSelected = !primary.remove(hit);
            if (nowSelected) primary.add(hit);
            dirty = true;
            invalidate();
            performClick();
            if (toggleListener != null) toggleListener.onMuscleToggled(hit, nowSelected);
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    /** Sval pod daným bodom v pixeloch View (alebo najbližší v malom okolí), inak null. */
    private String muscleAt(float px, float py) {
        if (!loaded) return null;
        Figure f;
        RectF dst;
        if (frontDst.contains(px, py)) {
            f = sFront;
            dst = frontDst;
        } else if (backDst.contains(px, py)) {
            f = sBack;
            dst = backDst;
        } else {
            return null;
        }
        int x = (int) ((px - dst.left) / dst.width() * f.w);
        int y = (int) ((py - dst.top) / dst.height() * f.h);

        int best = 0;
        int bestDist = Integer.MAX_VALUE;
        Rect r = new Rect(Math.max(0, x - TOUCH_SLOP_PX), Math.max(0, y - TOUCH_SLOP_PX),
                Math.min(f.w - 1, x + TOUCH_SLOP_PX), Math.min(f.h - 1, y + TOUCH_SLOP_PX));
        for (int yy = r.top; yy <= r.bottom; yy++) {
            for (int xx = r.left; xx <= r.right; xx++) {
                int idx = f.top[yy * f.w + xx];
                if (idx == 0) continue;
                int d = (xx - x) * (xx - x) + (yy - y) * (yy - y);
                if (d < bestDist) {
                    bestDist = d;
                    best = idx;
                }
            }
        }
        return best == 0 ? null : GROUPS[best - 1];
    }
}
