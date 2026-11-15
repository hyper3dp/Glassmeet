import Foundation

/// Dates, times and durations as GlassMeet writes them. Meeting times show in the host's time
/// zone (profiles.timezone), like the web app.
enum Fmt {
    // MARK: Timestamps from the database

    private static let fractional: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter(); f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]; return f
    }()
    private static let plain: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter(); f.formatOptions = [.withInternetDateTime]; return f
    }()

    /// "2026-11-18T14:30:00.123456+00:00" (any number of fraction digits, or none)
    static func timestamp(_ value: String?) -> Date? {
        guard let value, !value.isEmpty else { return nil }
        if let d = fractional.date(from: value) ?? plain.date(from: value) { return d }
        // Postgres may send microseconds, which the formatter doesn't read: drop the fraction
        if let dot = value.firstIndex(of: "."), let end = value[dot...].firstIndex(where: { $0 == "+" || $0 == "-" || $0 == "Z" }) {
            var trimmed = value
            trimmed.removeSubrange(dot..<end)
            return plain.date(from: trimmed)
        }
        return nil
    }

    static func iso(_ date: Date) -> String { plain.string(from: date) }

    // MARK: Calendars

    static func calendar(_ zone: TimeZone) -> Calendar {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = zone
        cal.locale = Locale.current
        return cal
    }

    // MARK: Meeting times

    static func time(_ date: Date, _ zone: TimeZone) -> String {
        var style = Date.FormatStyle(date: .omitted, time: .shortened)
        style.timeZone = zone
        return date.formatted(style)
    }

    static func timeRange(_ start: Date, _ end: Date, _ zone: TimeZone) -> String {
        "\(time(start, zone)) – \(time(end, zone))"
    }

    /// "Today", "Tomorrow", "Yesterday" or "Wed, Nov 18"
    static func dayTitle(_ date: Date, _ zone: TimeZone, now: Date = .now) -> String {
        let cal = calendar(zone)
        if cal.isDate(date, inSameDayAs: now) { return "Today" }
        if let tomorrow = cal.date(byAdding: .day, value: 1, to: now), cal.isDate(date, inSameDayAs: tomorrow) { return "Tomorrow" }
        if let yesterday = cal.date(byAdding: .day, value: -1, to: now), cal.isDate(date, inSameDayAs: yesterday) { return "Yesterday" }
        var style = Date.FormatStyle().weekday(.abbreviated).month(.abbreviated).day()
        if !cal.isDate(date, equalTo: now, toGranularity: .year) { style = style.year() }
        style.timeZone = zone
        return date.formatted(style)
    }

    /// "Today at 2:30 PM"
    static func when(_ date: Date, _ zone: TimeZone) -> String { "\(dayTitle(date, zone)) at \(time(date, zone))" }

    /// "Wednesday, November 18, 2026"
    static func longDate(_ date: Date, _ zone: TimeZone) -> String {
        var style = Date.FormatStyle(date: .complete, time: .omitted)
        style.timeZone = zone
        return date.formatted(style)
    }

    /// "2026-11-18" in that zone
    static func dateKey(_ date: Date, _ zone: TimeZone) -> String {
        let c = calendar(zone).dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", c.year ?? 0, c.month ?? 0, c.day ?? 0)
    }

    // MARK: Dates without a time ("2026-11-18"), read and shown in UTC so they never shift a day

    static let utc = TimeZone(identifier: "UTC")!

    static func keyDate(_ key: String) -> Date? {
        let parts = key.prefix(10).split(separator: "-").compactMap { Int($0) }
        guard parts.count == 3 else { return nil }
        return calendar(utc).date(from: DateComponents(year: parts[0], month: parts[1], day: parts[2]))
    }

    /// "Wed, Nov 18, 2026"
    static func keyLabel(_ key: String) -> String {
        guard let date = keyDate(key) else { return key }
        var style = Date.FormatStyle().weekday(.abbreviated).month(.abbreviated).day().year()
        style.timeZone = utc
        return date.formatted(style)
    }

    /// "Nov 18"
    static func keyShort(_ key: String) -> String {
        guard let date = keyDate(key) else { return key }
        var style = Date.FormatStyle().month(.abbreviated).day()
        style.timeZone = utc
        return date.formatted(style)
    }

    /// A date picked on this device ("today" here) as "2026-11-18"
    static func localKey(_ date: Date) -> String { dateKey(date, .current) }

    /// "2026-11-18" as a Date at local midnight, for date pickers
    static func localDate(_ key: String) -> Date? {
        let parts = key.prefix(10).split(separator: "-").compactMap { Int($0) }
        guard parts.count == 3 else { return nil }
        return calendar(.current).date(from: DateComponents(year: parts[0], month: parts[1], day: parts[2]))
    }

    // MARK: Clock times ("09:00:00", minutes since midnight; 1440 is 24:00, the end of the day)

    static func minutes(_ value: String) -> Int {
        let parts = value.split(separator: ":").compactMap { Int($0) }
        return (parts.first ?? 0) * 60 + (parts.count > 1 ? parts[1] : 0)
    }

    /// 540 -> "09:00"; 1440 -> "24:00"
    static func clock(_ minutes: Int) -> String { String(format: "%02d:%02d", minutes / 60, minutes % 60) }

    /// 540 -> "9:00 AM"; 1440 -> "12:00 AM"
    static func clockLabel(_ minutes: Int) -> String {
        let m = minutes % 1440
        let date = calendar(utc).date(from: DateComponents(year: 2000, month: 1, day: 1, hour: m / 60, minute: m % 60)) ?? .now
        return time(date, utc)
    }

    /// Minutes as a Date today, for time pickers
    static func pickerDate(_ minutes: Int) -> Date {
        let m = minutes % 1440
        return Calendar.current.date(bySettingHour: m / 60, minute: m % 60, second: 0, of: .now) ?? .now
    }

    static func minutes(of date: Date) -> Int {
        let c = Calendar.current.dateComponents([.hour, .minute], from: date)
        return (c.hour ?? 0) * 60 + (c.minute ?? 0)
    }

    // MARK: Durations

    /// 30 -> "30 min", 60 -> "1 hr", 90 -> "1 hr 30 min"
    static func duration(_ minutes: Int) -> String {
        if minutes < 60 { return "\(minutes) min" }
        let h = minutes / 60, m = minutes % 60
        return m == 0 ? "\(h) hr" : "\(h) hr \(m) min"
    }

    static func initials(_ name: String) -> String {
        let letters = name.split(separator: " ").prefix(2).compactMap { $0.first.map(String.init) }
        return letters.isEmpty ? "?" : letters.joined().uppercased()
    }

    static func plural(_ n: Int, _ one: String, _ many: String? = nil) -> String {
        "\(n) \(n == 1 ? one : (many ?? one + "s"))"
    }
}
