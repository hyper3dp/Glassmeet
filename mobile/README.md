# GlassMeet mobile apps

Two native host apps that use the same Supabase project as the web app. Guests still book on the web.

| Folder | Platform | Built with |
|---|---|---|
| `ios/` | iPhone, iOS 26+ | SwiftUI, system tab bar and Liquid Glass, [supabase-swift](https://github.com/supabase/supabase-swift) |
| `android/` | Android 8+ (API 26) | Kotlin, Jetpack Compose, Material 3, [supabase-kt](https://github.com/supabase-community/supabase-kt) |

Both follow `SPEC.md` (navigation, screens, states, design tokens, sign-in setup): sign in (email, Google, LinkedIn; Apple reserved), create account and verify code, reset password, Home, Meetings and a meeting's page, Scheduling and the full event type form, Availability (weekly hours, date overrides, daily limit, UAE holidays), Analytics, Calendar connections (Google, Microsoft, subscribe feed), Settings (profile, photo, account deletion) and WhatsApp messages.

## Building

There is no Mac or Android SDK on the development PC, so both apps are built by GitHub Actions
(`.github/workflows/mobile-apps.yml`) whenever something in `mobile/` changes:

- **iOS:** a macOS runner generates the Xcode project from `ios/project.yml` with XcodeGen and builds for the iOS Simulator. The zipped `.app` is saved as the `GlassMeet-ios-simulator` artifact.
- **iPhone .ipa:** the same runner also builds for real iPhones, unsigned, saved as the `GlassMeet-unsigned-ipa` artifact. It installs only after it is signed with an Apple ID (Xcode on a Mac, or a sideloading tool such as Sideloadly or AltStore on Windows). With a free Apple ID the install lasts 7 days; App Store or TestFlight distribution needs a paid Apple Developer account and a signed archive.
- **Android:** a Linux runner builds `app-debug.apk`, saved as the `GlassMeet-android` artifact.

On a Mac you can also run `brew install xcodegen && cd mobile/ios && xcodegen generate` and open `GlassMeet.xcodeproj`, or open `mobile/android` in Android Studio.

## Cloud phones (Appetize)

Add an Appetize API token as the repository secret `APPETIZE_API_TOKEN` (`APPITIZE_API` also works). Each build then uploads to Appetize and the run summary links the cloud iPhone and Android phone. Builds update the same two Appetize apps (their public keys are set in the workflow; the repository variables `APPETIZE_IOS_KEY` and `APPETIZE_ANDROID_KEY` override them):

- Cloud iPhone: https://appetize.io/app/4b3x6akp4arpb3xmgvmidck7my
- Cloud Android phone: https://appetize.io/app/5wtmxqfkukoh2kkicaev2z72sa

## App icons

From the brand kit's `iosicons/ver2_dark` set.
