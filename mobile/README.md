# GlassMeet mobile apps

Two native host apps that use the same Supabase project as the web app. Guests still book on the web.

| Folder | Platform | Built with |
|---|---|---|
| `ios/` | iPhone, iOS 17+ (Liquid Glass on iOS 26) | SwiftUI, system tab bar, [supabase-swift](https://github.com/supabase/supabase-swift) |
| `android/` | Android 8+ (API 26) | Kotlin, Jetpack Compose, Material 3, [supabase-kt](https://github.com/supabase-community/supabase-kt) |

Both follow `SPEC.md` (navigation, screens, states, design tokens, sign-in setup): sign in (email, Google, LinkedIn), create account and verify code, reset password, first-run onboarding, Home, Meetings and a meeting's page, Scheduling and the full event type form, Availability (weekly hours, date overrides, daily limit, UAE holidays), Analytics, Booking messages (the web's notifications), Calendar connections (subscribe feed, Google, Apple Calendar file, Microsoft), Settings (profile, photo, account deletion) and WhatsApp messages.

The tab bar is Home, Meetings, Scheduling, Availability and Settings. New event types start from the + button in Scheduling.

## Sign-in and links

- Google and LinkedIn sign-in come back to `glassmeet://auth-callback`. That address must be in Supabase → Authentication → URL Configuration → Redirect URLs (add `glassmeet://**`), or Supabase sends people to the website instead.
- GlassMeet links (`https://glassmeet.run.place/book/<id>`, `/bookings?id=…`, and `glassmeet://book/<id>`) open in the app: the host's own event type opens its editor, anyone else's booking page opens in an in-app browser.
  - Android lists the links in its manifest. To open them without asking, the site needs `/.well-known/assetlinks.json` with the SHA-256 of the app's signing key; until then the host can turn on Settings → Apps → GlassMeet → Open supported links.
  - iPhone needs a signed build with the Associated Domains capability (`applinks:glassmeet.run.place`) and `/.well-known/apple-app-site-association` on the site with the team ID. The unsigned .ipa can't use it; `glassmeet://` links work in every build.

## Building

There is no Mac or Android SDK on the development PC, so both apps are built by GitHub Actions
(`.github/workflows/mobile-apps.yml`) whenever something in `mobile/` changes:

- **iPhone .ipa:** a macOS runner generates the Xcode project from `ios/project.yml` with XcodeGen and builds for real iPhones (no simulator), unsigned, saved as the `GlassMeet-unsigned-ipa` artifact. It installs only after it is signed with an Apple ID (Xcode on a Mac, or a sideloading tool such as Sideloadly or AltStore on Windows). With a free Apple ID the install lasts 7 days; App Store or TestFlight distribution needs a paid Apple Developer account and a signed archive.
- **Android:** a Linux runner builds `app-debug.apk`, saved as the `GlassMeet-android` artifact.

On a Mac you can also run `brew install xcodegen && cd mobile/ios && xcodegen generate` and open `GlassMeet.xcodeproj`, or open `mobile/android` in Android Studio.

## Cloud phones (Appetize)

Add an Appetize API token as the repository secret `APPETIZE_API_TOKEN` (`APPITIZE_API` also works). Each Android build then uploads to the same Appetize app (the repository variable `APPETIZE_ANDROID_KEY` overrides it). iOS has no cloud phone: Appetize only runs simulator builds, and the iOS app is built for real iPhones.

- Cloud Android phone: https://appetize.io/app/5wtmxqfkukoh2kkicaev2z72sa

## App icons

From the brand kit's `iosicons/ver2_light` set.
