import Foundation
import Supabase

enum BookingsAPI {
    /// The host's meetings, newest first (web: -start_time, limit 1000)
    static func list() async throws -> [Booking] {
        let rows: [Lossy<Booking>] = try await supabase.from("bookings").select()
            .eq("host_id", value: API.uid())
            .order("start_time", ascending: false)
            .limit(1000)
            .execute().value
        return rows.unwrapped()
    }

    static func get(_ id: UUID) async throws -> Booking {
        try await supabase.from("bookings").select().eq("id", value: API.key(id)).single().execute().value
    }

    /// Updates a meeting and returns it as saved
    static func update(_ id: UUID, _ fields: Row) async throws -> Booking {
        do {
            return try await supabase.from("bookings").update(fields)
                .eq("id", value: API.key(id)).select().single().execute().value
        } catch {
            if String(describing: error).contains("PGRST116") {
                throw API.Message(text: "This meeting may have been deleted. Pull to refresh.")
            }
            throw error
        }
    }

    static func approve(_ id: UUID) async throws -> Booking {
        let saved = try await update(id, ["approval_status": "approved"])
        Edge.pushBooking(id)
        return saved
    }

    static func decline(_ id: UUID) async throws -> Booking {
        let saved = try await update(id, ["approval_status": "declined", "status": "cancelled"])
        Edge.pushBooking(id)
        return saved
    }

    static func cancel(_ id: UUID) async throws -> Booking {
        let saved = try await update(id, ["status": "cancelled"])
        Edge.pushBooking(id)
        return saved
    }
}
