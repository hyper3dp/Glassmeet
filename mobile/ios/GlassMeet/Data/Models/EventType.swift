import Foundation
import Supabase

/// public.event_types. Nullable or newer columns are optional; read them through the
/// computed values below, which apply the database defaults.
struct EventType: Decodable, Identifiable, Hashable {
    var id: UUID
    var name: String?
    var description: String?
    var note: String?
    var duration: Int?
    var location: String?
    var meetingURL: String?
    var address: String?
    var bookingType: String?
    var maxAttendees: Int?
    var active: Bool?
    var requiresApproval: Bool?
    var emailRequired: Bool?
    var color: String?
    var schedule: String?
    var freq: String?
    var every: Int?
    var days: [Int]?
    var startDate: String?
    var endDate: String?
    var dates: [String]?
    var fixedTimes: [String]?
    var maxFutureDays: Int?
    var minNotice: Int?
    var maxPerDay: Int?
    var bufferBefore: Int?
    var bufferAfter: Int?
    /// jsonb, read leniently (see `questions`)
    var customQuestionsJSON: AnyJSON?
    var createdDate: String?

    enum CodingKeys: String, CodingKey {
        case id, name, description, note, duration, location, address, active, color, schedule, freq, every, days, dates
        case meetingURL = "meeting_url"
        case bookingType = "booking_type"
        case maxAttendees = "max_attendees"
        case requiresApproval = "requires_approval"
        case emailRequired = "email_required"
        case startDate = "start_date"
        case endDate = "end_date"
        case fixedTimes = "fixed_times"
        case maxFutureDays = "max_future_days"
        case minNotice = "min_notice"
        case maxPerDay = "max_per_day"
        case bufferBefore = "buffer_before"
        case bufferAfter = "buffer_after"
        case customQuestionsJSON = "custom_questions"
        case createdDate = "created_date"
    }

    var title: String { (name?.isEmpty == false ? name : nil) ?? "Untitled event" }
    var minutes: Int { duration ?? 30 }
    var isActive: Bool { active ?? true }
    var needsApproval: Bool { requiresApproval ?? false }
    var isGroup: Bool { bookingType == "group" }
    var questions: [CustomQuestion] { CustomQuestion.list(from: customQuestionsJSON) }

    /// "30 min · Google Meet · Needs approval"
    var summary: String {
        var parts = [Fmt.duration(minutes)]
        if let location, !location.isEmpty { parts.append(location) }
        if isGroup { parts.append("Group of \(maxAttendees ?? 2)") }
        if needsApproval { parts.append("Needs approval") }
        return parts.joined(separator: " · ")
    }

    /// An event type made from a template's or a copy's fields (a new id; nothing is saved).
    static func prefill(_ data: [String: AnyJSON]) -> EventType? {
        var object = data
        object["id"] = .string(UUID().uuidString)
        guard let bytes = try? JSONEncoder().encode(AnyJSON.object(object)) else { return nil }
        return try? JSONDecoder().decode(EventType.self, from: bytes)
    }
}

/// One invitee question on the booking form (event_types.custom_questions)
struct CustomQuestion: Hashable {
    enum Kind: String, CaseIterable, Identifiable {
        case text, longText = "long_text", dropdown, multipleChoice = "multiple_choice"
        var id: String { rawValue }
        var label: String {
            switch self {
            case .text: "Short text"
            case .longText: "Long text"
            case .dropdown: "Dropdown"
            case .multipleChoice: "Multiple choice"
            }
        }
        var hasOptions: Bool { self == .dropdown || self == .multipleChoice }
    }

    var text: String
    var kind: Kind
    var required: Bool
    var options: [String]

    static func list(from value: AnyJSON?) -> [CustomQuestion] {
        guard let items = value?.arrayValue else { return [] }
        return items.compactMap { item in
            guard let object = item.objectValue else {
                // a bare string is a short text question
                return item.stringValue.map { CustomQuestion(text: $0, kind: .text, required: false, options: []) }
            }
            let text = object["text"]?.text ?? object["question"]?.text ?? object["label"]?.text ?? ""
            let kind = Kind(rawValue: object["type"]?.stringValue ?? "") ?? .text
            let options = object["options"]?.arrayValue?.compactMap(\.text) ?? []
            return CustomQuestion(text: text, kind: kind, required: object["required"]?.flag ?? false, options: options)
        }
    }

    var jsonValue: AnyJSON {
        var object: [String: AnyJSON] = ["text": .string(text), "type": .string(kind.rawValue), "required": .bool(required)]
        if kind.hasOptions { object["options"] = .array(options.map { AnyJSON.string($0) }) }
        return .object(object)
    }
}

/// public.event_templates: a host's saved starting points ({ name, data })
struct EventTemplate: Decodable, Identifiable, Hashable {
    var id: UUID
    var name: String?
    var data: AnyJSON?

    var fields: [String: AnyJSON] { data?.objectValue ?? [:] }
}

/// A starting point for a new event type: built in (src/lib/templates.js) or the host's own.
struct TemplateChoice: Identifiable, Hashable {
    var id: String
    var name: String
    var data: [String: AnyJSON]
    var savedID: UUID?

    /// "30 min · Google Meet · Weekly"
    var summary: String {
        var parts = [Fmt.duration(data["duration"]?.number ?? 30), data["location"]?.stringValue ?? "Google Meet"]
        if data["booking_type"]?.stringValue == "group" { parts.append("Group of \(data["max_attendees"]?.number ?? 2)") }
        let datesSchedule = data["schedule"]?.stringValue == "dates"
        if !datesSchedule, data["freq"]?.stringValue == "month" { parts.append("Monthly") }
        else if !datesSchedule, !(data["days"]?.arrayValue ?? []).isEmpty { parts.append("Weekly") }
        return parts.joined(separator: " · ")
    }

    static let builtIn: [TemplateChoice] = [
        TemplateChoice(id: "quick-chat", name: "Quick chat", data: [
            "name": "Quick chat", "duration": 15, "location": "Google Meet",
            "description": "A short call to say hello or answer a quick question.",
        ]),
        TemplateChoice(id: "meeting", name: "30 minute meeting", data: [
            "name": "30 minute meeting", "duration": 30, "location": "Google Meet",
        ]),
        TemplateChoice(id: "consultation", name: "Consultation", data: [
            "name": "Consultation", "duration": 60, "location": "Zoom", "requires_approval": true,
            "description": "A full hour to go through your needs. I confirm each request personally.",
        ]),
        TemplateChoice(id: "whatsapp-call", name: "WhatsApp call", data: [
            "name": "WhatsApp call", "duration": 30, "location": "WhatsApp call",
        ]),
        TemplateChoice(id: "in-person", name: "In-person meeting", data: [
            "name": "In-person meeting", "duration": 60, "location": "In person",
        ]),
        TemplateChoice(id: "office-hours", name: "Office hours", data: [
            "name": "Office hours", "duration": 15, "location": "Google Meet", "freq": "week", "every": 1, "days": [2, 4],
            "description": "Drop-in slots on Tuesdays and Thursdays.",
        ]),
        TemplateChoice(id: "group-class", name: "Weekly class", data: [
            "name": "Weekly class", "duration": 60, "location": "Zoom", "booking_type": "group", "max_attendees": 10,
            "freq": "week", "every": 1, "days": [1], "description": "A group session every Monday. Book a spot.",
        ]),
        TemplateChoice(id: "monthly-review", name: "Monthly check-in", data: [
            "name": "Monthly check-in", "duration": 45, "location": "Google Meet", "freq": "month", "every": 1,
            "description": "One session a month to review progress.",
        ]),
    ]

    /// The fields a template keeps (src/lib/templates.js TEMPLATE_FIELDS)
    static let fields = [
        "name", "description", "duration", "location", "meeting_url", "address",
        "booking_type", "max_attendees", "requires_approval", "email_required",
        "schedule", "freq", "every", "days", "fixed_times",
        "note", "max_future_days", "min_notice", "max_per_day", "buffer_before", "buffer_after", "custom_questions",
    ]
}
