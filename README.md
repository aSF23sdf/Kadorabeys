# Kadobabacloud GPS (Firebase sürümü)

Arka planda sürekli çalışan, pili minimal harcayan bir **Android konum takip uygulaması**.
Konumlar **Google'ın ücretsiz Firebase** veritabanına yazılır; cihazları **uygulamanın içindeki
admin panelinden** (giriş: `admin` / `12345`) haritada canlı görürsün.

**Kendi sunucun / VPS'in yok.** Ortadaki buluşma noktası Firebase'dir, onu da Google ücretsiz tutar.
Çok cihaz, mobil veriyle her yerden çalışır (WiFi şart değil).

> **Önemli / Yasal:** Bu sistemi yalnızca **sana ait** ya da **takip için açık izin aldığın**
> cihazlarda kullan. Birini rızası olmadan takip etmek çoğu ülkede suçtur.
> Uygulama çalışırken telefonda kalıcı bir bildirim gösterir (Android bunu zaten zorunlu kılar).

---

## Paketin içinde ne var?

```
gps-tracker/
├── app/                      → Android uygulaması (kaynak kod)
│   └── src/main/assets/admin_map.html  → uygulama içi harita paneli
├── gradle/ gradlew ...       → Derleme için hazır Gradle wrapper
└── .github/workflows/        → GitHub'da otomatik APK üretimi
```

---

## 1) Firebase'i Hazırla (bir kez, ~3 dakika, ücretsiz)

1. https://console.firebase.google.com adresine Google hesabınla gir.
2. **"Proje ekle"** → bir isim ver (ör. `kadobabacloud`) → oluştur.
3. Sol menüden **Build → Realtime Database** → **"Veritabanı oluştur"**.
   - Bölge seç (Avrupa için `europe-west1` iyidir).
   - Başlangıç modunda **"Test modunda başlat"**ı seç → Etkinleştir.
4. Açılan sayfanın üstündeki **veritabanı adresini** kopyala. Şuna benzer:
   ```
   https://kadobabacloud-default-rtdb.europe-west1.firebasedatabase.app
   ```
   (ya da `https://kadobabacloud-default-rtdb.firebaseio.com`)
   **Bu adresi uygulamaya gireceksin.**

> Test modu kuralları 30 gün açık kalır. Kalıcı ve güvenli hale getirmek için
> aşağıdaki **Güvenlik** bölümüne bak.

---

## 2) APK'yı Elde Et

### A Yolu — GitHub ile otomatik (bilgisayara kurulum yok) ✅
1. Ücretsiz bir [GitHub](https://github.com) hesabı aç, yeni bir repo oluştur.
2. Bu klasördeki tüm dosyaları repoya yükle (site üzerinden "Add file → Upload files").
3. Repoda **Actions → "APK Derle" → Run workflow**.
4. 2-3 dakikada biter; işin içinden **Artifacts → `kadobabacloud-gps-apk`**'yı indir.
5. Zip'ten `app-debug.apk` çıkar. 🎉

### B Yolu — Android Studio ile
1. [Android Studio](https://developer.android.com/studio) kur → bu klasörü **Open** ile aç.
2. **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
3. APK: `app/build/outputs/apk/debug/app-debug.apk`.

---

## 3) Telefona Kur
1. `app-debug.apk`'yı telefona at, dokun, "Bilinmeyen kaynak" uyarısını onayla.
2. **Kadobabacloud GPS**'i aç.

---

## 4) Takibi Başlat (takip edilecek telefonda)
1. **Firebase veritabanı adresi** kutusuna 1. adımdaki adresi yapıştır.
2. (Varsa) **Güvenlik anahtarı**nı gir — yoksa boş bırak.
3. **Cihaz etiketi** ver (ör. "Babamın telefonu").
4. **Takibi Başlat** → konum izninde **"Her zaman izin ver"** seç.

Artık telefon, konumunu WiFi ya da mobil veri üzerinden Firebase'e gönderir.

---

## 5) Cihazları Gör — Uygulama İçi Admin Paneli (profesyonel)
1. Aynı uygulamada (seninkinde) Firebase adresini gir.
2. **Admin Paneli — Cihazları Gör** → **admin / 12345** ile giriş.
3. Koyu temalı profesyonel panel açılır:
   - Tam ekran harita, çevrimiçi cihazlar için **nabız atan özel işaretçi**.
   - Altta cihaz kartları: her birinde **pil, adım, yürünen km, son görülme**.
   - Bir cihaza dokununca **detay ekranı**: adım, yürünen yol (km), batarya, hız (km/s),
     rakım (m), hassasiyet (m), tam koordinat ve son güncelleme zamanı — ikonlarıyla.

## Yeni özellikler
- **Adım sayısı:** Takip edilen telefonun adım sensöründen, takibe başladıktan sonraki adımlar.
- **Yürünen mesafe (km):** GPS noktalarından hesaplanan gerçek yol; GPS gürültüsü ayıklanır.
- **Profesyonel arayüz:** Yeni uygulama ikonu, koyu tema, kart tasarımı ve başlıkta **menü** (⋮):
  Cihaz ID kopyalama, pil optimizasyonu ayarlarına kısayol, Hakkında.
- **Detaylı harita:** Haritada ve kartlarda cihazın tüm bilgileri yazılı.

---

## 6) Uygulamayı Kapatsan Bile Çalışması İçin (ÖNEMLİ)
Uygulama; foreground service + otomatik yeniden başlatma + açılışta başlatma ile tasarlandı.
Bazı markalar pil için arka plan uygulamalarını yine de öldürür; **pil optimizasyonundan muaf tut**:

- **Genel:** Ayarlar → Uygulamalar → Kadobabacloud GPS → Pil → **"Kısıtlama yok"**
- **Xiaomi/Redmi:** İzinler → **Otomatik başlatma** aç; son uygulamalarda **kilitle**.
- **Samsung:** Ayarlar → Pil → **"Uyutulmayacak uygulamalar"**a ekle.
- **Huawei/Honor:** Pil → Uygulama başlatma → **Elle yönet** (hepsi açık).
- **Oppo/Realme/Vivo:** Arka plan + otomatik başlatma izinlerini aç.

---

## Güvenlik

Test modunda veritabanın adresini bilen herkes okuyabilir/yazabilir. Kilitlemek için:

1. Firebase Console → **Realtime Database → Kurallar** sekmesi, şunu yaz ve yayınla:
   ```json
   { "rules": { ".read": false, ".write": false } }
   ```
2. **Veritabanı gizli anahtarı** al: ⚙️ **Proje ayarları → Hizmet hesapları →
   Veritabanı gizli anahtarları** → bir anahtar oluştur/kopyala.
3. Bu anahtarı hem uygulamada **"Güvenlik anahtarı"** kutusuna hem de panelde kullanılacak
   şekilde gir (uygulama ikisini de aynı prefs'ten okur). Böylece yalnızca anahtarı bilen
   senin uygulaman erişebilir.

Ayrıca uygulama içi panelin **admin / 12345** girişini değiştir:
`app/src/main/java/com/example/gpstracker/AdminActivity.java`
```java
private static final String PANEL_USER = "admin";
private static final String PANEL_PASS = "12345"; // <-- değiştir
```

---

## Pil / Hassasiyet Ayarları
`app/src/main/java/com/example/gpstracker/LocationService.java` içinde:

| Ayar | Varsayılan | Açıklama |
|------|-----------|----------|
| `UPDATE_INTERVAL_MS` | 30 sn | Konum güncelleme aralığı. Büyüt → daha az pil/veri. |
| `MIN_DISPLACEMENT_M` | 10 m | Bu kadar hareket olmadan güncelleme alınmaz. |
| `PRIORITY_BALANCED_POWER_ACCURACY` | — | Ağ/WiFi konumunu da kullanır, pil dostu. Daha hassas: `PRIORITY_HIGH_ACCURACY`. |

---

## Teknik Özet
- **Dil:** Java (Android)
- **Veri yolu:** Firebase Realtime Database **REST** (SDK / google-services.json gerekmez)
- **Yazılan yer:** `/devices/{cihazId}` → `{ label, last:{lat,lng,accuracy,speed,altitude,battery,time,steps,distanceM} }`
- **Adım:** `Sensor.TYPE_STEP_COUNTER` — **Mesafe:** ardışık konumların haversine toplamı
- **Konum:** FusedLocationProvider, pil dengeli öncelik
- **Arka plan:** Foreground service + `START_STICKY` + boot receiver
- **Çevrimdışı:** Konumlar yerel `locations.log`'a da yazılır (veri kaybı olmaz)
- **Panel:** Uygulama içi WebView + Leaflet/OpenStreetMap (anahtarsız, ücretsiz)
- **minSdk:** 26 (Android 8.0) — **targetSdk:** 34 (Android 14)
