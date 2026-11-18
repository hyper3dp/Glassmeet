import Supabase
import SwiftUI

@main
struct GlassMeetApp: App {
    @State private var session = Session()
    @State private var data = AppData()
    @State private var router = Router()
    /// A booking page opened from a link, shown in an in-app browser
    @State private var browsing: URL?

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(session)
                .environment(data)
                .environment(router)
                .tint(Theme.primary)
                .inAppBrowser($browsing)
                .task { await session.listen() }
                .onOpenURL { url in Task { await open(url) } }
        }
    }

    /// glassmeet:// links, and GlassMeet web links (glassmeet.run.place) once the domain is
    /// associated with the app. Google and LinkedIn sign-in return inside their sign-in sheet, so
    /// auth-callback only arrives here when it's opened from outside (an email link).
    @MainActor
    private func open(_ url: URL) async {
        if url.scheme == Backend.scheme {
            switch url.host() {
            case "auth-callback":
                supabase.auth.handle(url)
            case "calendar-connections":
                router.openCalendarConnections(with: url)
            case "book":
                // glassmeet://book/<event type id>
                if let id = url.pathComponents.dropFirst().first.flatMap(UUID.init(uuidString:)) { await openBooking(id) }
            case "bookings", "availability", "event-types":
                router.open(webPath: "/\(url.host() ?? "")\(url.query().map { "?\($0)" } ?? "")")
            default:
                break
            }
            return
        }
        guard url.scheme == "https", url.host() == Backend.webOrigin.host() else { return }
        let parts = url.pathComponents.filter { $0 != "/" }
        if parts.first == "book", parts.count >= 2, let id = UUID(uuidString: parts[1]) {
            await openBooking(id)
        } else if session.signedIn {
            router.open(webPath: url.path() + (url.query().map { "?\($0)" } ?? ""))
        } else {
            browsing = url
        }
    }

    /// The host's own event type opens its editor; anyone else's booking page opens in the app's browser
    @MainActor
    private func openBooking(_ id: UUID) async {
        if session.signedIn {
            if data.eventTypes.isEmpty { await data.loadEventTypes() }
            if data.eventType(id) != nil {
                router.tab = .scheduling
                router.schedulingPath = [.eventForm(.edit(id))]
                return
            }
        }
        browsing = Backend.bookingURL(id)
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
    @Environment(Session.self) private var session
    @Environment(AppData.self) private var data
    @Environment(\.scenePhase) private var scenePhase
    @State private var onboarding = false

    var body: some View {
        tabs(router)
            .fullScreenCover(isPresented: $onboarding) {
                OnboardingView {
                    if let id = session.userID { Onboarding.markDone(id) }
                    onboarding = false
                }
            }
            // a new account sees onboarding once; hosts who already have event types skip it
            .task(id: session.userID) {
                guard let id = session.userID, !Onboarding.isDone(id) else { return }
                await data.loadAll()
                guard data.eventTypesState.isLoaded, data.bookingsState.isLoaded else { return }
                if data.eventTypes.isEmpty, data.bookings.isEmpty { onboarding = true } else { Onboarding.markDone(id) }
            }
            // booking messages: checked every minute while the app is open, like the web's bell
            .task(id: scenePhase) {
                guard scenePhase == .active else { return }
                while !Task.isCancelled {
                    await data.loadNotifications()
                    try? await Task.sleep(for: .seconds(60))
                }
            }
    }

    @ViewBuilder
    private func tabs(_ router: Router) -> some View {
        @Bindable var router = router
        let selection = $router.tabSelection
        // The system tab bar floats over the content on Liquid Glass and shrinks while scrolling down.
        if #available(iOS 18, *) {
            TabView(selection: selection) {
                Tab("Home", systemImage: "house", value: AppTab.home) {
                    TabStack(path: $router.homePath) { HomeView() }
                }
                .badge(data.unreadCount)
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
            .minimizeTabBarOnScroll()
        } else {
            // iOS 17 has no Tab type, so each page is tagged instead
            TabView(selection: selection) {
                TabStack(path: $router.homePath) { HomeView() }
                    .tabItem { Label("Home", systemImage: "house") }.tag(AppTab.home)
                    .badge(data.unreadCount)
                TabStack(path: $router.meetingsPath) { MeetingsView() }
                    .tabItem { Label("Meetings", systemImage: "person.2") }.tag(AppTab.meetings)
                TabStack(path: $router.schedulingPath) { SchedulingView() }
                    .tabItem { Label("Scheduling", systemImage: "link") }.tag(AppTab.scheduling)
                TabStack(path: $router.availabilityPath) { AvailabilityView() }
                    .tabItem { Label("Availability", systemImage: "clock") }.tag(AppTab.availability)
                TabStack(path: $router.settingsPath) { SettingsView() }
                    .tabItem { Label("Settings", systemImage: "gearshape") }.tag(AppTab.settings)
            }
        }
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
                    case .notifications: NotificationsView()
                    }
                }
        }
    }
}
