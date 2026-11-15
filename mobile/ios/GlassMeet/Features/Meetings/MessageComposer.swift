import SwiftUI
import UIKit

/// Pick a message (confirmation, reminders, cancellation, decline), edit it, then send it
/// from WhatsApp, copy it or email it. Nothing is sent until the host sends it.
struct MessageComposer: View {
    @Binding var kind: Messages.Kind
    @Binding var message: String
    var phone: String?
    var email: String?
    var subject: String
    var copied: () -> Void
    @Environment(\.openURL) private var openURL

    var body: some View {
        Section {
            Picker("Message", selection: $kind) {
                ForEach(Messages.Kind.allCases) { Text($0.label).tag($0) }
            }
            .pickerStyle(.menu)
            .tint(Theme.link)

            TextField("Message", text: $message, axis: .vertical)
                .lineLimit(4...14)
                .font(.subheadline)
                .padding(Theme.Space.s)
                .background(Theme.whatsappBubble, in: UnevenRoundedRectangle(topLeadingRadius: 16, bottomLeadingRadius: 16, bottomTrailingRadius: 16, topTrailingRadius: 4))
                .listRowSeparator(.hidden)

            HStack(spacing: Theme.Space.s) {
                if phone != nil {
                    Button { openURL(Messages.whatsAppURL(phone: phone, message: message)) } label: {
                        Label("WhatsApp", systemImage: "paperplane.fill")
                    }
                    .buttonStyle(PrimaryButtonStyle(compact: true))
                }
                Button {
                    UIPasteboard.general.string = message
                    copied()
                } label: {
                    Label("Copy", systemImage: "doc.on.doc")
                }
                .buttonStyle(SecondaryButtonStyle(compact: true))
                if let email, let url = Messages.mailURL(to: email, subject: subject, body: message) {
                    Button { openURL(url) } label: {
                        Label("Email", systemImage: "envelope")
                    }
                    .buttonStyle(SecondaryButtonStyle(compact: true))
                }
            }
            .labelStyle(.titleAndIcon)
            .frame(maxWidth: .infinity, alignment: .leading)
        } header: {
            Text("Message")
        } footer: {
            Text(phone == nil
                 ? "This guest gave no WhatsApp number. Copy the message or email it."
                 : "GlassMeet prepares the message and opens WhatsApp; you send it.")
        }
        .listRowBackground(Theme.surface)
    }
}
