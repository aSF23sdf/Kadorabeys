import Foundation
import UIKit

/// Ayarların kalıcı saklandığı yer (Android'deki SharedPreferences karşılığı).
enum Store {
    private static let d = UserDefaults.standard

    static var firebaseURL: String {
        get { d.string(forKey: "firebase_url") ?? "" }
        set { d.set(newValue, forKey: "firebase_url") }
    }
    static var secret: String {
        get { d.string(forKey: "firebase_secret") ?? "" }
        set { d.set(newValue, forKey: "firebase_secret") }
    }
    static var label: String {
        get { d.string(forKey: "device_label") ?? "" }
        set { d.set(newValue, forKey: "device_label") }
    }
    static var totalDistance: Double {
        get { d.double(forKey: "total_distance_m") }
        set { d.set(newValue, forKey: "total_distance_m") }
    }
    static var tracking: Bool {
        get { d.bool(forKey: "tracking_on") }
        set { d.set(newValue, forKey: "tracking_on") }
    }

    /// Cihaz için kalıcı benzersiz kimlik.
    static var deviceId: String {
        if let id = d.string(forKey: "device_id"), !id.isEmpty { return id }
        let id = UIDevice.current.identifierForVendor?.uuidString ?? UUID().uuidString
        d.set(id, forKey: "device_id")
        return id
    }
}
