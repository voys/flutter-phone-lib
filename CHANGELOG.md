# Changelog

## [1.0.12] - Unreleased

- The SIP password is no longer kept in plain text. It was stored that way to be able to start natively, before Flutter runs, and is now kept in the iOS Keychain and with the Android Keystore through [shared_secure_storage](https://pub.dev/packages/shared_secure_storage). A password stored by an earlier version is moved the first time the app starts.
- On iOS, `stop()` now removes the stored SIP account, as Android already did. Before, the phone lib would still start natively with the previous account after the app had stopped it, for example on logout.

## [1.0.11] - 2026-11-20

- On iOS, `onCallEnded` now passes the call's end reason (e.g. `Busy Here`), as Android already did. Before, it was always empty.
- Fixed a bug on iOS where a call that started the app from a push always used the default ringtone, even when the app asked for its own.
- Fixed a bug on iOS where a contact without a given or family name (e.g. only a company) showed as a blank name. The caller's display name and number now show instead.
- Fixed a bug on iOS where the missed-call notification delegate never called its completion handler, which iOS requires.
- Fixed `Calls.active` throwing when the call has a contact.
- Fixed `RemoteMessage.fromMap` throwing when the message has no data.

## [1.0.10] - 2026-11-17

- Call states are now the same on Android and iOS. An incoming call that hasn't been answered is `RINGING` on both; Android reported it as `INITIALIZING`. An outgoing call is `INITIALIZING` until the other side rings, then `RINGING`; iOS reported it as `RINGING` straight away. Apps that check for `INITIALIZING` to detect an unanswered incoming call must check for `RINGING` instead. Use `Call.direction` to tell incoming and outgoing calls apart.
- On iOS a call is now `HELD_BY_LOCAL` as soon as hold starts, as it already was on Android. Before, iOS reported it as `CONNECTED` until the hold was confirmed.
- Fixed a bug on Android where ended calls were reported as `ERROR` instead of `ENDED`, and calls being updated or renegotiated as `ERROR` instead of `CONNECTED`.
- Added `PhoneLib.isRegistered`, which says whether the SIP account is currently registered.

## [1.0.9] - 2026-11-06

- Migrated the Android plugin to Flutter's built-in Kotlin. It no longer applies the Kotlin Gradle Plugin (KGP), so apps no longer get the warning that future Flutter versions will fail to build with it. Apps that keep `android.builtInKotlin=false` still build: Flutter applies KGP to the plugin, as long as the app declares KGP in its root `plugins {}` block.
- The Android plugin now compiles against SDK 36 (was 33). AGP 9 builds failed with SDK 33 because the androidx libraries that come with Flutter need SDK 34 or later. `minSdk` stays at 26.
- The example app uses AGP 9.1.0, Gradle 9.3.1 and built-in Kotlin, and its dependencies are upgraded.

## [1.0.8] - 2026-11-04

- Requires Flutter 3.47.0 or later (Dart 3.13 or later).
- Requires `equatable` 3. Apps still on `equatable` 2 must upgrade.
- `freezed` and `json_serializable` are now dev dependencies, so they are no longer pulled into apps that use this plugin.
- The example app uses the iOS UIScene lifecycle and the Gradle, AGP and Kotlin versions that Flutter 3.47 requires.
- Fixed a bug on Android where switching the audio route away from the speaker turned on software echo cancellation, even on devices with built-in echo cancellation. Both then ran at the same time, which can make the audio sound distorted or crackly.

## [1.0.7] - 2026-10-26

- The plugin no longer adds the `https://download.flutter.io` Maven repository to every project in the app's Android build. Builds failed when that host served a mismatched TLS certificate, and the Flutter Gradle plugin already provides the engine repository.

## [1.0.6] - 2026-10-24

- Resolve the iOS Linphone SDK package from the official GitHub mirror instead of gitlab.linphone.org

## [1.0.5] - 2026-10-23

- Upgraded the Android Linphone SDK from 5.4.94 to 5.5.35 to include upstream AAudio sound-card race and player lifecycle crash fixes.

## [1.0.4] - 2026-10-05

- Fixed a bug on iOS where the speaker button briefly showed as off after unholding a call that was on speaker. The audio state now keeps reporting the speaker while the call is on hold.

## [1.0.3] - 2026-09-30

- Added `PhoneLib.unregister()`, which removes the SIP registration from the server and completes once the server has confirmed it (or after about 5 seconds). `stop()` and `close()` don't wait for the server, so the registration could stay active until it expired. Call `unregister()` before them when logging out.

## [1.0.2] - 2026-09-30 [REVERTED]

- Reverted in 1.0.3. `stop()` and `close()` waited for the SIP unregister before clearing the credentials. They're back to the 1.0.1 behaviour, use `PhoneLib.unregister()` instead.

## [1.0.1] - 2026-09-03

- Supplementary contacts have been removed. The `SupplementaryContact` class is gone and `Preferences` no longer takes a `supplementaryContacts` argument.

## [1.0.0] - 2026-09-03

- The native libraries are now part of this plugin. Android Phone Integration Lib (0.1.147) and iOS Phone Lib (0.1.18) were vendored into the repository and are no longer external dependencies. Calling behaviour is unchanged.

## [0.0.46] - 2026-07-21

- Upgraded Android Phone Lib to 0.1.147 so ringtone isn't played briefly on the phone before switching to bluetooth

## [0.0.45] - 2026-03-27

- Upgraded Android Phone Lib to 0.1.146, you can now receive Vialer calls while on a GSM call

## [0.0.44] - 2025-12-16

- Fixed a bug on Android where background isolates (e.g. firebase_messaging) could interfere with the MethodChannel by setting it before the main app

## [0.0.42] - 2025-12-10

- Upgraded iOS Phone Lib to 0.1.18 to fix audio route reset issue when a call starts

## [0.0.41] - 2025-12-04

- Fixed an issue on iOS that transfer events weren't properly passed along

## [0.0.40] - 2025-11-17

- Added `playToneLocally()` method to play DTMF tones on-demand
- Added `playToneLocally` parameter to `sendDtmf()` for automatic local playback when sending DTMF

## [0.0.39] - 2025-10-27

- Fixed JVM target compatibility issue between Java and Kotlin compilation tasks on Android.

## [0.0.38] - 2025-10-27

- Solved dependency conflict with freezed 3.x in the project.

## [0.0.37] - 2025-07-29

- Migrated for iOS to Swift Package Manager 
- Upgraded APL to 0.1.142
- Upgraded IPL to 0.1.15

## [0.0.36] - 2025-03-18

- Android: Calls will now be rejected if you are in GSM or other-app call

## [0.0.35] - 2025-02-25

- Android: Calls will now be rejected if you are in GSM or other-app call

## [0.0.32] - 2024-10-25

- Android: It is now possible to enable advanced logging for voip

## [0.0.31] - 2024-10-25

- Updated iOSPhoneLib to 0.1.14 to optimise contact searching

## [0.0.30] - 2024-10-07

- Forcing ipv4

## [0.0.29] - 2024-10-04

- Upgraded APL to 0.1.138 to fix a bug where calls were ending early

## [0.0.28] - 2024-10-01

- Upgraded APL to 0.1.136 for improved incoming call handling

## [0.0.27] - 2024-09-27

- Upgraded APL to 0.1.135 for improved incoming call handling

## [0.0.26] - 2024-09-13

- Upgraded APL to 0.1.134 for improved incoming call handling
## [0.0.25] - 2024-09-06

- Fixed background push notifications not working on iOS

## [0.0.24] - 2024-09-05

- Microphone fix

## [0.0.23] - 2024-09-04

- Reverted change to re-add microphone as foreground service to fix audio disappearing when phone goes to background during a call

## [0.0.22] - 2024-08-27

- Improved experience when answering calls on iOS

## [0.0.21] - 2024-08-22

- Upgraded APL for foreground service requirements

## [0.0.19] - 2024-06-21

- Upgraded IPL to 0.1.11 for app icon badge support

## [0.0.18] - 2024-06-20

- Upgraded IPL to 0.1.10

## [0.0.15] - 2024-05-30

- Upgraded APL to 0.1.129

## [0.0.14] - 2024-02-27

- iOS: Supplementary contacts will be used for incoming calls

## [0.0.13] - 2024-02-26

- Android: Fixed a bug where media playback would not resume after an incoming call

## [0.0.12] - 2024-02-22

- Added generated files back to git

## [0.0.11] - 2024-02-22

- Lowered meta dependency requirement

## [0.0.10] - 2024-02-22

- Supports supplementary contacts
- Updated gradle project
- Removed generated files from git tracking

## [0.0.9] - 2024-02-08

- Fixed issue with preferences object

## [0.0.8] - 2024-02-08

- Upgraded to APL 0.1.123, allowing for adding supplementary contacts.

## [0.0.7] - 2024-01-25

- Upgraded to APL 0.1.122, emergency calls will now open in the native dialer
- 
## [0.0.6] - 2024-01-25

- Upgraded to APL 0.1.122, emergency calls will now open in the native dialer

## [0.0.5] - 2024-01-24

- Upgraded to APL 0.1.121, allowing emergency calls to be made
- 
## [0.0.4] - 2024-01-24

- Upgraded to APL 0.1.121, allowing emergency calls to be made

## [0.0.3] - 2024-01-18

- Upgraded to iOSPhoneLib 0.1.5, fixing an issue where the iOS dynamic island would not update call duration.

## [0.0.2] - 2024-01-17

- Pinning to a specific version of the IPL

## [0.0.1] - 2024-01-17

- Initial release