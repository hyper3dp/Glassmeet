import SwiftUI

struct MeetingsView: View {
    @Environment(Session.self) private var session
    @Environment(AppData.self) private var data
    @Environment(Router.self) private var router
    @State private var query = ""

    var body: some View {
        @Bindable var router = router
        let zone = session.timeZone
        let now = Date.now
        let kept = data.bookings.filter { !$0.isExpiredCancellation(at: now) }
        let requestCount = kept.filter(\.isPending).count
        let shown = filtered(kept, now: now)
        let cal = Fmt.calendar(zone)
        let groups = group(shown, cal: cal)

        List {
            if shown.isEmpty, data.bookingsState.isLoaded {
                Section {
                    empty
                }
                .listRowBackground(Color.clear)
            }
            ForEach(groups, id: \.day) { group in
                Section {
                    ForEach(group.items) { booking in
                        NavigationLink(value: Route.meeting(booking.id)) {
                            MeetingRow(booking: booking, event: data.eventType(booking.eventTypeID), zone: zone)
                        }
                    }
                } header: {
                    Text(Fmt.dayTitle(group.day, zone))
                }
                .listRowBackground(Theme.surface)
            }
        }
        .themedList()
        .overlay {
            if data.bookings.isEmpty, !data.bookingsState.isLoaded {
                if let error = data.bookingsState.error {
                    ErrorStateView(message: error) { await data.loadBookings() }
                } else {
                    LoadingView()
                }
            }
        }
        .navigationTitle("Meetings")
        .searchable(text: $query, prompt: "Name, email, event or phone")
        .safeAreaBar(edge: .top) {
            if query.isEmpty {
                Picker("Show", selection: $router.meetingFilter) {
                    Text("Upcoming").tag(MeetingFilter.upcoming)
                    Text(requestCount > 0 ? "Requests (\(requestCount))" : "Requests").tag(MeetingFilter.requests)
                    Text("Past").tag(MeetingFilter.past)
                    Text("Cancelled").tag(MeetingFilter.cancelled)
                }
                .pickerStyle(.segmented)
                .padding(.horizontal, Theme.Space.m)
                .padding(.bottom, Theme.Space.xs)
            }
        }
        .refreshable { await data.loadBookings() }
        .task { await data.loadAll() }
    }

    private func filtered(_ list: [Booking], now: Date) -> [Booking] {
        if !query.trimmingCharacters(in: .whitespaces).isEmpty {
            return list.filter { $0.matches(query) }.sorted { $0.start > $1.start }
        }
        switch router.meetingFilter {
        case .upcoming:
            return list.filter { $0.isUpcoming(at: now) }.sorted { $0.start < $1.start }
        case .requests:
            return list.filter(\.isPending).sorted { $0.start < $1.start }
        case .past:
            return list.filter { !$0.isUpcoming(at: now) && !$0.isCancelled }.sorted { $0.start > $1.start }
        case .cancelled:
            return list.filter(\.isCancelled).sorted { $0.start > $1.start }
        }
    }

    private struct DayGroup { var day: Date; var items: [Booking] }

    /// Consecutive meetings on the same day, in the list's order
    private func group(_ list: [Booking], cal: Calendar) -> [DayGroup] {
        var groups: [DayGroup] = []
        for booking in list {
            let day = cal.startOfDay(for: booking.start)
            if let last = groups.indices.last, groups[last].day == day {
                groups[last].items.append(booking)
            } else {
                groups.append(DayGroup(day: day, items: [booking]))
            }
        }
        return groups
    }

    @ViewBuilder private var empty: some View {
        if !query.isEmpty {
            EmptyStateView(title: "No matches", systemImage: "magnifyingglass", message: "No meeting matches “\(query)”.")
        } else {
            switch router.meetingFilter {
            case .upcoming:
                EmptyStateView(title: "No upcoming meetings", systemImage: "calendar",
                               message: "Meetings booked from your links show up here.")
            case .requests:
                EmptyStateView(title: "No requests", systemImage: "tray",
                               message: "Bookings for event types that need your approval wait here.")
            case .past:
                EmptyStateView(title: "No past meetings", systemImage: "clock.arrow.circlepath",
                               message: "Meetings you’ve had are kept here.")
            case .cancelled:
                EmptyStateView(title: "Nothing cancelled", systemImage: "xmark.circle",
                               message: "Cancelled and declined meetings show here for two days.")
            }
        }
    }
}

/// Avatar, guest, time range, event with its colour dot, status chip
struct MeetingRow: View {
    var booking: Booking
    var event: EventType?
    var zone: TimeZone

    var body: some View {
        HStack(spacing: Theme.Space.s) {
            Avatar(name: booking.name, size: 40)
            VStack(alignment: .leading, spacing: 3) {
                Text(booking.name)
                    .font(.headline)
                    .foregroundStyle(Theme.text)
                    .lineLimit(1)
                Text(Fmt.timeRange(booking.start, booking.end, zone))
                    .font(.subheadline.monospacedDigit())
                    .foregroundStyle(Theme.textSecondary)
                HStack(spacing: 6) {
                    EventDot(color: Theme.eventColor(event?.color), size: 8)
                    Text(booking.eventTitle)
                        .font(.footnote)
                        .foregroundStyle(Theme.textSecondary)
                        .lineLimit(1)
                }
            }
            Spacer(minLength: 0)
            let status = BookingStatus(booking)
            if status == .request || status == .cancelled || status == .declined {
                StatusChip(status: status)
            }
        }
        .padding(.vertical, 4)
        .accessibilityElement(children: .combine)
    }
}
