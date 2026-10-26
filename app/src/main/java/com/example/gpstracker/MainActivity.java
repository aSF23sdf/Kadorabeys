package com.example.gpstracker;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tek ekranlı kurulum arayüzü:
 *  - Sunucu (admin paneli) adresi girilir
 *  - Cihaza bir etiket verilir
 *  - İzinler istenir ve takip başlatılır/durdurulur
 */
public class MainActivity extends AppCompatActivity {

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

        // Cihaz kimliği yoksa üret
        String deviceId = prefs.getString(Prefs.DEVICE_ID, null);
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString().substring(0, 8);
            prefs.edit().putString(Prefs.DEVICE_ID, deviceId).apply();
        }
        deviceIdText.setText("Cihaz ID: " + deviceId);

        // Kayıtlı değerleri yükle
        serverInput.setText(prefs.getString(Prefs.FIREBASE_URL, ""));
        secretInput.setText(prefs.getString(Prefs.FIREBASE_SECRET, ""));
        labelInput.setText(prefs.getString(Prefs.DEVICE_LABEL, android.os.Build.MODEL));

        startBtn.setOnClickListener(v -> onStartClicked());
        stopBtn.setOnClickListener(v -> onStopClicked());
        adminBtn.setOnClickListener(v -> {
            // Firebase bilgilerini kaydet ki panel kullanabilsin
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

    // --- İzin akışı ---
    private void requestPermissionsThenStart() {
        List<String> needed = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
            needed.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), REQ_LOCATION);
        } else {
            requestBackgroundLocation();
        }
    }

    private void requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this,
                    "Lütfen 'Her zaman izin ver' seçeneğini seç",
                    Toast.LENGTH_LONG).show();
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                    REQ_BACKGROUND);
        } else {
            requestNotificationPermission();
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQ_NOTIF);
        } else {
            startTracking();
        }
    }

    private void startTracking() {
        Intent serviceIntent = new Intent(this, LocationService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        statusText.setText("Durum: Takip aktif ✓");
        Toast.makeText(this, "Konum takibi başlatıldı", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;

        switch (requestCode) {
            case REQ_LOCATION:
                if (granted) requestBackgroundLocation();
                else statusText.setText("Durum: Konum izni reddedildi");
                break;
            case REQ_BACKGROUND:
                requestNotificationPermission(); // izin verilmese de devam (ön planda çalışır)
                break;
            case REQ_NOTIF:
                startTracking();
                break;
        }
    }
}
