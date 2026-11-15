import SwiftUI

struct HomeView: View {
    @Environment(Account.self) private var account
    @State private var bookings: [Booking] = []
    @State private var eventTypes: [EventType] = []
    @State private var loaded = false
    @State private var error: String?

    var body: some View {
        let upcoming = bookings.filter { $0.isUpcoming() }
        let requests = bookings.filter(\.isPending)
        let links = eventTypes.filter(\.active)

        List {
            Section("Next meeting") {
                if let next = upcoming.first {
                    NavigationLink(value: Route.meeting(next.id)) {
                        VStack(alignment: .leading, spacing: 6) {
                            Text(next.guestName).font(.title2.bold())
                            Label(Format.when(next.start), systemImage: "calendar")
                                .foregroundStyle(Color.gmLink)
                            Label(next.eventName ?? "Meeting", systemImage: "link")
                                .foregroundStyle(.secondary)
                        }
                        .padding(.vertical, 6)
                    }
                } else if loaded {
                    ContentUnavailableView("Nothing booked yet", systemImage: "calendar",
                                           description: Text("Share a booking link and new meetings will show up here."))
                }
            }

            if !requests.isEmpty {
                Section("Waiting for your reply") {
                    ForEach(requests.prefix(5)) { MeetingRow(booking: $0) }
                }
            }

            if upcoming.count > 1 {
                Section("Coming up") {
                    ForEach(upcoming.dropFirst().prefix(4)) { MeetingRow(booking: $0) }
                }
            }

            Section {
                if links.isEmpty {
                    NavigationLink("Create your first event type", value: Route.eventType(nil))
                } else {
                    ForEach(links.prefix(4)) { event in
                        ShareLink(item: Backend.bookingURL(event.id)) {
                            HStack {
                                VStack(alignment: .leading) {
                                    Text(event.name).foregroundStyle(.primary)
                                    Text("\(event.duration) min").font(.subheadline).foregroundStyle(.secondary)
                                }
                                Spacer()
                                Image(systemName: "square.and.arrow.up")
                            }
                        }
                    }
                }
            } header: {
                Text("Share a booking link")
            } footer: {
                Text("Guests open the link, pick a time and book. They never need an account.")
            }
        }
        .navigationTitle(account.hostName.isEmpty ? "Home" : "Hello, \(account.hostName.split(separator: " ").first.map(String.init) ?? "")")
        .overlay { if !loaded { ProgressView() } }
        .refreshable { await load() }
        .task { await load() }
        .errorAlert($error)
    }

    private func load() async {
        do {
            async let b = API.bookings()
            async let e = API.eventTypes()
            (bookings, eventTypes) = try await (b, e)
        } catch {
            self.error = error.readable
        }
        loaded = true
    }
}

struct MeetingRow: View {
    var booking: Booking
    var body: some View {
        NavigationLink(value: Route.meeting(booking.id)) {
            HStack(spacing: 12) {
                Avatar(name: booking.guestName)
                VStack(alignment: .leading, spacing: 2) {
                    Text(booking.guestName)
                    Text("\(Format.when(booking.start)) · \(booking.eventName ?? "Meeting")")
                        .font(.subheadline).foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
                if booking.isPending {
                    StatusBadge(text: "Request", color: .orange)
                } else if booking.isCancelled {
                    StatusBadge(text: "Cancelled", color: .secondary)
                }
            }
        }
    }
}

struct MeetingsView: View {
    enum Filter: String, CaseIterable { case upcoming = "Upcoming", requests = "Requests", past = "Past" }
    @State private var filter: Filter = .upcoming
    @State private var bookings: [Booking] = []
    @State private var loaded = false
    @State private var error: String?

    var body: some View {
        let shown: [Booking] = switch filter {
        case .upcoming: bookings.filter { $0.isUpcoming() }
        case .requests: bookings.filter(\.isPending)
        case .past: Array(bookings.filter { !$0.isUpcoming() && !$0.isPending }.reversed())
        }
        List {
            if shown.isEmpty, loaded {
                ContentUnavailableView {
                    Label(emptyTitle, systemImage: "person.2")
                } description: {
                    Text(emptyMessage)
                }
                .listRowBackground(Color.clear)
            }
            ForEach(shown) { MeetingRow(booking: $0) }
        }
        .navigationTitle("Meetings")
        .safeAreaInset(edge: .top) {
            Picker("Show", selection: $filter) {
                ForEach(Filter.allCases, id: \.self) { Text($0.rawValue) }
            }
            .pickerStyle(.segmented)
            .padding(.horizontal)
            .padding(.bottom, 8)
        }
        .overlay { if !loaded { ProgressView() } }
        .refreshable { await load() }
        .task { await load() }
        .errorAlert($error)
    }

    private var emptyTitle: String {
        switch filter {
        case .upcoming: "No upcoming meetings"
        case .requests: "No requests"
        case .past: "No past meetings"
        }
    }
    private var emptyMessage: String {
        switch filter {
        case .upcoming: "Meetings booked from your links appear here."
        case .requests: "Bookings for event types that need your approval wait here."
        case .past: "Finished and cancelled meetings are kept here."
        }
    }

    private func load() async {
        do { bookings = try await API.bookings() } catch { self.error = error.readable }
        loaded = true
    }
}
