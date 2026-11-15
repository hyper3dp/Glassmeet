import Foundation
import Supabase

/// The same Supabase project and publishable key as the web app. The key is public by design;
/// Row Level Security protects the data.
enum Backend {
    static let url = URL(string: "https://sjxraemrdpzleipfmwun.supabase.co")!
    static let key = "sb_publishable_0T8UhXmO8GUjyZa67yIU5w_RZwkPa4O"

    /// Guests book on the web: /book/<event type id>
    static let webOrigin = URL(string: "https://glassmeet.run.place")!
    static func bookingURL(_ eventTypeID: UUID) -> URL {
        webOrigin.appending(path: "book/\(eventTypeID.uuidString.lowercased())")
    }
    static func web(_ path: String) -> URL { webOrigin.appending(path: path) }

    /// The app's URL scheme (Info.plist CFBundleURLTypes)
    static let scheme = "glassmeet"
    /// Where Google and LinkedIn sign-in come back to. Must be in Supabase Auth's Redirect URLs.
    static let authCallback = URL(string: "glassmeet://auth-callback")!
    /// Where calendar connections come back to (google-calendar and connections functions)
    static let calendarReturn = "glassmeet://calendar-connections"

    /// The host's private .ics subscription feed
    static func feedURL(token: String) -> URL {
        url.appending(path: "functions/v1/calendar-feed/\(token).ics")
    }
}

let supabase = SupabaseClient(
    supabaseURL: Backend.url,
    supabaseKey: Backend.key,
    options: SupabaseClientOptions(auth: .init(emitLocalSessionAsInitialSession: true))
)
