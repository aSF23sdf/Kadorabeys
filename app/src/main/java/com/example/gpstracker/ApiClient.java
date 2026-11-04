package com.example.gpstracker;

import android.util.Log;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Konum verisini doğrudan Firebase Realtime Database'e (REST ile) yazar.
 * Firebase SDK / google-services.json gerektirmez — sadece veritabanı adresi yeter.
 *
 * Yazılan yer:  {FIREBASE_URL}/devices/{deviceId}.json
 * İçerik:       { "label": "...", "last": { lat, lng, ... } }
 */
public class ApiClient {

    private static final String TAG = "ApiClient";
    private final String baseUrl;   // https://proje-default-rtdb.firebaseio.com
    private final String secret;    // opsiyonel ?auth=
    private final String deviceId;
    private final String label;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final long HISTORY_MIN_MS = 4_000L; // geçmiş kaydı en sık bu aralıkla
    private long lastHistoryMs = 0;

    public ApiClient(String baseUrl, String secret, String deviceId, String label) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        this.secret = secret == null ? "" : secret.trim();
        this.deviceId = deviceId;
        this.label = label;
    }

    public void uploadAsync(double lat, double lng, float accuracy,
                            float speed, double altitude, int battery, long time,
                            long steps, double distanceM) {
        if (baseUrl.isEmpty()) {
            Log.w(TAG, "Firebase adresi ayarlanmamış, gönderim atlandı.");
            return;
        }
        executor.execute(() ->
                upload(lat, lng, accuracy, speed, altitude, battery, time, steps, distanceM));
    }

    private void upload(double lat, double lng, float accuracy,
                        float speed, double altitude, int battery, long time,
                        long steps, double distanceM) {
        try {
            JSONObject last = new JSONObject();
            last.put("lat", lat);
            last.put("lng", lng);
            last.put("accuracy", accuracy);
            last.put("speed", speed);
            last.put("altitude", altitude);
            last.put("battery", battery);
            last.put("time", time);
            last.put("steps", steps);
            last.put("distanceM", Math.round(distanceM));

            JSONObject node = new JSONObject();
            node.put("label", label != null ? label : deviceId);
            node.put("last", last);

            String auth = secret.isEmpty() ? "" : "?auth=" + enc(secret);

            // 1) Son konum: tüm cihaz düğümünü güncelle
            int code = send("PUT", baseUrl + "/devices/" + enc(deviceId) + ".json" + auth, node);
            Log.d(TAG, "Firebase yanıtı: " + code);

            // 2) Rota geçmişi: /history/{cihaz} altına yeni nokta ekle (POST = sıralı anahtar).
            //    "Son konum" her saniye güncellenir; geçmiş ise ~4 sn'de bir yazılır ki
            //    Firebase gereksiz şişmesin (canlı takip yine anlık kalır).
            if (time - lastHistoryMs >= HISTORY_MIN_MS) {
                lastHistoryMs = time;
                JSONObject point = new JSONObject();
                point.put("lat", lat);
                point.put("lng", lng);
                point.put("accuracy", accuracy);
                point.put("speed", speed);
                point.put("altitude", altitude);
                point.put("battery", battery);
                point.put("time", time);
                point.put("steps", steps);
                send("POST", baseUrl + "/history/" + enc(deviceId) + ".json" + auth, point);
            }
        } catch (Exception e) {
            // Ağ hatasında sessizce geç; veri yerel kuyrukta duruyor.
            Log.w(TAG, "Gönderim hatası: " + e.getMessage());
        }
    }

    private int send(String method, String urlStr, JSONObject body) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);
            conn.setRequestMethod(method);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            return conn.getResponseCode();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String enc(String s) throws Exception {
        return URLEncoder.encode(s, "UTF-8");
    }

    public void shutdown() {
        executor.shutdown();
    }
}
