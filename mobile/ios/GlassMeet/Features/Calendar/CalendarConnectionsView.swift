import AuthenticationServices
import SwiftUI
import UIKit

/// Google Calendar, Microsoft Outlook / Teams and the subscribe feed. Connecting opens the
/// provider in ASWebAuthenticationSession, which comes back to glassmeet://calendar-connections.
struct CalendarConnectionsView: View {
    @Environment(Router.self) private var router
    @Environment(\.webAuthenticationSession) private var webAuthenticationSession
    @Environment(\.openURL) private var openURL

    @State private var loaded = false
    @State private var google: GoogleAccount?
    @State private var googleUnavailable = false
    @State private var microsoft: ProviderConnection?
    @State private var microsoftAvailable = false
    @State private var feedToken: String?
    @State private var feedError: String?
    @State private var busy: Busy?
    @State private var calendars: [GoogleCalendar]?
    @State private var pickingCalendar = false
    @State private var confirm: Confirm?
    @State private var error: String?
    @State private var toast: String?

    private enum Busy: Equatable { case googleConnect, googleSync, googleSettings, googleDisconnect, msConnect, msSync, msSettings, msDisconnect, feed }
    private enum Confirm: Identifiable { case googleDisconnect, msDisconnect, resetFeed; var id: Self { self } }

    var body: some View {
        List {
            googleSection
            if microsoftAvailable || microsoft != nil { microsoftSection }
            feedSection
        }
        .themedList()
        .overlay { if !loaded { LoadingView() } }
        .navigationTitle("Calendar connections")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $pickingCalendar) {
            CalendarPicker(calendars: calendars ?? [], selected: google?.calendarID ?? "primary") { id in
                Task { await saveCalendar(id) }
            }
        }
        .confirmationDialog(confirmTitle, isPresented: Binding(get: { confirm != nil }, set: { if !$0 { confirm = nil } }),
                            titleVisibility: .visible, presenting: confirm) { which in
            switch which {
            case .googleDisconnect: Button("Disconnect", role: .destructive) { Task { await googleDisconnect() } }
            case .msDisconnect: Button("Disconnect", role: .destructive) { Task { await msDisconnect() } }
            case .resetFeed: Button("Reset link", role: .destructive) { Task { await resetFeed() } }
            }
            Button("Keep it", role: .cancel) {}
        } message: { which in
            switch which {
            case .googleDisconnect: Text("Busy times in Google stop hiding slots and new bookings stop being added. Events already in Google stay there.")
            case .msDisconnect: Text("Busy times in Outlook stop hiding slots and new bookings stop being added.")
            case .resetFeed: Text("Calendars subscribed to the old link stop updating. Subscribe again with the new one.")
            }
        }
        .refreshable { await load() }
        .task { if !loaded { await load() } }
        .task(id: router.calendarCallback) {
            if let url = router.calendarCallback {
                router.calendarCallback = nil
                await finish(url)
            }
        }
        .errorAlert($error)
        .toast($toast)
    }

    private var confirmTitle: String {
        switch confirm {
        case .googleDisconnect: "Disconnect Google Calendar?"
        case .msDisconnect: "Disconnect Outlook?"
        case .resetFeed: "Reset your calendar link?"
        case nil: ""
        }
    }

    // MARK: Google

    private var googleSection: some View {
        Section {
            ProviderHeader(name: "Google Calendar", systemImage: "calendar", color: Color(uiColor: UIColor(hex: 0x4285F4)),
                           status: googleStatusText, statusColor: google?.connected == true ? Theme.success : google != nil ? Theme.warning : Theme.textSecondary)
            if googleUnavailable, google == nil {
                Text("Not available yet").font(.subheadline).foregroundStyle(Theme.textSecondary)
            } else if let google, google.connected == true {
                Button { Task { await openCalendars() } } label: {
                    LabeledContent("Calendar", value: google.calendarLabel)
                }
                .tint(Theme.text)
                .disabled(busy != nil)
                Toggle("Auto-sync new bookings", isOn: Binding(get: { google.autoSync ?? true }, set: { v in Task { await googleAutoSync(v) } }))
                    .tint(Theme.primary)
                    .disabled(busy != nil)
                Button { Task { await googleSync() } } label: {
                    HStack {
                        Label("Sync now", systemImage: "arrow.triangle.2.circlepath")
                        Spacer()
                        if busy == .googleSync { ProgressView() }
                        else if let synced = Fmt.timestamp(google.syncedDate) {
                            Text("Last synced \(synced.formatted(.relative(presentation: .named)))").font(.footnote).foregroundStyle(Theme.textSecondary)
                        }
                    }
                }
                .foregroundStyle(Theme.link)
                .disabled(busy != nil)
                Button("Disconnect", role: .destructive) { confirm = .googleDisconnect }
                    .disabled(busy != nil)
            } else {
                connectButton(google == nil ? "Connect Google Calendar" : "Reconnect Google Calendar", busy: busy == .googleConnect) {
                    Task { await connectGoogle() }
                }
            }
        } footer: {
            Text("GlassMeet hides times you’re busy in Google and adds confirmed bookings to your calendar.")
        }
        .listRowBackground(Theme.surface)
    }

    private var googleStatusText: String {
        guard let google else { return googleUnavailable ? "Not available yet" : "Not connected" }
        guard google.connected == true else { return "Needs reconnecting" }
        return google.email.map { "Connected as \($0)" } ?? "Connected"
    }

    // MARK: Microsoft

    private var microsoftSection: some View {
        Section {
            ProviderHeader(name: "Microsoft Outlook / Teams", systemImage: "envelope.badge", color: Color(uiColor: UIColor(hex: 0x0078D4)),
                           status: msStatusText, statusColor: microsoft?.connected == true ? Theme.success : microsoft != nil ? Theme.warning : Theme.textSecondary)
            if let microsoft, microsoft.connected == true {
                Toggle("Auto-sync new bookings", isOn: Binding(get: { microsoft.autoSync ?? true }, set: { v in Task { await msAutoSync(v) } }))
                    .tint(Theme.primary)
                    .disabled(busy != nil)
                Button { Task { await msSync() } } label: {
                    HStack {
                        Label("Sync now", systemImage: "arrow.triangle.2.circlepath")
                        Spacer()
                        if busy == .msSync { ProgressView() }
                        else if let synced = Fmt.timestamp(microsoft.syncedDate) {
                            Text("Last synced \(synced.formatted(.relative(presentation: .named)))").font(.footnote).foregroundStyle(Theme.textSecondary)
                        }
                    }
                }
                .foregroundStyle(Theme.link)
                .disabled(busy != nil)
                Button("Disconnect", role: .destructive) { confirm = .msDisconnect }
                    .disabled(busy != nil)
            } else {
                connectButton(microsoft == nil ? "Connect Outlook" : "Reconnect Outlook", busy: busy == .msConnect) {
                    Task { await connectMicrosoft() }
                }
            }
        } footer: {
            Text(microsoft?.teams == true ? "Teams meetings are created for Microsoft Teams events." : "Work and school accounts can also create Teams meetings.")
        }
        .listRowBackground(Theme.surface)
    }

    private var msStatusText: String {
        guard let microsoft else { return "Not connected" }
        guard microsoft.connected == true else { return "Needs reconnecting" }
        return microsoft.email.map { "Connected as \($0)" } ?? "Connected"
    }

    // MARK: Feed

    private var feedSection: some View {
        Section {
            if let feedToken {
                let url = Backend.feedURL(token: feedToken)
                Text(url.absoluteString)
                    .font(.footnote.monospaced())
                    .foregroundStyle(Theme.textSecondary)
                    .lineLimit(2)
                    .truncationMode(.middle)
                    .textSelection(.enabled)
                Button {
                    UIPasteboard.general.url = url
                    toast = "Feed link copied"
                } label: {
                    Label("Copy link", systemImage: "doc.on.doc")
                }
                .foregroundStyle(Theme.link)
                if let webcal = URL(string: url.absoluteString.replacingOccurrences(of: "https:", with: "webcal:")) {
                    Button { openURL(webcal) } label: { Label("Add to Apple Calendar", systemImage: "apple.logo") }
                        .foregroundStyle(Theme.link)
                    if let google = URL(string: "https://calendar.google.com/calendar/r?cid=\(webcal.absoluteString.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? "")") {
                        Button { openURL(google) } label: { Label("Add to Google Calendar", systemImage: "calendar.badge.plus") }
                            .foregroundStyle(Theme.link)
                    }
                }
                if let outlook = URL(string: "https://outlook.live.com/calendar/0/addfromweb?url=\(url.absoluteString.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? "")&name=GlassMeet") {
                    Button { openURL(outlook) } label: { Label("Add to Outlook", systemImage: "envelope") }
                        .foregroundStyle(Theme.link)
                }
                Button(role: .destructive) { confirm = .resetFeed } label: {
                    HStack {
                        Text("Reset link")
                        if busy == .feed { Spacer(); ProgressView() }
                    }
                }
                .disabled(busy != nil)
            } else if let feedError {
                Text(feedError).font(.subheadline).foregroundStyle(Theme.textSecondary)
            } else {
                ProgressView()
            }
        } header: {
            Text("Subscribe to your meetings")
        } footer: {
            Text("Any calendar app can subscribe to this private link and shows your GlassMeet meetings. Keep it to yourself.")
        }
        .listRowBackground(Theme.surface)
    }

    private func connectButton(_ title: String, busy isBusy: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                Text(title).fontWeight(.semibold)
                Spacer()
                if isBusy { ProgressView() } else { Image(systemName: "arrow.up.forward.app").foregroundStyle(Theme.textTertiary) }
            }
        }
        .foregroundStyle(Theme.link)
        .disabled(busy != nil)
    }

    // MARK: Loading

    private func load() async {
        async let g = CalendarAPI.googleStatus()
        async let m = CalendarAPI.microsoftStatus()
        async let available = CalendarAPI.microsoftAvailable()
        async let token = CalendarAPI.feedTokenIfAvailable()
        google = await g
        microsoft = await m
        microsoftAvailable = await available
        if let t = await token { feedToken = t; feedError = nil } else if feedToken == nil { feedError = "Your calendar link isn’t available yet." }
        loaded = true
    }

    private func report(_ error: Error, _ prefix: String) {
        if let edge = error as? EdgeError {
            if edge.isUnavailable { googleUnavailable = true; self.error = "Not available yet. \(prefix) isn’t set up on the server."; return }
            if edge.needsReconnect { Task { google = await CalendarAPI.googleStatus(); microsoft = await CalendarAPI.microsoftStatus() } }
        }
        self.error = "\(prefix): \(error.readable)"
    }

    // MARK: Connecting

    private func connectGoogle() async {
        busy = .googleConnect
        defer { busy = nil }
        do {
            let url = try await CalendarAPI.googleAuthURL()
            let callback = try await webAuthenticationSession.authenticate(using: url, callbackURLScheme: Backend.scheme)
            await finish(callback)
        } catch {
            if !AuthAPI.isCancel(error) { report(error, "Google Calendar") }
        }
    }

    private func connectMicrosoft() async {
        busy = .msConnect
        defer { busy = nil }
        do {
            let url = try await CalendarAPI.microsoftAuthURL()
            let callback = try await webAuthenticationSession.authenticate(using: url, callbackURLScheme: Backend.scheme)
            await finish(callback)
        } catch {
            if !AuthAPI.isCancel(error) { report(error, "Outlook") }
        }
    }

    /// glassmeet://calendar-connections?google=finish&code=&state= (or ?connect=finish&provider=microsoft&code=&state=,
    /// or google=error / connect=error with a reason)
    private func finish(_ url: URL) async {
        let items = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems ?? []
        func value(_ name: String) -> String? { items.first { $0.name == name }?.value }
        let reason = value("reason")
        let reasonText = reason == "denied" ? "access was not allowed." : reason == "state" ? "that sign-in expired. Try again." : "try again."

        if let result = value("google") {
            if result == "finish", let code = value("code"), let state = value("state") {
                do {
                    let email = try await CalendarAPI.googleFinish(code: code, state: state)
                    toast = email.map { "Google Calendar connected (\($0))" } ?? "Google Calendar connected"
                } catch {
                    report(error, "Couldn’t connect Google Calendar")
                }
            } else if result == "error" {
                error = "Google Calendar wasn’t connected: \(reasonText)"
            }
        } else if let result = value("connect") {
            if result == "finish", let code = value("code"), let state = value("state") {
                do {
                    let email = try await CalendarAPI.microsoftFinish(code: code, state: state)
                    toast = email.map { "Outlook connected (\($0))" } ?? "Outlook connected"
                } catch {
                    report(error, "Couldn’t connect Outlook")
                }
            } else if result == "error" {
                error = "Outlook wasn’t connected: \(reasonText)"
            }
        }
        await load()
    }

    // MARK: Google actions

    private func openCalendars() async {
        busy = .googleSettings
        defer { busy = nil }
        do {
            if calendars == nil { calendars = try await CalendarAPI.googleCalendars() }
            pickingCalendar = true
        } catch {
            report(error, "Couldn’t load your Google calendars")
        }
    }

    private func saveCalendar(_ id: String) async {
        busy = .googleSettings
        defer { busy = nil }
        do {
            let result = try await CalendarAPI.googleSaveSettings(["calendarId": .string(id)])
            let o = result.objectValue ?? [:]
            google?.calendarID = o["calendarId"]?.stringValue ?? id
            google?.calendarName = o["calendarName"]?.stringValue ?? calendars?.first { $0.id == id }?.summary
            toast = "Calendar saved. Sync now moves upcoming meetings there."
        } catch {
            report(error, "Couldn’t save your calendar choice")
        }
    }

    private func googleAutoSync(_ on: Bool) async {
        let before = google?.autoSync
        google?.autoSync = on
        busy = .googleSettings
        defer { busy = nil }
        do {
            let result = try await CalendarAPI.googleSaveSettings(["autoSync": .bool(on)])
            google?.autoSync = result.objectValue?["autoSync"]?.flag ?? on
        } catch {
            google?.autoSync = before
            report(error, "Couldn’t change auto-sync")
        }
    }

    private func googleSync() async {
        busy = .googleSync
        defer { busy = nil }
        do {
            toast = try await CalendarAPI.googleSync()
            google?.syncedDate = Fmt.iso(.now)
        } catch {
            report(error, "Couldn’t sync Google Calendar")
        }
    }

    private func googleDisconnect() async {
        busy = .googleDisconnect
        defer { busy = nil }
        do {
            try await CalendarAPI.googleDisconnect()
            google = nil
            calendars = nil
            toast = "Google Calendar disconnected"
        } catch {
            report(error, "Couldn’t disconnect Google Calendar")
        }
    }

    // MARK: Microsoft actions

    private func msAutoSync(_ on: Bool) async {
        let before = microsoft?.autoSync
        microsoft?.autoSync = on
        busy = .msSettings
        defer { busy = nil }
        do {
            try await CalendarAPI.microsoftAutoSync(on)
        } catch {
            microsoft?.autoSync = before
            report(error, "Couldn’t change auto-sync")
        }
    }

    private func msSync() async {
        busy = .msSync
        defer { busy = nil }
        do {
            toast = try await CalendarAPI.microsoftSync()
            microsoft?.syncedDate = Fmt.iso(.now)
        } catch {
            report(error, "Couldn’t sync Outlook")
        }
    }

    private func msDisconnect() async {
        busy = .msDisconnect
        defer { busy = nil }
        do {
            try await CalendarAPI.microsoftDisconnect()
            microsoft = nil
            toast = "Outlook disconnected"
        } catch {
            report(error, "Couldn’t disconnect Outlook")
        }
    }

    // MARK: Feed actions

    private func resetFeed() async {
        busy = .feed
        defer { busy = nil }
        do {
            feedToken = try await CalendarAPI.rotateFeed()
            toast = "New calendar link made"
        } catch {
            self.error = "Couldn’t reset the link. \(error.readable)"
        }
    }
}

private struct ProviderHeader: View {
    var name: String
    var systemImage: String
    var color: Color
    var status: String
    var statusColor: Color

    var body: some View {
        HStack(spacing: Theme.Space.s) {
            IconTile(systemImage: systemImage, color: color, size: 40, glyph: .white)
            VStack(alignment: .leading, spacing: 2) {
                Text(name).font(.headline).foregroundStyle(Theme.text)
                Text(status).font(.subheadline).foregroundStyle(statusColor).lineLimit(1)
            }
        }
        .padding(.vertical, Theme.Space.xxs)
    }
}

private struct CalendarPicker: View {
    var calendars: [GoogleCalendar]
    @State var selected: String
    var save: (String) -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section {
                    ForEach(calendars) { c in
                        Button { selected = c.id } label: {
                            HStack {
                                Text(c.primary ? "\(c.summary) (primary)" : c.summary).foregroundStyle(Theme.text)
                                Spacer()
                                if selected == c.id { Image(systemName: "checkmark").foregroundStyle(Theme.primary) }
                            }
                        }
                    }
                } footer: {
                    Text("Confirmed bookings are added to this calendar. Busy times are checked in it and your primary one.")
                }
                .listRowBackground(Theme.surface)
            }
            .themedList()
            .navigationTitle("Choose a calendar")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel", systemImage: "xmark") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save", systemImage: "checkmark") { save(selected); dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}
