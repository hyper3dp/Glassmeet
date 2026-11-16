import SwiftUI

// The app runs on iOS 17 and later. iOS 26 gets Liquid Glass; iOS 17 and 18 get the closest
// system look (bar materials, bordered buttons). Views use these wrappers instead of calling
// the iOS 26 APIs directly.

extension View {
    /// A bar that floats over the content at the top or bottom edge
    @ViewBuilder func floatingBar<Bar: View>(edge: VerticalEdge, @ViewBuilder _ bar: () -> Bar) -> some View {
        if #available(iOS 26, *) {
            safeAreaBar(edge: edge, content: bar)
        } else {
            safeAreaInset(edge: edge, spacing: 0) {
                bar()
                    .padding(edge == .bottom ? .top : .bottom, Theme.Space.xs)
                    .frame(maxWidth: .infinity)
                    .background(.bar)
            }
        }
    }

    /// Glass buttons on iOS 26, bordered capsules before it
    @ViewBuilder func glassButton(prominent: Bool = false) -> some View {
        if #available(iOS 26, *) {
            if prominent { buttonStyle(.glassProminent) } else { buttonStyle(.glass) }
        } else {
            if prominent {
                buttonStyle(.borderedProminent).buttonBorderShape(.capsule)
            } else {
                buttonStyle(.bordered).buttonBorderShape(.capsule)
            }
        }
    }

    /// A floating capsule, such as a toast
    @ViewBuilder func glassCapsule() -> some View {
        if #available(iOS 26, *) {
            glassEffect(.regular, in: Capsule())
        } else {
            background(.regularMaterial, in: Capsule())
                .shadow(color: .black.opacity(0.12), radius: 8, y: 2)
        }
    }

    /// The tab bar shrinks while scrolling down (iOS 26 only)
    @ViewBuilder func minimizeTabBarOnScroll() -> some View {
        if #available(iOS 26, *) {
            tabBarMinimizeBehavior(.onScrollDown)
        } else {
            self
        }
    }
}

/// Lets glass buttons next to each other blend on iOS 26; a plain group before it
struct GlassGroup<Content: View>: View {
    var spacing: CGFloat
    @ViewBuilder var content: Content

    var body: some View {
        if #available(iOS 26, *) {
            GlassEffectContainer(spacing: spacing) { content }
        } else {
            content
        }
    }
}
