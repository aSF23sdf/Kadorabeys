package com.example.gpstracker;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Uygulama içi Admin Paneli.
 *  - admin / 12345 ile giriş (uygulama içinde kontrol edilir)
 *  - Giriş sonrası, assets içindeki harita sayfasını WebView'de açar
 *    ve sunucudan cihazları çekip haritada gösterir.
 */
public class AdminActivity extends AppCompatActivity {

    // Panel girişi (istersen burayı değiştir)
    private static final String PANEL_USER = "admin";
    private static final String PANEL_PASS = "12345";

    private WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        webView = findViewById(R.id.webview);
        EditText user = findViewById(R.id.adminUser);
        EditText pass = findViewById(R.id.adminPass);
        TextView err = findViewById(R.id.adminErr);
        Button loginBtn = findViewById(R.id.adminLoginBtn);

        // WebView ayarları
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        // file:// sayfasının uzaktaki sunucuya erişebilmesi için
        webView.getSettings().setAllowUniversalAccessFromFileURLs(true);
        webView.getSettings().setAllowFileAccessFromFileURLs(true);
        webView.setWebViewClient(new WebViewClient());

        loginBtn.setOnClickListener(v -> {
            String u = user.getText().toString().trim();
            String p = pass.getText().toString();
            if (u.equals(PANEL_USER) && p.equals(PANEL_PASS)) {
                err.setText("");
                openPanel();
            } else {
                err.setText("Kullanıcı adı veya şifre hatalı");
            }
        });
    }

    private void openPanel() {
        SharedPreferences prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE);
        String firebase = prefs.getString(Prefs.FIREBASE_URL, "");
        String secret = prefs.getString(Prefs.FIREBASE_SECRET, "");
        if (firebase.isEmpty()) {
            Toast.makeText(this,
                    "Önce ana ekrandan Firebase adresini kaydet",
                    Toast.LENGTH_LONG).show();
            return;
        }

        String url = "file:///android_asset/admin_map.html"
                + "?firebase=" + Uri.encode(firebase)
                + "&secret=" + Uri.encode(secret);

        findViewById(R.id.loginBox).setVisibility(android.view.View.GONE);
        webView.setVisibility(android.view.View.VISIBLE);
        webView.loadUrl(url);
    }

    @Override
    public void onBackPressed() {
        // Harita açıkken geri tuşu önce giriş ekranına döndürsün
        if (webView.getVisibility() == android.view.View.VISIBLE) {
            webView.setVisibility(android.view.View.GONE);
            webView.loadUrl("about:blank");
            findViewById(R.id.loginBox).setVisibility(android.view.View.VISIBLE);
        } else {
            super.onBackPressed();
        }
    }
}
