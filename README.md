# TripPilot - Smart Driver Assistant for Bharat Taxi & Rapido

**Repository**: [https://github.com/omparkashk22/trip-pilot-](https://github.com/omparkashk22/trip-pilot-)

TripPilot is a Kotlin & Jetpack Compose Android application designed for commercial cab and bike taxi drivers in India. It monitors incoming ride offers on screen using an Accessibility Service, parses fares, pickup & destination details, evaluates them against custom distance/fare/location filters, and automatically accepts profitable trips or displays heads-up alerts.

---

## Supported Driver Apps
- **Bharat Taxi**
- **Rapido**
*(Note: Exclusively targets verified driver applications; customer apps are ignored).*

---

## Key Features
1. **Accessibility Offer Parser**: Reads offer fares (including `₹BASE + ₹EXTRA`), pickup/drop distance & ETAs, and pickup/drop addresses in real-time.
2. **Mandatory Destination Logging**: Every detected offer is persisted locally to a Room database with its full destination address, skip reason, and tap latency.
3. **Smart Rule Engine**: Evaluates app status -> ride types -> fare range -> fare per km -> pickup distance -> drop distance -> location keywords (case-insensitive substring match).
4. **4 Match Priority Modes**: First match from top, Highest fare, Highest fare per km, or Nearest pickup.
5. **Calibrated Tap Strategies**: Automatically selects between `NODE_CLICK`, `PARENT_CLICK`, and `GESTURE_TAP` based on real-time success stats, with fallback retry.
6. **Built-in Developer Tools**:
   - **Screen Inspector**: Captures and dumps the accessibility window tree without needing ADB.
   - **Ride Offer Simulator**: Local visual mockup of Bharat Taxi & Rapido cards with overlap pill collision testing.

---

## Firebase Setup Guide

TripPilot includes full support for Firebase Authentication (Email/Password) and Cloud Firestore for driver subscription plans.

### 1. Add `google-services.json`
1. Go to the [Firebase Console](https://console.firebase.google.com/).
2. Create a project and register an Android app with package ID:
   ```
   com.aistudio.trippilot.vqxrt
   ```
3. Download `google-services.json` and place it in the `/app` directory:
   ```
   /app/google-services.json
   ```

### 2. Firestore Database Structure
Create a Firestore collection named `users`:

**Document Path**: `users/{uid}`
```json
{
  "name": "Ramesh Kumar",
  "email": "driver@example.com",
  "plan": "Pro Driver Monthly",
  "status": "Active",
  "expiresAt": 1790000000000
}
```

*Fields*:
- `name` (String): Driver's full name.
- `email` (String): Registered email address.
- `plan` (String): "Trial (14 Days)", "Pro Driver Monthly", or "Pro Driver Yearly".
- `status` (String): "Active" or "Expired". If expired or if `System.currentTimeMillis() > expiresAt`, automatic accepts are blocked and a live red countdown is displayed.
- `expiresAt` (Number / Timestamp): Unix epoch timestamp in milliseconds when subscription expires.

### 3. Offline / Local Mode
For evaluation without Firebase credentials, drivers can tap **"Continue in Offline / Local Mode"** on the Sign-In screen. This launches a local session with an active 14-day trial without network requirements.

---

## How to Use the Screen Inspector

1. Go to **Settings** > **Developer Tools**.
2. Under **Screen Inspector**, select the driver app you wish to inspect (Rapido or Bharat Taxi).
3. Tap **Capture Screen Tree (5s Delay)**.
4. Immediately switch to the driver app offer screen.
5. After the 5-second countdown, TripPilot dumps the full window hierarchy to app storage and opens a summary dialog showing:
   - Total node count & nodes with text
   - Verification of the "Accept" button
   - Clickability of the node or its ancestors
   - Share button to export the raw dump file for parser adjustments.
6. Alternatively, turn on **Capture on next offer** to automatically dump the tree the next time an offer appears.

---

## Release Builds & In-App Update Management

TripPilot includes an automated in-app update checker that supports both GitHub Releases and hosted `version.json` endpoints.

### 1. Versioning & Build Number Configuration
The app version is strictly managed via integer build numbers:
- `versionCode` = build number (integer). Set via the environment variable `BUILD_NUMBER` or `GITHUB_RUN_NUMBER`. If unset, Gradle falls back to the counter stored in `version.properties` (which automatically increments on release builds).
- `versionName` = `"1.0.<versionCode>"` (e.g. `1.0.7`).
- The `applicationId` remains constant (`com.aistudio.trippilot.vqxrt`).

### 2. How to Tag a Release & Attach the APK
To publish an update on GitHub that TripPilot can detect and install:
1. Tag your commit using a build descriptor:
   ```bash
   git tag "build 7"
   git push origin "build 7"
   ```
   *(Supported tag formats include: `"build 7"`, `"Build-7"`, `"build_7"`, `"v1.0.7"`, `"7"`, or `"release 7"`).*
2. Build the signed release APK:
   ```bash
   BUILD_NUMBER=7 gradle assembleRelease
   ```
3. Create a GitHub Release for the tag and attach the release APK:
   - Ensure the asset name ends with `.apk` (e.g., `app-release.apk` or `app-release-7.apk`).
   - If desired, include a line in the release description body:
     ```
     versionCode: 7
     ```
4. Publish the release (TripPilot considers all non-draft releases; pre-releases are included by default).

### 3. How the In-App Update Checker Reads Releases
When checking GitHub Releases, TripPilot queries the releases endpoint (`/repos/<owner>/<repo>/releases`) with strict cache-busting headers (`Cache-Control: no-cache`, `Pragma: no-cache`, `Accept: application/vnd.github+json`) and parses the remote build integer in this exact priority order:
1. `versionCode: N` defined in the release body text.
2. `(?i)build[\s_-]*(\d+)` regex on `tag_name`, then on release `name`.
3. `(?i)release[\s_-]*(\d+)` regex on `tag_name`, then on release `name`.
4. Trailing integer `(\d+)\s*$` on `tag_name` or `name`.
5. Embedded integer in the APK asset filename (e.g., `app-release-7.apk`).

TripPilot evaluates all eligible releases, selects the release with the **highest parsed build number** (not merely the latest by date), and compares:
$$\text{updateAvailable} = \text{remoteBuild} > \text{installedVersionCode}$$

If the tag cannot be parsed (e.g. `"latest"`), TripPilot transitions to the `ERROR` state with an explanatory message, never falsely claiming `"up to date"`.

### 4. Hosted `version.json` Alternative
TripPilot can also read updates from a hosted JSON file with the following format:
```json
{
  "versionCode": 7,
  "versionName": "1.0.7",
  "apkUrl": "https://example.com/downloads/app-release-7.apk",
  "sha256": "abcdef1234567890...",
  "notes": "Bug fixes and faster accept response",
  "minSupportedVersionCode": 1
}
```
Requests to `version.json` automatically include a cache-busting timestamp parameter (`?t=<millis>`).

