import Supabase
import SwiftUI

@main
struct GlassMeetApp: App {
    @State private var session = Session()
    @State private var data = AppData()
    @State private var router = Router()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(session)
                .environment(data)
                .environment(router)
                .tint(Theme.primary)
                .task { await session.listen() }
                .onOpenURL(perform: open)
        }
    }

    /// glassmeet:// links. Google and LinkedIn sign-in return inside their sign-in sheet, so
    /// auth-callback only arrives here when it's opened from outside (an email link).
    private func open(_ url: URL) {
        guard url.scheme == Backend.scheme else { return }
        switch url.host() {
        case "auth-callback":
            supabase.auth.handle(url)
        case "calendar-connections":
            router.openCalendarConnections(with: url)
        default:
            break
        }
    }
}

struct RootView: View {
    @Environment(Session.self) private var session
    @Environment(AppData.self) private var data
    @Environment(Router.self) private var router

    var body: some View {
        Group {
            if !session.ready {
                // the launch screen stays up while the stored session is read
                Theme.background.ignoresSafeArea()
            } else if session.signedIn {
                MainTabView()
                    .transition(.opacity)
            } else {
                AuthFlowView()
                    .transition(.opacity)
            }
        }
        .animation(.smooth, value: session.signedIn)
        .onChange(of: session.userID) { _, id in
            if id == nil {
                data.reset()
                router.reset()
            }
        }
    }
}

struct MainTabView: View {
    @Environment(Router.self) private var router

    var body: some View {
        @Bindable var router = router
        // The system tab bar floats over the content on Liquid Glass and shrinks while
        // scrolling down. Creating things is an action, so it lives in each page's toolbar.
        TabView(selection: $router.tab) {
            Tab("Home", systemImage: "house", value: AppTab.home) {
                TabStack(path: $router.homePath) { HomeView() }
            }
            Tab("Meetings", systemImage: "person.2", value: AppTab.meetings) {
                TabStack(path: $router.meetingsPath) { MeetingsView() }
            }
            Tab("Scheduling", systemImage: "link", value: AppTab.scheduling) {
                TabStack(path: $router.schedulingPath) { SchedulingView() }
            }
            Tab("Availability", systemImage: "clock", value: AppTab.availability) {
                TabStack(path: $router.availabilityPath) { AvailabilityView() }
            }
            Tab("Settings", systemImage: "gearshape", value: AppTab.settings) {
                TabStack(path: $router.settingsPath) { SettingsView() }
            }
        }
        .tabBarMinimizeBehavior(.onScrollDown)
    }
}

/// A navigation stack that knows every page a tab can push
struct TabStack<Content: View>: View {
    @Binding var path: [Route]
    @ViewBuilder var content: Content

    var body: some View {
        NavigationStack(path: $path) {
            content
                .navigationDestination(for: Route.self) { route in
                    switch route {
                    case .meeting(let id): MeetingView(id: id)
                    case .eventForm(let source): EventTypeFormView(source: source)
                    case .analytics: AnalyticsView()
                    case .profile: ProfileView()
                    case .whatsApp: WhatsAppSettingsView()
                    case .calendarConnections: CalendarConnectionsView()
                    case .addresses: AddressesView()
                    }
                }
        }
    }
}
