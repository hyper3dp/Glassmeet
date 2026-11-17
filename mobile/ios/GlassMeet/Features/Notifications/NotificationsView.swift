import SwiftUI

/// Booking messages: new bookings, requests, cancellations and holiday notes, the same list as
/// the web's bell. Tapping one marks it read and opens what it is about.
struct NotificationsView: View {
    @Environment(AppData.self) private var data
    @Environment(Router.self) private var router
    @State private var error: String?

    var body: some View {
        List {
            ForEach(data.notifications) { n in
                Button { open(n) } label: { NotificationRow(notification: n) }
                    .buttonStyle(.plain)
                    .listRowBackground(n.isRead ? Theme.surface : Theme.primarySoft.opacity(0.6))
                    .swipeActions(edge: .leading) {
                        if !n.isRead {
                            Button("Mark read", systemImage: "envelope.open") { Task { await mark([n.id]) } }
                                .tint(Theme.primary)
                        }
                    }
            }
        }
        .themedList()
        .overlay {
            if data.notifications.isEmpty {
                if let message = data.notificationsState.error {
                    ErrorStateView(message: message) { await data.loadNotifications() }
                } else if data.notificationsState.isLoaded {
                    EmptyStateView(title: "No booking messages", systemImage: "bell",
                                   message: "New bookings, requests and cancellations show up here.")
                } else {
                    LoadingView()
                }
            }
        }
        .navigationTitle("Booking messages")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if data.unreadCount > 0 {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Mark all read") { Task { await mark(data.notifications.filter { !$0.isRead }.map(\.id)) } }
                }
            }
        }
        .refreshable { await data.loadNotifications() }
        .task { await data.loadNotifications() }
        .errorAlert($error)
    }

    private func mark(_ ids: [UUID]) async {
        let ok = await data.markRead(ids)
        if !ok { error = "Couldn’t mark them read. Try again." }
    }

    private func open(_ n: AppNotification) {
        if !n.isRead { Task { _ = await data.markRead([n.id]) } }
        if let link = n.link, !link.isEmpty { router.open(webPath: link) }
    }
}

private struct NotificationRow: View {
    var notification: AppNotification

    var body: some View {
        HStack(alignment: .top, spacing: Theme.Space.s) {
            Image(systemName: notification.systemImage)
                .font(.body.weight(.semibold))
                .foregroundStyle(Theme.link)
                .frame(width: 36, height: 36)
                .background(Theme.primarySoft, in: .circle)
            VStack(alignment: .leading, spacing: 2) {
                Text(notification.title ?? "Notification")
                    .font(notification.isRead ? .subheadline : .subheadline.weight(.semibold))
                    .foregroundStyle(Theme.text)
                if let message = notification.message, !message.isEmpty {
                    Text(message).font(.footnote).foregroundStyle(Theme.textSecondary).lineLimit(3)
                }
                if let date = notification.date {
                    Text(date.formatted(.relative(presentation: .named)))
                        .font(.caption)
                        .foregroundStyle(Theme.textTertiary)
                }
            }
            Spacer(minLength: 0)
            if !notification.isRead {
                Circle().fill(Theme.primary).frame(width: 8, height: 8).padding(.top, 6)
                    .accessibilityLabel("Unread")
            }
        }
        .padding(.vertical, 2)
        .contentShape(.rect)
        .accessibilityElement(children: .combine)
    }
}

/// The bell in Home's toolbar, with the unread count
struct NotificationsBell: View {
    @Environment(AppData.self) private var data

    var body: some View {
        NavigationLink(value: Route.notifications) {
            Image(systemName: data.unreadCount > 0 ? "bell.badge" : "bell")
                .symbolRenderingMode(data.unreadCount > 0 ? .multicolor : .monochrome)
        }
        .accessibilityLabel(data.unreadCount > 0 ? "Booking messages, \(data.unreadCount) unread" : "Booking messages")
    }
}
