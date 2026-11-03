# ᛃᚷᚷᛞᚱᚨᛋᛁᛚ Yggdrasil Launcher

The Android home screen of [Yggdrasil OS](https://github.com/Ozorhaar/yggdrasil-os): every app is a
carved longship shield, text is set in **Yggdrasil Runic**, and the colors are iron, gold, frost and ember.

## Features

- **Two layouts, switchable:** a familiar *Grid* (pages of shields + a dock) or the *Longhall*
  (a runestone with a runic clock, date and a Hafnir assistant button above a carved list of your apps)
- **App drawer** with instant search (swipe up anywhere; type and press Go to launch the top match)
- **Carved shields:** apps with Android's themed (monochrome) icon get a clean carved glyph; others
  are inlaid in the shield
- **Unread badges** (ember dots), **swipe down** for notifications, **double-tap** to lock
- Hold an app for: Move, Add/Remove from home, Pin/Unpin dock, App info, Uninstall
- Hold empty space for settings

## Install

Download the APK from [Releases](https://github.com/Ozorhaar/yggdrasil-launcher/releases/latest), open it on
the phone (allow your browser/Files app to install apps when asked), then press Home and pick **Yggdrasil**.
In the launcher's settings, grant notification access (badges) and turn on the accessibility service
(double-tap to lock).

Every push to `main` builds a new signed APK and release. Newer APKs install straight over older ones.

## Building

GitHub Actions builds with Gradle 8.11 / AGP 8.7 / Kotlin 2.1 / Compose. The release signing key is held in
the repository secrets `YGG_KEYSTORE_B64` and `YGG_KEYSTORE_PASSWORD`.
