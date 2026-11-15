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

            TemplateSection(title: "Confirmation", text: $confirmation, sample: sample, fallback: Messages.confirmation)
            TemplateSection(title: "Reminder 24 hours before", text: $reminder24h, sample: sample, fallback: Messages.reminder24h)
            TemplateSection(title: "Reminder 1 hour before", text: $reminder1h, sample: sample, fallback: Messages.reminder1h)
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
                .lineLimit(3...12)
                .font(.subheadline)
                .onChange(of: text) { _, new in if new.count > 2000 { text = String(new.prefix(2000)) } }
            VStack(alignment: .leading, spacing: Theme.Space.xs) {
                Text("Insert").font(.footnote.weight(.semibold)).foregroundStyle(Theme.textSecondary)
                FlowLayout(spacing: 6) {
                    ForEach(Messages.placeholders, id: \.self) { token in
                        ChoiceChip(title: token) {
                            text += (text.hasSuffix(" ") || text.hasSuffix("\n") || text.isEmpty ? "" : " ") + token
                        }
                    }
                }
            }
            .padding(.vertical, Theme.Space.xxs)
            VStack(alignment: .leading, spacing: Theme.Space.xs) {
                Text("Preview").font(.footnote.weight(.semibold)).foregroundStyle(Theme.textSecondary)
                MessageBubble(text: Messages.fill(text, sample))
            }
            .padding(.vertical, Theme.Space.xxs)
            if text != fallback {
                Button("Use the default message") { text = fallback }
                    .font(.subheadline)
                    .foregroundStyle(Theme.link)
            }
        } header: {
            Text(title)
        }
        .listRowBackground(Theme.surface)
    }
}
