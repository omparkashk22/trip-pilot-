# TripPilot - Smart Driver Assistant for Bharat Taxi & Rapido

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
