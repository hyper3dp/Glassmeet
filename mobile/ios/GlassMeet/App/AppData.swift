import Foundation
import Observation

/// Meetings and event types, shared by Home, Meetings and Scheduling so a change on one
/// screen shows on the others straight away.
@Observable @MainActor
final class AppData {
    var bookings: [Booking] = []
    var bookingsState: LoadState = .idle
    var eventTypes: [EventType] = []
    var eventTypesState: LoadState = .idle
    var notifications: [AppNotification] = []
    var notificationsState: LoadState = .idle

    var unreadCount: Int { notifications.filter { !$0.isRead }.count }

    func loadNotifications() async {
        if notifications.isEmpty { notificationsState = .loading }
        do {
            notifications = try await NotificationsAPI.list()
            notificationsState = .loaded
        } catch where AppData.isCancel(error) {
            if notificationsState == .loading { notificationsState = .idle }
        } catch {
            // a background check failing keeps the list already shown
            if notifications.isEmpty { notificationsState = .failed(error.readable) }
        }
    }

    /// Marks these read here first; puts them back if the server says no
    func markRead(_ ids: [UUID]) async -> Bool {
        let marked = Set(ids)
        guard !marked.isEmpty else { return true }
        let before = notifications
        notifications = notifications.map { n in
            var n = n
            if marked.contains(n.id) { n.read = true }
            return n
        }
        do {
            try await NotificationsAPI.markRead(ids)
            return true
        } catch {
            notifications = before
            return false
        }
    }

    func loadBookings() async {
        if bookings.isEmpty { bookingsState = .loading }
        do {
            bookings = try await BookingsAPI.list()
            bookingsState = .loaded
        } catch where AppData.isCancel(error) {
            if bookingsState == .loading { bookingsState = .idle }
        } catch {
            bookingsState = .failed(error.readable)
        }
    }

    func loadEventTypes() async {
        if eventTypes.isEmpty { eventTypesState = .loading }
        do {
            eventTypes = try await EventTypesAPI.list()
            eventTypesState = .loaded
        } catch where AppData.isCancel(error) {
            if eventTypesState == .loading { eventTypesState = .idle }
        } catch {
            eventTypesState = .failed(error.readable)
        }
    }

    func loadAll() async {
        async let b: Void = loadBookings()
        async let e: Void = loadEventTypes()
        _ = await (b, e)
    }

    func update(_ booking: Booking) {
        if let i = bookings.firstIndex(where: { $0.id == booking.id }) { bookings[i] = booking } else { bookings.insert(booking, at: 0) }
    }

    func update(_ event: EventType) {
        if let i = eventTypes.firstIndex(where: { $0.id == event.id }) { eventTypes[i] = event } else { eventTypes.insert(event, at: 0) }
    }

    func remove(eventType id: UUID) {
        eventTypes.removeAll { $0.id == id }
    }

    func eventType(_ id: UUID?) -> EventType? {
        guard let id else { return nil }
        return eventTypes.first { $0.id == id }
    }

    nonisolated static func isCancel(_ error: Error) -> Bool {
        error is CancellationError || (error as? URLError)?.code == .cancelled
    }

    func reset() {
        bookings = []
        eventTypes = []
        notifications = []
        notificationsState = .idle
        bookingsState = .idle
        eventTypesState = .idle
    }
}
