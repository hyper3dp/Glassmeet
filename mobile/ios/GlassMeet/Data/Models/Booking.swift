import Foundation
import Supabase

/// public.bookings. guest_email (and every other column but id) may be null: one row without it
/// must never break a list.
struct Booking: Decodable, Identifiable, Hashable {
    var id: UUID
    var eventTypeID: UUID?
    var eventName: String?
    var guestName: String?
    var guestEmail: String?
    var guestPhone: String?
    var guestNotes: String?
    /// The host's private notes
    var notes: String?
    var startTime: String?
    var endTime: String?
    var timezone: String?
    var meetingURL: String?
    var status: String?
    var approvalStatus: String?
    /// jsonb, read leniently (see `answers`)
    var customAnswersJSON: AnyJSON?
    var attendeeCount: Int?
    var createdDate: String?
    var updatedDate: String?

    enum CodingKeys: String, CodingKey {
        case id, notes, timezone, status
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
        case customAnswersJSON = "custom_answers"
        case attendeeCount = "attendee_count"
        case createdDate = "created_date"
        case updatedDate = "updated_date"
    }

    var name: String { guestName.flatMap { $0.trimmingCharacters(in: .whitespaces).isEmpty ? nil : $0 } ?? "Guest" }
    var firstName: String { name.split(separator: " ").first.map(String.init) ?? name }
    var email: String? { guestEmail.flatMap { $0.isEmpty ? nil : $0 } }
    var phone: String? { guestPhone.flatMap { $0.filter(\.isNumber).isEmpty ? nil : $0 } }
    var eventTitle: String { eventName.flatMap { $0.isEmpty ? nil : $0 } ?? "Meeting" }
    var link: String? { meetingURL.flatMap { $0.isEmpty ? nil : $0 } }

    var start: Date { Fmt.timestamp(startTime) ?? .distantPast }
    var end: Date { Fmt.timestamp(endTime) ?? start.addingTimeInterval(30 * 60) }
    var lengthMinutes: Int { max(0, Int(end.timeIntervalSince(start) / 60)) }

    var isCancelled: Bool { status == "cancelled" }
    var isDeclined: Bool { approvalStatus == "declined" }
    var isPending: Bool { approvalStatus == "pending" && !isCancelled }
    /// A meeting stays upcoming until it ends (web: status confirmed and end >= now)
    func isUpcoming(at now: Date = .now) -> Bool { status == "confirmed" && end >= now }
    func isOver(at now: Date = .now) -> Bool { end < now }

    /// Cancelled meetings leave the lists two days after they were cancelled (src/lib/bookings.js)
    func isExpiredCancellation(at now: Date = .now) -> Bool {
        guard isCancelled else { return false }
        let changed = Fmt.timestamp(updatedDate) ?? Fmt.timestamp(createdDate) ?? start
        return now.timeIntervalSince(changed) > 2 * 86400
    }

    /// The guest's answers to the event's questions: [{question, answer}] or {question: answer}
    var answers: [(question: String, answer: String)] {
        switch customAnswersJSON ?? .null {
        case .array(let items):
            return items.compactMap { item -> (question: String, answer: String)? in
                guard let object = item.objectValue else { return nil }
                let question = object["question"]?.text ?? object["text"]?.text ?? object["label"]?.text ?? "Question"
                guard let answer = object["answer"]?.text ?? object["value"]?.text, !answer.isEmpty else { return nil }
                return (question, answer)
            }
        case .object(let object):
            return object.keys.sorted().compactMap { key -> (question: String, answer: String)? in
                guard let answer = object[key]?.text, !answer.isEmpty else { return nil }
                return (key, answer)
            }
        default:
            return []
        }
    }

    func matches(_ query: String) -> Bool {
        let q = query.trimmingCharacters(in: .whitespaces).lowercased()
        guard !q.isEmpty else { return true }
        if [guestName, guestEmail, eventName].contains(where: { $0?.lowercased().contains(q) == true }) { return true }
        let digits = q.filter(\.isNumber)
        return digits.count >= 3 && (guestPhone ?? "").filter(\.isNumber).contains(digits)
    }
}

enum BookingStatus {
    case request, cancelled, declined, finished, confirmed

    init(_ booking: Booking, now: Date = .now) {
        if booking.isPending { self = .request }
        else if booking.isDeclined { self = .declined }
        else if booking.isCancelled { self = .cancelled }
        else if booking.isOver(at: now) { self = .finished }
        else { self = .confirmed }
    }

    var label: String {
        switch self {
        case .request: "Request"
        case .cancelled: "Cancelled"
        case .declined: "Declined"
        case .finished: "Finished"
        case .confirmed: "Confirmed"
        }
    }
}
