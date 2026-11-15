import Foundation
import Supabase

/// Shared pieces of the request layer. Each area has its own namespace (BookingsAPI, ...).
enum API {
    struct SignedOut: LocalizedError {
        var errorDescription: String? { "You’re signed out. Sign in again." }
    }

    struct Message: LocalizedError {
        var text: String
        var errorDescription: String? { text }
    }

    /// The signed-in user's id, lowercased as Postgres writes it
    static func uid() throws -> String {
        guard let id = supabase.auth.currentUser?.id else { throw SignedOut() }
        return id.uuidString.lowercased()
    }

    static func key(_ id: UUID) -> String { id.uuidString.lowercased() }
}

/// An edge function's error: { error: code, message }
struct EdgeError: LocalizedError {
    var code: String
    var message: String
    var errorDescription: String? { message }

    /// The provider isn't set up on the server (or the function isn't deployed)
    var isUnavailable: Bool { code == "not_configured" || code == "unavailable" }
    var needsReconnect: Bool { code == "reconnect" || code == "not_connected" }
}

/// Calls the google-calendar and connections edge functions (POST JSON { action, ... }).
enum Edge {
    @discardableResult
    static func call(_ function: String, _ body: Row) async throws -> AnyJSON {
        do {
            let result: AnyJSON? = try await supabase.functions.invoke(
                function,
                options: FunctionInvokeOptions(body: body),
                decode: { data, _ in try? JSONDecoder().decode(AnyJSON.self, from: data) }
            )
            return result ?? .null
        } catch let error as FunctionsError {
            switch error {
            case .relayError:
                throw EdgeError(code: "unavailable", message: "This isn’t available yet.")
            case .httpError(let status, let data):
                let detail = (try? JSONDecoder().decode(AnyJSON.self, from: data))?.objectValue
                let code = detail?["error"]?.stringValue
                if status == 404, code == nil { throw EdgeError(code: "unavailable", message: "This isn’t available yet.") }
                if status == 503, code == nil { throw EdgeError(code: "not_configured", message: "This isn’t available yet.") }
                throw EdgeError(code: code ?? "error", message: detail?["message"]?.stringValue ?? "The server didn’t respond. Try again.")
            }
        }
    }

    /// After a booking changes, the host's connected calendars follow. Never fails the caller.
    static func pushBooking(_ id: UUID) {
        Task.detached {
            async let google: Void = push("google-calendar", id)
            async let microsoft: Void = push("connections", id)
            _ = await (google, microsoft)
        }
    }

    private static func push(_ function: String, _ id: UUID) async {
        _ = try? await call(function, ["action": "pushBooking", "bookingId": .string(API.key(id))])
    }
}
