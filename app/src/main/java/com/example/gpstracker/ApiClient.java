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

    public ApiClient(String baseUrl, String secret, String deviceId, String label) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        this.secret = secret == null ? "" : secret.trim();
        this.deviceId = deviceId;
        this.label = label;
    }

    public void uploadAsync(double lat, double lng, float accuracy,
                            float speed, double altitude, int battery, long time) {
        if (baseUrl.isEmpty()) {
            Log.w(TAG, "Firebase adresi ayarlanmamış, gönderim atlandı.");
            return;
        }
        executor.execute(() -> upload(lat, lng, accuracy, speed, altitude, battery, time));
    }

    private void upload(double lat, double lng, float accuracy,
                        float speed, double altitude, int battery, long time) {
        HttpURLConnection conn = null;
        try {
            JSONObject last = new JSONObject();
            last.put("lat", lat);
            last.put("lng", lng);
            last.put("accuracy", accuracy);
            last.put("speed", speed);
            last.put("altitude", altitude);
            last.put("battery", battery);
            last.put("time", time);

            JSONObject node = new JSONObject();
            node.put("label", label != null ? label : deviceId);
            node.put("last", last);

            String urlStr = baseUrl + "/devices/" + enc(deviceId) + ".json";
            if (!secret.isEmpty()) urlStr += "?auth=" + enc(secret);

            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);
            conn.setRequestMethod("PUT"); // tüm cihaz düğümünü güncelle
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

            byte[] out = node.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(out);
            }

            int code = conn.getResponseCode();
            Log.d(TAG, "Firebase yanıtı: " + code);
        } catch (Exception e) {
            // Ağ hatasında sessizce geç; veri yerel kuyrukta duruyor.
            Log.w(TAG, "Gönderim hatası: " + e.getMessage());
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
