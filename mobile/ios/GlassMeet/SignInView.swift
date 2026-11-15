import Supabase
import SwiftUI

struct SignInView: View {
    @Environment(\.openURL) private var openURL
    @State private var email = ""
    @State private var password = ""
    @State private var busy = false
    @State private var error: String?
    @FocusState private var focus: Field?
    private enum Field { case email, password }

    var body: some View {
        ScrollView {
            VStack(spacing: 32) {
                VStack(spacing: 10) {
                    // The brand kit's mark, used as supplied and tinted brand teal
                    Image("Mark")
                        .renderingMode(.template)
                        .resizable().scaledToFit()
                        .frame(width: 72, height: 72)
                        .foregroundStyle(Color.gmBrand)
                        .accessibilityLabel("GlassMeet")
                    Text("Sign in to GlassMeet")
                        .font(.largeTitle.bold())
                        .multilineTextAlignment(.center)
                    Text("Schedule meetings. Stay on WhatsApp.")
                        .foregroundStyle(.secondary)
                }
                .padding(.top, 48)

                VStack(spacing: 12) {
                    TextField("Email", text: $email)
                        .textContentType(.username)
                        .keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .submitLabel(.next)
                        .focused($focus, equals: .email)
                        .onSubmit { focus = .password }
                        .fieldStyle()
                    SecureField("Password", text: $password)
                        .textContentType(.password)
                        .submitLabel(.go)
                        .focused($focus, equals: .password)
                        .onSubmit { Task { await signIn() } }
                        .fieldStyle()
                    if let error {
                        Text(error)
                            .font(.subheadline)
                            .foregroundStyle(.red)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    Button {
                        Task { await signIn() }
                    } label: {
                        Group {
                            if busy { ProgressView() } else { Text("Sign in").fontWeight(.semibold) }
                        }
                        .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.glassProminent)
                    .controlSize(.large)
                    .disabled(busy)
                }

                VStack(spacing: 4) {
                    Button("Forgot password?") { openURL(Backend.web("forgot-password")) }
                    Button("Create an account") { openURL(Backend.web("register")) }
                }
                .buttonStyle(.borderless)
                .foregroundStyle(Color.gmLink)
            }
            .padding(24)
            .frame(maxWidth: 480)
            .frame(maxWidth: .infinity)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(Color(uiColor: .systemGroupedBackground))
        .sensoryFeedback(.error, trigger: error)
    }

    private func signIn() async {
        guard !email.trimmingCharacters(in: .whitespaces).isEmpty, !password.isEmpty else {
            error = "Enter your email and password."
            return
        }
        busy = true
        defer { busy = false }
        do {
            try await supabase.auth.signIn(email: email.trimmingCharacters(in: .whitespaces), password: password)
            error = nil
        } catch {
            let text = error.readable
            self.error = text.localizedCaseInsensitiveContains("invalid")
                ? "That email and password don’t match. Check them and try again."
                : text.localizedCaseInsensitiveContains("confirm")
                    ? "Confirm your email before signing in. Check your inbox (and spam) for the code."
                    : text
        }
    }
}

private extension View {
    func fieldStyle() -> some View {
        padding(.horizontal, 16)
            .frame(minHeight: 52)
            .background(Color(uiColor: .secondarySystemGroupedBackground), in: .rect(cornerRadius: 14))
    }
}
