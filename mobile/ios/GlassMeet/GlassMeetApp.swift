import Supabase
import SwiftUI

@main
struct GlassMeetApp: App {
    @State private var account = Account()

    var body: some Scene {
        WindowGroup {
            Group {
                if !account.ready {
                    // The launch screen stays up while the stored session is read
                    Color(uiColor: .systemGroupedBackground).ignoresSafeArea()
                } else if account.userID == nil {
                    SignInView()
                } else {
                    MainTabView()
                }
            }
            .environment(account)
            .tint(.gmTeal)
            .task { await account.listen() }
        }
    }
}

/// Who is signed in, kept in step with Supabase's auth events.
@Observable @MainActor
final class Account {
    var ready = false
    var userID: UUID?
    var email: String?
    var metadataName: String?
    var profile: Profile?

    var hostName: String {
        if let name = profile?.fullName, !name.isEmpty { return name }
        if let name = metadataName, !name.isEmpty { return name }
        return email?.split(separator: "@").first.map(String.init) ?? ""
    }

    func listen() async {
        for await (_, session) in supabase.auth.authStateChanges {
            let user = session?.user
            let changed = user?.id != userID
            userID = user?.id
            email = user?.email
            metadataName = user?.userMetadata["full_name"]?.stringValue
            ready = true
            if user == nil { profile = nil } else if changed { await reloadProfile() }
        }
    }

    func reloadProfile() async {
        profile = try? await API.profile()
    }

    func signOut() async {
        try? await supabase.auth.signOut()
    }
}

/// The app's sections, drawn by the system tab bar.
enum AppTab: Hashable { case home, meetings, scheduling, hours, settings }

/// Pages pushed inside a tab.
enum Route: Hashable {
    case meeting(UUID)
    case eventType(UUID?)
}

struct MainTabView: View {
    @State private var tab: AppTab = .home

    var body: some View {
        // The system tab bar: on iOS 26 it floats over the content on Liquid Glass and shrinks
        // while scrolling down. Five tabs with short labels and SF Symbols, so none overflow into
        // "More"; creating an event type is an action, so it lives in a navigation bar instead.
        TabView(selection: $tab) {
            Tab("Home", systemImage: "house", value: AppTab.home) {
                Stacked { HomeView() }
            }
            Tab("Meetings", systemImage: "person.2", value: AppTab.meetings) {
                Stacked { MeetingsView() }
            }
            Tab("Scheduling", systemImage: "link", value: AppTab.scheduling) {
                Stacked { SchedulingView() }
            }
            Tab("Hours", systemImage: "clock", value: AppTab.hours) {
                Stacked { HoursView() }
            }
            Tab("Settings", systemImage: "gearshape", value: AppTab.settings) {
                Stacked { SettingsView() }
            }
        }
        .tabBarMinimizeBehavior(.onScrollDown)
    }
}

/// A navigation stack that knows every page a tab can push.
private struct Stacked<Content: View>: View {
    @ViewBuilder var content: Content
    var body: some View {
        NavigationStack {
            content.navigationDestination(for: Route.self) { route in
                switch route {
                case .meeting(let id): MeetingView(id: id)
                case .eventType(let id): EventTypeEditor(id: id)
                }
            }
        }
    }
}
