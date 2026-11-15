import SwiftUI

/// UAE public holidays (profiles.holidays = "ae"): closes bookings on those days, and lists
/// meetings already booked on one so the host can keep them, or cancel and ask for a new time.
struct HolidaysSection: View {
    @Environment(Session.self) private var session
    @Environment(AppData.self) private var data
    @Environment(\.openURL) private var openURL
    @State private var holidays: [Holiday] = []
    @State private var upcoming: [Booking] = []
    @State private var saving = false
    @State private var error: String?
    @State private var cancelling: Booking?
    @State private var cancelled: Set<UUID> = []

    private let region = "ae"
    private var on: Bool { session.profile?.holidays == region }

    var body: some View {
        Section {
            Toggle(isOn: Binding(get: { on }, set: { value in Task { await toggle(value) } })) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("UAE public holidays")
                    Text("Close bookings on UAE and Islamic holidays").font(.footnote).foregroundStyle(Theme.textSecondary)
                }
            }
            .tint(Theme.primary)
            .disabled(saving || session.profile == nil)

            if on {
                ForEach(clashes, id: \.booking.id) { clash in
                    clashRow(clash.booking, holiday: clash.holiday)
                }
                ForEach(grouped.prefix(4), id: \.start) { h in
                    LabeledContent(h.name, value: h.start == h.end ? Fmt.keyLabel(h.start) : "\(Fmt.keyLabel(h.start)) – \(Fmt.keyShort(h.end))")
                        .font(.subheadline)
                }
            }
        } header: {
            Text("Public holidays")
        } footer: {
            Text(error ?? (on ? "Islamic holidays follow the moon, so a date may move by a day." : "They also stay off your booking pages for every event type."))
                .foregroundStyle(error == nil ? Theme.textSecondary : Theme.danger)
        }
        .listRowBackground(Theme.surface)
        .task(id: on) { await load() }
        .confirmationDialog("Cancel this meeting?", isPresented: Binding(get: { cancelling != nil }, set: { if !$0 { cancelling = nil } }),
                            titleVisibility: .visible, presenting: cancelling) { booking in
            Button("Cancel meeting", role: .destructive) { Task { await cancel(booking) } }
            Button("Keep it", role: .cancel) {}
        } message: { booking in
            Text("Then send \(booking.firstName) a message to pick a new time.")
        }
    }

    private struct Clash { var booking: Booking; var holiday: String }
    private struct Span { var name: String; var start: String; var end: String }

    private var clashes: [Clash] {
        let names = Dictionary(holidays.map { ($0.date, $0.name) }, uniquingKeysWith: { a, _ in a })
        let zone = session.timeZone
        return upcoming
            .filter { (!$0.isCancelled || cancelled.contains($0.id)) && !$0.isDeclined && $0.start > .now }
            .compactMap { b in names[Fmt.dateKey(b.start, zone)].map { Clash(booking: b, holiday: $0) } }
    }

    /// One line per holiday: Eid al-Fitr spans three days
    private var grouped: [Span] {
        let today = Fmt.localKey(.now)
        var spans: [Span] = []
        for h in holidays where h.date >= today {
            if let last = spans.indices.last, spans[last].name == h.name { spans[last].end = h.date } else { spans.append(Span(name: h.name, start: h.date, end: h.date)) }
        }
        return spans
    }

    @ViewBuilder private func clashRow(_ b: Booking, holiday: String) -> some View {
        let zone = session.timeZone
        VStack(alignment: .leading, spacing: Theme.Space.xs) {
            HStack(alignment: .top) {
                Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(Theme.warning)
                VStack(alignment: .leading, spacing: 2) {
                    Text(b.name).font(.headline).foregroundStyle(Theme.text)
                    Text("\(Fmt.when(b.start, zone)) · \(holiday)").font(.subheadline).foregroundStyle(Theme.textSecondary)
                }
            }
            if cancelled.contains(b.id) || b.isCancelled {
                if b.phone != nil {
                    Button {
                        openURL(Messages.whatsAppURL(phone: b.phone, message: Messages.reschedule(b, holiday: holiday, zone: zone)))
                    } label: {
                        Label("Ask for a new time on WhatsApp", systemImage: "paperplane.fill")
                    }
                    .buttonStyle(PrimaryButtonStyle(compact: true))
                } else {
                    Text("Cancelled. This guest gave no WhatsApp number.").font(.footnote).foregroundStyle(Theme.textSecondary)
                }
            } else {
                Button("Cancel and reschedule", role: .destructive) { cancelling = b }
                    .buttonStyle(.borderless)
                    .font(.subheadline.weight(.semibold))
            }
        }
        .padding(.vertical, Theme.Space.xxs)
    }

    private func load() async {
        guard on else { holidays = []; return }
        do {
            async let h = ProfileAPI.holidays(region: region)
            async let b = AvailabilityAPI.upcomingBookings()
            (holidays, upcoming) = try await (h, b)
        } catch where AppData.isCancel(error) {
        } catch {
            self.error = "Couldn’t load the holidays. \(error.readable)"
        }
    }

    private func toggle(_ value: Bool) async {
        saving = true
        defer { saving = false }
        do {
            session.profile = try await ProfileAPI.update(["holidays": value ? .string(region) : .null])
            error = nil
        } catch {
            self.error = "Couldn’t change this setting. \(error.readable)"
        }
    }

    private func cancel(_ b: Booking) async {
        do {
            let saved = try await BookingsAPI.cancel(b.id)
            data.update(saved)
            cancelled.insert(b.id)
            if let i = upcoming.firstIndex(where: { $0.id == b.id }) { upcoming[i] = saved }
        } catch {
            self.error = "Couldn’t cancel this meeting. \(error.readable)"
        }
    }
}
