package com.example.gpstracker;

/** SharedPreferences anahtar sabitleri. */
public final class Prefs {
    public static final String NAME = "kadobabacloud_prefs";
    // Firebase Realtime Database adresi, ör:
    //   https://PROJE-default-rtdb.firebaseio.com
    public static final String FIREBASE_URL = "firebase_url";
    // İsteğe bağlı güvenlik anahtarı (database secret). Boş olabilir.
    public static final String FIREBASE_SECRET = "firebase_secret";
    public static final String DEVICE_ID = "device_id";
    public static final String DEVICE_LABEL = "device_label";

    private Prefs() {}
}
