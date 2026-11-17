import Foundation
import Supabase

/// public.notifications: the bell on the web (new bookings, cancellations, requests, holidays).
/// Rows are made by database triggers; the host can only read them and mark them read.
struct AppNotification: Decodable, Identifiable, Hashable {
    var id: UUID
    var title: String?
    var message: String?
    /// A web path such as /bookings?id=<uuid>
    var link: String?
    var read: Bool?
    var createdDate: String?

    enum CodingKeys: String, CodingKey {
        case id, title, message, link, read
        case createdDate = "created_date"
    }

    var isRead: Bool { read ?? false }
    var date: Date? { Fmt.timestamp(createdDate) }

    /// What kind of change it is, from its words, for the icon
    var systemImage: String {
        let text = "\(title ?? "") \(message ?? "")".lowercased()
        if text.contains("cancel") || text.contains("declin") { return "calendar.badge.minus" }
        if text.contains("request") || text.contains("approv") { return "hourglass" }
        if text.contains("holiday") { return "sun.max" }
        if text.contains("book") { return "calendar.badge.plus" }
        return "bell"
    }
}

enum NotificationsAPI {
    /// The newest 50, like the web's list (it shows 20)
    static func list() async throws -> [AppNotification] {
        let rows: [Lossy<AppNotification>] = try await supabase.from("notifications")
            .select("id, title, message, link, read, created_date")
            .eq("created_by_id", value: API.uid())
            .order("created_date", ascending: false)
            .limit(50)
            .execute().value
        return rows.unwrapped()
    }

    static func markRead(_ ids: [UUID]) async throws {
        guard !ids.isEmpty else { return }
        try await supabase.from("notifications").update(["read": .bool(true)] as Row, returning: .minimal)
            .in("id", values: ids.map(API.key))
            .execute()
    }
}
