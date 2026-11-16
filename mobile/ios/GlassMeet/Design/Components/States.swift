import SwiftUI

/// Where a screen's data is: shown as a spinner, an error with Try again, or the content.
enum LoadState: Equatable {
    case idle, loading, loaded
    case failed(String)

    var isLoaded: Bool { self == .loaded }
    var error: String? {
        if case .failed(let message) = self { return message }
        return nil
    }
}

struct LoadingView: View {
    var label: String = "Loading"
    var body: some View {
        VStack(spacing: Theme.Space.s) {
            ProgressView().controlSize(.large)
            Text(label).font(.subheadline).foregroundStyle(Theme.textSecondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// Icon, title, one sentence, one action
struct EmptyStateView<Action: View>: View {
    var title: String
    var systemImage: String
    var message: String
    @ViewBuilder var action: Action

    var body: some View {
        ContentUnavailableView {
            Label {
                Text(title).font(.title3.weight(.semibold)).foregroundStyle(Theme.text)
            } icon: {
                Image(systemName: systemImage).foregroundStyle(Theme.primary)
            }
        } description: {
            Text(message).foregroundStyle(Theme.textSecondary)
        } actions: {
            action
        }
    }
}

extension EmptyStateView where Action == EmptyView {
    init(title: String, systemImage: String, message: String) {
        self.init(title: title, systemImage: systemImage, message: message) { EmptyView() }
    }
}

/// A plain message and Try again
struct ErrorStateView: View {
    var title: String = "Couldn’t load this"
    var message: String
    var retry: () async -> Void

    var body: some View {
        ContentUnavailableView {
            Label {
                Text(title).font(.title3.weight(.semibold)).foregroundStyle(Theme.text)
            } icon: {
                Image(systemName: "wifi.exclamationmark").foregroundStyle(Theme.warning)
            }
        } description: {
            Text(message).foregroundStyle(Theme.textSecondary)
        } actions: {
            Button("Try again") { Task { await retry() } }
                .buttonStyle(PrimaryButtonStyle(compact: true))
        }
    }
}

/// Shows loading / error / content for a screen whose data may not be there yet
struct Loadable<Content: View>: View {
    var state: LoadState
    var isEmpty: Bool
    var retry: () async -> Void
    @ViewBuilder var content: Content

    var body: some View {
        if isEmpty, let error = state.error {
            ErrorStateView(message: error, retry: retry)
        } else if isEmpty, !state.isLoaded {
            LoadingView()
        } else {
            content
        }
    }
}

// MARK: - Buttons for content (glass is kept for bars and toolbars)

/// A filled primary capsule
struct PrimaryButtonStyle: ButtonStyle {
    var compact = false
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline)
            .foregroundStyle(Theme.onPrimary)
            .padding(.horizontal, compact ? Theme.Space.l : Theme.Space.xl)
            .frame(minHeight: compact ? Theme.minTouch : 50)
            .frame(maxWidth: compact ? nil : .infinity)
            .background(Theme.primary.opacity(isEnabled ? (configuration.isPressed ? 0.8 : 1) : 0.4), in: Capsule())
            .contentShape(Capsule())
    }
}

/// A tinted capsule on primarySoft
struct SecondaryButtonStyle: ButtonStyle {
    var compact = false
    var tint: Color = Theme.link
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(compact ? .subheadline.weight(.semibold) : .headline)
            .foregroundStyle(tint.opacity(isEnabled ? 1 : 0.4))
            .padding(.horizontal, compact ? Theme.Space.m : Theme.Space.xl)
            .frame(minHeight: compact ? 36 : 50)
            .frame(maxWidth: compact ? nil : .infinity)
            .background(Theme.primarySoft.opacity(configuration.isPressed ? 0.7 : 1), in: Capsule())
            // compact is 36 pt to look at, 44 pt to tap
            .frame(minHeight: Theme.minTouch)
            .contentShape(.rect)
    }
}

// MARK: - Alerts and toasts

extension View {
    /// Caps a text field at the web app's limit (src/lib/inputRules.js LIMITS)
    func maxLength(_ text: Binding<String>, _ limit: Int) -> some View {
        onChange(of: text.wrappedValue) { _, new in
            if new.count > limit { text.wrappedValue = String(new.prefix(limit)) }
        }
    }

    /// Shows an error once, with OK
    func errorAlert(_ message: Binding<String?>) -> some View {
        alert("Something went wrong", isPresented: Binding(get: { message.wrappedValue != nil }, set: { if !$0 { message.wrappedValue = nil } })) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(message.wrappedValue ?? "")
        }
    }

    /// A short confirmation ("Link copied") at the top of the screen
    func toast(_ message: Binding<String?>) -> some View {
        overlay(alignment: .top) {
            if let text = message.wrappedValue {
                Text(text)
                    .font(.subheadline.weight(.semibold))
                    .padding(.horizontal, Theme.Space.m)
                    .padding(.vertical, Theme.Space.s)
                    .glassCapsule()
                    .padding(.top, Theme.Space.xs)
                    .transition(.move(edge: .top).combined(with: .opacity))
                    .task(id: text) {
                        try? await Task.sleep(for: .seconds(2))
                        withAnimation { message.wrappedValue = nil }
                    }
                    .accessibilityAddTraits(.isStaticText)
            }
        }
        .animation(.snappy, value: message.wrappedValue)
        .sensoryFeedback(.success, trigger: message.wrappedValue) { _, new in new != nil }
    }
}

extension Error {
    /// The error in plain words
    var readable: String {
        if let url = self as? URLError {
            return url.code == .notConnectedToInternet || url.code == .networkConnectionLost
                ? "You’re offline. Check your connection and try again."
                : "Couldn’t reach GlassMeet. Check your connection and try again."
        }
        if let local = self as? LocalizedError, let text = local.errorDescription, !text.isEmpty { return text }
        return localizedDescription
    }
}
