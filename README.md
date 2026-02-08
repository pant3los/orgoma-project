# OrgOMa — Organic Olive Management (Android)

OrgOMa is an Android application for managing olive groves on a map, designed for organic olive farming workflows. The app supports **role-based usage** (admin / worker), **Google sign-in**, and **Firebase** persistence.

## Key Features

- **Google Sign-In** (Firebase Authentication)
- **Role-based access**
  - **Admin**: add and edit groves on the map
  - **Worker**: view grove info and use proximity/location features
- **Google Maps** integration (markers & grove locations)
- **Firebase Firestore** for storing users and grove data
- Optional **notifications** (used for in-app alerts/reminders)

## Tech Stack

- Android (Gradle)
- Java Activities + XML layouts (core UI)
- Firebase: Auth, Firestore, Messaging
- Google Play Services: Maps, Location, Auth

## Repository Structure

- `app/`
  - Prebuilt Android APK (`OrgOMa.apk`) for quick installation/testing
- `src/`
  - Source code
  - `src/smartphone/OrgOMa/` → Android Studio project
  - `src/backend/` → backend placeholder (no implementation included)
- `report/`
  - Project report (PDF)

## Getting Started (Run in Android Studio)

### Prerequisites
- Android Studio (recommended latest stable)
- Android SDK installed (project targets modern SDK versions)
- A device/emulator with Google Play Services

### Steps
1. Open Android Studio
2. **Open** the project folder:
   - `src/smartphone/OrgOMa/`
3. Let Gradle sync
4. Run the app on an emulator or physical device

## Firebase / Google Services Notes

This project uses Firebase and Google services (Auth/Firestore/Maps). If you fork or recreate the setup:
- Ensure you have a Firebase project configured
- Add/replace the `google-services.json` inside:
  - `src/smartphone/OrgOMa/app/`
- Ensure your Google Maps API key and Firebase settings are configured correctly for your package name.

## Build an APK

From Android Studio:
- `Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`

Or via Gradle (from the Android project root):
- `./gradlew assembleDebug`

## Installation (APK)

A prebuilt APK may be available here:
- `app/OrgOMa.apk`

Copy it to an Android device and install it (you may need to allow installation from unknown sources).

## License

Academic / course project repository (no explicit license provided).
