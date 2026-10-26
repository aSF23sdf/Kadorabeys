package com.example.gpstracker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

/**
 * Arka planda sürekli çalışan konum takip servisi.
 * - Foreground service olarak çalışır; sistem tarafından kolay kolay öldürülmez.
 * - Pil tasarrufu için güç dengeli (balanced) konum önceliği kullanır.
 * - Her konumu önce yerel olarak kuyruğa yazar, sonra admin paneli sunucusuna gönderir.
 */
public class LocationService extends Service {

    private static final String TAG = "LocationService";
    private static final String CHANNEL_ID = "gps_tracker_channel";
    private static final int NOTIFICATION_ID = 1001;

    // --- PİL / HASSASİYET AYARLARI ---
    // Konum güncelleme aralığı (ms). 30 saniye = pil dostu bir denge.
    private static final long UPDATE_INTERVAL_MS = 30_000L;
    // En hızlı kabul edilebilir aralık (ms).
    private static final long FASTEST_INTERVAL_MS = 15_000L;
    // Minimum yer değişimi (metre). Hareket yoksa güncelleme almaz → pil tasarrufu.
    private static final float MIN_DISPLACEMENT_M = 10f;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LocationRepository repository;
    private ApiClient apiClient;
    private String deviceId;

    @Override
    public void onCreate() {
        super.onCreate();
        repository = new LocationRepository(this);

        SharedPreferences prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE);
        String firebaseUrl = prefs.getString(Prefs.FIREBASE_URL, "");
        String secret = prefs.getString(Prefs.FIREBASE_SECRET, "");
        deviceId = prefs.getString(Prefs.DEVICE_ID, "unknown");
        String label = prefs.getString(Prefs.DEVICE_LABEL, deviceId);
        apiClient = new ApiClient(firebaseUrl, secret, deviceId, label);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        createNotificationChannel();
        setupLocationCallback();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification("Konum takibi aktif"));
        startLocationUpdates();
        // Servis öldürülürse sistem onu yeniden başlatsın
        return START_STICKY;
    }

    private void setupLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                Location loc = result.getLastLocation();
                if (loc == null) return;

                long time = System.currentTimeMillis();
                // 1) Yerel kuyruğa yaz (ağ yoksa veri kaybolmasın)
                repository.saveLocation(
                        loc.getLatitude(), loc.getLongitude(),
                        loc.getAccuracy(), loc.getSpeed(),
                        loc.getAltitude(), time);

                // 2) Sunucuya gönder (arka plan thread'inde)
                apiClient.uploadAsync(
                        loc.getLatitude(), loc.getLongitude(),
                        loc.getAccuracy(), loc.getSpeed(),
                        loc.getAltitude(), readBatteryPercent(), time);

                Log.d(TAG, "Konum: " + loc.getLatitude() + ", " + loc.getLongitude());
                updateNotification(String.format(
                        "Son konum: %.5f, %.5f", loc.getLatitude(), loc.getLongitude()));
            }
        };
    }

    private void startLocationUpdates() {
        // PRIORITY_BALANCED_POWER_ACCURACY: salt GPS yerine ağ/WiFi konumunu da
        // kullanır → şehir içinde yeterli hassasiyet, belirgin pil tasarrufu.
        // Daha yüksek hassasiyet gerekirse PRIORITY_HIGH_ACCURACY yapılabilir.
        LocationRequest request = new LocationRequest.Builder(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY, UPDATE_INTERVAL_MS)
                .setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
                .setMinUpdateDistanceMeters(MIN_DISPLACEMENT_M)
                .setWaitForAccurateLocation(false)
                .build();

        try {
            fusedLocationClient.requestLocationUpdates(
                    request, locationCallback, Looper.getMainLooper());
        } catch (SecurityException e) {
            Log.e(TAG, "Konum izni yok: " + e.getMessage());
            stopSelf();
        }
    }

    /** Anlık batarya yüzdesini okur (0-100). Okunamazsa -1 döner. */
    private int readBatteryPercent() {
        try {
            android.content.IntentFilter f =
                    new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent b = registerReceiver(null, f);
            if (b == null) return -1;
            int level = b.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
            int scale = b.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1);
            if (level < 0 || scale <= 0) return -1;
            return Math.round(level * 100f / scale);
        } catch (Exception e) {
            return -1;
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "GPS Takip",
                    NotificationManager.IMPORTANCE_LOW); // LOW = sessiz, pil dostu
            channel.setDescription("Arka plan konum takibi");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text) {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, openIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Kadobabacloud GPS")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentIntent(pi)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification(text));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        if (apiClient != null) apiClient.shutdown();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
