# NMEA Navigation Simulator

Android app for testing NMEA → N2K / SeaTalk conversion firmware. Simulates vessel navigation with autopilot commands and sends NMEA sentences over TCP to your ESP32-based converter.

## Features

- **TCP Client** — Connect to any IP:Port (default: 192.168.1.100:10110)
- **NMEA Sentences** — Generates navigation and marine sensor sentences with proper checksums
- **Navigation Simulation** — Waypoint tracking with autopilot corrections and current-adjusted SOG
- **Environmental Simulation** — Wind, depth, water temperature, and current controls
- **Course Deviation** — Inject off-track scenarios to test autopilot response
- **Track Visualization** — Real-time canvas showing vessel position, heading, and track
- **Material Design 3** — Clean, modern UI with collapsible slider sections

## NMEA Sentences Generated

| Sentence | Description |
|----------|-------------|
| `$GPAPB` | Autopilot Sentence B — bearing, XTE, destination |
| `$GPXTE` | Cross-Track Error — deviation from planned track |
| `$GPRMC` | Recommended Minimum Navigation Information |
| `$GPGGA` | GPS Fix Data — position, quality, satellites |
| `$GPVTG` | Course Over Ground and Ground Speed |
| `$GPVHW` | Water speed and heading |
| `$GPRMB` | Recommended Minimum Navigation Information |
| `$WIMWV` | Wind speed and angle |
| `$WIMWD` | Wind direction and speed |
| `$SDDBD` | Depth below transducer |
| `$SDDPT` | Depth |
| `$IIVBW` | Dual ground and water speed |
| `$HCHDT` | True heading |
| `$HCHDG` | Heading, deviation, and variation |
| `$YCMTW` | Water temperature |

### Depth Output Notes

Depth is a manual simulator setting, so it stays static until the depth slider changes. The value flows from `SimulatorSettings.depthMeters` into `NavigationSnapshot.depthMeters` and is emitted on every update as:

- `$SDDBD,<feet>,f,<meters>,M,<fathoms>,F*hh`
- `$SDDPT,<meters>,0.0,*hh`

The TCP client writes the full generated list on each update, including both depth sentences. Firmware should accept the `SD` talker, parse meters from field 3 of `DBD` or field 1 of `DPT`, and verify or ignore the standard NMEA checksum according to its existing parser policy.

## Simulator Controls

- **Speed (knots)** — 1–30 knots STW, adjustable in 0.5 knot steps
- **Update Rate (Hz)** — 1–10 updates per second
- **Course Deviation (NM)** — Inject ±0.5 NM deviation to test autopilot corrections
- **Wind** — Direction and speed for MWV/MWD output
- **Depth and Temperature** — Depth and water temperature for DBD/DPT/MTW output
- **Current** — Direction and speed used to derive COG/SOG from vessel STW

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
