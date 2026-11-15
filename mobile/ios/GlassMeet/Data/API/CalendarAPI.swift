import Foundation
import Supabase

/// Google Calendar (edge function google-calendar), Microsoft Outlook / Teams (connections)
/// and the .ics subscription feed (cal_feeds).
enum CalendarAPI {
    // MARK: Google

    /// nil when not connected (or the table isn't there yet)
    static func googleStatus() async -> GoogleAccount? {
        guard let id = try? API.uid() else { return nil }
        let rows: [Lossy<GoogleAccount>]? = try? await supabase.from("google_accounts")
            .select("email, calendar_id, calendar_name, auto_sync, connected, synced_date")
            .eq("user_id", value: id).execute().value
        return rows?.unwrapped().first
    }

    static func googleAuthURL() async throws -> URL {
        let result = try await Edge.call("google-calendar", ["action": "authUrl", "returnTo": .string(Backend.calendarReturn)])
        guard let text = result.objectValue?["url"]?.stringValue, let url = URL(string: text) else {
            throw EdgeError(code: "unavailable", message: "Google Calendar isn’t available yet.")
        }
        return url
    }

    /// Hands Google's code to the function as the signed-in host; returns the connected email
    static func googleFinish(code: String, state: String) async throws -> String? {
        let result = try await Edge.call("google-calendar", ["action": "finish", "code": .string(code), "state": .string(state)])
        return result.objectValue?["email"]?.stringValue
    }

    static func googleCalendars() async throws -> [GoogleCalendar] {
        let result = try await Edge.call("google-calendar", ["action": "listCalendars"])
        let items = result.arrayValue ?? result.objectValue?["calendars"]?.arrayValue ?? []
        return items.compactMap { item -> GoogleCalendar? in
            guard let o = item.objectValue, let id = o["id"]?.stringValue else { return nil }
            return GoogleCalendar(id: id, summary: o["summary"]?.stringValue ?? id, primary: o["primary"]?.flag ?? false)
        }
    }

    static func googleSaveSettings(_ settings: Row) async throws -> AnyJSON {
        var body = settings
        body["action"] = "saveSettings"
        return try await Edge.call("google-calendar", body)
    }

    /// Returns a sentence about what changed
    static func googleSync() async throws -> String {
        let result = try await Edge.call("google-calendar", ["action": "sync"])
        return syncSummary(result, where: "Google Calendar")
    }

    static func googleDisconnect() async throws {
        try await Edge.call("google-calendar", ["action": "disconnect"])
    }

    // MARK: Microsoft

    /// Whether Microsoft can be offered (false while not configured on the server)
    static func microsoftAvailable() async -> Bool {
        let result = try? await Edge.call("connections", ["action": "available"])
        return result?.objectValue?["microsoft"]?.flag ?? false
    }

    static func microsoftStatus() async -> ProviderConnection? {
        guard let id = try? API.uid() else { return nil }
        let rows: [Lossy<ProviderConnection>]? = try? await supabase.from("connections")
            .select("provider, email, teams, auto_sync, connected, synced_date")
            .eq("user_id", value: id).execute().value
        return rows?.unwrapped().first { $0.provider == "microsoft" }
    }

    static func microsoftAuthURL() async throws -> URL {
        let result = try await Edge.call("connections", ["action": "authUrl", "provider": "microsoft", "returnTo": .string(Backend.calendarReturn)])
        guard let text = result.objectValue?["url"]?.stringValue, let url = URL(string: text) else {
            throw EdgeError(code: "unavailable", message: "Outlook isn’t available yet.")
        }
        return url
    }

    static func microsoftFinish(code: String, state: String) async throws -> String? {
        let result = try await Edge.call("connections", ["action": "finish", "code": .string(code), "state": .string(state)])
        return result.objectValue?["email"]?.stringValue
    }

    static func microsoftAutoSync(_ on: Bool) async throws {
        try await Edge.call("connections", ["action": "saveSettings", "provider": "microsoft", "autoSync": .bool(on)])
    }

    static func microsoftSync() async throws -> String {
        let result = try await Edge.call("connections", ["action": "sync", "provider": "microsoft"])
        return syncSummary(result, where: "Outlook")
    }

    static func microsoftDisconnect() async throws {
        try await Edge.call("connections", ["action": "disconnect", "provider": "microsoft"])
    }

    private static func syncSummary(_ result: AnyJSON, where name: String) -> String {
        let o = result.objectValue ?? [:]
        let failed = o["failed"]?.number ?? 0
        if failed > 0 { return "\(Fmt.plural(failed, "meeting")) couldn’t be synced. Try again later." }
        let changed = (o["created"]?.number ?? 0) + (o["updated"]?.number ?? 0) + (o["deleted"]?.number ?? 0)
        return changed > 0 ? "Synced \(Fmt.plural(changed, "meeting")) to \(name)." : "\(name) is up to date."
    }

    // MARK: Subscription feed

    /// The host's feed token, made on first use
    static func feedToken() async throws -> String {
        let id = try API.uid()
        struct Feed: Decodable { var token: String }
        let rows: [Feed] = try await supabase.from("cal_feeds").select("token").eq("owner", value: id).execute().value
        if let token = rows.first?.token { return token }
        do {
            let created: Feed = try await supabase.from("cal_feeds").insert(["owner": AnyJSON.string(id)]).select("token").single().execute().value
            return created.token
        } catch {
            // another device made it first
            let again: [Feed] = try await supabase.from("cal_feeds").select("token").eq("owner", value: id).execute().value
            if let token = again.first?.token { return token }
            throw error
        }
    }

    static func feedTokenIfAvailable() async -> String? {
        try? await feedToken()
    }

    /// A new token; calendars subscribed to the old link stop updating
    static func rotateFeed() async throws -> String {
        let token: String = try await supabase.rpc("rotate_cal_feed").execute().value
        return token
    }
}
