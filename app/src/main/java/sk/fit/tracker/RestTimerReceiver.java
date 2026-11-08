package sk.fit.tracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/** Koniec pauzy (alarm z RestTimer): zavibruje a ukáže notifikáciu "Pauza skončila". */
public class RestTimerReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        // Alarm mohol prísť aj o chvíľu skôr/neskôr; ak medzitým používateľ odpočet zrušil, nič nerob
        if (RestTimer.getEndAt(ctx) == 0) return;
        RestTimer.markFinished(ctx);
        RestTimer.showFinishedNotification(ctx);
        // Vibrácia aj bez povolenia notifikácií (napr. ak ich používateľ odmietol)
        if (!RestTimer.canNotify(ctx)) vibrate(ctx);
    }

    static void vibrate(Context ctx) {
        Vibrator v;
        if (Build.VERSION.SDK_INT >= 31) {
            VibratorManager vm = (VibratorManager) ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            v = vm != null ? vm.getDefaultVibrator() : null;
        } else {
            v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
        }
        if (v == null || !v.hasVibrator()) return;
        v.vibrate(VibrationEffect.createWaveform(new long[]{0, 400, 200, 400, 200, 600}, -1));
    }
}
