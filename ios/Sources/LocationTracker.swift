import Foundation
import CoreLocation
import CoreMotion
import UIKit

/// Konumu toplar ve doğrudan Firebase Realtime Database'e (REST, PUT) yazar.
/// Android'deki LocationService + ApiClient karşılığı; aynı veri şemasını kullanır:
///   {FIREBASE}/devices/{deviceId}.json  ->  { "label": ..., "last": { lat, lng, ... } }
final class LocationTracker: NSObject, ObservableObject, CLLocationManagerDelegate {

    static let shared = LocationTracker()

    private let manager = CLLocationManager()
    private let pedometer = CMPedometer()
    private var lastLocation: CLLocation?
    private var currentSteps: Int = 0

    @Published var isTracking = false
    @Published var statusText = "Hazır"
    @Published var lastCoord = "—"

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyNearestTenMeters
        manager.distanceFilter = 10            // ~10 m (Android MIN_DISPLACEMENT_M ile uyumlu)
        manager.pausesLocationUpdatesAutomatically = false
        manager.activityType = .otherNavigation
        UIDevice.current.isBatteryMonitoringEnabled = true
        isTracking = Store.tracking
    }

    func start() {
        manager.requestAlwaysAuthorization()
        beginUpdates()
    }

    func stop() {
        manager.stopUpdatingLocation()
        manager.stopMonitoringSignificantLocationChanges()
        pedometer.stopUpdates()
        isTracking = false
        Store.tracking = false
        statusText = "Durduruldu"
    }

    private func beginUpdates() {
        guard CLLocationManager.locationServicesEnabled() else {
            statusText = "Konum servisleri kapalı"
            return
        }
        manager.allowsBackgroundLocationUpdates = true
        manager.showsBackgroundLocationIndicator = true
        manager.startUpdatingLocation()
        manager.startMonitoringSignificantLocationChanges()
        startPedometer()
        isTracking = true
        Store.tracking = true
        statusText = "Takip aktif"
    }

    private func startPedometer() {
        guard CMPedometer.isStepCountingAvailable() else { return }
        pedometer.startUpdates(from: Date()) { [weak self] data, _ in
            guard let self = self, let data = data else { return }
            self.currentSteps = data.numberOfSteps.intValue
        }
    }

    // MARK: - CLLocationManagerDelegate

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let loc = locations.last else { return }
        if let prev = lastLocation {
            let d = loc.distance(from: prev)
            if d.isFinite && d >= 0 { Store.totalDistance += d }
        }
        lastLocation = loc
        let coord = String(format: "%.5f, %.5f", loc.coordinate.latitude, loc.coordinate.longitude)
        DispatchQueue.main.async { self.lastCoord = coord }
        upload(loc)
    }

    func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus) {
        if status == .authorizedAlways || status == .authorizedWhenInUse {
            beginUpdates()
        }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        // Ağ/geçici hatalarda sessizce geç.
    }

    // MARK: - Upload

    private func batteryPercent() -> Int {
        let lvl = UIDevice.current.batteryLevel
        if lvl < 0 { return -1 }
        return Int((lvl * 100).rounded())
    }

    private func upload(_ loc: CLLocation) {
        let base = Store.firebaseURL
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "/+$", with: "", options: .regularExpression)
        guard !base.isEmpty else { return }

        let secret = Store.secret.trimmingCharacters(in: .whitespacesAndNewlines)
        let devId = Store.deviceId
        let label = Store.label.isEmpty ? devId : Store.label

        let last: [String: Any] = [
            "lat": loc.coordinate.latitude,
            "lng": loc.coordinate.longitude,
            "accuracy": loc.horizontalAccuracy,
            "speed": max(loc.speed, 0),
            "altitude": loc.altitude,
            "battery": batteryPercent(),
            "time": Int(Date().timeIntervalSince1970 * 1000),
            "steps": currentSteps,
            "distanceM": Int(Store.totalDistance.rounded())
        ]
        let node: [String: Any] = ["label": label, "last": last]

        let allowed = CharacterSet.alphanumerics
        let encId = devId.addingPercentEncoding(withAllowedCharacters: allowed) ?? devId
        var urlStr = base + "/devices/" + encId + ".json"
        if !secret.isEmpty {
            let encSec = secret.addingPercentEncoding(withAllowedCharacters: allowed) ?? secret
            urlStr += "?auth=" + encSec
        }

        guard let url = URL(string: urlStr),
              let body = try? JSONSerialization.data(withJSONObject: node) else { return }

        var req = URLRequest(url: url)
        req.httpMethod = "PUT"          // tüm cihaz düğümünü güncelle (Android ile aynı)
        req.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        req.httpBody = body
        req.timeoutInterval = 15
        URLSession.shared.dataTask(with: req).resume()
    }
}
