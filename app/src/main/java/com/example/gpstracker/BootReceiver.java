package com.example.gpstracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

/**
 * Cihaz yeniden başlatıldığında konum servisini otomatik başlatır.
 * Yalnızca daha önce sunucu adresi ayarlanmışsa devreye girer.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        if (action.equals(Intent.ACTION_BOOT_COMPLETED)
                || action.equals("android.intent.action.QUICKBOOT_POWERON")) {

            SharedPreferences prefs =
                    context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE);
            String firebaseUrl = prefs.getString(Prefs.FIREBASE_URL, "");
            if (firebaseUrl.isEmpty()) {
                Log.w(TAG, "Firebase ayarlı değil, servis başlatılmadı.");
                return;
            }

            Intent serviceIntent = new Intent(context, LocationService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
            Log.d(TAG, "Açılışta konum servisi başlatıldı.");
        }
    }
}
