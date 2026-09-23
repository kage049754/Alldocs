# Alldocs

Native Android offline document workspace built with Kotlin and Jetpack Compose.

## Build
GitHub Actions builds a debug APK on every push to `main`. The APK is uploaded as the **Alldocs-debug-apk** workflow artifact.

## Storage
Documents are stored locally with Android DataStore and remain available after the app is closed and reopened.

## Privacy
The app does not declare the Android `INTERNET` permission. It uses Android's local document UI rather than broad external-storage access.

## Current scope
- Offline document library
- Search
- Create/edit/save documents
- Delete documents
- Local persistent storage
- Material 3 UI
- GitHub Actions APK build

This is an installable debug build; it is not Play Store-signed.
