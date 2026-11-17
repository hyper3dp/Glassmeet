import SwiftUI
import UIKit

struct SchedulingView: View {
    @Environment(AppData.self) private var data
    @Environment(Router.self) private var router
    @State private var deleting: EventType?
    @State private var error: String?
    @State private var toast: String?

    /// The New event type sheet; the tab bar's Create button opens it too
    private var picking: Binding<Bool> {
        Binding(get: { router.pickingTemplate }, set: { router.pickingTemplate = $0 })
    }

    var body: some View {
        List {
            Section {
                NavigationLink(value: Route.availability) {
                    HStack(spacing: Theme.Space.s) {
                        IconTile(systemImage: "clock.fill", color: Theme.primary, size: 36)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Availability").font(.headline).foregroundStyle(Theme.text)
                            Text("Weekly hours, days off and holidays").font(.subheadline).foregroundStyle(Theme.textSecondary)
                        }
                    }
                    .padding(.vertical, 2)
                }
            }
            .listRowBackground(Theme.surface)

            if data.eventTypes.isEmpty, data.eventTypesState.isLoaded {
                Section {
                    EmptyStateView(title: "Create your first event type", systemImage: "link.badge.plus",
                                   message: "An event type is a meeting guests can book, like a 30 minute call. Share its link and guests pick a time.") {
                        Button("New event type") { router.pickingTemplate = true }
                            .buttonStyle(PrimaryButtonStyle(compact: true))
                    }
                }
                .listRowBackground(Color.clear)
            }
            ForEach(data.eventTypes) { event in
                Section {
                    EventTypeRow(event: event, copied: { toast = "Link copied" }, toggle: { on in Task { await setActive(event, on) } },
                                 edit: { router.schedulingPath.append(.eventForm(.edit(event.id))) },
                                 duplicate: { duplicate(event) },
                                 saveTemplate: { Task { await saveTemplate(event) } },
                                 delete: { deleting = event })
                }
                .listRowBackground(Theme.surface)
            }
        }
        .themedList()
        .overlay {
            if data.eventTypes.isEmpty, !data.eventTypesState.isLoaded {
                if let error = data.eventTypesState.error {
                    ErrorStateView(message: error) { await data.loadEventTypes() }
                } else {
                    LoadingView().padding(.top, 120)
                }
            }
        }
        .navigationTitle("Scheduling")
        .toolbar {
            // drawn by the system as a Liquid Glass button in the navigation bar
            ToolbarItem(placement: .topBarTrailing) {
                Button { router.pickingTemplate = true } label: { Image(systemName: "plus") }
                    .accessibilityLabel("New event type")
            }
        }
        .sheet(isPresented: picking) {
            TemplatePickerSheet { choice in
                router.pickingTemplate = false
                router.schedulingPath.append(.eventForm(.new(choice.flatMap { EventType.prefill($0.data) })))
            }
        }
        .confirmationDialog("Delete “\(deleting?.title ?? "")”?", isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } }),
                            titleVisibility: .visible, presenting: deleting) { event in
            Button("Delete", role: .destructive) { Task { await delete(event) } }
            if event.isActive {
                Button("Turn off instead") { Task { await setActive(event, false) } }
            }
            Button("Keep it", role: .cancel) {}
        } message: { _ in
            Text("Its booking link stops working. Meetings already booked stay on your calendar.")
        }
        .refreshable { await data.loadEventTypes() }
        .task { await data.loadEventTypes() }
        .errorAlert($error)
        .toast($toast)
    }

    private func setActive(_ event: EventType, _ on: Bool) async {
        var changed = event
        changed.active = on
        data.update(changed)
        do {
            try await EventTypesAPI.setActive(event.id, on)
        } catch {
            data.update(event)
            self.error = "Couldn’t turn \(on ? "on" : "off") “\(event.title)”. \(error.readable)"
        }
    }

    private func duplicate(_ event: EventType) {
        var fields = EventTypesAPI.fields(of: event)
        fields["name"] = .string("\(event.title) (copy)")
        if let start = event.startDate { fields["start_date"] = .string(start) }
        if let end = event.endDate { fields["end_date"] = .string(end) }
        let today = Fmt.localKey(.now)
        if let dates = event.dates { fields["dates"] = json(dates.filter { $0 >= today }) }
        router.schedulingPath.append(.eventForm(.new(EventType.prefill(fields))))
    }

    private func saveTemplate(_ event: EventType) async {
        do {
            _ = try await EventTypesAPI.saveTemplate(name: event.name ?? "", data: EventTypesAPI.fields(of: event))
            toast = "Saved as a template"
        } catch {
            self.error = "Couldn’t save the template. \(error.readable)"
        }
    }

    private func delete(_ event: EventType) async {
        do {
            try await EventTypesAPI.delete(event.id)
            withAnimation { data.remove(eventType: event.id) }
        } catch {
            self.error = "Couldn’t delete “\(event.title)”. \(error.readable)"
        }
    }
}

/// Colour dot, name, summary, active switch, Copy link, Share and a More menu
private struct EventTypeRow: View {
    var event: EventType
    var copied: () -> Void
    var toggle: (Bool) -> Void
    var edit: () -> Void
    var duplicate: () -> Void
    var saveTemplate: () -> Void
    var delete: () -> Void

    var body: some View {
        let url = Backend.bookingURL(event.id)
        Button(action: edit) {
            HStack(alignment: .top, spacing: Theme.Space.s) {
                EventDot(color: Theme.eventColor(event.color), size: 12)
                    .padding(.top, 6)
                VStack(alignment: .leading, spacing: 3) {
                    Text(event.title).font(.headline).foregroundStyle(Theme.text)
                    Text(event.summary).font(.subheadline).foregroundStyle(Theme.textSecondary)
                    if !event.isActive {
                        Text("Off: guests can’t book it").font(.footnote.weight(.semibold)).foregroundStyle(Theme.warning)
                    }
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundStyle(Theme.textTertiary)
                    .padding(.top, 6)
            }
            .contentShape(.rect)
        }
        .buttonStyle(.plain)

        HStack(spacing: Theme.Space.s) {
            Button {
                UIPasteboard.general.url = url
                copied()
            } label: {
                Label("Copy link", systemImage: "doc.on.doc")
            }
            .buttonStyle(SecondaryButtonStyle(compact: true))

            ShareLink(item: url, message: Text("Book a time with me: \(url.absoluteString)")) {
                Label("Share", systemImage: "square.and.arrow.up")
            }
            .buttonStyle(SecondaryButtonStyle(compact: true))

            Spacer(minLength: 0)

            Menu {
                Button("Edit", systemImage: "pencil", action: edit)
                Button("Duplicate", systemImage: "plus.square.on.square", action: duplicate)
                Button("Save as template", systemImage: "square.and.arrow.down", action: saveTemplate)
                if event.isActive {
                    Button("Turn off", systemImage: "pause.circle") { toggle(false) }
                } else {
                    Button("Turn on", systemImage: "play.circle") { toggle(true) }
                }
                Divider()
                Button("Delete", systemImage: "trash", role: .destructive, action: delete)
            } label: {
                Image(systemName: "ellipsis")
                    .font(.headline)
                    .foregroundStyle(Theme.link)
                    .frame(width: Theme.minTouch, height: Theme.minTouch)
                    .contentShape(.rect)
            }
            .accessibilityLabel("More for \(event.title)")
        }
        .opacity(event.isActive ? 1 : 0.7)
    }
}

/// Start from scratch, the host's templates, or a built-in one
struct TemplatePickerSheet: View {
    var pick: (TemplateChoice?) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var saved: [EventTemplate] = []
    @State private var error: String?

    var body: some View {
        NavigationStack {
            List {
                Section {
                    templateRow(name: "Start from scratch", summary: "A blank event type", icon: "plus") { pick(nil) }
                }
                .listRowBackground(Theme.surface)

                if !saved.isEmpty {
                    Section("Your templates") {
                        ForEach(saved) { t in
                            let choice = TemplateChoice(id: t.id.uuidString, name: t.name ?? "Template", data: t.fields, savedID: t.id)
                            templateRow(name: choice.name, summary: choice.summary, icon: "doc.text") { pick(choice) }
                                .swipeActions {
                                    Button("Delete", systemImage: "trash", role: .destructive) { Task { await remove(t) } }
                                }
                        }
                    }
                    .listRowBackground(Theme.surface)
                }

                Section("Templates") {
                    ForEach(TemplateChoice.builtIn) { t in
                        templateRow(name: t.name, summary: t.summary, icon: icon(for: t)) { pick(t) }
                    }
                }
                .listRowBackground(Theme.surface)
            }
            .themedList()
            .navigationTitle("New event type")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel", systemImage: "xmark") { dismiss() }
                }
            }
            .task { saved = await EventTypesAPI.templates() }
            .errorAlert($error)
        }
        .presentationDetents([.large])
    }

    private func templateRow(name: String, summary: String, icon: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: Theme.Space.s) {
                Image(systemName: icon)
                    .font(.body.weight(.semibold))
                    .foregroundStyle(Theme.link)
                    .frame(width: 36, height: 36)
                    .background(Theme.primarySoft, in: .rect(cornerRadius: 10))
                VStack(alignment: .leading, spacing: 2) {
                    Text(name).font(.headline).foregroundStyle(Theme.text)
                    Text(summary).font(.subheadline).foregroundStyle(Theme.textSecondary)
                }
                Spacer(minLength: 0)
            }
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
    }

    private func icon(for t: TemplateChoice) -> String {
        switch t.id {
        case "quick-chat": "bubble.left"
        case "consultation": "person.badge.shield.checkmark"
        case "whatsapp-call": "phone"
        case "in-person": "mappin.and.ellipse"
        case "office-hours": "door.left.hand.open"
        case "group-class": "person.3"
        case "monthly-review": "calendar"
        default: "clock"
        }
    }

    private func remove(_ t: EventTemplate) async {
        do {
            try await EventTypesAPI.deleteTemplate(t.id)
            saved.removeAll { $0.id == t.id }
        } catch {
            self.error = "Couldn’t delete the template. \(error.readable)"
        }
    }
}
