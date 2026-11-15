import Foundation
import Observation
import Supabase

/// Who is signed in, kept in step with Supabase's auth events, plus the host's profile and
/// WhatsApp settings that every screen reads.
@Observable @MainActor
final class Session {
    var ready = false
    var userID: UUID?
    var email: String?
    var metadataName: String?
    var profile: Profile?
    var whatsApp: WhatsAppSettings?
    /// Keeps the signed-out screens up while a password reset finishes: verifying the reset
    /// code signs in before the new password is set.
    var holdSignedOut = false

    var signedIn: Bool { userID != nil && !holdSignedOut }

    var hostName: String {
        if let name = profile?.fullName, !name.isEmpty { return name }
        if let metadataName, !metadataName.isEmpty { return metadataName }
        return email?.split(separator: "@").first.map(String.init) ?? ""
    }

    var firstName: String { hostName.split(separator: " ").first.map(String.init) ?? hostName }

    /// The host's zone (profiles.timezone), used for every meeting time
    var timeZone: TimeZone {
        profile?.timezone.flatMap { TimeZone(identifier: $0) } ?? .current
    }

    func listen() async {
        for await (_, session) in supabase.auth.authStateChanges {
            let user = session?.user
            let changed = user?.id != userID
            userID = user?.id
            email = user?.email
            metadataName = user?.userMetadata["full_name"]?.stringValue ?? user?.userMetadata["name"]?.stringValue
            ready = true
            if user == nil {
                profile = nil
                whatsApp = nil
            } else if changed {
                await reloadProfile()
                await reloadWhatsApp()
                if let profile { await ProfileAPI.seedHoursIfNeeded(profile) }
            }
        }
    }

    func reloadProfile() async {
        if let loaded = try? await ProfileAPI.load(email: email, metadataName: metadataName) { profile = loaded }
    }

    func reloadWhatsApp() async {
        whatsApp = try? await WhatsAppAPI.settings()
    }

    func signOut() async {
        await AuthAPI.signOut()
    }
}
