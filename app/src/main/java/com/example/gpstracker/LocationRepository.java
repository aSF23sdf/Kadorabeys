package com.example.gpstracker;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Konumları cihaz üzerinde yerel olarak saklayan basit depo.
 * Her konum, uygulamanın özel dizinindeki locations.log dosyasına
 * JSON satırı olarak eklenir (ağ olmasa bile veri kaybolmaz).
 */
public class LocationRepository {

    private static final String TAG = "LocationRepository";
    private static final String FILE_NAME = "locations.log";
    private final File logFile;

    public LocationRepository(Context context) {
        logFile = new File(context.getFilesDir(), FILE_NAME);
    }

    public synchronized void saveLocation(double lat, double lng, float accuracy,
                                          float speed, double altitude, long time) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("lat", lat);
            obj.put("lng", lng);
            obj.put("accuracy", accuracy);
            obj.put("speed", speed);
            obj.put("altitude", altitude);
            obj.put("time", time);

            try (FileWriter fw = new FileWriter(logFile, true)) {
                fw.append(obj.toString()).append("\n");
            }
        } catch (IOException | org.json.JSONException e) {
            Log.e(TAG, "Yerel kayıt hatası: " + e.getMessage());
        }
    }

    public File getLogFile() {
        return logFile;
    }
}
