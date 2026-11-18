import SwiftUI

/// The host's WhatsApp number and three message templates, with placeholder chips and a live
/// preview. GlassMeet prepares the message and opens WhatsApp; the host sends it.
struct WhatsAppSettingsView: View {
    @Environment(Session.self) private var session
    @State private var phone = ""
    @State private var confirmation = Messages.confirmation
    @State private var reminder24h = Messages.reminder24h
    @State private var reminder1h = Messages.reminder1h
    @State private var seeded = false
    @State private var saving = false
    @State private var saved = Snapshot()
    @State private var error: String?
    @State private var toast: String?
    @State private var editing: Messages.Kind = .confirmation

    private struct Snapshot: Equatable {
        var phone = ""
        var confirmation = ""
        var reminder24h = ""
        var reminder1h = ""
    }

    private var current: Snapshot { Snapshot(phone: phone, confirmation: confirmation, reminder24h: reminder24h, reminder1h: reminder1h) }

    var body: some View {
        let sample = Messages.Context.sample(hostName: session.hostName, zone: session.timeZone)
        Form {
            Section {
                TextField("+971 50 123 4567", text: $phone)
                    .keyboardType(.phonePad)
                    .textContentType(.telephoneNumber)
            } header: {
                Text("Your WhatsApp number")
            } footer: {
                Text("GlassMeet prepares the message and opens WhatsApp; you send it.")
            }
            .listRowBackground(Theme.surface)

            Section {
                Picker("Message", selection: $editing) {
                    Text("Confirmation").tag(Messages.Kind.confirmation)
                    Text("24 h reminder").tag(Messages.Kind.reminder24h)
                    Text("1 h reminder").tag(Messages.Kind.reminder1h)
                }
                .pickerStyle(.segmented)
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.clear)
            } header: {
                Text("Messages")
            }

            switch editing {
            case .reminder24h:
                TemplateSection(title: "Reminder 24 hours before", text: $reminder24h, sample: sample, fallback: Messages.reminder24h)
            case .reminder1h:
                TemplateSection(title: "Reminder 1 hour before", text: $reminder1h, sample: sample, fallback: Messages.reminder1h)
            default:
                TemplateSection(title: "Confirmation", text: $confirmation, sample: sample, fallback: Messages.confirmation)
            }

            Section {} footer: {
                Text("Reminders aren’t sent for you: open a meeting and choose the reminder to send it yourself.")
            }
        }
        .scrollContentBackground(.hidden)
        .background(Theme.background)
        .scrollDismissesKeyboard(.interactively)
        .navigationTitle("WhatsApp messages")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                if saving {
                    ProgressView()
                } else {
                    Button("Save") { Task { await save() } }.disabled(!seeded || current == saved)
                }
            }
        }
        .task { await load() }
        .errorAlert($error)
        .toast($toast)
    }

    private func load() async {
        guard !seeded else { return }
        await session.reloadWhatsApp()
        let s = session.whatsApp
        phone = s?.phoneNumber ?? session.profile?.whatsappNumber ?? ""
        confirmation = Messages.template(.confirmation, settings: s)
        reminder24h = Messages.template(.reminder24h, settings: s)
        reminder1h = Messages.template(.reminder1h, settings: s)
        saved = current
        seeded = true
    }

    private func save() async {
        let number = phone.trimmingCharacters(in: .whitespaces)
        if !number.isEmpty, !(7...15).contains(number.filter(\.isNumber).count) {
            error = "Enter the number with its country code, such as +971 50 123 4567."
            return
        }
        saving = true
        defer { saving = false }
        do {
            session.whatsApp = try await WhatsAppAPI.save(phone: trimmedOrNil(number), confirmation: trimmedOrNil(confirmation),
                                                          reminder24h: trimmedOrNil(reminder24h), reminder1h: trimmedOrNil(reminder1h))
            saved = current
            toast = "Saved"
        } catch {
            self.error = "Your WhatsApp settings couldn’t be saved. \(error.readable)"
        }
    }
}

private struct TemplateSection: View {
    var title: String
    @Binding var text: String
    var sample: Messages.Context
    var fallback: String

    var body: some View {
        Section {
            TextField(title, text: $text, axis: .vertical)
                .lineLimit(4...14)
                .font(.subheadline)
                .padding(.vertical, Theme.Space.xxs)
                .onChange(of: text) { _, new in if new.count > 2000 { text = String(new.prefix(2000)) } }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(Messages.placeholders, id: \.self) { token in
                        ChoiceChip(title: token) {
                            text += (text.hasSuffix(" ") || text.hasSuffix("\n") || text.isEmpty ? "" : " ") + token
                        }
                    }
                }
                .padding(.horizontal, Theme.Space.m)
            }
            .listRowInsets(EdgeInsets())
            .accessibilityLabel("Add a detail")
        } header: {
            HStack {
                Text(title)
                Spacer()
                if text != fallback {
                    Button("Use default") { text = fallback }
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(Theme.link)
                        .textCase(nil)
                }
            }
        } footer: {
            Text("Tap a detail to add it. Each one is filled in for the guest.")
        }
        .listRowBackground(Theme.surface)

        Section {
            VStack(alignment: .trailing, spacing: 4) {
                MessageBubble(text: Messages.fill(text, sample))
                    .frame(maxWidth: 300, alignment: .trailing)
            }
            .frame(maxWidth: .infinity, alignment: .trailing)
            .padding(Theme.Space.s)
            .background(Theme.surfaceRaised, in: .rect(cornerRadius: Theme.Radius.field))
            .listRowInsets(EdgeInsets(top: 6, leading: 6, bottom: 6, trailing: 6))
        } header: {
            Text("Preview")
        }
        .listRowBackground(Theme.surface)
    }
}
