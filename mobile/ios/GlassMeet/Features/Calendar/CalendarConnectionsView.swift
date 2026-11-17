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

    private enum Busy: Equatable { case googleConnect, googleSync, googleSettings, googleDisconnect, googleTest, msConnect, msSync, msSettings, msDisconnect, feed, export }
    private enum Confirm: Identifiable { case googleDisconnect, msDisconnect, resetFeed; var id: Self { self } }

    @Environment(Session.self) private var session
    @State private var googleVerified = false
    @State private var exportFile: ExportFile?
    @State private var exportNote: String?

    var body: some View {
        List {
            feedSection
            googleSection
            appleSection
            microsoftSection
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
        .sheet(item: $exportFile) { file in
            ActivityView(items: [file.url]).presentationDetents([.medium, .large]).ignoresSafeArea()
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
                Button { Task { await testGoogle() } } label: {
                    HStack {
                        Label("Test connection", systemImage: "checkmark.circle")
                        Spacer()
                        if busy == .googleTest { ProgressView() }
                        else if googleVerified { Text("Working").font(.footnote.weight(.semibold)).foregroundStyle(Theme.success) }
                    }
                }
                .foregroundStyle(Theme.link)
                .disabled(busy != nil)
                Button("Disconnect", role: .destructive) { confirm = .googleDisconnect }
                    .disabled(busy != nil)
            } else if !googleUnavailable {
                connectButton(google == nil ? "Connect Google Calendar" : "Reconnect Google Calendar", busy: busy == .googleConnect) {
                    Task { await connectGoogle() }
                }
            }
        } footer: {
            Text(googleDescription)
        }
        .listRowBackground(Theme.surface)
    }

    private var googleDescription: String {
        if let google, google.connected == true {
            let synced = google.autoSync ?? true ? ", and new bookings are added to Google Calendar" : ""
            return "Times you’re busy in Google are hidden from your booking pages\(synced). Events held on Google Meet get a new Meet link for each booking."
        }
        if let google {
            return "Google stopped accepting the connection\(google.email.map { " to \($0)" } ?? ""). Connect again to keep busy times and bookings in sync."
        }
        if googleUnavailable { return "Google Calendar isn’t available yet. Until then, guests can add meetings to Google Calendar from their confirmation page." }
        return "Connect Google Calendar so times you’re busy stay off your booking pages, new bookings are added to your calendar, and events held on Google Meet get a link for each booking."
    }

    // MARK: Apple Calendar

    private var appleSection: some View {
        Section {
            ProviderHeader(name: "Apple Calendar", systemImage: "apple.logo", color: .black, status: "Always available", statusColor: Theme.success)
            Button { Task { await exportMeetings() } } label: {
                HStack {
                    Label("Add my meetings", systemImage: "calendar.badge.plus")
                    Spacer()
                    if busy == .export { ProgressView() }
                }
            }
            .foregroundStyle(Theme.link)
            .disabled(busy != nil)
            Button { exportTestEvent() } label: {
                Label("Try a test event", systemImage: "calendar.badge.checkmark")
            }
            .foregroundStyle(Theme.link)
            .disabled(busy != nil)
            if let exportNote {
                Text(exportNote).font(.footnote).foregroundStyle(Theme.textSecondary)
            }
        } footer: {
            Text("Makes a calendar file with your upcoming meetings to open in Calendar or any calendar app. No account needed; add it again to pick up new bookings. To keep it updated on its own, use Subscribe above.")
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
            } else if microsoftAvailable || microsoft != nil {
                connectButton(microsoft == nil ? "Connect Outlook" : "Reconnect Outlook", busy: busy == .msConnect) {
                    Task { await connectMicrosoft() }
                }
            } else if loaded {
                Label("Coming soon", systemImage: "clock").font(.subheadline.weight(.semibold)).foregroundStyle(Theme.textSecondary)
            }
        } footer: {
            Text(msDescription)
        }
        .listRowBackground(Theme.surface)
    }

    private var msDescription: String {
        if let microsoft, microsoft.connected == true {
            let synced = microsoft.autoSync ?? true ? ", and new bookings are added to Outlook" : ""
            let teams = microsoft.teams == true ? "Events held on Microsoft Teams get a new link for each booking." : "This account can’t create Teams meetings, which need a work or school account."
            return "Times you’re busy in Outlook are hidden from your booking pages\(synced). \(teams)"
        }
        if microsoft != nil { return "Outlook stopped accepting the connection. Connect again to keep it working." }
        if !microsoftAvailable { return "Microsoft isn’t available yet. Until then, guests can add meetings to Outlook from their confirmation page." }
        return "Connect a Microsoft 365 or Outlook.com account so times you’re busy stay off your booking pages and new bookings are added to your calendar. Events held on Microsoft Teams get a link for each booking, with a work or school account."
    }

    private var msStatusText: String {
        if microsoft == nil, !microsoftAvailable, loaded { return "Coming soon" }
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
            Text("Add this link to your calendar app once. New bookings and cancellations then show up on their own: Apple Calendar refreshes about every hour, Google Calendar can take up to a day. Anyone with the link can see your meeting times and guest details, so keep it private and reset it if it gets shared.")
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

    /// Reads the calendar list: if Google answers, the connection works (web: Test)
    private func testGoogle() async {
        busy = .googleTest
        defer { busy = nil }
        do {
            calendars = try await CalendarAPI.googleCalendars()
            googleVerified = true
            toast = "Google Calendar is working"
        } catch {
            googleVerified = false
            report(error, "Google Calendar didn’t answer")
        }
    }

    // MARK: Apple Calendar actions

    /// One .ics with every upcoming confirmed meeting (web: exportMyMeetings)
    private func exportMeetings() async {
        busy = .export
        defer { busy = nil }
        do {
            let now = Date.now
            let upcoming = try await BookingsAPI.list()
                .filter { !$0.isCancelled && !$0.isPending && $0.end >= now }
                .sorted { $0.start < $1.start }
            guard !upcoming.isEmpty else {
                exportNote = "No upcoming meetings to add yet. New bookings will appear here."
                return
            }
            let ics = ICS.calendar(name: "GlassMeet: \(session.hostName.isEmpty ? "Meetings" : session.hostName)", events: upcoming.map(ICS.Event.init))
            exportFile = try ExportFile(name: "glassmeet-meetings.ics", text: ics)
            exportNote = "\(Fmt.plural(upcoming.count, "upcoming meeting")) ready. Choose Calendar, or save the file and open it."
        } catch {
            self.error = "Couldn’t load your meetings. \(error.readable)"
        }
    }

    private func exportTestEvent() {
        let start = Calendar.current.date(byAdding: .day, value: 1, to: Calendar.current.startOfDay(for: .now))?.addingTimeInterval(10 * 3600) ?? .now
        let event = ICS.Event(id: "glassmeet-test", title: "GlassMeet test event", start: start, end: start.addingTimeInterval(1800),
                              details: "A test from GlassMeet. If you can see this, your calendar app accepts GlassMeet events. You can delete it.", location: nil, url: nil)
        do {
            exportFile = try ExportFile(name: "glassmeet-test.ics", text: ICS.calendar(name: "GlassMeet test", events: [event]))
            exportNote = "Test event made. Add it to check your calendar app accepts GlassMeet events."
        } catch {
            self.error = error.readable
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

/// A calendar file written to the temporary folder, ready for the share sheet
private struct ExportFile: Identifiable {
    var url: URL
    var id: String { url.absoluteString }

    init(name: String, text: String) throws {
        url = FileManager.default.temporaryDirectory.appending(path: name)
        try Data(text.utf8).write(to: url, options: .atomic)
    }
}

/// The system share sheet (Calendar, Files, Mail, AirDrop…)
struct ActivityView: UIViewControllerRepresentable {
    var items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}

/// iCalendar (RFC 5545) text for meetings, like the web's src/lib/ics.js
enum ICS {
    struct Event {
        var id: String
        var title: String
        var start: Date
        var end: Date
        var details: String?
        var location: String?
        var url: String?

        init(id: String, title: String, start: Date, end: Date, details: String?, location: String?, url: String?) {
            self.id = id
            self.title = title
            self.start = start
            self.end = end
            self.details = details
            self.location = location
            self.url = url
        }

        init(_ b: Booking) {
            var lines = ["Guest: \(b.name)"]
            if let email = b.email { lines.append("Email: \(email)") }
            if let phone = b.phone { lines.append("Phone: \(phone)") }
            if let link = b.link { lines.append("Join: \(link)") }
            self.init(id: "gm\(API.key(b.id))", title: "\(b.eventTitle) with \(b.name)", start: b.start, end: b.end,
                      details: lines.joined(separator: "\n"), location: b.link, url: b.link.flatMap { $0.hasPrefix("http") ? $0 : nil })
        }
    }

    static func calendar(name: String, events: [Event]) -> String {
        var out = ["BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//GlassMeet//GlassMeet app//EN", "CALSCALE:GREGORIAN", "METHOD:PUBLISH", "X-WR-CALNAME:\(text(name))"]
        let stamp = utc(.now)
        for e in events {
            out += ["BEGIN:VEVENT", "UID:\(e.id)@glassmeet", "DTSTAMP:\(stamp)", "DTSTART:\(utc(e.start))", "DTEND:\(utc(e.end))", "SUMMARY:\(text(e.title))"]
            if let d = e.details, !d.isEmpty { out.append("DESCRIPTION:\(text(d))") }
            if let l = e.location, !l.isEmpty { out.append("LOCATION:\(text(l))") }
            if let u = e.url { out.append("URL:\(u)") }
            out.append("END:VEVENT")
        }
        out.append("END:VCALENDAR")
        return out.map(fold).joined(separator: "\r\n") + "\r\n"
    }

    private static func utc(_ date: Date) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone(identifier: "UTC")
        f.dateFormat = "yyyyMMdd'T'HHmmss'Z'"
        return f.string(from: date)
    }

    /// Escapes guest-written text (backslash, ; , and new lines)
    private static func text(_ s: String) -> String {
        s.replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: ";", with: "\\;")
            .replacingOccurrences(of: ",", with: "\\,")
            .replacingOccurrences(of: "\r\n", with: "\\n")
            .replacingOccurrences(of: "\n", with: "\\n")
    }

    /// Lines longer than 75 octets continue on the next line after a space
    private static func fold(_ line: String) -> String {
        var parts: [String] = []
        var current = ""
        for ch in line {
            if (current + String(ch)).utf8.count > (parts.isEmpty ? 75 : 74) {
                parts.append(current)
                current = ""
            }
            current.append(ch)
        }
        parts.append(current)
        return parts.joined(separator: "\r\n ")
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
