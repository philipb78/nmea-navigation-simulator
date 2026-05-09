# NMEA Navigation Simulator

Android app for testing NMEA → N2K / SeaTalk conversion firmware. Simulates vessel navigation with autopilot commands and sends NMEA sentences over TCP to your ESP32-based converter.

## Features

- **TCP Client** — Connect to any IP:Port (default: 192.168.1.100:10110)
- **NMEA Sentences** — Generates APB, XTE, RMC, GGA, VTG with proper checksums
- **Navigation Simulation** — Waypoint tracking with autopilot corrections
- **Course Deviation** — Inject off-track scenarios to test autopilot response
- **Track Visualization** — Real-time canvas showing vessel position, heading, and track
- **Material Design 3** — Clean, modern UI with sliders for speed, update rate, and deviation

## NMEA Sentences Generated

| Sentence | Description |
|----------|-------------|
| `$GPAPB` | Autopilot Sentence B — bearing, XTE, destination |
| `$GPXTE` | Cross-Track Error — deviation from planned track |
| `$GPRMC` | Recommended Minimum Navigation Information |
| `$GPGGA` | GPS Fix Data — position, quality, satellites |
| `$GPVTG` | Course Over Ground and Ground Speed |

## Simulator Controls

- **Speed (knots)** — 1–30 knots, adjustable in 0.5 knot steps
- **Update Rate (Hz)** — 1–10 updates per second
- **Course Deviation (NM)** — Inject ±0.5 NM deviation to test autopilot corrections

## Build & Run

1. Open in **Android Studio** (Arctic Fox or newer)
2. Sync Gradle files
3. Build and run on device or emulator

**Minimum SDK:** 24 (Android 7.0)

## Usage for Testing

1. **Connect** — Enter your Nauti-Controller IP and port (default: 192.168.1.100:10110)
2. **Start Simulation** — Vessel begins navigating between waypoints
3. **Adjust Deviation** — Use the course deviation slider to simulate off-track scenarios
4. **Monitor** — Watch heading, speed, XTE, and track visualization
5. **Test Autopilot** — ESP32 receives NMEA, converts to N2K/SeaTalk, autopilot should correct

## Project Structure

```
app/src/main/java/com/nauticontrol/nmeanavigationsimulator/
├── model/           # Data classes (GeoPoint, Waypoint, NavigationSnapshot)
├── network/         # TCP client for NMEA transmission
├── nmea/            # NMEA sentence generator with checksum
├── simulation/      # Simulation engine and geo math
└── ui/              # MainActivity, ViewModel, TrackView
```

## License

MIT — Use for testing your Nauti Control projects.
