import SwiftUI

enum AuthRoute: Hashable {
    case createAccount
    case verify(email: String)
    case reset(email: String)
}

/// Sign in, Create account, Verify code and Reset password, in one stack
struct AuthFlowView: View {
    @State private var path: [AuthRoute] = []

    var body: some View {
        NavigationStack(path: $path) {
            SignInView(path: $path)
                .navigationDestination(for: AuthRoute.self) { route in
                    switch route {
                    case .createAccount: CreateAccountView(path: $path)
                    case .verify(let email): VerifyCodeView(email: email)
                    case .reset(let email): ResetPasswordView(email: email)
                    }
                }
        }
    }
}

/// The brand mark, tinted brand teal, over a title and a line
struct AuthHeader: View {
    var title: String
    var subtitle: String

    var body: some View {
        VStack(spacing: Theme.Space.s) {
            Image("Mark")
                .renderingMode(.template)
                .resizable()
                .scaledToFit()
                .frame(width: 64, height: 64)
                .foregroundStyle(Theme.brandTeal)
                .accessibilityLabel("GlassMeet")
            Text(title)
                .font(Theme.Typography.title)
                .foregroundStyle(Theme.text)
                .multilineTextAlignment(.center)
            Text(subtitle)
                .font(.callout)
                .foregroundStyle(Theme.textSecondary)
                .multilineTextAlignment(.center)
        }
        .padding(.top, Theme.Space.xl)
        .frame(maxWidth: .infinity)
    }
}

/// A labelled text field on a raised fill
struct AuthField: View {
    enum Kind { case email, password, newPassword, name, code }

    var title: String
    @Binding var text: String
    var kind: Kind

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).font(.subheadline.weight(.semibold)).foregroundStyle(Theme.text)
            Group {
                switch kind {
                case .password, .newPassword:
                    SecureField(title, text: $text)
                        .textContentType(kind == .password ? .password : .newPassword)
                case .email:
                    TextField("name@example.com", text: $text)
                        .textContentType(.username)
                        .keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                case .name:
                    TextField("Your full name", text: $text)
                        .textContentType(.name)
                case .code:
                    TextField("123456", text: $text)
                        .textContentType(.oneTimeCode)
                        .keyboardType(.numberPad)
                        .font(.title3.monospacedDigit())
                }
            }
            .fieldBackground()
        }
    }
}

/// A plain-words problem under a field or button
struct InlineError: View {
    var text: String?
    var body: some View {
        if let text {
            Label(text, systemImage: "exclamationmark.circle.fill")
                .font(.subheadline)
                .foregroundStyle(Theme.danger)
                .frame(maxWidth: .infinity, alignment: .leading)
                .transition(.opacity)
        }
    }
}

/// The main button on a sign-in page, with a spinner while busy
struct AuthButton: View {
    var title: String
    var busy: Bool
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            ZStack {
                Text(title).opacity(busy ? 0 : 1)
                if busy { ProgressView().tint(Theme.onPrimary) }
            }
        }
        .buttonStyle(PrimaryButtonStyle())
        .disabled(busy)
    }
}

/// A page of the sign-in flow: scrolls, keeps a readable width, dismisses the keyboard
struct AuthPage<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        ScrollView {
            VStack(spacing: Theme.Space.xl) { content }
                .padding(.horizontal, Theme.Space.l)
                .padding(.bottom, Theme.Space.xxl)
                .frame(maxWidth: 480)
                .frame(maxWidth: .infinity)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(Theme.background)
    }
}

enum AuthRules {
    static func validEmail(_ email: String) -> Bool {
        let e = email.trimmingCharacters(in: .whitespaces)
        guard let at = e.firstIndex(of: "@") else { return false }
        return e[e.index(after: at)...].contains(".") && !e.contains(" ")
    }

    /// 6 to 10 digits
    static func validCode(_ code: String) -> Bool { (6...10).contains(code.count) && code.allSatisfy(\.isNumber) }
}
