import Foundation

/// WhatsApp messages, ported from the web app's src/lib/whatsapp.js. GlassMeet prepares the
/// message and opens WhatsApp; the host sends it.
enum Messages {
    enum Kind: String, CaseIterable, Identifiable {
        case confirmation, reminder24h, reminder1h, cancellation, decline
        var id: String { rawValue }
        var label: String {
            switch self {
            case .confirmation: "Confirmation"
            case .reminder24h: "Reminder 24h"
            case .reminder1h: "Reminder 1h"
            case .cancellation: "Cancellation"
            case .decline: "Decline"
            }
        }

        /// The message that fits what last happened to a meeting
        static func suggested(for booking: Booking) -> Kind {
            if booking.isDeclined { return .decline }
            if booking.isCancelled { return .cancellation }
            return .confirmation
        }
    }

    static let placeholders = ["[Name]", "[Host]", "[Date]", "[Time]", "[Duration]", "[Meeting Link]", "[Booking Link]", "[Timezone]"]

    static let confirmation = """
    Hi [Name]! Your meeting with [Host] is confirmed.

    Date: [Date]
    Time: [Time] ([Timezone])
    Duration: [Duration]
    Meeting: [Meeting Link]

    See you then!
    """
    static let reminder24h = """
    Reminder: Your meeting with [Host] is tomorrow at [Time] ([Timezone]).

    Meeting: [Meeting Link]
    """
    static let reminder1h = """
    Starting soon: Your meeting with [Host] is in 1 hour.

    Meeting: [Meeting Link]
    """
    static let cancellation = """
    Hi [Name], I'm sorry, I need to cancel our meeting on [Date] at [Time] ([Timezone]).

    You can pick a new time here: [Booking Link]
    """
    static let decline = """
    Hi [Name], thanks for your request for [Date] at [Time]. Unfortunately I can't make that time.

    Please pick another time here: [Booking Link]
    """

    static func template(_ kind: Kind, settings: WhatsAppSettings?) -> String {
        func pick(_ saved: String?, _ fallback: String) -> String {
            guard let saved, !saved.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return fallback }
            return saved
        }
        switch kind {
        case .confirmation: return pick(settings?.confirmationTemplate, confirmation)
        case .reminder24h: return pick(settings?.reminder24hTemplate, reminder24h)
        case .reminder1h: return pick(settings?.reminder1hTemplate, reminder1h)
        case .cancellation: return cancellation
        case .decline: return decline
        }
    }

    struct Context {
        var guestName: String
        var hostName: String
        var date: String
        var time: String
        var duration: String
        var meetingLink: String
        var bookingLink: String
        var timezone: String

        init(guestName: String, hostName: String, date: String, time: String, duration: String, meetingLink: String, bookingLink: String, timezone: String) {
            self.guestName = guestName
            self.hostName = hostName
            self.date = date
            self.time = time
            self.duration = duration
            self.meetingLink = meetingLink
            self.bookingLink = bookingLink
            self.timezone = timezone
        }

        init(booking: Booking, hostName: String, zone: TimeZone) {
            self.init(
                guestName: booking.guestName ?? "",
                hostName: hostName.isEmpty ? "Your host" : hostName,
                date: Fmt.longDate(booking.start, zone),
                time: Fmt.time(booking.start, zone),
                duration: Fmt.duration(booking.lengthMinutes > 0 ? booking.lengthMinutes : 30),
                meetingLink: booking.link ?? "",
                bookingLink: booking.eventTypeID.map { Backend.bookingURL($0).absoluteString } ?? "",
                timezone: zone.identifier
            )
        }

        /// What the template editor previews
        static func sample(hostName: String, zone: TimeZone) -> Context {
            let start = Calendar.current.date(byAdding: .day, value: 1, to: Calendar.current.date(bySettingHour: 14, minute: 30, second: 0, of: .now) ?? .now) ?? .now
            return Context(guestName: "Sara Ahmed", hostName: hostName.isEmpty ? "Your host" : hostName,
                           date: Fmt.longDate(start, zone), time: Fmt.time(start, zone), duration: "30 min",
                           meetingLink: "https://meet.google.com/abc-defg-hij",
                           bookingLink: Backend.web("book/example").absoluteString, timezone: zone.identifier)
        }
    }

    /// Fills the placeholders. A line whose placeholder had no value ("Meeting: ") is left out
    /// rather than sent half empty.
    static func fill(_ template: String, _ ctx: Context) -> String {
        let values: [(String, String)] = [
            ("[Name]", ctx.guestName), ("[Host]", ctx.hostName), ("[Date]", ctx.date), ("[Time]", ctx.time),
            ("[Duration]", ctx.duration), ("[Meeting Link]", ctx.meetingLink), ("[Booking Link]", ctx.bookingLink),
            ("[Timezone]", ctx.timezone),
        ]
        var text = template
        for (key, value) in values { text = text.replacingOccurrences(of: key, with: value) }
        return text.components(separatedBy: "\n").filter { line in
            guard let colon = line.firstIndex(of: ":") else { return true }
            let label = line[..<colon]
            guard !label.isEmpty, label.count <= 20 else { return true }
            let rest = line[line.index(after: colon)...].trimmingCharacters(in: .whitespaces)
            return !(rest.isEmpty || rest.replacingOccurrences(of: " ", with: "") == "()")
        }.joined(separator: "\n")
    }

    /// wa.me link with the message filled in; no number opens WhatsApp's chat picker
    static func whatsAppURL(phone: String?, message: String) -> URL {
        let digits = (phone ?? "").filter(\.isNumber)
        var parts = URLComponents(string: digits.isEmpty ? "https://wa.me/" : "https://wa.me/\(digits)")!
        if !message.isEmpty { parts.queryItems = [URLQueryItem(name: "text", value: message)] }
        return parts.url ?? URL(string: "https://wa.me/")!
    }

    static func mailURL(to email: String, subject: String, body: String) -> URL? {
        var parts = URLComponents()
        parts.scheme = "mailto"
        parts.path = email
        parts.queryItems = [URLQueryItem(name: "subject", value: subject), URLQueryItem(name: "body", value: body)]
        return parts.url
    }

    /// After cancelling a meeting that falls on a holiday (src/lib/holidays.js)
    static func reschedule(_ booking: Booking, holiday: String, zone: TimeZone) -> String {
        let first = booking.guestName.map { $0.split(separator: " ").first.map(String.init) ?? "" } ?? ""
        let link = booking.eventTypeID.map { Backend.bookingURL($0).absoluteString } ?? Backend.webOrigin.absoluteString
        let abbreviation = zone.abbreviation(for: booking.start).map { " (\($0))" } ?? ""
        return """
        Hi\(first.isEmpty ? "" : " \(first)"), I'm sorry, I need to reschedule our meeting on \(Fmt.longDate(booking.start, zone)) at \(Fmt.time(booking.start, zone))\(abbreviation) because of \(holiday).

        Please pick a new time that suits you here: \(link)
        """
    }
}
