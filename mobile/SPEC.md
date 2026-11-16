# GlassMeet mobile: product and design spec

The single source for the iOS app (`ios/`), the Android app (`android/`) and their Figma design. Each platform follows
its own guidelines (Apple HIG for iOS 26, Material 3 for Android); the structure, content and behaviour below are the same
on both. Where a platform guideline forces a difference, it is noted.

## Navigation

Five top-level sections, each a tab (iOS system tab bar on Liquid Glass) or a navigation bar destination (Android Material 3):

| Tab | iOS symbol | Android icon | Contains |
|---|---|---|---|
| Home | `house` | `home` | dashboard; links to Analytics |
| Meetings | `person.2` | `groups` | bookings list and a meeting's page |
| Scheduling | `link` | `link` | event types and the event type form |
| Availability | `clock` | `schedule` | weekly hours, date overrides, daily limit, holidays |
| Settings | `gearshape` | `settings` | profile, WhatsApp, Calendar connections, account |

Pushed pages: Meeting, Event type form, Analytics (from Home), WhatsApp (from Settings), Calendar connections (from Settings).
Signed out: Sign in, Create account, Verify code, Reset password.
Creating something is an action, so it lives in the top bar (iOS toolbar `+` button on glass; Android top app bar action or a FAB on Scheduling), never in the tab bar.

## Screens and content

### Sign in
- Brand mark (from the brand kit, tinted brand teal), "Sign in to GlassMeet", tagline "Schedule meetings. Stay on WhatsApp."
- Email, Password, primary **Sign in**.
- Divider "or".
- **Continue with Google**, **Continue with LinkedIn** (Supabase OAuth providers `google` and `linkedin_oidc`).
- Links: Forgot password, Create an account.
- Create account: name, email, password -> Verify code (6 to 10 digit code from email, Resend) -> signed in.
- Reset password: email -> code -> new password.
- Errors in plain words under the field or button (wrong password, unconfirmed email, network).

### Home
- Large title "Home" (iOS) / top app bar "Home"; greeting line "Good morning, {first name}" with today's meeting count.
- Requests banner when bookings wait for approval: "{n} requests waiting" -> Meetings, Requests filter.
- Stats row (4 tiles): Today, Upcoming, All bookings, Event types. Tappable tile "Insights" -> Analytics.
- Next meeting card: guest name, time ("Today at 2:30 PM"), event name with its colour dot, Join (if link), Open.
- Upcoming: next 7 days grouped by day ("Today", "Tomorrow", "Wed, Nov 18"), rows with time, guest, event colour dot.
- Share your booking link: the newest active event type, Copy and Share (system share sheet, which includes WhatsApp).
- Empty state: "Nothing booked yet" + "Share your booking link and new meetings show up here." + Share button.

### Meetings
- Filters: Upcoming, Requests ({n}), Past, Cancelled (segmented control on iOS, segmented buttons / filter chips on Android). Cancelled hides items older than 2 days, like the web.
- Search (iOS `.searchable` in the navigation bar; Android search field in the top app bar): name, email, event name, phone digits.
- Rows grouped by day: avatar initials, guest name, time range, event name with colour dot, status chip (Request, Cancelled, Declined).
- Meeting page: guest header (avatar, name, event, status chip); When (date, time range, time zone, Join); Guest (email, phone, WhatsApp); Answers (custom_answers); Guest notes; Your notes (editable, autosave to `notes`); Meeting link (editable, autosave to `meeting_url`); Message (choose Confirmation / Reminder 24h / Reminder 1h / Cancellation / Decline, editable text, Send on WhatsApp, Copy, Email); actions: pending -> Approve + Decline; confirmed upcoming -> Cancel meeting. After any status or link change, call `pushBooking` on both calendar functions (ignore their errors).
- Floating action area at the bottom (iOS: Liquid Glass bar via `safeAreaBar` + `GlassEffectContainer`; Android: bottom app bar / surface): Approve/Decline, or Send on WhatsApp.

### Scheduling
- List of event types: colour dot, name, "30 min · Google Meet · Needs approval", active switch, Copy link, Share, More menu (Edit, Duplicate, Delete or "Turn off instead").
- New event type -> template picker (built-in templates from `src/lib/templates.js` + the host's `event_templates`) -> form.
- Event type form sections:
  1. Basics: kind (One-on-one / Group / One-off), name, duration (15/30/45/60/90 or custom 5-720), location (Google Meet, Zoom, Microsoft Teams, WhatsApp call, Phone call, In person, Other), meeting link, address (saved addresses), description (500), private note (2000).
  2. When: "Use my working hours" or custom: repeats (daily/weekly/monthly, every N, weekdays, start date, end date) or specific dates; fixed start times (optional, "HH:MM").
  3. Rules: minimum notice (minutes/hours/days), how far ahead (max_future_days), limit per day, buffer before, buffer after.
  4. Booking form: custom questions (max 10; short text, long text, dropdown, multiple choice; required; options), email required.
  5. Options: requires approval, group size (max_attendees) for Group.
  6. Danger: Delete.
- Saving also writes `host_name`, `host_photo`, `host_timezone` from the profile.

### Availability
- Weekly hours: each weekday (Monday first) with an on/off switch and one or more time ranges (+ add range, remove range); end may be 24:00. Saves per row (update/insert/delete), not delete-all-then-insert.
- Date overrides: list of upcoming `availability_exceptions`; add sheet: date, mode (Day off / Block hours / Custom hours), times, label.
- Daily limit across all event types (`profiles.max_per_day`, 1-100 or off).
- UAE public holidays switch (`profiles.holidays = 'ae'`), with clashes listed.

### Analytics
- Stat tiles: Total booked, Confirmed, Cancelled, Average length.
- Insights: Most popular event, Most popular time ("Not enough data" when unclear), Total attendees, Event types.
- Chart: Bookings by month (bars). Chart: Busiest times heatmap (7 weekdays x 8am-8pm).
- iOS: Swift Charts. Android: Compose Canvas (no extra chart library).

### Calendar connections
- Google Calendar card: status, connected email, calendar picker (listCalendars), Auto-sync switch, Sync now with last-synced time, Disconnect; Connect when not connected.
- Microsoft Outlook / Teams card: same pattern (provider `microsoft`); hidden when `available` says not configured.
- Subscribe feed: copy the .ics feed URL, Add to Apple Calendar (`webcal:`), Google, Outlook; Reset link (RPC `rotate_cal_feed`) with a confirmation.
- Connecting: call `authUrl` with `returnTo: "glassmeet://calendar-connections"`, open the URL in ASWebAuthenticationSession (iOS, callback scheme `glassmeet`) or Custom Tabs (Android), read `code` and `state` (and `provider`) from the callback, call `finish`.

### Settings
- Profile: photo (upload to Storage `avatars/<uid>/avatar-<timestamp>.<ext>`), name, username, email (read-only), bio, time zone, WhatsApp number. Saving updates `host_*` on all event types.
- Rows: WhatsApp messages ->, Calendar connections ->, Addresses (optional), Reset password, Open GlassMeet on the web, Privacy Policy, Terms.
- Sign out (confirm). Delete account (type DELETE; disconnect calendars, RPC `delete_my_account`, sign out). Apple requires in-app account deletion.

### WhatsApp messages
- Your WhatsApp number.
- Templates: Confirmation, Reminder 24 hours before, Reminder 1 hour before, each with placeholder chips [Name] [Host] [Date] [Time] [Duration] [Meeting Link] [Booking Link] [Timezone] and a live preview bubble.
- Note: "GlassMeet prepares the message and opens WhatsApp; you send it." Never promise automatic sending.

### States for every list and page
- Loading: native progress (iOS `ProgressView`, Android `CircularProgressIndicator`) or skeleton rows.
- Empty: icon, title, one sentence, one action.
- Error: plain message + Try again.
- Pull to refresh on every list.

## Design tokens

Colours (light / dark). Names are shared by Figma variables, `Theme.swift` and `Theme.kt`.

| Token | Light | Dark | Use |
|---|---|---|---|
| background | #F6F9F9 | #0F1516 | page |
| surface | #FFFFFF | #162022 | cards, grouped rows |
| surfaceRaised | #EEF4F4 | #1D2A2C | fields, chips, tiles |
| text | #344248 | #E6EEEE | primary text (brand slate in light) |
| textSecondary | #55686D | #A0B4B4 | secondary text |
| textTertiary | #66787C | #7F9495 | hints, captions |
| primary | #2B7A75 | #66ACA9 | buttons, selection, tint |
| onPrimary | #FFFFFF | #14201F | text on primary |
| primarySoft | #E3F1F0 | #1B3332 | soft fills, selected chips, avatars |
| link | #24706B | #7FC4C0 | coloured text |
| border | #D6E2E1 | #2A3A3C | hairlines |
| brandTeal | #66ACA9 | #66ACA9 | logo mark only, never text |
| success | #0A8A4C | #3FB97A | confirmed |
| warning | #B25E00 | #E3A044 | requests |
| danger | #C8372D | #FF7B72 | destructive |
| whatsappBubble | #D9FDD3 | #144D37 | message previews only |

Event colours come from each event type's `color` (fallback primary).

Type:
- iOS: SF Pro via Dynamic Type text styles (largeTitle 34, title 28, title2 22, title3 20, headline 17 semibold, body 17, callout 16, subheadline 15, footnote 13, caption 12). Numbers in stats use `.monospacedDigit()`.
- Android: Material 3 type scale on Roboto (displaySmall 36, headlineMedium 28, headlineSmall 24, titleLarge 22, titleMedium 16 medium, bodyLarge 16, bodyMedium 14, labelLarge 14 medium, labelMedium 12).

Spacing: 4, 8, 12, 16, 20, 24, 32. Screen side margin 16 (iOS lists use the system inset grouped margins).
Shape: iOS cards 26 (concentric with the iOS 26 display), fields 12, buttons capsule. Android: Material shape scale (small 8, medium 12, large 16, extraLarge 28), buttons full.
Touch targets: iOS 44 pt minimum, Android 48 dp minimum.

Liquid Glass (iOS only): the system tab bar, navigation bar buttons, toolbars, sheets, and the floating action bar on Meeting / Event type form. Content cards are opaque `surface`. Android has no glass or blur anywhere.

Widths to check: iPhone SE (375 x 667), iPhone 16 (393 x 852), Android small (360 x 640), Android large (412 x 915).

## Sign-in configuration (OAuth)

- Redirect URL for both apps: `glassmeet://auth-callback`. It must be in Supabase Auth -> URL Configuration -> Redirect URLs.
- iOS registers the URL scheme `glassmeet` (Info.plist `CFBundleURLTypes`); supabase-swift `signInWithOAuth(provider:redirectTo:)` runs ASWebAuthenticationSession.
- Android registers `glassmeet://auth-callback` (and `glassmeet://calendar-connections`) with an intent filter; supabase-kt `Auth { scheme = "glassmeet"; host = "auth-callback" }`, `handleDeeplinks` in the activity, external auth via Custom Tabs.
- Only the public anon/publishable key ships in the apps. Never the service role key or any OAuth client secret.
