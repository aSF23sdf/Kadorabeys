package com.example.gpstracker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

/**
 * Arka planda sürekli çalışan konum takip servisi (SADE sürüm).
 * - Yalnızca Android çatısını kullanır (dış kütüphane yok).
 * - LocationManager ile GPS + ağ konumu alır (pil için 30 sn / 10 m eşik).
 * - Foreground service + START_STICKY ile uygulama kapansa da çalışır.
 * - Her konumu yerel dosyaya yazar ve Firebase'e gönderir.
 */
public class LocationService extends Service
        implements LocationListener, SensorEventListener {

    private static final String TAG = "LocationService";
    private static final String CHANNEL_ID = "gps_tracker_channel";
    private static final int NOTIFICATION_ID = 1001;

    // --- PİL / HASSASİYET AYARLARI ---
    private static final long UPDATE_INTERVAL_MS = 30_000L; // 30 sn
    private static final float MIN_DISPLACEMENT_M = 10f;    // 10 m

    private LocationManager locationManager;
    private LocationRepository repository;
    private ApiClient apiClient;
    private SharedPreferences prefs;

    // Adım sayacı
    private SensorManager sensorManager;
    private Sensor stepSensor;
    private long sessionSteps = 0;      // bu takip oturumunda atılan adım
    private float stepBaseline = -1;    // sensörün ilk okuması

    // Mesafe birikimi (haversine)
    private double totalDistanceM = 0;  // toplam yürünen mesafe (metre)
    private Location lastLocation = null;

    @Override
    public void onCreate() {
        super.onCreate();
        repository = new LocationRepository(this);

        prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE);
        String firebaseUrl = prefs.getString(Prefs.FIREBASE_URL, "");
        String secret = prefs.getString(Prefs.FIREBASE_SECRET, "");
        String deviceId = prefs.getString(Prefs.DEVICE_ID, "unknown");
        String label = prefs.getString(Prefs.DEVICE_LABEL, deviceId);
        apiClient = new ApiClient(firebaseUrl, secret, deviceId, label);

        // Önceki toplam mesafeyi geri yükle (servis yeniden başlamış olabilir)
        totalDistanceM = prefs.getFloat(Prefs.TOTAL_DISTANCE_M, 0f);
        stepBaseline = prefs.getFloat(Prefs.STEP_BASELINE, -1f);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        setupStepSensor();
        createNotificationChannel();
    }

    private void setupStepSensor() {
        try {
            sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            if (sensorManager != null) {
                stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
                if (stepSensor != null) {
                    sensorManager.registerListener(this, stepSensor,
                            SensorManager.SENSOR_DELAY_NORMAL);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Adım sensörü yok: " + e.getMessage());
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_STEP_COUNTER) return;
        float total = event.values[0]; // açılıştan beri toplam adım
        if (stepBaseline < 0) {
            stepBaseline = total;
            prefs.edit().putFloat(Prefs.STEP_BASELINE, stepBaseline).apply();
        }
        sessionSteps = (long) (total - stepBaseline);
        if (sessionSteps < 0) sessionSteps = 0;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification("Konum takibi aktif"));
        startLocationUpdates();
        return START_STICKY;
    }

    private void startLocationUpdates() {
        if (locationManager == null) { stopSelf(); return; }
        try {
            // Hem GPS hem ağ sağlayıcısından güncelleme iste (hangisi varsa)
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        UPDATE_INTERVAL_MS, MIN_DISPLACEMENT_M, this);
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        UPDATE_INTERVAL_MS, MIN_DISPLACEMENT_M, this);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Konum izni yok: " + e.getMessage());
            stopSelf();
        }
    }

    @Override
    public void onLocationChanged(Location loc) {
        if (loc == null) return;
        long time = System.currentTimeMillis();

        // Mesafe birikimi: önceki konumla arasındaki gerçek yolu ekle.
        // Çok küçük sıçramaları (GPS gürültüsü) ve ışınlanmaları ayıkla.
        if (lastLocation != null) {
            float d = lastLocation.distanceTo(loc);
            if (d >= 5f && d <= 2000f) {
                totalDistanceM += d;
                prefs.edit().putFloat(Prefs.TOTAL_DISTANCE_M, (float) totalDistanceM).apply();
            }
        }
        lastLocation = loc;

        repository.saveLocation(
                loc.getLatitude(), loc.getLongitude(),
                loc.getAccuracy(), loc.getSpeed(),
                loc.getAltitude(), time);

        apiClient.uploadAsync(
                loc.getLatitude(), loc.getLongitude(),
                loc.getAccuracy(), loc.getSpeed(),
                loc.getAltitude(), readBatteryPercent(), time,
                sessionSteps, totalDistanceM);

        Log.d(TAG, "Konum: " + loc.getLatitude() + ", " + loc.getLongitude()
                + " | adım=" + sessionSteps + " | mesafe=" + Math.round(totalDistanceM) + "m");
        updateNotification(String.format(
                "%.2f km · %d adım", totalDistanceM / 1000.0, sessionSteps));
    }

    // Eski API'ler için gerekli boş metotlar
    @Override public void onStatusChanged(String p, int s, Bundle extras) {}
    @Override public void onProviderEnabled(String provider) {}
    @Override public void onProviderDisabled(String provider) {}

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
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "GPS Takip", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Arka plan konum takibi");
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    private Notification buildNotification(String text) {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, openIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Kadobabacloud GPS")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification(text));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (locationManager != null) {
            try { locationManager.removeUpdates(this); } catch (Exception ignored) {}
        }
        if (sensorManager != null) {
            try { sensorManager.unregisterListener(this); } catch (Exception ignored) {}
        }
        if (apiClient != null) apiClient.shutdown();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
