import Foundation
import Supabase

/// The event type form's values, and how they turn into the columns the web app writes
/// (src/pages/Event types.jsx: EventModal values()).
struct EventTypeDraft: Equatable {
    enum Kind: String, CaseIterable, Identifiable {
        case oneOnOne = "one_on_one", group, oneOff = "one_off"
        var id: String { rawValue }
        var label: String {
            switch self {
            case .oneOnOne: "One-on-one"
            case .group: "Group"
            case .oneOff: "One-off"
            }
        }
        var hint: String {
            switch self {
            case .oneOnOne: "One guest per time"
            case .group: "Several guests per time"
            case .oneOff: "A time outside your regular schedule"
            }
        }
        var icon: String {
            switch self {
            case .oneOnOne: "person"
            case .group: "person.3"
            case .oneOff: "calendar.badge.clock"
            }
        }
    }

    enum LocationKind { case video, phone, inPerson, other }

    static let locations: [(name: String, kind: LocationKind)] = [
        ("Google Meet", .video), ("Zoom", .video), ("Microsoft Teams", .video),
        ("WhatsApp call", .phone), ("Phone call", .phone), ("In person", .inPerson), ("Other", .other),
    ]
    static let durations = [15, 30, 45, 60, 90]
    enum NoticeUnit: String, CaseIterable, Identifiable {
        case minutes, hours, days
        var id: String { rawValue }
        var factor: Int { self == .minutes ? 1 : self == .hours ? 60 : 1440 }
    }
    enum Freq: String, CaseIterable, Identifiable {
        case day, week, month
        var id: String { rawValue }
        func label(_ every: Int) -> String { every == 1 ? rawValue : rawValue + "s" }
    }

    struct Question: Identifiable, Equatable {
        var id = UUID()
        var text = ""
        var kind: CustomQuestion.Kind = .text
        var required = false
        /// One answer per line
        var options = ""

        var cleanOptions: [String] {
            var seen = Set<String>()
            return options.components(separatedBy: "\n")
                .map { $0.trimmingCharacters(in: .whitespaces) }
                .filter { !$0.isEmpty && seen.insert($0).inserted }
                .prefix(20).map { $0 }
        }
    }

    var kind: Kind = .oneOnOne
    var name = ""
    var duration = 30
    var customDuration = false
    var location = "Google Meet"
    var customLocation = ""
    var meetingURL = ""
    var address = ""
    var description = ""
    var note = ""
    var maxAttendees = 2

    /// true: whenever the host's working hours are open; false: the custom schedule below
    var useWorkingHours = true
    /// true: repeats (weekly schedule); false: specific dates
    var repeats = true
    var freq: Freq = .week
    var every = 1
    var days: Set<Int> = [0, 1, 2, 3, 4, 5, 6]
    var startDate = Calendar.current.startOfDay(for: .now)
    var hasEndDate = false
    var endDate = Calendar.current.startOfDay(for: .now)
    var maxFutureDays = 60
    var dates: Set<DateComponents> = []
    /// true: only these start times ("HH:MM"); false: any free time inside the host's hours
    var useFixedTimes = false
    var fixedTimes: [Int] = [9 * 60]

    var noticeValue = 30
    var noticeUnit: NoticeUnit = .minutes
    var limitPerDay = false
    var maxPerDay = 5
    var bufferBefore = 0
    var bufferAfter = 0

    var questions: [Question] = []
    var emailRequired = true
    var requiresApproval = false
    var active = true

    var locationKind: LocationKind { Self.locations.first { $0.name == location }?.kind ?? .other }

    // MARK: From an event type or template

    init() {}

    init(_ e: EventType, isNew: Bool) {
        kind = e.isGroup ? .group : (e.schedule == "dates" && !(e.fixedTimes ?? []).isEmpty ? .oneOff : .oneOnOne)
        name = e.name ?? ""
        duration = e.minutes
        customDuration = !Self.durations.contains(duration)
        if let loc = e.location, !loc.isEmpty {
            if Self.locations.contains(where: { $0.name == loc && $0.name != "Other" }) { location = loc } else { location = "Other"; customLocation = loc }
        }
        meetingURL = e.meetingURL ?? ""
        address = e.address ?? ""
        description = String((e.description ?? "").prefix(500))
        note = e.note ?? ""
        maxAttendees = max(2, e.maxAttendees ?? 2)

        let srcDays = (e.days ?? []).filter { (0...6).contains($0) }
        let srcFreq = Freq(rawValue: e.freq ?? "week") ?? .week
        let fixed = (e.fixedTimes ?? []).map { Fmt.minutes($0) }
        useWorkingHours = !(e.schedule == "dates" || !fixed.isEmpty || srcFreq != .week || (e.every ?? 1) > 1 || (!srcDays.isEmpty && srcDays.count < 7))
        repeats = e.schedule != "dates"
        freq = srcFreq
        every = max(1, e.every ?? 1)
        days = srcDays.isEmpty ? [0, 1, 2, 3, 4, 5, 6] : Set(srcDays)
        if let s = e.startDate.flatMap(Fmt.localDate) { startDate = s }
        if let end = e.endDate.flatMap(Fmt.localDate) { hasEndDate = true; endDate = end }
        maxFutureDays = e.maxFutureDays ?? 60
        dates = Set((e.dates ?? []).compactMap(Self.components))
        useFixedTimes = !fixed.isEmpty
        if !fixed.isEmpty { fixedTimes = fixed.sorted() }

        // new events start bookable from 30 minutes away; existing ones keep what they have
        let notice = e.minNotice ?? (isNew ? 30 : 0)
        if notice > 0, notice % 1440 == 0 { noticeUnit = .days } else if notice > 0, notice % 60 == 0 { noticeUnit = .hours } else { noticeUnit = .minutes }
        noticeValue = notice / noticeUnit.factor
        limitPerDay = (e.maxPerDay ?? 0) > 0
        maxPerDay = e.maxPerDay ?? 5
        bufferBefore = e.bufferBefore ?? 0
        bufferAfter = e.bufferAfter ?? 0
        questions = e.questions.map { Question(text: $0.text, kind: $0.kind, required: $0.required, options: $0.options.joined(separator: "\n")) }
        emailRequired = e.emailRequired ?? true
        requiresApproval = e.needsApproval
        active = isNew ? true : e.isActive
    }

    static func components(_ key: String) -> DateComponents? {
        let parts = key.prefix(10).split(separator: "-").compactMap { Int($0) }
        guard parts.count == 3 else { return nil }
        return DateComponents(calendar: Calendar(identifier: .gregorian), year: parts[0], month: parts[1], day: parts[2])
    }

    static func key(_ c: DateComponents) -> String? {
        guard let y = c.year, let m = c.month, let d = c.day else { return nil }
        return String(format: "%04d-%02d-%02d", y, m, d)
    }

    var dateKeys: [String] { dates.compactMap(Self.key).sorted() }

    // MARK: What gets saved

    private var oneOff: Bool { kind == .oneOff }
    private var usesHours: Bool { !oneOff && useWorkingHours }
    private var scheduleValue: String { oneOff ? "dates" : usesHours ? "weekly" : (repeats ? "weekly" : "dates") }
    private var isWeekly: Bool { scheduleValue == "weekly" }
    private var fixed: Bool { oneOff || (!usesHours && useFixedTimes) }
    private var freqValue: Freq { usesHours ? .week : freq }
    private var everyValue: Int { usesHours ? 1 : max(1, min(52, every)) }
    private var daysValue: [Int] { usesHours ? [0, 1, 2, 3, 4, 5, 6] : days.sorted() }
    private var clampedDuration: Int { min(720, max(5, duration)) }

    /// A problem to fix before saving, in plain words
    func problem() -> String? {
        if name.trimmingCharacters(in: .whitespaces).isEmpty { return "Give the event a name." }
        if locationKind == .inPerson, address.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return "Add the address where you will meet." }
        if locationKind == .other, customLocation.trimmingCharacters(in: .whitespaces).isEmpty { return "Describe where the meeting happens." }
        if isWeekly, freqValue == .week, daysValue.isEmpty { return "Pick at least one day the event repeats on." }
        if isWeekly, hasEndDate, endDate < startDate { return "The last date is before the first date." }
        if !isWeekly, dates.isEmpty { return oneOff ? "Pick the date for this one-off meeting." : "Pick at least one date." }
        if !isWeekly, dates.count > 366 { return "Pick up to 366 dates." }
        if fixed {
            if fixedTimes.isEmpty { return "Add at least one start time." }
            if fixedTimes.contains(where: { $0 + clampedDuration > 1440 }) { return "A start time is too late for a meeting this long to end by midnight." }
        }
        if questions.contains(where: { $0.text.trimmingCharacters(in: .whitespaces).isEmpty }) { return "Write each invitee question, or remove the empty one." }
        if questions.contains(where: { $0.kind.hasOptions && $0.cleanOptions.isEmpty }) { return "Add the answers guests can choose from." }
        return nil
    }

    /// The event type's columns as saved (host_* are added by EventTypesAPI.save)
    func fields() -> Row {
        let loc = locationKind
        var row = Row()
        row["name"] = .string(name.trimmingCharacters(in: .whitespaces))
        row["description"] = json(trimmedOrNil(description))
        row["note"] = json(trimmedOrNil(note))
        row["duration"] = .integer(clampedDuration)
        row["location"] = .string(loc == .other ? customLocation.trimmingCharacters(in: .whitespaces) : location)
        row["meeting_url"] = .string(loc == .video || loc == .other ? meetingURL.trimmingCharacters(in: .whitespaces) : "")
        row["address"] = loc == .inPerson ? .string(address.trimmingCharacters(in: .whitespacesAndNewlines)) : .null
        row["booking_type"] = .string(kind == .group ? "group" : "one_on_one")
        row["max_attendees"] = .integer(kind == .group ? min(500, max(2, maxAttendees)) : 1)
        row["active"] = .bool(active)
        row["requires_approval"] = .bool(requiresApproval)
        row["email_required"] = .bool(emailRequired)
        row["schedule"] = .string(scheduleValue)
        row["freq"] = .string(freqValue.rawValue)
        row["every"] = .integer(everyValue)
        row["days"] = isWeekly && freqValue == .week && daysValue.count < 7 ? json(daysValue) : .null
        row["start_date"] = isWeekly ? .string(Fmt.localKey(startDate)) : .null
        row["end_date"] = isWeekly && hasEndDate ? .string(Fmt.localKey(endDate)) : .null
        row["dates"] = isWeekly ? .null : json(dateKeys)
        row["fixed_times"] = fixed ? json(Array(Set(fixedTimes)).sorted().map(Fmt.clock)) : .null
        row["max_future_days"] = .integer(min(730, max(1, maxFutureDays)))
        row["min_notice"] = .integer(max(0, noticeValue) * noticeUnit.factor)
        row["max_per_day"] = limitPerDay ? .integer(min(100, max(1, maxPerDay))) : .null
        row["buffer_before"] = .integer(min(1440, max(0, bufferBefore)))
        row["buffer_after"] = .integer(min(1440, max(0, bufferAfter)))
        row["custom_questions"] = .array(questions.prefix(10).map {
            CustomQuestion(text: $0.text.trimmingCharacters(in: .whitespaces), kind: $0.kind, required: $0.required, options: $0.cleanOptions).jsonValue
        })
        return row
    }

    /// "Every week on Mon, Wed" and the like
    var scheduleSummary: String {
        if oneOff { return dates.isEmpty ? "Pick a date and time" : "\(Fmt.plural(dates.count, "date")) at \(fixedTimes.map(Fmt.clockLabel).joined(separator: ", "))" }
        if usesHours { return "Your working hours, every day they’re open" }
        if !repeats { return dates.isEmpty ? "Pick the dates" : Fmt.plural(dates.count, "date") }
        let unit = every == 1 ? freq.rawValue : "\(every) \(freq.label(every))"
        var text = "Every \(unit)"
        if freq == .week {
            let names = Calendar.current.shortWeekdaySymbols
            let picked = [1, 2, 3, 4, 5, 6, 0].filter { days.contains($0) }.map { names[$0] }
            text += days.count == 7 ? ", every day" : picked.isEmpty ? "" : " on \(picked.joined(separator: ", "))"
        }
        return text
    }
}
