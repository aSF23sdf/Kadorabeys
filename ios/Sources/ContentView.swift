import SwiftUI

struct ContentView: View {
    @StateObject private var tracker = LocationTracker.shared
    @State private var firebaseURL = Store.firebaseURL
    @State private var secret = Store.secret
    @State private var label = Store.label
    @State private var showAdmin = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Kadobabacloud GPS")
                    .font(.title2).bold()
                    .padding(.top, 8)

                field("Firebase veritabanı adresi",
                      text: $firebaseURL,
                      placeholder: "https://...firebasedatabase.app")
                field("Güvenlik anahtarı (opsiyonel)",
                      text: $secret,
                      placeholder: "boş bırakılabilir")
                field("Cihaz etiketi",
                      text: $label,
                      placeholder: "ör. Babamın telefonu")

                HStack(spacing: 8) {
                    Circle()
                        .fill(tracker.isTracking ? Color.green : Color.gray)
                        .frame(width: 10, height: 10)
                    Text(tracker.statusText).foregroundColor(.secondary)
                }
                Text("Son konum: \(tracker.lastCoord)")
                    .font(.footnote).foregroundColor(.secondary)

                if tracker.isTracking {
                    Button(action: stopTracking) { bigLabel("Takibi Durdur", color: .red) }
                } else {
                    Button(action: startTracking) { bigLabel("Takibi Başlat", color: .blue) }
                }

                Button(action: openAdmin) {
                    bigLabel("📍 Admin Paneli (cihazları gör)", color: .gray)
                }

                Text("Bu uygulamayı yalnızca sana ait ya da açık izin aldığın cihazlarda kullan. Uygulama arka planda çalışırken iOS bir konum göstergesi gösterir.")
                    .font(.caption2).foregroundColor(.secondary)
                    .padding(.top, 8)
            }
            .padding()
        }
        .sheet(isPresented: $showAdmin) {
            AdminView(firebaseURL: firebaseURL, secret: secret)
        }
    }

    // MARK: - Küçük yardımcılar

    private func field(_ title: String, text: Binding<String>, placeholder: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(.caption).foregroundColor(.secondary)
            TextField(placeholder, text: text)
                .autocapitalization(.none)
                .disableAutocorrection(true)
                .keyboardType(.URL)
                .padding(10)
                .background(Color(.secondarySystemBackground))
                .cornerRadius(8)
        }
    }

    private func bigLabel(_ t: String, color: Color) -> some View {
        Text(t)
            .frame(maxWidth: .infinity)
            .padding()
            .background(color)
            .foregroundColor(.white)
            .cornerRadius(10)
    }

    private func save() {
        Store.firebaseURL = firebaseURL.trimmingCharacters(in: .whitespacesAndNewlines)
        Store.secret = secret.trimmingCharacters(in: .whitespacesAndNewlines)
        Store.label = label.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func startTracking() { save(); tracker.start() }
    private func stopTracking() { tracker.stop() }
    private func openAdmin() { save(); showAdmin = true }
}
