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
        bookingsState = .idle
        eventTypesState = .idle
    }
}
