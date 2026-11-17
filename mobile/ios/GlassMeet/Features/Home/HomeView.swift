import SwiftUI
import UIKit

struct HomeView: View {
    @Environment(Session.self) private var session
    @Environment(AppData.self) private var data
    @Environment(Router.self) private var router
    @State private var toast: String?

    var body: some View {
        let zone = session.timeZone
        let now = Date.now
        let cal = Fmt.calendar(zone)
        // like the web dashboard: requests waiting for approval aren't meetings yet
        let on = data.bookings.filter { $0.status == "confirmed" && $0.approvalStatus != "pending" && $0.approvalStatus != "declined" }
        let upcoming = on.filter { $0.end >= now }.sorted { $0.start < $1.start }
        let today = on.filter { cal.isDate($0.start, inSameDayAs: now) }
        let requests = data.bookings.filter(\.isPending)
        let weekEnd = cal.date(byAdding: .day, value: 7, to: cal.startOfDay(for: now)) ?? now
        let week = upcoming.filter { $0.start < weekEnd }
        let shareEvent = data.eventTypes.first(where: \.isActive)

        ScrollView {
            VStack(alignment: .leading, spacing: Theme.Space.l) {
                greeting(todayCount: today.count)

                if !requests.isEmpty {
                    RequestsBanner(count: requests.count) { router.showRequests() }
                }

                // A daily digest first (what's next, then the week), like Calendly's Home;
                // the numbers come after
                if data.bookings.isEmpty, data.bookingsState.isLoaded {
                    EmptyHomeCard(event: shareEvent)
                } else if let next = upcoming.first {
                    NextMeetingCard(booking: next, event: data.eventType(next.eventTypeID), zone: zone)
                }

                if week.count > (upcoming.first == nil ? 0 : 1) {
                    UpcomingWeek(bookings: Array(week.dropFirst()), events: data.eventTypes, zone: zone)
                }

                if let shareEvent {
                    ShareLinkCard(event: shareEvent) { toast = "Link copied" }
                } else if data.eventTypesState.isLoaded {
                    CreateFirstEventCard()
                }

                VStack(alignment: .leading, spacing: Theme.Space.s) {
                    Text("At a glance")
                        .font(Theme.Typography.title3)
                        .foregroundStyle(Theme.text)
                    // every booking of the host's, as loaded (BookingsAPI.list caps at 1000)
                    StatsGrid(today: "\(today.count)", upcoming: "\(upcoming.count)",
                              all: data.bookings.count >= 1000 ? "1000+" : "\(data.bookings.count)",
                              eventTypes: "\(data.eventTypes.count)")
                }

                if let error = data.bookingsState.error, data.bookings.isEmpty {
                    ErrorStateView(message: error) { await data.loadAll() }
                }
            }
            .padding(.horizontal, Theme.Space.m)
            .padding(.bottom, Theme.Space.xxl)
        }
        .background(Theme.background)
        .overlay {
            if data.bookings.isEmpty, !data.bookingsState.isLoaded, data.bookingsState.error == nil { LoadingView() }
        }
        .navigationTitle("Home")
        .toolbar {
            // sharing a link is the main thing hosts do on the go
            ToolbarItem(placement: .topBarTrailing) {
                if data.eventTypes.contains(where: \.isActive) {
                    Menu {
                        Section("Share a booking link") {
                            ForEach(data.eventTypes.filter(\.isActive)) { event in
                                ShareLink(item: Backend.bookingURL(event.id),
                                          message: Text("Book a time with me: \(Backend.bookingURL(event.id).absoluteString)")) {
                                    Label(event.title, systemImage: "link")
                                }
                            }
                        }
                    } label: {
                        Image(systemName: "square.and.arrow.up")
                    }
                    .accessibilityLabel("Share a booking link")
                }
            }
            // booking messages: new bookings, requests and cancellations (Insights is further down)
            ToolbarItem(placement: .topBarTrailing) {
                NotificationsBell()
            }
        }
        .refreshable {
            async let all: Void = data.loadAll()
            async let inbox: Void = data.loadNotifications()
            _ = await (all, inbox)
        }
        .task { await data.loadAll() }
        .toast($toast)
    }

    private func greeting(todayCount: Int) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(greetingLine)
                .font(Theme.Typography.title3)
                .foregroundStyle(Theme.text)
            Text(todayCount == 0 ? "No meetings today." : "You have \(Fmt.plural(todayCount, "meeting")) today.")
                .font(.subheadline)
                .foregroundStyle(Theme.textSecondary)
        }
        .padding(.top, Theme.Space.xxs)
    }

    private var greetingLine: String {
        let hour = Fmt.calendar(session.timeZone).component(.hour, from: .now)
        let part = hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening"
        return session.firstName.isEmpty ? part : "\(part), \(session.firstName)"
    }
}

// MARK: - Pieces

private struct RequestsBanner: View {
    var count: Int
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: Theme.Space.s) {
                Image(systemName: "bell.badge.fill")
                    .font(.title3)
                    .foregroundStyle(Theme.warning)
                VStack(alignment: .leading, spacing: 2) {
                    Text("\(Fmt.plural(count, "request")) waiting")
                        .font(.headline)
                        .foregroundStyle(Theme.text)
                    Text("Approve or decline them in Meetings.")
                        .font(.subheadline)
                        .foregroundStyle(Theme.textSecondary)
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundStyle(Theme.textTertiary)
            }
            .padding(Theme.Space.m)
            .background(Theme.warning.opacity(0.12), in: .rect(cornerRadius: Theme.Radius.card))
            .contentShape(.rect(cornerRadius: Theme.Radius.card))
        }
        .buttonStyle(.plain)
    }
}

private struct StatsGrid: View {
    var today: String
    var upcoming: String
    var all: String
    var eventTypes: String

    var body: some View {
        let columns = [GridItem(.flexible(), spacing: Theme.Space.s), GridItem(.flexible(), spacing: Theme.Space.s)]
        LazyVGrid(columns: columns, spacing: Theme.Space.s) {
            StatTile(title: "Today", value: today, systemImage: "sun.max")
            StatTile(title: "Upcoming", value: upcoming, systemImage: "calendar")
            StatTile(title: "All bookings", value: all, systemImage: "tray.full")
            StatTile(title: "Event types", value: eventTypes, systemImage: "link")
        }
        NavigationLink(value: Route.analytics) {
            HStack(spacing: Theme.Space.s) {
                IconTile(systemImage: "chart.bar.fill", color: Theme.primary)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Insights").font(.headline).foregroundStyle(Theme.text)
                    Text("Bookings by month and your busiest times").font(.subheadline).foregroundStyle(Theme.textSecondary)
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundStyle(Theme.textTertiary)
            }
            .card()
        }
        .buttonStyle(.plain)
    }
}

private struct StatTile: View {
    var title: String
    var value: String
    var systemImage: String

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Space.xs) {
            Image(systemName: systemImage)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(Theme.link)
            Text(value)
                .font(Theme.Typography.stat)
                .foregroundStyle(Theme.text)
                .contentTransition(.numericText())
            Text(title)
                .font(.footnote)
                .foregroundStyle(Theme.textSecondary)
        }
        .padding(Theme.Space.m)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.surface, in: .rect(cornerRadius: Theme.Radius.tile))
        .accessibilityElement(children: .combine)
    }
}

private struct NextMeetingCard: View {
    var booking: Booking
    var event: EventType?
    var zone: TimeZone
    @Environment(\.openURL) private var openURL

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Space.s) {
            Text("Next meeting")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(Theme.textSecondary)
            HStack(spacing: Theme.Space.s) {
                Avatar(name: booking.name, size: 48)
                VStack(alignment: .leading, spacing: 2) {
                    Text(booking.name).font(Theme.Typography.title3).foregroundStyle(Theme.text).lineLimit(1)
                    Text(Fmt.when(booking.start, zone)).font(.subheadline.weight(.semibold)).foregroundStyle(Theme.link)
                }
                Spacer(minLength: 0)
                if booking.isPending { StatusChip(status: .request) }
            }
            HStack(spacing: 6) {
                EventDot(color: Theme.eventColor(event?.color))
                Text(booking.eventTitle).font(.subheadline).foregroundStyle(Theme.textSecondary).lineLimit(1)
            }
            HStack(spacing: Theme.Space.s) {
                if let link = booking.link ?? event?.meetingURL, let url = URL(string: link) {
                    Button { openURL(url) } label: { Label("Join", systemImage: "video.fill") }
                        .buttonStyle(PrimaryButtonStyle(compact: true))
                }
                NavigationLink(value: Route.meeting(booking.id)) {
                    Text("Open")
                }
                .buttonStyle(SecondaryButtonStyle(compact: true))
            }
            .padding(.top, Theme.Space.xxs)
        }
        .card()
    }
}

private struct UpcomingWeek: View {
    var bookings: [Booking]
    var events: [EventType]
    var zone: TimeZone

    var body: some View {
        let cal = Fmt.calendar(zone)
        let days = Dictionary(grouping: bookings) { cal.startOfDay(for: $0.start) }
        VStack(alignment: .leading, spacing: Theme.Space.s) {
            Text("Next 7 days")
                .font(Theme.Typography.title3)
                .foregroundStyle(Theme.text)
            VStack(alignment: .leading, spacing: 0) {
                ForEach(days.keys.sorted(), id: \.self) { day in
                    Text(Fmt.dayTitle(day, zone))
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(Theme.textSecondary)
                        .padding(.top, Theme.Space.s)
                        .padding(.bottom, Theme.Space.xxs)
                    ForEach(days[day] ?? []) { booking in
                        NavigationLink(value: Route.meeting(booking.id)) {
                            HStack(spacing: Theme.Space.s) {
                                Text(Fmt.time(booking.start, zone))
                                    .font(.subheadline.monospacedDigit().weight(.semibold))
                                    .foregroundStyle(Theme.text)
                                    .frame(width: 76, alignment: .leading)
                                EventDot(color: Theme.eventColor(events.first { $0.id == booking.eventTypeID }?.color), size: 8)
                                VStack(alignment: .leading, spacing: 1) {
                                    Text(booking.name).font(.subheadline.weight(.semibold)).foregroundStyle(Theme.text).lineLimit(1)
                                    Text(booking.eventTitle).font(.footnote).foregroundStyle(Theme.textSecondary).lineLimit(1)
                                }
                                Spacer(minLength: 0)
                                if booking.isPending { StatusChip(status: .request) }
                                Image(systemName: "chevron.right").font(.caption.weight(.semibold)).foregroundStyle(Theme.textTertiary)
                            }
                            .frame(minHeight: Theme.minTouch)
                            .contentShape(.rect)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .card()
        }
    }
}

private struct ShareLinkCard: View {
    var event: EventType
    var copied: () -> Void

    var body: some View {
        let url = Backend.bookingURL(event.id)
        VStack(alignment: .leading, spacing: Theme.Space.s) {
            Text("Share your booking link")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(Theme.textSecondary)
            HStack(spacing: 6) {
                EventDot(color: Theme.eventColor(event.color))
                Text(event.title).font(.headline).foregroundStyle(Theme.text).lineLimit(1)
            }
            Text(url.absoluteString)
                .font(.footnote)
                .foregroundStyle(Theme.link)
                .lineLimit(1)
                .truncationMode(.middle)
            HStack(spacing: Theme.Space.s) {
                Button {
                    UIPasteboard.general.url = url
                    copied()
                } label: {
                    Label("Copy", systemImage: "doc.on.doc")
                }
                .buttonStyle(SecondaryButtonStyle(compact: true))
                ShareLink(item: url, message: Text("Book a time with me: \(url.absoluteString)")) {
                    Label("Share", systemImage: "square.and.arrow.up")
                }
                .buttonStyle(PrimaryButtonStyle(compact: true))
            }
        }
        .card()
    }
}

private struct EmptyHomeCard: View {
    var event: EventType?

    var body: some View {
        VStack(spacing: Theme.Space.s) {
            Image(systemName: "calendar.badge.plus")
                .font(.system(size: 40))
                .foregroundStyle(Theme.primary)
            Text("Nothing booked yet")
                .font(Theme.Typography.title3)
                .foregroundStyle(Theme.text)
            Text("Share your booking link and new meetings show up here.")
                .font(.subheadline)
                .foregroundStyle(Theme.textSecondary)
                .multilineTextAlignment(.center)
            if let event {
                ShareLink(item: Backend.bookingURL(event.id)) {
                    Label("Share link", systemImage: "square.and.arrow.up")
                }
                .buttonStyle(PrimaryButtonStyle(compact: true))
                .padding(.top, Theme.Space.xxs)
            }
        }
        .padding(.vertical, Theme.Space.l)
        .frame(maxWidth: .infinity)
        .card()
    }
}

private struct CreateFirstEventCard: View {
    var body: some View {
        NavigationLink(value: Route.eventForm(.new(nil))) {
            HStack(spacing: Theme.Space.s) {
                IconTile(systemImage: "plus", color: Theme.primary)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Create your first event type").font(.headline).foregroundStyle(Theme.text)
                    Text("A meeting guests can book, like a 30 minute call.").font(.subheadline).foregroundStyle(Theme.textSecondary)
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundStyle(Theme.textTertiary)
            }
            .card()
        }
        .buttonStyle(.plain)
    }
}
