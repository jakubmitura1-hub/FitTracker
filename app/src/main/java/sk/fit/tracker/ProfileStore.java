package sk.fit.tracker;

import android.content.Context;
import android.content.SharedPreferences;

/** Základné údaje používateľa (výška, váha) — vstup pre výpočet spálených kalórií. */
public class ProfileStore {

    private static final String PREFS_NAME = "FitTrackerProfile";
    private static final String KEY_HEIGHT_CM = "height_cm";
    private static final String KEY_WEIGHT_KG = "weight_kg"; // staré jednohodnotové pole — nahradené WeightStore (história)
    private static final String KEY_LAST_BACKUP_AT = "last_backup_at";

    public static float getHeightCm(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getFloat(KEY_HEIGHT_CM, 0f);
    }

    public static void setHeightCm(Context ctx, float heightCm) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putFloat(KEY_HEIGHT_CM, heightCm).commit();
    }

    /**
     * Aktuálna váha = najnovší záznam z {@link WeightStore}. Ak história ešte nemá žiadny
     * záznam (staršia inštalácia appky pred zavedením histórie), spadne späť na pôvodné
     * jednohodnotové pole, aby sa nestratil už zadaný údaj.
     */
    public static float getWeightKg(Context ctx) {
        float latest = WeightStore.getLatestWeight(ctx);
        if (latest > 0) return latest;
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getFloat(KEY_WEIGHT_KG, 0f);
    }

    /** Uloží výšku a zaznamená váhu do histórie pod dnešný dátum. */
    public static void setProfile(Context ctx, float heightCm, float weightKg) {
        setHeightCm(ctx, heightCm);
        if (weightKg > 0) WeightStore.setWeight(ctx, ActivityStore.todayKey(), weightKg);
    }

    /** Máme dosť údajov na výpočet kalórií? (potrebná aspoň váha) */
    public static boolean hasProfile(Context ctx) {
        return getWeightKg(ctx) > 0;
    }

    /** Čas (millis) poslednej úspešnej zálohy, alebo 0 ak sa ešte nikdy nezálohovalo. */
    public static long getLastBackupAt(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getLong(KEY_LAST_BACKUP_AT, 0L);
    }

    public static void setLastBackupAt(Context ctx, long millis) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putLong(KEY_LAST_BACKUP_AT, millis).commit();
    }
}
