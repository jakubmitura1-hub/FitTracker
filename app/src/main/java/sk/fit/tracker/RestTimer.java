package sk.fit.tracker;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

/**
 * Odpočet pauzy medzi sériami. Stav (koniec odpočtu) je v SharedPreferences, takže prežije aj
 * zatvorenie appky; koniec pauzy ohlási RestTimerReceiver cez AlarmManager — funguje aj pri
 * zhasnutom displeji (mobil vo vrecku). Počas odpočtu beží tichá notifikácia s odpočtom.
 *
 * UI je RestTimerBar (obrazovka dňa aj dialóg cviku), ktorá sa len pýta na stav.
 */
public final class RestTimer {

    private static final String PREFS = "FitTrackerRestTimer";
    private static final String KEY_END_AT = "end_at";
    private static final String KEY_TOTAL_MS = "total_ms";
    private static final String KEY_FINISHED_AT = "finished_at";
    private static final String KEY_DEFAULT_S = "default_s";
    private static final String KEY_AUTO = "auto_start";
    private static final String KEY_ASKED_NOTIF = "asked_notif";

    static final String CHANNEL_RUNNING = "rest_timer_running";
    static final String CHANNEL_DONE = "rest_timer_done";
    static final int NOTIFICATION_ID = 4711;

    public static final int[] PRESETS_S = {60, 90, 120, 180};

    private RestTimer() {}

    // ---------------------------------------------------------------- nastavenia

    public static int getDefaultSeconds(Context ctx) {
        return prefs(ctx).getInt(KEY_DEFAULT_S, 90);
    }

    public static void setDefaultSeconds(Context ctx, int seconds) {
        prefs(ctx).edit().putInt(KEY_DEFAULT_S, seconds).apply();
    }

    /** Spustiť pauzu automaticky po pridaní série (predvolene áno). */
    public static boolean isAutoStart(Context ctx) {
        return prefs(ctx).getBoolean(KEY_AUTO, true);
    }

    public static void setAutoStart(Context ctx, boolean auto) {
        prefs(ctx).edit().putBoolean(KEY_AUTO, auto).apply();
    }

    // ---------------------------------------------------------------- stav

    /** Čas konca odpočtu (millis), 0 = nebeží. */
    public static long getEndAt(Context ctx) {
        return prefs(ctx).getLong(KEY_END_AT, 0L);
    }

    public static long getTotalMillis(Context ctx) {
        return prefs(ctx).getLong(KEY_TOTAL_MS, 1L);
    }

    public static long remainingMillis(Context ctx) {
        long end = getEndAt(ctx);
        return end == 0 ? 0 : Math.max(0, end - System.currentTimeMillis());
    }

    public static boolean isRunning(Context ctx) {
        return remainingMillis(ctx) > 0;
    }

    // ---------------------------------------------------------------- ovládanie

    public static void startDefault(Context ctx) {
        start(ctx, getDefaultSeconds(ctx));
    }

    public static void start(Context ctx, int seconds) {
        long total = seconds * 1000L;
        long end = System.currentTimeMillis() + total;
        prefs(ctx).edit().putLong(KEY_END_AT, end).putLong(KEY_TOTAL_MS, total)
                .putLong(KEY_FINISHED_AT, 0L).apply();
        schedule(ctx, end);
    }

    /** Pridá/uberie sekundy bežiacemu odpočtu; pri poklese na nulu ho zastaví. */
    public static void addSeconds(Context ctx, int delta) {
        long end = getEndAt(ctx);
        if (end == 0 || !isRunning(ctx)) return;
        long newEnd = end + delta * 1000L;
        if (newEnd <= System.currentTimeMillis() + 1000) {
            stop(ctx);
            return;
        }
        long total = Math.max(getTotalMillis(ctx) + delta * 1000L, newEnd - System.currentTimeMillis());
        prefs(ctx).edit().putLong(KEY_END_AT, newEnd).putLong(KEY_TOTAL_MS, total).apply();
        schedule(ctx, newEnd);
    }

    /** Zrušenie používateľom — na rozdiel od dobehnutia sa neohlasuje "Pauza skončila". */
    public static void stop(Context ctx) {
        prefs(ctx).edit().putLong(KEY_END_AT, 0L).putLong(KEY_FINISHED_AT, 0L).apply();
        alarm(ctx).cancel(alarmIntent(ctx));
        notifications(ctx).cancel(NOTIFICATION_ID);
    }

    /** Volá RestTimerReceiver, keď odpočet dobehol. */
    static void markFinished(Context ctx) {
        long end = getEndAt(ctx);
        prefs(ctx).edit().putLong(KEY_END_AT, 0L).putLong(KEY_FINISHED_AT, end).apply();
    }

    /**
     * Kedy naposledy pauza prirodzene dobehla (millis), 0 = nedobehla / bola zrušená.
     * Pokrýva aj chvíľu, keď už dobehla, ale alarm (RestTimerReceiver) ešte neprišiel.
     */
    public static long lastFinishedAt(Context ctx) {
        long end = getEndAt(ctx);
        if (end != 0) return end <= System.currentTimeMillis() ? end : 0;
        return prefs(ctx).getLong(KEY_FINISHED_AT, 0L);
    }

    /**
     * Po reštarte telefónu (alebo aktualizácii appky) Android zabudne naplánovaný alarm, hoci podľa
     * uloženého stavu odpočet ešte beží — koniec pauzy by potom neprišiel. Ak alarm chýba, naplánuj
     * ho znova. Volá RestTimerBar pri zobrazení.
     */
    public static void ensureScheduled(Context ctx) {
        if (!isRunning(ctx)) return;
        Intent i = new Intent(ctx, RestTimerReceiver.class);
        PendingIntent existing = PendingIntent.getBroadcast(ctx, 0, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_NO_CREATE);
        if (existing == null) schedule(ctx, getEndAt(ctx));
    }

    // ---------------------------------------------------------------- notifikácie a alarm

    /** Na Android 13+ treba povolenie na notifikácie — opýtame sa raz, pri prvom spustení pauzy. */
    public static void maybeAskNotificationPermission(android.app.Activity activity) {
        if (Build.VERSION.SDK_INT < 33) return;
        if (activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) return;
        SharedPreferences p = prefs(activity);
        if (p.getBoolean(KEY_ASKED_NOTIF, false)) return;
        p.edit().putBoolean(KEY_ASKED_NOTIF, true).apply();
        activity.requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 4712);
    }

    static boolean canNotify(Context ctx) {
        return Build.VERSION.SDK_INT < 33 || ctx.checkSelfPermission(
                android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private static void schedule(Context ctx, long endAt) {
        AlarmManager am = alarm(ctx);
        PendingIntent pi = alarmIntent(ctx);
        am.cancel(pi);
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, pi);
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, pi);
        showRunningNotification(ctx, endAt);
    }

    private static void showRunningNotification(Context ctx, long endAt) {
        if (!canNotify(ctx)) return;
        ensureChannels(ctx);
        Notification n = new Notification.Builder(ctx, CHANNEL_RUNNING)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("⏱ Pauza medzi sériami")
                .setContentText("Ďalšia séria o chvíľu")
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(endAt)
                .setShowWhen(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openAppIntent(ctx))
                .build();
        notifications(ctx).notify(NOTIFICATION_ID, n);
    }

    static void showFinishedNotification(Context ctx) {
        if (!canNotify(ctx)) return;
        ensureChannels(ctx);
        Notification n = new Notification.Builder(ctx, CHANNEL_DONE)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Pauza skončila 💪")
                .setContentText("Čas na ďalšiu sériu")
                .setCategory(Notification.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setTimeoutAfter(60_000)
                .setContentIntent(openAppIntent(ctx))
                .build();
        notifications(ctx).notify(NOTIFICATION_ID, n);
    }

    static void ensureChannels(Context ctx) {
        NotificationManager nm = notifications(ctx);
        if (nm.getNotificationChannel(CHANNEL_RUNNING) == null) {
            NotificationChannel running = new NotificationChannel(CHANNEL_RUNNING,
                    "Odpočet pauzy", NotificationManager.IMPORTANCE_LOW);
            running.setDescription("Zostávajúci čas pauzy medzi sériami");
            running.setShowBadge(false);
            nm.createNotificationChannel(running);
        }
        if (nm.getNotificationChannel(CHANNEL_DONE) == null) {
            NotificationChannel done = new NotificationChannel(CHANNEL_DONE,
                    "Koniec pauzy", NotificationManager.IMPORTANCE_HIGH);
            done.setDescription("Upozornenie, že pauza medzi sériami skončila");
            done.enableVibration(true);
            done.setVibrationPattern(new long[]{0, 400, 200, 400, 200, 600});
            nm.createNotificationChannel(done);
        }
    }

    /** Ťuk na notifikáciu vráti používateľa do appky (do rozrobenej obrazovky, nie novej). */
    private static PendingIntent openAppIntent(Context ctx) {
        Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
        if (launch == null) launch = new Intent(ctx, MainActivity.class);
        launch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return PendingIntent.getActivity(ctx, 0, launch, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static PendingIntent alarmIntent(Context ctx) {
        Intent i = new Intent(ctx, RestTimerReceiver.class);
        return PendingIntent.getBroadcast(ctx, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static AlarmManager alarm(Context ctx) {
        return (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
    }

    private static NotificationManager notifications(Context ctx) {
        return (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
