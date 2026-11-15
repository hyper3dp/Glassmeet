import Foundation
import Supabase

/// The same Supabase project and publishable key as the web app. The key is public by design;
/// Row Level Security protects the data.
enum Backend {
    static let url = URL(string: "https://sjxraemrdpzleipfmwun.supabase.co")!
    static let key = "sb_publishable_0T8UhXmO8GUjyZa67yIU5w_RZwkPa4O"

    /// Guests book on the web: /book/<event type id>
    static let webOrigin = URL(string: "https://glassmeet.run.place")!
    static func bookingURL(_ eventTypeID: UUID) -> URL {
        webOrigin.appending(path: "book/\(eventTypeID.uuidString.lowercased())")
    }
    static func web(_ path: String) -> URL { webOrigin.appending(path: path) }
}

let supabase = SupabaseClient(
    supabaseURL: Backend.url,
    supabaseKey: Backend.key,
    options: SupabaseClientOptions(auth: .init(emitLocalSessionAsInitialSession: true))
)

// MARK: - Rows (the columns this app reads and writes; see supabase/migrations in the web app)

struct Profile: Codable, Hashable {
    var id: UUID
    var email: String?
    var fullName: String?
    enum CodingKeys: String, CodingKey { case id, email; case fullName = "full_name" }
}

struct EventType: Codable, Identifiable, Hashable {
    var id: UUID
    var name: String
    var description: String?
    var duration: Int
    var location: String?
    var meetingURL: String?
    var active: Bool
    var requiresApproval: Bool
    var color: String?
    enum CodingKeys: String, CodingKey {
        case id, name, description, duration, location, active, color
        case meetingURL = "meeting_url"
        case requiresApproval = "requires_approval"
    }
}

/// The fields the event type form saves. Empty optional fields are sent as null so they clear.
struct EventTypeInput: Encodable {
    var name: String
    var description: String?
    var duration: Int
    var location: String
    var meetingURL: String?
    var requiresApproval: Bool
    enum CodingKeys: String, CodingKey {
        case name, description, duration, location
        case meetingURL = "meeting_url"
        case requiresApproval = "requires_approval"
    }
    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(name, forKey: .name)
        try c.encode(description, forKey: .description)
        try c.encode(duration, forKey: .duration)
        try c.encode(location, forKey: .location)
        try c.encode(meetingURL, forKey: .meetingURL)
        try c.encode(requiresApproval, forKey: .requiresApproval)
    }
}

struct Booking: Codable, Identifiable, Hashable {
    var id: UUID
    var eventTypeID: UUID?
    var eventName: String?
    var guestName: String
    var guestEmail: String
    var guestPhone: String?
    var guestNotes: String?
    var startTime: String
    var endTime: String
    var timezone: String?
    var meetingURL: String?
    var status: String
    var approvalStatus: String
    enum CodingKeys: String, CodingKey {
        case id, timezone, status
        case eventTypeID = "event_type_id"
        case eventName = "event_name"
        case guestName = "guest_name"
        case guestEmail = "guest_email"
        case guestPhone = "guest_phone"
        case guestNotes = "guest_notes"
        case startTime = "start_time"
        case endTime = "end_time"
        case meetingURL = "meeting_url"
        case approvalStatus = "approval_status"
    }

    var start: Date { Timestamp.parse(startTime) }
    var end: Date { Timestamp.parse(endTime) }
    var isCancelled: Bool { status == "cancelled" }
    var isDeclined: Bool { approvalStatus == "declined" }
    var isPending: Bool { approvalStatus == "pending" && !isCancelled }
    func isOver(at now: Date = .now) -> Bool { end < now }
    func isUpcoming(at now: Date = .now) -> Bool { status == "confirmed" && approvalStatus == "approved" && !isOver(at: now) }
}

struct AvailabilityRule: Codable, Identifiable, Hashable {
    var id: UUID
    var dayOfWeek: Int
    var startTime: String
    var endTime: String
    enum CodingKeys: String, CodingKey {
        case id
        case dayOfWeek = "day_of_week"
        case startTime = "start_time"
        case endTime = "end_time"
    }
}

struct WhatsAppSettings: Codable, Hashable {
    var id: UUID
    var phoneNumber: String?
    var confirmationTemplate: String?
    enum CodingKeys: String, CodingKey {
        case id
        case phoneNumber = "phone_number"
        case confirmationTemplate = "confirmation_template"
    }
}

enum Timestamp {
    private static let plain: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter(); f.formatOptions = [.withInternetDateTime]; return f
    }()
    private static let fractional: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter(); f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]; return f
    }()
    static func parse(_ value: String) -> Date {
        fractional.date(from: value) ?? plain.date(from: value) ?? .distantPast
    }
}

// MARK: - Requests

enum API {
    struct SignedOut: LocalizedError { var errorDescription: String? { "You are signed out." } }

    static func userID() async throws -> UUID {
        guard let id = supabase.auth.currentUser?.id else { throw SignedOut() }
        return id
    }
    private static func owner() async throws -> String { try await userID().uuidString.lowercased() }

    // profile
    static func profile() async throws -> Profile? {
        let rows: [Profile] = try await supabase.from("profiles")
            .select("id, email, full_name").eq("id", value: owner()).execute().value
        return rows.first
    }
    static func updateName(_ name: String) async throws {
        try await supabase.from("profiles").update(["full_name": name]).eq("id", value: owner()).execute()
    }

    // event types
    static func eventTypes() async throws -> [EventType] {
        try await supabase.from("event_types").select()
            .eq("created_by_id", value: owner()).order("created_date", ascending: true).execute().value
    }
    static func eventType(_ id: UUID) async throws -> EventType {
        try await supabase.from("event_types").select().eq("id", value: id.uuidString.lowercased()).single().execute().value
    }
    static func createEventType(_ input: EventTypeInput) async throws {
        try await supabase.from("event_types").insert(input).execute()
    }
    static func updateEventType(_ id: UUID, _ input: EventTypeInput) async throws {
        try await supabase.from("event_types").update(input).eq("id", value: id.uuidString.lowercased()).execute()
    }
    static func setActive(_ id: UUID, _ active: Bool) async throws {
        try await supabase.from("event_types").update(["active": active]).eq("id", value: id.uuidString.lowercased()).execute()
    }
    static func deleteEventType(_ id: UUID) async throws {
        try await supabase.from("event_types").delete().eq("id", value: id.uuidString.lowercased()).execute()
    }

    // bookings
    static func bookings() async throws -> [Booking] {
        try await supabase.from("bookings").select()
            .eq("host_id", value: owner()).order("start_time", ascending: true).execute().value
    }
    static func booking(_ id: UUID) async throws -> Booking {
        try await supabase.from("bookings").select().eq("id", value: id.uuidString.lowercased()).single().execute().value
    }
    static func updateBooking(_ id: UUID, _ fields: [String: String]) async throws -> Booking {
        try await supabase.from("bookings").update(fields)
            .eq("id", value: id.uuidString.lowercased()).select().single().execute().value
    }

    // weekly hours
    static func availability() async throws -> [AvailabilityRule] {
        try await supabase.from("availability_rules").select("id, day_of_week, start_time, end_time")
            .eq("created_by_id", value: owner()).order("day_of_week").order("start_time").execute().value
    }
    struct NewRule: Encodable {
        var day_of_week: Int
        var start_time: String
        var end_time: String
    }
    /// Replaces one weekday's hours; nil means the day is off.
    static func setDay(_ day: Int, from: String?, to: String?) async throws {
        try await supabase.from("availability_rules").delete()
            .eq("created_by_id", value: owner()).eq("day_of_week", value: day).execute()
        if let from, let to {
            try await supabase.from("availability_rules").insert(NewRule(day_of_week: day, start_time: from, end_time: to)).execute()
        }
    }

    // WhatsApp
    static func whatsApp() async throws -> WhatsAppSettings? {
        let rows: [WhatsAppSettings] = try await supabase.from("whatsapp_settings")
            .select("id, phone_number, confirmation_template").eq("created_by_id", value: owner()).execute().value
        return rows.first
    }
    struct WhatsAppRow: Encodable {
        var created_by_id: String
        var phone_number: String?
        var confirmation_template: String?
        func encode(to encoder: Encoder) throws {
            var c = encoder.container(keyedBy: Keys.self)
            try c.encode(created_by_id, forKey: .created_by_id)
            try c.encode(phone_number, forKey: .phone_number)
            try c.encode(confirmation_template, forKey: .confirmation_template)
        }
        enum Keys: String, CodingKey { case created_by_id, phone_number, confirmation_template }
    }
    static func saveWhatsApp(phone: String?, template: String?) async throws {
        try await supabase.from("whatsapp_settings")
            .upsert(WhatsAppRow(created_by_id: owner(), phone_number: phone, confirmation_template: template), onConflict: "created_by_id")
            .execute()
    }
}
