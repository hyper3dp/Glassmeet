import Foundation
import Observation

/// The tab bar: four sections and a Create button in the middle
enum AppTab: Hashable {
    case home, meetings, create, scheduling, settings
}

/// Pages pushed inside a tab
enum Route: Hashable {
    case meeting(UUID)
    case eventForm(EventFormSource)
    case analytics
    case profile
    case whatsApp
    case calendarConnections
    case addresses
    case availability
    case notifications
}

/// What the event type form opens with
enum EventFormSource: Hashable {
    case edit(UUID)
    /// A new event type, optionally filled from a template or a copy
    case new(EventType?)
}

enum MeetingFilter: String, CaseIterable, Identifiable {
    case upcoming, requests, past, cancelled
    var id: String { rawValue }
}

/// The selected tab and each tab's navigation path, so any screen (or a glassmeet:// link) can
/// send the host somewhere else.
@Observable @MainActor
final class Router {
    var tab: AppTab = .home
    var homePath: [Route] = []
    var meetingsPath: [Route] = []
    var schedulingPath: [Route] = []
    var settingsPath: [Route] = []
    var meetingFilter: MeetingFilter = .upcoming
    /// A glassmeet://calendar-connections return that arrived from outside the app
    var calendarCallback: URL?
    /// The tab bar's Create button: Scheduling opens its "New event type" sheet
    var pickingTemplate = false

    /// The tab bar selection; Create never stays selected, it opens the new event type sheet
    var tabSelection: AppTab {
        get { tab }
        set {
            if newValue == .create { createEventType() } else { tab = newValue }
        }
    }

    func createEventType() {
        tab = .scheduling
        schedulingPath = []
        pickingTemplate = true
    }

    func showRequests() {
        meetingFilter = .requests
        meetingsPath = []
        tab = .meetings
    }

    func openMeeting(_ id: UUID) {
        tab = .meetings
        meetingsPath = [.meeting(id)]
    }

    func openAvailability() {
        tab = .scheduling
        schedulingPath = [.availability]
    }

    func openCalendarConnections(with callback: URL?) {
        calendarCallback = callback
        tab = .settings
        settingsPath = [.calendarConnections]
    }

    /// A notification's web link (/bookings?id=…, /availability…, /calendar-connections)
    func open(webPath link: String) {
        guard let parts = URLComponents(string: link) else { return }
        let path = parts.path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        switch path {
        case "bookings":
            if let id = parts.queryItems?.first(where: { $0.name == "id" })?.value.flatMap(UUID.init(uuidString:)) {
                openMeeting(id)
            } else {
                meetingsPath = []
                tab = .meetings
            }
        case "availability": openAvailability()
        case "calendar-connections": openCalendarConnections(with: nil)
        case "event-types": tab = .scheduling; schedulingPath = []
        default: break
        }
    }

    func reset() {
        tab = .home
        homePath = []
        meetingsPath = []
        schedulingPath = []
        settingsPath = []
        meetingFilter = .upcoming
        pickingTemplate = false
    }
}
