import Foundation
import Observation

/// Weekly hours and date overrides. Every change shows at once and saves behind it, one row at
/// a time (update, insert or delete), in the order the changes were made.
@Observable @MainActor
final class AvailabilityModel {
    struct Range: Identifiable, Equatable {
        let id: UUID
        /// nil until the insert comes back
        var serverID: UUID?
        var day: Int
        var start: Int
        var end: Int
    }

    var ranges: [Range] = []
    var exceptions: [AvailabilityException] = []
    var state: LoadState = .idle
    var saving = 0
    var saveError: String?

    private var queue: Task<Void, Never>?
    private var pending: [UUID: Task<Void, Never>] = [:]

    func load() async {
        if ranges.isEmpty && exceptions.isEmpty { state = .loading }
        do {
            async let r = AvailabilityAPI.rules()
            async let e = AvailabilityAPI.exceptions()
            let (rules, excs) = try await (r, e)
            ranges = rules.map { Range(id: $0.id, serverID: $0.id, day: $0.dayOfWeek, start: Fmt.minutes($0.startTime), end: endMinutes($0.endTime)) }
                .sorted { $0.start < $1.start }
            exceptions = excs
            state = .loaded
        } catch where AppData.isCancel(error) {
            if state == .loading { state = .idle }
        } catch {
            state = .failed(error.readable)
        }
    }

    /// "00:00" as an end time means midnight at the end of the day
    private func endMinutes(_ value: String) -> Int {
        let m = Fmt.minutes(value)
        return m == 0 ? 1440 : m
    }

    func ranges(on day: Int) -> [Range] { ranges.filter { $0.day == day } }

    // MARK: Weekly hours

    func setDay(_ day: Int, on: Bool) {
        if on {
            guard ranges(on: day).isEmpty else { return }
            add(day: day, start: 9 * 60, end: 17 * 60)
        } else {
            for r in ranges(on: day) { remove(r.id) }
        }
    }

    /// An hour after the day's last range, for an hour (shorter near midnight). nil: day is full.
    func nextRange(on day: Int) -> (Int, Int)? {
        let lastEnd = ranges(on: day).map(\.end).max() ?? 0
        guard lastEnd < 1440 else { return nil }
        let start = lastEnd + 60 < 1440 ? lastEnd + 60 : lastEnd
        return (start, min(start + 60, 1440))
    }

    func addRange(on day: Int) {
        guard let next = nextRange(on: day) else { return }
        add(day: day, start: next.0, end: next.1)
    }

    private func add(day: Int, start: Int, end: Int) {
        let local = Range(id: UUID(), serverID: nil, day: day, start: start, end: end)
        ranges.append(local)
        enqueue { [weak self] in
            let saved = try await AvailabilityAPI.insertRule(day: day, start: start, end: end)
            guard let self else { return }
            guard let i = self.ranges.firstIndex(where: { $0.id == local.id }) else {
                // removed while it was saving
                try await AvailabilityAPI.deleteRule(saved.id)
                return
            }
            self.ranges[i].serverID = saved.id
            let now = self.ranges[i]
            if now.start != start || now.end != end {
                try await AvailabilityAPI.updateRule(saved.id, start: now.start, end: now.end)
            }
        }
    }

    func setStart(_ id: UUID, _ minutes: Int) {
        guard let i = ranges.firstIndex(where: { $0.id == id }) else { return }
        ranges[i].start = minutes
        // keep the range valid: moving one end past the other pushes it along
        if minutes >= ranges[i].end { ranges[i].end = min(minutes + 60, 1440) }
        scheduleUpdate(id)
    }

    func setEnd(_ id: UUID, _ minutes: Int) {
        guard let i = ranges.firstIndex(where: { $0.id == id }) else { return }
        let value = minutes == 0 ? 1440 : minutes
        ranges[i].end = value
        if value <= ranges[i].start { ranges[i].start = max(value - 60, 0) }
        scheduleUpdate(id)
    }

    func remove(_ id: UUID) {
        guard let i = ranges.firstIndex(where: { $0.id == id }) else { return }
        let serverID = ranges[i].serverID
        ranges.remove(at: i)
        pending[id]?.cancel()
        pending[id] = nil
        if let serverID { enqueue { try await AvailabilityAPI.deleteRule(serverID) } }
    }

    /// Time pickers send many changes while they turn: save the last one after a short pause
    private func scheduleUpdate(_ id: UUID) {
        pending[id]?.cancel()
        pending[id] = Task { [weak self] in
            try? await Task.sleep(for: .milliseconds(600))
            guard !Task.isCancelled, let self else { return }
            self.pending[id] = nil
            guard let r = self.ranges.first(where: { $0.id == id }), let serverID = r.serverID else { return }
            let start = r.start, end = r.end
            self.enqueue { try await AvailabilityAPI.updateRule(serverID, start: start, end: end) }
        }
    }

    // MARK: Date overrides

    func addException(date: Date, type: String, start: Int?, end: Int?, label: String?) {
        enqueue { [weak self] in
            let saved = try await AvailabilityAPI.addException(date: Fmt.localKey(date), type: type, start: start, end: end, label: label)
            self?.exceptions.append(saved)
            self?.exceptions.sort { $0.date < $1.date }
        }
    }

    func removeException(_ id: UUID) {
        exceptions.removeAll { $0.id == id }
        enqueue { try await AvailabilityAPI.deleteException(id) }
    }

    // MARK: Save queue

    private func enqueue(_ work: @escaping @MainActor () async throws -> Void) {
        let previous = queue
        saving += 1
        queue = Task { [weak self] in
            await previous?.value
            do {
                try await work()
                self?.saveError = nil
            } catch {
                self?.saveError = "Your last change didn’t save, so your saved hours were reloaded. \(error.readable)"
                await self?.load()
            }
            self?.saving -= 1
        }
    }
}
