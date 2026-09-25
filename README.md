# JetTrail

JetTrail is a private, account-free Android flight logger for a Galaxy Z Fold4. It records entirely on-device, uses no backend or analytics, and deliberately has no `INTERNET` permission.

## Open and run

1. Open `D:\JetTrail` in Android Studio.
2. Let Gradle sync, connect the phone by USB, enable Developer options and USB debugging, and approve the phone's RSA prompt.
3. Choose the Fold4 in the device selector and click **Run**. Android Studio builds and installs the local debug app automatically.

The project targets API 35 and runs on Android 16. It uses Kotlin, Compose, Room, platform GNSS, and a location foreground service. The repository contains a Gradle wrapper; this workstation also has a project-local SDK selected by `local.properties`.

## Before a real flight

- Open JetTrail while still on the ground and grant precise location and notification permission.
- Enable airplane mode when instructed by the crew, then confirm that **Location remains enabled**. GNSS itself is passive and works offline.
- Tap **Start Flight while JetTrail is visible**. Android requires this user action before the location foreground service can continue under the lock screen.
- Keep the persistent JetTrail notification enabled. Samsung battery saver/location power modes can reduce updates; JetTrail displays a warning when detected.
- Put the phone securely away during takeoff and landing. XP and badges never require interaction during critical phases.

## Simulation Lab

Open **Simulation Lab**, choose compression and whether to include dropouts/outliers, then start. It injects deterministic taxi, ascent, high-speed cruise, descent, noisy GNSS, gaps, turbulence, and impossible spikes through the same filter and Room persistence path used for real samples. The resulting session appears in Logbook and is marked as a simulation internally.

## Offline data and privacy

- The app bundles 4,133 scheduled-service airports from [OurAirports](https://ourairports.com/data/) for offline origin/destination inference.
- The lightweight country map is Natural Earth 1:110m public-domain geometry. There are no online tiles.
- Raw samples, including rejected outliers and dropout ticks, remain in the local Room database. Visible statistics exclude rejected values and label measured, estimated, and unavailable values.
- Uninstalling the app or clearing its storage removes the private logbook. There is intentionally no account, sync, sharing, or export.

## Measurement limitations

- GNSS reports **ground speed, not airspeed**. Winds and the aircraft system make true airspeed different.
- A metal aircraft cabin and a windowless/aisle position can cause long GNSS gaps or no fix at all.
- GPS altitude is noisier than horizontal position and is not the aircraft's certified pressure altitude.
- When a barometer exists, JetTrail estimates **cabin pressure altitude** using a standard-atmosphere assumption. In a pressurized cabin it is neither aircraft altitude nor height above terrain.
- The turbulence indicator is a rough phone-motion estimate. Handheld movement can dominate it; it is not an aviation instrument.
- Android can stop recording after a force-stop, reboot, permission removal, or aggressive device power management. An active Room session is retained for recovery rather than silently discarded.

## Verification

Run `gradlew.bat testDebugUnitTest assembleDebug` from Android Studio's terminal. Domain tests cover geodesy/filtering, statistics and phases, airport inference/gamification, persistence mapping, deterministic simulation, dropouts, and outliers.
