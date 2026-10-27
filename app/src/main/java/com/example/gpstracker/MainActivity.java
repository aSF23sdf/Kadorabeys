package com.example.gpstracker;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tek ekranlı kurulum arayüzü (SADE sürüm, dış kütüphane yok):
 *  - Firebase veritabanı adresi girilir
 *  - Cihaza etiket verilir
 *  - İzinler istenir ve takip başlatılır/durdurulur
 *  - Admin paneli açılır
 */
public class MainActivity extends Activity {

    private static final int REQ_LOCATION = 100;
    private static final int REQ_BACKGROUND = 101;
    private static final int REQ_NOTIF = 102;

    private EditText serverInput, secretInput, labelInput;
    private TextView statusText, deviceIdText;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE);

        serverInput = findViewById(R.id.serverInput);
        secretInput = findViewById(R.id.secretInput);
        labelInput = findViewById(R.id.labelInput);
        statusText = findViewById(R.id.statusText);
        deviceIdText = findViewById(R.id.deviceIdText);
        Button startBtn = findViewById(R.id.startBtn);
        Button stopBtn = findViewById(R.id.stopBtn);
        Button adminBtn = findViewById(R.id.adminBtn);
        ImageButton menuBtn = findViewById(R.id.menuBtn);
        menuBtn.setOnClickListener(this::showMenu);

        String deviceId = prefs.getString(Prefs.DEVICE_ID, null);
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString().substring(0, 8);
            prefs.edit().putString(Prefs.DEVICE_ID, deviceId).apply();
        }
        deviceIdText.setText("Cihaz ID: " + deviceId);

        serverInput.setText(prefs.getString(Prefs.FIREBASE_URL, ""));
        secretInput.setText(prefs.getString(Prefs.FIREBASE_SECRET, ""));
        labelInput.setText(prefs.getString(Prefs.DEVICE_LABEL, Build.MODEL));

        startBtn.setOnClickListener(v -> onStartClicked());
        stopBtn.setOnClickListener(v -> onStopClicked());
        adminBtn.setOnClickListener(v -> {
            prefs.edit()
                    .putString(Prefs.FIREBASE_URL, serverInput.getText().toString().trim())
                    .putString(Prefs.FIREBASE_SECRET, secretInput.getText().toString().trim())
                    .apply();
            startActivity(new Intent(this, AdminActivity.class));
        });
    }

    private void onStartClicked() {
        String server = serverInput.getText().toString().trim();
        String secret = secretInput.getText().toString().trim();
        String label = labelInput.getText().toString().trim();

        if (TextUtils.isEmpty(server)) {
            Toast.makeText(this, "Firebase adresini gir", Toast.LENGTH_SHORT).show();
            return;
        }
        prefs.edit()
                .putString(Prefs.FIREBASE_URL, server)
                .putString(Prefs.FIREBASE_SECRET, secret)
                .putString(Prefs.DEVICE_LABEL, label)
                .apply();

        requestPermissionsThenStart();
    }

    private void onStopClicked() {
        stopService(new Intent(this, LocationService.class));
        statusText.setText("Durum: Takip durduruldu");
    }

    // --- İzin akışı (framework API'leri) ---
    private void requestPermissionsThenStart() {
        List<String> needed = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
            needed.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        // Adım sayımı izni (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACTIVITY_RECOGNITION);
        }
        if (!needed.isEmpty()) {
            requestPermissions(needed.toArray(new String[0]), REQ_LOCATION);
        } else {
            requestBackgroundLocation();
        }
    }

    private void requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this,
                    "Lütfen 'Her zaman izin ver' seçeneğini seç",
                    Toast.LENGTH_LONG).show();
            requestPermissions(
                    new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                    REQ_BACKGROUND);
        } else {
            requestNotificationPermission();
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQ_NOTIF);
        } else {
            startTracking();
        }
    }

    private void startTracking() {
        Intent serviceIntent = new Intent(this, LocationService.class);
        startForegroundService(serviceIntent);
        statusText.setText("Durum: Takip aktif ✓");
        Toast.makeText(this, "Konum takibi başlatıldı", Toast.LENGTH_SHORT).show();
    }

    // --- Menü (başlıktaki ⋮ butonu) ---
    private void showMenu(android.view.View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        pm.getMenuInflater().inflate(R.menu.main_menu, pm.getMenu());
        pm.setOnMenuItemClickListener(this::handleMenu);
        pm.show();
    }

    private boolean handleMenu(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_copy_id) {
            String did = prefs.getString(Prefs.DEVICE_ID, "-");
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("device_id", did));
            Toast.makeText(this, "Cihaz ID kopyalandı: " + did, Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.menu_battery) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
            return true;
        } else if (id == R.id.menu_about) {
            new AlertDialog.Builder(this)
                    .setTitle("Kadobabacloud GPS")
                    .setMessage("Sürüm 1.0\n\nArka planda çalışan konum takip uygulaması. "
                            + "Konum, adım ve mesafe bilgisi Firebase üzerinden Admin Paneli'nde "
                            + "haritada görüntülenir.\n\nYalnızca sana ait ya da takip için açık "
                            + "izin aldığın cihazlarda kullan.")
                    .setPositiveButton("Tamam", null)
                    .show();
            return true;
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;

        switch (requestCode) {
            case REQ_LOCATION:
                if (granted) requestBackgroundLocation();
                else statusText.setText("Durum: Konum izni reddedildi");
                break;
            case REQ_BACKGROUND:
                requestNotificationPermission();
                break;
            case REQ_NOTIF:
                startTracking();
                break;
        }
    }
}
