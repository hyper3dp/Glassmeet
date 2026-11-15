import SwiftUI
import UIKit

// MARK: - Colours (the web app's host tokens, light and dark)

extension Color {
    private static func dynamic(_ light: UInt32, _ dark: UInt32) -> Color {
        Color(uiColor: UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: dark) : UIColor(hex: light) })
    }
    /// Buttons, selection and the tab bar tint
    static let gmTeal = dynamic(0x2B7A75, 0x66ACA9)
    /// Coloured text and links
    static let gmLink = dynamic(0x24706B, 0x7FC4C0)
    /// The logo mark only
    static let gmBrand = Color(uiColor: UIColor(hex: 0x66ACA9))
    static let gmSoft = dynamic(0xE3F1F0, 0x1B3332)
    /// WhatsApp chat bubbles only
    static let gmBubble = dynamic(0xD9FDD3, 0x144D37)
}

extension UIColor {
    convenience init(hex: UInt32) {
        self.init(red: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255, blue: CGFloat(hex & 0xFF) / 255, alpha: 1)
    }
}

// MARK: - Dates

enum Format {
    static func day(_ date: Date) -> String {
        let cal = Calendar.current
        if cal.isDateInToday(date) { return "Today" }
        if cal.isDateInTomorrow(date) { return "Tomorrow" }
        if cal.isDateInYesterday(date) { return "Yesterday" }
        return date.formatted(.dateTime.weekday(.abbreviated).month(.abbreviated).day())
    }
    static func time(_ date: Date) -> String { date.formatted(date: .omitted, time: .shortened) }
    static func when(_ date: Date) -> String { "\(day(date)) at \(time(date))" }

    /// "09:00:00" → a Date today at 9:00, for time pickers
    static func clock(_ value: String) -> Date {
        let parts = value.split(separator: ":").compactMap { Int($0) }
        return Calendar.current.date(bySettingHour: parts.first ?? 9, minute: parts.count > 1 ? parts[1] : 0, second: 0, of: .now) ?? .now
    }
    /// A Date → "09:00"
    static func clockString(_ date: Date) -> String {
        let c = Calendar.current.dateComponents([.hour, .minute], from: date)
        return String(format: "%02d:%02d", c.hour ?? 0, c.minute ?? 0)
    }

    static func initials(_ name: String) -> String {
        let letters = name.split(separator: " ").prefix(2).compactMap { $0.first.map(String.init) }
        return letters.isEmpty ? "?" : letters.joined().uppercased()
    }
}

// MARK: - WhatsApp (ported from the web app's src/lib/whatsapp.js)

/// GlassMeet prepares the message; the host sends it from their own WhatsApp.
enum WhatsApp {
    static let confirmation = """
    Hi [Name]! Your meeting with [Host] is confirmed.

    Date: [Date]
    Time: [Time] ([Timezone])
    Duration: [Duration]
    Meeting: [Meeting Link]

    See you then!
    """
    static let cancellation = """
    Hi [Name], I'm sorry, I need to cancel our meeting on [Date] at [Time] ([Timezone]).

    You can pick a new time here: [Booking Link]
    """
    static let decline = """
    Hi [Name], thanks for your request for [Date] at [Time]. Unfortunately I can't make that time.

    Please pick another time here: [Booking Link]
    """

    static func link(phone: String?, message: String) -> URL {
        let digits = (phone ?? "").filter(\.isNumber)
        var parts = URLComponents(string: digits.isEmpty ? "https://wa.me/" : "https://wa.me/\(digits)")!
        parts.queryItems = [URLQueryItem(name: "text", value: message)]
        return parts.url!
    }

    static func fill(_ template: String, booking: Booking, eventType: EventType?, host: String) -> String {
        let zone = TimeZone(identifier: booking.timezone ?? "") ?? .current
        var date = Date.FormatStyle(date: .complete, time: .omitted); date.timeZone = zone
        var time = Date.FormatStyle(date: .omitted, time: .shortened); time.timeZone = zone
        let minutes = Int(booking.end.timeIntervalSince(booking.start) / 60)
        let values: [String: String] = [
            "[Name]": booking.guestName,
            "[Host]": host.isEmpty ? "your host" : host,
            "[Date]": booking.start.formatted(date),
            "[Time]": booking.start.formatted(time),
            "[Duration]": "\(eventType?.duration ?? minutes) min",
            "[Meeting Link]": booking.meetingURL ?? eventType?.meetingURL ?? "",
            "[Booking Link]": booking.eventTypeID.map { Backend.bookingURL($0).absoluteString } ?? "",
            "[Timezone]": zone.identifier,
        ]
        var text = template
        for (key, value) in values { text = text.replacingOccurrences(of: key, with: value) }
        // A line whose placeholder had no value ("Meeting: ") is left out rather than sent half empty
        return text.split(separator: "\n", omittingEmptySubsequences: false)
            .filter { line in
                guard let colon = line.firstIndex(of: ":"), line.distance(from: line.startIndex, to: colon) <= 20 else { return true }
                let rest = line[line.index(after: colon)...].trimmingCharacters(in: .whitespaces)
                return !(rest.isEmpty || rest == "()")
            }
            .joined(separator: "\n")
    }
}

// MARK: - Small shared views

struct Avatar: View {
    var name: String
    var size: CGFloat = 40
    var body: some View {
        Text(Format.initials(name))
            .font(.system(size: size * 0.38, weight: .semibold))
            .foregroundStyle(Color.gmLink)
            .frame(width: size, height: size)
            .background(Color.gmSoft, in: .circle)
            .accessibilityHidden(true)
    }
}

struct StatusBadge: View {
    var text: String
    var color: Color
    var body: some View {
        Text(text)
            .font(.caption.weight(.semibold))
            .foregroundStyle(color)
            .padding(.horizontal, 8).padding(.vertical, 2)
            .overlay(Capsule().strokeBorder(color, lineWidth: 1))
    }
}

/// Shows an error once, with a dismiss button.
struct ErrorBanner: ViewModifier {
    @Binding var message: String?
    func body(content: Content) -> some View {
        content.alert("Something went wrong", isPresented: Binding(get: { message != nil }, set: { if !$0 { message = nil } })) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(message ?? "")
        }
    }
}

extension View {
    func errorAlert(_ message: Binding<String?>) -> some View { modifier(ErrorBanner(message: message)) }
}

extension Error {
    var readable: String { (self as? LocalizedError)?.errorDescription ?? localizedDescription }
}
