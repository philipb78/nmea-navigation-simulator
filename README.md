# NMEA Navigation Simulator

Android Kotlin app for generating simulated NMEA 0183 navigation traffic over TCP for N2K / SeaTalk converter testing.

## Features

- Material 3 XML UI
- IP/port connection controls
- Start/stop simulation
- Track visualization with custom `TrackView`
- Live heading, speed, XTE, and waypoint status
- Generated sentences:
  - `$GPAPB`
  - `$GPXTE`
  - `$GPRMC`
  - `$GPGGA`
  - `$GPVTG`
- TCP client with reconnect loop and connection logging
- ViewModel-driven state management

## Structure

- `app/src/main/java/.../ui` - activity, view model, custom track view
- `app/src/main/java/.../network` - TCP NMEA client
- `app/src/main/java/.../nmea` - NMEA sentence generator with checksum handling
- `app/src/main/java/.../simulation` - vessel motion and navigation math
- `app/src/main/java/.../model` - UI and navigation models

## Notes

- Minimum SDK is 24.
- The Gradle wrapper properties are included, but `gradle-wrapper.jar` was not generated in this workspace because no JDK/Gradle runtime is available locally.
- Import the project in Android Studio to let it sync and regenerate wrapper artifacts if needed.
