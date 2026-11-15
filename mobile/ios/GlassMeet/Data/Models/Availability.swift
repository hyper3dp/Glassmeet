import Foundation

/// public.availability_rules: one time range on a weekday (0 = Sunday). end_time may be 24:00.
struct AvailabilityRule: Decodable, Identifiable, Hashable {
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

/// public.availability_exceptions: a date off, blocked hours, or custom hours on one date
struct AvailabilityException: Decodable, Identifiable, Hashable {
    var id: UUID
    var date: String
    var type: String?
    var startTime: String?
    var endTime: String?
    var label: String?

    enum CodingKeys: String, CodingKey {
        case id, date, type, label
        case startTime = "start_time"
        case endTime = "end_time"
    }

    /// "Unavailable", "Blocked 9:00 AM – 12:00 PM", "10:00 AM – 2:00 PM"
    var summary: String {
        let range: String? = {
            guard let startTime, let endTime else { return nil }
            return "\(Fmt.clockLabel(Fmt.minutes(startTime))) – \(Fmt.clockLabel(Fmt.minutes(endTime)))"
        }()
        if type == "blocked" { return range.map { "Blocked \($0)" } ?? "Unavailable all day" }
        return range ?? "Available all day"
    }
}

/// public.holidays: a public holiday in a region
struct Holiday: Decodable, Hashable {
    var date: String
    var name: String
}

/// public.whatsapp_settings: the host's number and message templates
struct WhatsAppSettings: Decodable, Hashable {
    var id: UUID?
    var phoneNumber: String?
    var confirmationTemplate: String?
    var reminder24hTemplate: String?
    var reminder1hTemplate: String?

    enum CodingKeys: String, CodingKey {
        case id
        case phoneNumber = "phone_number"
        case confirmationTemplate = "confirmation_template"
        case reminder24hTemplate = "reminder_24h_template"
        case reminder1hTemplate = "reminder_1h_template"
    }
}

/// public.google_accounts: the host's Google Calendar connection
struct GoogleAccount: Decodable, Hashable {
    var email: String?
    var calendarID: String?
    var calendarName: String?
    var autoSync: Bool?
    var connected: Bool?
    var syncedDate: String?

    enum CodingKeys: String, CodingKey {
        case email, connected
        case calendarID = "calendar_id"
        case calendarName = "calendar_name"
        case autoSync = "auto_sync"
        case syncedDate = "synced_date"
    }

    var calendarLabel: String {
        if let calendarName, !calendarName.isEmpty { return calendarName }
        if calendarID == nil || calendarID == "primary" { return "Primary calendar" }
        return calendarID ?? "Primary calendar"
    }
}

/// public.connections: a Microsoft (Outlook, Teams) connection
struct ProviderConnection: Decodable, Hashable {
    var provider: String
    var email: String?
    var teams: Bool?
    var autoSync: Bool?
    var connected: Bool?
    var syncedDate: String?

    enum CodingKeys: String, CodingKey {
        case provider, email, teams, connected
        case autoSync = "auto_sync"
        case syncedDate = "synced_date"
    }
}

/// A calendar in the host's Google account (listCalendars)
struct GoogleCalendar: Identifiable, Hashable {
    var id: String
    var summary: String
    var primary: Bool
}
