import SwiftUI

struct MeetingView: View {
    var id: UUID
    @Environment(Account.self) private var account
    @Environment(\.openURL) private var openURL
    @State private var booking: Booking?
    @State private var eventType: EventType?
    @State private var template: String?
    @State private var busy = false
    @State private var error: String?
    @State private var confirming: Confirm?
    @State private var done = 0

    private enum Confirm: Identifiable {
        case cancel, decline
        var id: Self { self }
    }

    var body: some View {
        Group {
            if let booking { content(booking) } else { ProgressView() }
        }
        .navigationTitle("Meeting")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
        .refreshable { await load() }
        .errorAlert($error)
        .sensoryFeedback(.success, trigger: done)
    }

    @ViewBuilder private func content(_ booking: Booking) -> some View {
        let live = !booking.isCancelled && !booking.isDeclined && !booking.isOver()
        let meetingLink = booking.meetingURL ?? eventType?.meetingURL

        List {
            Section {
                HStack(spacing: 14) {
                    Avatar(name: booking.guestName, size: 56)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(booking.guestName).font(.title2.bold())
                        Text(booking.eventName ?? eventType?.name ?? "Meeting").foregroundStyle(.secondary)
                        status(booking)
                    }
                }
                .padding(.vertical, 4)
            }

            Section("When") {
                LabeledContent(booking.start.formatted(date: .complete, time: .omitted),
                               value: "\(Format.time(booking.start)) to \(Format.time(booking.end))")
                if let meetingLink, let url = URL(string: meetingLink) {
                    Link(destination: url) { Label("Join meeting", systemImage: "video") }
                }
            }

            Section("Guest") {
                if let mail = URL(string: "mailto:\(booking.guestEmail)") {
                    Link(destination: mail) { LabeledContent("Email", value: booking.guestEmail) }
                }
                if let phone = booking.guestPhone, let tel = URL(string: "tel:\(phone.filter { $0.isNumber || $0 == "+" })") {
                    Link(destination: tel) { LabeledContent("Phone", value: phone) }
                }
            }

            if let notes = booking.guestNotes, !notes.isEmpty {
                Section("Notes from the guest") { Text(notes) }
            }

            if live, !booking.isPending, booking.guestPhone != nil {
                Section {
                    Text(message(template ?? WhatsApp.confirmation, booking))
                        .font(.subheadline)
                        .padding(14)
                        .background(Color.gmBubble, in: UnevenRoundedRectangle(topLeadingRadius: 12, bottomLeadingRadius: 12, bottomTrailingRadius: 12, topTrailingRadius: 0))
                        .listRowBackground(Color.clear)
                        .listRowInsets(EdgeInsets())
                } header: {
                    Text("Message preview")
                } footer: {
                    Text("The message opens in WhatsApp ready to send. Nothing is sent until you send it.")
                }
            }

            if live, !booking.isPending {
                Section {
                    Button("Cancel meeting", role: .destructive) { confirming = .cancel }
                }
            }
        }
        // The page's main action floats over the content on Liquid Glass, above the tab bar
        .safeAreaBar(edge: .bottom) {
            if live { actions(booking).padding(.horizontal).padding(.bottom, 8) }
        }
        .confirmationDialog(confirming == .decline ? "Decline this request?" : "Cancel this meeting?",
                            isPresented: Binding(get: { confirming != nil }, set: { if !$0 { confirming = nil } }),
                            titleVisibility: .visible,
                            presenting: confirming) { kind in
            Button(kind == .decline ? "Decline" : "Cancel meeting", role: .destructive) {
                Task {
                    if kind == .decline {
                        await change(["approval_status": "declined", "status": "cancelled"], then: WhatsApp.decline)
                    } else {
                        await change(["status": "cancelled"], then: WhatsApp.cancellation)
                    }
                }
            }
            Button("Keep meeting", role: .cancel) {}
        } message: { kind in
            Text(kind == .decline ? "\(booking.guestName) will not be booked for this time." : "The time becomes free again on your booking page.")
        }
    }

    @ViewBuilder private func actions(_ booking: Booking) -> some View {
        GlassEffectContainer(spacing: 12) {
            HStack(spacing: 12) {
                if booking.isPending {
                    Button(role: .destructive) { confirming = .decline } label: {
                        Text("Decline").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.glass)
                    Button { Task { await change(["approval_status": "approved"], then: nil) } } label: {
                        Text("Approve").fontWeight(.semibold).frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.glassProminent)
                } else if booking.guestPhone != nil {
                    Button { openURL(WhatsApp.link(phone: booking.guestPhone, message: message(template ?? WhatsApp.confirmation, booking))) } label: {
                        Label("Confirm on WhatsApp", systemImage: "paperplane.fill").fontWeight(.semibold).frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.glassProminent)
                } else if let mail = URL(string: "mailto:\(booking.guestEmail)") {
                    Button { openURL(mail) } label: {
                        Label("Email guest", systemImage: "envelope.fill").fontWeight(.semibold).frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.glassProminent)
                }
            }
            .controlSize(.large)
            .disabled(busy)
        }
    }

    @ViewBuilder private func status(_ b: Booking) -> some View {
        if b.isPending { StatusBadge(text: "Waiting for your reply", color: .orange) }
        else if b.isDeclined { StatusBadge(text: "Declined", color: .secondary) }
        else if b.isCancelled { StatusBadge(text: "Cancelled", color: .secondary) }
        else if b.isOver() { StatusBadge(text: "Finished", color: .secondary) }
        else { StatusBadge(text: "Confirmed", color: .green) }
    }

    private func message(_ template: String, _ booking: Booking) -> String {
        WhatsApp.fill(template, booking: booking, eventType: eventType, host: account.hostName)
    }

    private func load() async {
        do {
            let b = try await API.booking(id)
            booking = b
            if let eid = b.eventTypeID { eventType = try? await API.eventType(eid) }
            template = try? await API.whatsApp()?.confirmationTemplate
        } catch {
            self.error = error.readable
        }
    }

    private func change(_ fields: [String: String], then followUp: String?) async {
        busy = true
        defer { busy = false }
        do {
            let updated = try await API.updateBooking(id, fields)
            booking = updated
            done += 1
            if let followUp, updated.guestPhone != nil {
                openURL(WhatsApp.link(phone: updated.guestPhone, message: message(followUp, updated)))
            }
        } catch {
            self.error = "This meeting could not be updated. Try again."
        }
    }
}
