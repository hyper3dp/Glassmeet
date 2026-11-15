import Foundation
import Observation

/// The five sections, drawn by the system tab bar
enum AppTab: Hashable {
    case home, meetings, scheduling, availability, settings
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
    var availabilityPath: [Route] = []
    var settingsPath: [Route] = []
    var meetingFilter: MeetingFilter = .upcoming
    /// A glassmeet://calendar-connections return that arrived from outside the app
    var calendarCallback: URL?

    func showRequests() {
        meetingFilter = .requests
        meetingsPath = []
        tab = .meetings
    }

    func openCalendarConnections(with callback: URL?) {
        calendarCallback = callback
        tab = .settings
        settingsPath = [.calendarConnections]
    }

    func reset() {
        tab = .home
        homePath = []
        meetingsPath = []
        schedulingPath = []
        availabilityPath = []
        settingsPath = []
        meetingFilter = .upcoming
    }
}
