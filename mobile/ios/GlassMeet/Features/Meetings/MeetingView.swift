import SwiftUI
import UIKit

/// A meeting's page: who, when, answers, notes, the meeting link, a message to the guest and
/// the actions that change it. After any change the host's connected calendars follow.
struct MeetingView: View {
    var id: UUID
    @Environment(Session.self) private var session
    @Environment(AppData.self) private var data
    @Environment(\.openURL) private var openURL

    @State private var booking: Booking?
    @State private var loadError: String?
    @State private var notes = ""
    @State private var link = ""
    @State private var notesSave = AutosaveState.idle
    @State private var linkSave = AutosaveState.idle
    @State private var kind: Messages.Kind = .confirmation
    @State private var message = ""
    @State private var busy = false
    @State private var error: String?
    @State private var toast: String?
    @State private var confirming: Confirm?
    @State private var seeded = false
    @State private var notesTask: Task<Void, Never>?
    @State private var linkTask: Task<Void, Never>?

    private enum Confirm: Identifiable {
        case cancel, decline
        var id: Self { self }
    }

    var body: some View {
        Group {
            if let booking {
                content(booking)
            } else if let loadError {
                ErrorStateView(message: loadError) { await load() }
            } else {
                LoadingView()
            }
        }
        .background(Theme.background)
        .navigationTitle(booking?.name ?? "Meeting")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
        .errorAlert($error)
        .toast($toast)
    }

    // MARK: Content

    @ViewBuilder private func content(_ b: Booking) -> some View {
        let zone = session.timeZone
        let event = data.eventType(b.eventTypeID)
        let status = BookingStatus(b)
        let canCancel = status == .confirmed

        List {
            Section {
                header(b, event: event, status: status)
            }
            .listRowBackground(Theme.surface)

            Section("When") {
                LabeledContent("Date", value: Fmt.longDate(b.start, zone))
                LabeledContent("Time", value: Fmt.timeRange(b.start, b.end, zone))
                LabeledContent("Time zone", value: zone.identifier)
                if let url = (trimmedOrNil(link) ?? event?.meetingURL).flatMap(URL.init(string:)), !b.isCancelled {
                    Button { openURL(url) } label: { Label("Join meeting", systemImage: "video.fill") }
                        .foregroundStyle(Theme.link)
                }
            }
            .listRowBackground(Theme.surface)

            Section("Guest") {
                if let email = b.email, let url = URL(string: "mailto:\(email)") {
                    Button { openURL(url) } label: { LabeledContent("Email", value: email) }
                        .tint(Theme.text)
                } else {
                    LabeledContent("Email", value: "Not given")
                }
                if let phone = b.phone {
                    if let tel = URL(string: "tel:\(phone.filter { $0.isNumber || $0 == "+" })") {
                        Button { openURL(tel) } label: { LabeledContent("Phone", value: phone) }
                            .tint(Theme.text)
                    }
                    Button { openURL(Messages.whatsAppURL(phone: phone, message: "")) } label: {
                        Label("Chat on WhatsApp", systemImage: "message.fill")
                    }
                    .foregroundStyle(Theme.link)
                }
            }
            .listRowBackground(Theme.surface)

            let answers = b.answers
            if !answers.isEmpty {
                Section("Answers") {
                    ForEach(Array(answers.enumerated()), id: \.offset) { _, item in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(item.question).font(.footnote).foregroundStyle(Theme.textSecondary)
                            Text(item.answer).foregroundStyle(Theme.text).textSelection(.enabled)
                        }
                        .padding(.vertical, 2)
                    }
                }
                .listRowBackground(Theme.surface)
            }

            if let guestNotes = trimmedOrNil(b.guestNotes ?? "") {
                Section("Guest notes") {
                    Text(guestNotes).foregroundStyle(Theme.text).textSelection(.enabled)
                }
                .listRowBackground(Theme.surface)
            }

            Section {
                TextField("Only you can see these", text: $notes, axis: .vertical)
                    .lineLimit(3...10)
                    .onChange(of: notes) { _, _ in scheduleNotes() }
            } header: {
                Text("Your notes")
            } footer: {
                AutosaveFooter(state: notesSave)
            }
            .listRowBackground(Theme.surface)

            Section {
                TextField("https://", text: $link)
                    .maxLength($link, 500)
                    .keyboardType(.URL)
                    .textContentType(.URL)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .onChange(of: link) { _, _ in scheduleLink() }
            } header: {
                Text("Meeting link")
            } footer: {
                AutosaveFooter(state: linkSave, idle: "Guests get this link with their confirmation.")
            }
            .listRowBackground(Theme.surface)

            MessageComposer(kind: $kind, message: $message, phone: b.phone, email: b.email, subject: "\(b.eventTitle) on \(Fmt.longDate(b.start, zone))") {
                toast = "Message copied"
            }
            .onChange(of: kind) { _, new in message = text(for: new, b) }

            if canCancel {
                Section {
                    Button("Cancel meeting", role: .destructive) { confirming = .cancel }
                        .frame(maxWidth: .infinity)
                } footer: {
                    Text("The time becomes free again on your booking page.")
                }
                .listRowBackground(Theme.surface)
            }
        }
        .themedList()
        .refreshable { await load() }
        // The page's main action floats over the content on Liquid Glass
        .floatingBar(edge: .bottom) {
            actionBar(b)
        }
        .confirmationDialog(confirming == .decline ? "Decline this request?" : "Cancel this meeting?",
                            isPresented: Binding(get: { confirming != nil }, set: { if !$0 { confirming = nil } }),
                            titleVisibility: .visible, presenting: confirming) { which in
            Button(which == .decline ? "Decline request" : "Cancel meeting", role: .destructive) {
                Task { await change(which == .decline ? .decline : .cancel) }
            }
            Button("Keep it", role: .cancel) {}
        } message: { which in
            Text(which == .decline ? "\(b.name) isn’t booked, and the time is free again." : "\(b.name) keeps this meeting until you send a message.")
        }
    }

    private func header(_ b: Booking, event: EventType?, status: BookingStatus) -> some View {
        HStack(spacing: Theme.Space.m) {
            Avatar(name: b.name, size: 60)
            VStack(alignment: .leading, spacing: 4) {
                Text(b.name).font(Theme.Typography.title2).foregroundStyle(Theme.text)
                HStack(spacing: 6) {
                    EventDot(color: Theme.eventColor(event?.color), size: 8)
                    Text(b.eventTitle).font(.subheadline).foregroundStyle(Theme.textSecondary).lineLimit(2)
                }
                StatusChip(status: status)
            }
        }
        .padding(.vertical, Theme.Space.xs)
    }

    @ViewBuilder private func actionBar(_ b: Booking) -> some View {
        let showSend = !b.isPending && b.phone != nil && !message.isEmpty
        if b.isPending || showSend {
            GlassGroup(spacing: Theme.Space.s) {
                HStack(spacing: Theme.Space.s) {
                    if b.isPending {
                        Button(role: .destructive) { confirming = .decline } label: {
                            Text("Decline").frame(maxWidth: .infinity)
                        }
                        .glassButton()
                        Button { Task { await change(.approve) } } label: {
                            Text("Approve").fontWeight(.semibold).frame(maxWidth: .infinity)
                        }
                        .glassButton(prominent: true)
                    } else {
                        Button { openURL(Messages.whatsAppURL(phone: b.phone, message: message)) } label: {
                            Label("Send on WhatsApp", systemImage: "paperplane.fill")
                                .fontWeight(.semibold)
                                .frame(maxWidth: .infinity)
                        }
                        .glassButton(prominent: true)
                    }
                }
                .controlSize(.large)
                .disabled(busy)
            }
            .padding(.horizontal, Theme.Space.m)
            .padding(.bottom, Theme.Space.xs)
        }
    }

    // MARK: Data

    private func text(for kind: Messages.Kind, _ b: Booking) -> String {
        Messages.fill(Messages.template(kind, settings: session.whatsApp),
                      Messages.Context(booking: Booking(copying: b, link: trimmedOrNil(link)), hostName: session.hostName, zone: session.timeZone))
    }

    private func load() async {
        do {
            let b = try await BookingsAPI.get(id)
            apply(b, resetMessage: !seeded)
            loadError = nil
        } catch where AppData.isCancel(error) {
        } catch {
            if booking == nil { loadError = error.readable } else { self.error = error.readable }
        }
    }

    private func apply(_ b: Booking, resetMessage: Bool) {
        booking = b
        data.update(b)
        if !seeded {
            notes = b.notes ?? ""
            link = b.meetingURL ?? ""
            seeded = true
        }
        if resetMessage {
            kind = Messages.Kind.suggested(for: b)
            message = text(for: kind, b)
        }
    }

    private enum Change { case approve, decline, cancel }

    private func change(_ what: Change) async {
        busy = true
        defer { busy = false }
        do {
            let saved: Booking
            switch what {
            case .approve: saved = try await BookingsAPI.approve(id)
            case .decline: saved = try await BookingsAPI.decline(id)
            case .cancel: saved = try await BookingsAPI.cancel(id)
            }
            booking = saved
            data.update(saved)
            // the guest usually needs to hear about it: the matching message is ready to send
            kind = what == .approve ? .confirmation : what == .decline ? .decline : .cancellation
            message = text(for: kind, saved)
            toast = what == .approve ? "Approved" : what == .decline ? "Request declined" : "Meeting cancelled"
        } catch {
            self.error = "This meeting couldn’t be updated. \(error.readable)"
        }
    }

    private func scheduleNotes() {
        guard seeded, let b = booking, notes != (b.notes ?? "") else { return }
        notesTask?.cancel()
        notesSave = .pending
        let value = notes
        notesTask = Task {
            try? await Task.sleep(for: .milliseconds(900))
            guard !Task.isCancelled else { return }
            notesSave = .saving
            do {
                let saved = try await BookingsAPI.update(id, ["notes": json(trimmedOrNil(value))])
                booking?.notes = saved.notes
                data.update(saved)
                notesSave = .saved
            } catch {
                notesSave = .failed
            }
        }
    }

    private func scheduleLink() {
        guard seeded, let b = booking, link != (b.meetingURL ?? "") else { return }
        linkTask?.cancel()
        linkSave = .pending
        let value = link
        linkTask = Task {
            try? await Task.sleep(for: .milliseconds(900))
            guard !Task.isCancelled else { return }
            linkSave = .saving
            do {
                let saved = try await BookingsAPI.update(id, ["meeting_url": json(trimmedOrNil(value))])
                booking?.meetingURL = saved.meetingURL
                data.update(saved)
                Edge.pushBooking(id)
                linkSave = .saved
            } catch {
                linkSave = .failed
            }
        }
    }
}

private extension Booking {
    /// The booking with the link being typed, for message previews
    init(copying b: Booking, link: String?) {
        self = b
        self.meetingURL = link ?? b.meetingURL
    }
}

enum AutosaveState { case idle, pending, saving, saved, failed }

struct AutosaveFooter: View {
    var state: AutosaveState
    var idle: String = "Saves automatically."

    var body: some View {
        switch state {
        case .idle: Text(idle)
        case .pending, .saving: Label("Saving…", systemImage: "arrow.triangle.2.circlepath")
        case .saved: Label("Saved", systemImage: "checkmark").foregroundStyle(Theme.success)
        case .failed: Label("Couldn’t save. Check your connection; it saves on your next change.", systemImage: "exclamationmark.triangle").foregroundStyle(Theme.danger)
        }
    }
}
