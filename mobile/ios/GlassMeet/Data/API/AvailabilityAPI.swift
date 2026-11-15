import Foundation
import Supabase

/// Weekly hours and date overrides. Hours are saved one row at a time (update, insert or
/// delete), never by deleting a whole day and inserting it again.
enum AvailabilityAPI {
    static func rules() async throws -> [AvailabilityRule] {
        let rows: [Lossy<AvailabilityRule>] = try await supabase.from("availability_rules")
            .select("id, day_of_week, start_time, end_time")
            .eq("created_by_id", value: API.uid())
            .order("day_of_week").order("start_time")
            .execute().value
        return rows.unwrapped()
    }

    static func insertRule(day: Int, start: Int, end: Int) async throws -> AvailabilityRule {
        let row: Row = ["day_of_week": .integer(day), "start_time": .string(Fmt.clock(start)), "end_time": .string(Fmt.clock(end))]
        return try await supabase.from("availability_rules").insert(row)
            .select("id, day_of_week, start_time, end_time").single().execute().value
    }

    static func updateRule(_ id: UUID, start: Int, end: Int) async throws {
        let row: Row = ["start_time": .string(Fmt.clock(start)), "end_time": .string(Fmt.clock(end))]
        try await supabase.from("availability_rules").update(row, returning: .minimal).eq("id", value: API.key(id)).execute()
    }

    static func deleteRule(_ id: UUID) async throws {
        try await supabase.from("availability_rules").delete(returning: .minimal).eq("id", value: API.key(id)).execute()
    }

    // MARK: Date overrides

    static func exceptions() async throws -> [AvailabilityException] {
        let rows: [Lossy<AvailabilityException>] = try await supabase.from("availability_exceptions").select()
            .eq("created_by_id", value: API.uid())
            .order("date")
            .execute().value
        return rows.unwrapped()
    }

    static func addException(date: String, type: String, start: Int?, end: Int?, label: String?) async throws -> AvailabilityException {
        let row: Row = [
            "date": .string(date),
            "type": .string(type),
            "start_time": json(start.map(Fmt.clock)),
            "end_time": json(end.map(Fmt.clock)),
            "label": json(label),
        ]
        return try await supabase.from("availability_exceptions").insert(row).select().single().execute().value
    }

    static func deleteException(_ id: UUID) async throws {
        try await supabase.from("availability_exceptions").delete(returning: .minimal).eq("id", value: API.key(id)).execute()
    }

    /// Upcoming meetings, for holiday clashes
    static func upcomingBookings() async throws -> [Booking] {
        let rows: [Lossy<Booking>] = try await supabase.from("bookings").select()
            .eq("host_id", value: API.uid())
            .gte("start_time", value: Fmt.iso(.now))
            .order("start_time")
            .limit(500)
            .execute().value
        return rows.unwrapped()
    }
}
