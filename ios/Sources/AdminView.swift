import SwiftUI
import WebKit

/// Uygulama içi admin paneli: admin / 12345 girişi, sonra gömülü harita (admin_map.html).
struct AdminView: View {
    let firebaseURL: String
    let secret: String

    @Environment(\.presentationMode) private var presentationMode
    @State private var loggedIn = false
    @State private var user = ""
    @State private var pass = ""
    @State private var err = ""

    var body: some View {
        if loggedIn {
            WebPanel(firebaseURL: firebaseURL, secret: secret)
                .edgesIgnoringSafeArea(.all)
        } else {
            VStack(spacing: 14) {
                Text("Admin Paneli").font(.title3).bold()
                TextField("Kullanıcı adı", text: $user)
                    .textFieldStyle(.roundedBorder)
                    .autocapitalization(.none)
                    .disableAutocorrection(true)
                SecureField("Şifre", text: $pass)
                    .textFieldStyle(.roundedBorder)
                if !err.isEmpty {
                    Text(err).foregroundColor(.red).font(.caption)
                }
                Button("Giriş") { login() }
                    .buttonStyle(.borderedProminent)
                Button("Kapat") { presentationMode.wrappedValue.dismiss() }
                    .font(.caption)
                Text("Varsayılan: admin / 12345 (kaynaktan değiştir)")
                    .font(.caption2).foregroundColor(.secondary)
            }
            .padding()
        }
    }

    private func login() {
        if user == "admin" && pass == "12345" {
            err = ""
            loggedIn = true
        } else {
            err = "Kullanıcı adı veya şifre hatalı"
        }
    }
}

/// admin_map.html'i WKWebView'de açar; Firebase adresi/anahtarını sayfaya enjekte eder.
struct WebPanel: UIViewRepresentable {
    let firebaseURL: String
    let secret: String

    func makeUIView(context: Context) -> WKWebView {
        let wv = WKWebView(frame: .zero, configuration: WKWebViewConfiguration())
        loadPanel(into: wv)
        return wv
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}

    private func loadPanel(into wv: WKWebView) {
        let base = firebaseURL
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "/+$", with: "", options: .regularExpression)

        guard let htmlURL = Bundle.main.url(forResource: "admin_map", withExtension: "html"),
              var html = try? String(contentsOf: htmlURL, encoding: .utf8) else { return }

        let allowed = CharacterSet.alphanumerics
        let encFb = base.addingPercentEncoding(withAllowedCharacters: allowed) ?? base
        let encSec = secret.addingPercentEncoding(withAllowedCharacters: allowed) ?? secret

        // Sayfa ?firebase=..&secret=.. query'sini okur; loadHTMLString query taşımadığı için
        // en başta history.replaceState ile aynı origin altında ekliyoruz.
        let inject = "<script>try{history.replaceState(null,'',location.pathname+'?firebase=\(encFb)&secret=\(encSec)');}catch(e){}</script>"
        if let headRange = html.range(of: "<head>") {
            html.replaceSubrange(headRange, with: "<head>" + inject)
        } else {
            html = inject + html
        }

        // baseURL = Firebase kökü → sayfanın origin'i Firebase ile aynı olur,
        // böylece /devices.json fetch'i CORS'a takılmaz.
        let baseURL = URL(string: base.isEmpty ? "https://localhost/" : base + "/")
        wv.loadHTMLString(html, baseURL: baseURL)
    }
}
