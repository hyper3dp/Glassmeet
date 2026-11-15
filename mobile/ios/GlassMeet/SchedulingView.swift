import SwiftUI
import UIKit

struct SchedulingView: View {
    @State private var events: [EventType] = []
    @State private var loaded = false
    @State private var error: String?
    @State private var copied: UUID?

    var body: some View {
        List {
            if events.isEmpty, loaded {
                ContentUnavailableView {
                    Label("No event types yet", systemImage: "link")
                } description: {
                    Text("An event type is a kind of meeting guests can book, with its own link.")
                } actions: {
                    NavigationLink("Create event type", value: Route.eventType(nil))
                        .buttonStyle(.glassProminent)
                }
                .listRowBackground(Color.clear)
            }
            ForEach($events) { $event in
                Section {
                    NavigationLink(value: Route.eventType(event.id)) {
                        HStack(spacing: 12) {
                            Circle().fill(Color.gmTeal).frame(width: 10, height: 10)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(event.name).font(.headline)
                                Text(summary(event)).font(.subheadline).foregroundStyle(.secondary)
                            }
                        }
                    }
                    Toggle("Bookable", isOn: Binding(get: { event.active }, set: { on in
                        event.active = on
                        Task { await setActive(event.id, on) }
                    }))
                    HStack {
                        Button {
                            UIPasteboard.general.url = Backend.bookingURL(event.id)
                            copied = event.id
                        } label: {
                            Label(copied == event.id ? "Copied" : "Copy link", systemImage: copied == event.id ? "checkmark" : "doc.on.doc")
                                .frame(maxWidth: .infinity)
                        }
                        Divider()
                        ShareLink(item: Backend.bookingURL(event.id)) {
                            Label("Share", systemImage: "square.and.arrow.up").frame(maxWidth: .infinity)
                        }
                    }
                    .buttonStyle(.borderless)
                    .foregroundStyle(Color.gmLink)
                }
                .opacity(event.active ? 1 : 0.6)
            }
        }
        .navigationTitle("Scheduling")
        .toolbar {
            // On iOS 26 the system draws this as a Liquid Glass button in the navigation bar
            ToolbarItem(placement: .topBarTrailing) {
                NavigationLink(value: Route.eventType(nil)) {
                    Image(systemName: "plus")
                }
                .accessibilityLabel("New event type")
            }
        }
        .overlay { if !loaded { ProgressView() } }
        .refreshable { await load() }
        .task { await load() }
        .errorAlert($error)
        .sensoryFeedback(.success, trigger: copied)
    }

    private func summary(_ e: EventType) -> String {
        ["\(e.duration) min", e.location, e.requiresApproval ? "Needs approval" : nil].compactMap { $0 }.joined(separator: " · ")
    }

    private func load() async {
        do { events = try await API.eventTypes() } catch { self.error = error.readable }
        loaded = true
    }

    private func setActive(_ id: UUID, _ on: Bool) async {
        do { try await API.setActive(id, on) } catch {
            if let i = events.firstIndex(where: { $0.id == id }) { events[i].active = !on }
            self.error = "That change could not be saved. Try again."
        }
    }
}

struct EventTypeEditor: View {
    var id: UUID?
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var description = ""
    @State private var duration = 30
    @State private var location = "Google Meet"
    @State private var meetingLink = ""
    @State private var approval = false
    @State private var loading = false
    @State private var busy = false
    @State private var error: String?
    @State private var confirmDelete = false

    private let durations = [15, 30, 45, 60, 90]
    private let locations = ["Google Meet", "Zoom", "WhatsApp call", "Phone call", "In person"]

    var body: some View {
        Form {
            Section {
                TextField("Name, such as 30 minute meeting", text: $name)
            }
            Section("Details") {
                Picker("Duration", selection: $duration) {
                    ForEach(Array(Set(durations + [duration])).sorted(), id: \.self) { Text("\($0) min") }
                }
                Picker("Where", selection: $location) {
                    ForEach(locations.contains(location) ? locations : locations + [location], id: \.self) { Text($0) }
                }
                TextField("Meeting link (optional)", text: $meetingLink)
                    .keyboardType(.URL)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
            }
            Section {
                TextField("What is this meeting for? (optional)", text: $description, axis: .vertical)
                    .lineLimit(3...8)
            } header: {
                Text("Description")
            } footer: {
                Text("Guests see this on the booking page.")
            }
            Section {
                Toggle("Approve each booking", isOn: $approval)
            } footer: {
                Text("When on, a booking waits for your approval before it is confirmed.")
            }
            if id != nil {
                Section {
                    Button("Delete event type", role: .destructive) { confirmDelete = true }
                }
            }
        }
        .navigationTitle(id == nil ? "New event type" : "Event type")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button("Save") { Task { await save() } }
                    .disabled(busy || name.trimmingCharacters(in: .whitespaces).isEmpty)
            }
        }
        .disabled(loading)
        .overlay { if loading { ProgressView() } }
        .task { await load() }
        .errorAlert($error)
        .confirmationDialog("Delete this event type?", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Delete", role: .destructive) { Task { await remove() } }
        } message: {
            Text("Its booking link stops working. Meetings already booked are kept.")
        }
    }

    private func load() async {
        guard let id else { return }
        loading = true
        defer { loading = false }
        do {
            let e = try await API.eventType(id)
            name = e.name
            description = e.description ?? ""
            duration = e.duration
            location = e.location ?? "Google Meet"
            meetingLink = e.meetingURL ?? ""
            approval = e.requiresApproval
        } catch {
            self.error = "This event type could not be loaded."
        }
    }

    private func save() async {
        busy = true
        defer { busy = false }
        let trim = { (s: String) -> String? in let t = s.trimmingCharacters(in: .whitespacesAndNewlines); return t.isEmpty ? nil : t }
        let input = EventTypeInput(name: name.trimmingCharacters(in: .whitespaces), description: trim(description), duration: duration,
                                   location: location, meetingURL: trim(meetingLink), requiresApproval: approval)
        do {
            if let id { try await API.updateEventType(id, input) } else { try await API.createEventType(input) }
            dismiss()
        } catch {
            self.error = "Your changes could not be saved. Check your connection and try again."
        }
    }

    private func remove() async {
        guard let id else { return }
        do { try await API.deleteEventType(id); dismiss() } catch { self.error = "This event type could not be deleted. Try again." }
    }
}
