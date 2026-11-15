import Foundation

/// public.profiles. Every column but id may be null (or missing on an older database).
struct Profile: Decodable, Hashable {
    var id: UUID
    var email: String?
    var fullName: String?
    var profilePhoto: String?
    var timezone: String?
    var username: String?
    var whatsappNumber: String?
    var bio: String?
    /// Public holiday region blocked on booking pages ("ae"), or nil
    var holidays: String?
    /// The most meetings a day across all event types (1-100), or nil for no limit
    var maxPerDay: Int?
    var hoursSeeded: Bool?

    enum CodingKeys: String, CodingKey {
        case id, email, timezone, username, bio, holidays
        case fullName = "full_name"
        case profilePhoto = "profile_photo"
        case whatsappNumber = "whatsapp_number"
        case maxPerDay = "max_per_day"
        case hoursSeeded = "hours_seeded"
    }

    var photoURL: URL? { profilePhoto.flatMap { $0.isEmpty ? nil : URL(string: $0) } }
}

/// public.addresses: places a host meets guests
struct Address: Decodable, Identifiable, Hashable {
    var id: UUID
    var label: String?
    var address: String?

    /// "Office: 12 Main St, Dubai"
    var title: String {
        let line = (address ?? "").replacingOccurrences(of: "\n", with: ", ")
        if let label, !label.isEmpty { return "\(label): \(line)" }
        return line
    }
}
