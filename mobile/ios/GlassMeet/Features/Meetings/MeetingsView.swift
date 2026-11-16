import SwiftUI

struct MeetingsView: View {
    @Environment(Session.self) private var session
    @Environment(AppData.self) private var data
    @Environment(Router.self) private var router
    @State private var query = ""
    @State private var toast: String?
    @State private var error: String?
    @Environment(\.openURL) private var openURL

    var body: some View {
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
                        // quick actions without opening the meeting, like Calendly and Cal.com
                        .swipeActions(edge: .leading, allowsFullSwipe: true) {
                            if booking.isPending {
                                Button { Task { await approve(booking) } } label: { Label("Approve", systemImage: "checkmark") }
                                    .tint(Theme.success)
                            }
                        }
                        .swipeActions(edge: .trailing, allowsFullSwipe: false) { quickActions(booking, now: now) }
                        .contextMenu {
                            if booking.isPending {
                                Button { Task { await approve(booking) } } label: { Label("Approve", systemImage: "checkmark") }
                            }
                            quickActions(booking, now: now)
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
        .floatingBar(edge: .top) {
            if query.isEmpty {
                // chips scroll sideways, so the requests count never truncates at 375 pt
                ScrollView(.horizontal) {
                    HStack(spacing: Theme.Space.xs) {
                        FilterChip(title: "Upcoming", selected: router.meetingFilter == .upcoming) { router.meetingFilter = .upcoming }
                        FilterChip(title: "Requests", count: requestCount, selected: router.meetingFilter == .requests) { router.meetingFilter = .requests }
                        FilterChip(title: "Past", selected: router.meetingFilter == .past) { router.meetingFilter = .past }
                        FilterChip(title: "Cancelled", selected: router.meetingFilter == .cancelled) { router.meetingFilter = .cancelled }
                    }
                    .padding(.horizontal, Theme.Space.m)
                }
                .scrollIndicators(.hidden)
                .padding(.bottom, Theme.Space.xxs)
                .sensoryFeedback(.selection, trigger: router.meetingFilter)
            }
        }
        .refreshable { await data.loadBookings() }
        .task { await data.loadAll() }
        .errorAlert($error)
        .toast($toast)
    }

    /// Message the guest on WhatsApp, or join the call while the meeting is still ahead
    @ViewBuilder private func quickActions(_ b: Booking, now: Date) -> some View {
        if let link = b.link ?? data.eventType(b.eventTypeID)?.meetingURL, let url = URL(string: link), b.isUpcoming(at: now), !b.isPending {
            Button { openURL(url) } label: { Label("Join", systemImage: "video.fill") }
                .tint(Theme.link)
        }
        if b.phone != nil {
            Button { openURL(Messages.whatsAppURL(phone: b.phone, message: "")) } label: { Label("WhatsApp", systemImage: "message.fill") }
                .tint(Theme.primary)
        }
    }

    private func approve(_ b: Booking) async {
        do {
            let saved = try await BookingsAPI.approve(b.id)
            withAnimation { data.update(saved) }
            toast = "Approved. Open it to message \(b.name)."
        } catch {
            self.error = "This request couldn’t be approved. \(error.readable)"
        }
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

/// A filter capsule with an optional count badge (Requests 3)
private struct FilterChip: View {
    var title: String
    var count: Int = 0
    var selected: Bool
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Text(title).font(.subheadline.weight(.semibold))
                if count > 0 {
                    Text("\(count)")
                        .font(.caption.weight(.bold).monospacedDigit())
                        .foregroundStyle(selected ? Theme.primary : Theme.onPrimary)
                        .padding(.horizontal, 6)
                        .frame(minWidth: 20, minHeight: 20)
                        .background(selected ? Theme.onPrimary : Theme.warning, in: Capsule())
                }
            }
            .foregroundStyle(selected ? Theme.onPrimary : Theme.text)
            .padding(.horizontal, 14)
            .frame(minHeight: 36)
            .background(selected ? Theme.primary : Theme.surface, in: Capsule())
            .overlay(Capsule().strokeBorder(selected ? .clear : Theme.border, lineWidth: 1))
            .frame(minHeight: Theme.minTouch)
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(count > 0 ? "\(title), \(count)" : title)
        .accessibilityAddTraits(selected ? .isSelected : [])
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
