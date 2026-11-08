package sk.fit.tracker;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

/**
 * Svetlý/tmavý vzhľad appky — prepína ho výhradne tlačidlo v appke (MainActivity),
 * nie systémové nastavenie telefónu. Aktivity nededia AppCompatActivity, takže
 * AppCompatDelegate.setDefaultNightMode by nezabralo; namiesto toho každá aktivita
 * v attachBaseContext() zabalí kontext s vynúteným UI_MODE_NIGHT_* bitom podľa tejto
 * uloženej preferencie, čím sa aj bez systémového tmavého režimu použijú zdroje
 * z values-night/ (alebo naopak, ignorujú, ak si používateľ zvolil svetlý režim
 * na telefóne s tmavým systémovým nastavením).
 */
public class ThemeStore {

    private static final String PREFS_NAME = "FitTrackerTheme";
    private static final String KEY_DARK = "dark_mode";

    private ThemeStore() {}

    public static boolean isDarkMode(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_DARK, false);
    }

    public static void setDarkMode(Context ctx, boolean dark) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_DARK, dark).commit();
    }

    /** Zavolať z attachBaseContext() každej aktivity, pred super.attachBaseContext(). */
    public static Context wrap(Context base) {
        boolean dark = isDarkMode(base);
        Configuration config = new Configuration(base.getResources().getConfiguration());
        int flag = dark ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO;
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | flag;
        return base.createConfigurationContext(config);
    }
}
