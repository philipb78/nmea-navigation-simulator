# NMEA Navigation Simulator

Android app for testing NMEA → N2K / SeaTalk conversion firmware. Simulates vessel navigation with autopilot commands and sends NMEA sentences over TCP to your ESP32-based converter.

## Features

- **TCP Client** — Connect to any IP:Port (default: `192.168.1.1:8091`)
- **NMEA Sentences** — Generates navigation and marine sensor sentences with proper checksums
- **Navigation Simulation** — Waypoint tracking with autopilot corrections and current-adjusted SOG
- **Environmental Simulation** — Wind, depth, water temperature, and current range controls with smooth live variation
- **Rudder / AIS** — RSA rudder angle, optional MWV/RSA invalid status, and synthetic AIS targets
- **Magnetic variation** — HDG is magnetic; HDT stays true (default 4°W for the Lough Neagh route)
- **Fault inject** — Mute TX (keep TCP up), GPS loss (RMC V / GGA 0 / GLL V), blank depth
- **Course Deviation** — Inject off-track scenarios to test autopilot response
- **Track Visualization** — Real-time canvas showing vessel position, heading, and track
- **Material Design 3** — Clean, modern UI with collapsible slider sections

## NMEA Sentences Generated

| Sentence | Description |
|----------|-------------|
| `$GPAPB` | Autopilot Sentence B — bearing, XTE, destination |
| `$GPXTE` | Cross-Track Error — deviation from planned track |
| `$GPRMC` | Recommended Minimum Navigation Information (status V on GPS loss) |
| `$GPGGA` | GPS Fix Data — position, quality, satellites (quality 0 on GPS loss) |
| `$GPGLL` | Geographic position (status V on GPS loss) |
| `$GPVTG` | Course Over Ground and Ground Speed |
| `$GPVHW` | Water speed and heading (true + magnetic) |
| `$GPRMB` | Recommended Minimum Navigation Information |
| `$WIMWV` | Wind speed and angle (status A, or V when toggled) |
| `$SDDBT` | Depth below transducer |
| `$SDDPT` | Depth |
| `$IIVBW` | Dual ground and water speed |
| `$IIRSA` | Rudder sensor angle |
| `$HCHDT` | True heading |
| `$HCHDG` | Magnetic heading + variation |
| `$YCMTW` | Water temperature |
| `!AIVDM` | Synthetic AIS targets (types 1, 5, 18) when Emit AIS is on |
| `!AIVDO` | Optional own-ship AIS type 1 when Emit AIVDO is on |

### Depth Output Notes

Depth (and other environmental values) use min/max range sliders. During simulation, `SimulationEngine` smoothly varies each value within its range and emits the instantaneous reading on every update. Depth flows into `NavigationSnapshot.depthMeters` as:

- `$SDDBT,<feet>,f,<meters>,M,<fathoms>,F*hh`
- `$SDDPT,<meters>,0.0,*hh`

Use **DBT** (not the non-standard DBD talker/sentence). The TCP client writes the full generated list on each update, including both depth sentences. Firmware should accept the `SD` talker, parse meters from field 3 of `DBT` or field 1 of `DPT`, and verify or ignore the standard NMEA checksum according to its existing parser policy.

Enable **Depth invalid** to emit blank numeric fields (`$SDDBT,,f,,M,,F` / `$SDDPT,,0.0,`) so you can see how the hub treats a lost sounder.

### Heading (HDT / HDG)

- `$HCHDT` is always **true** heading
- `$HCHDG` is **magnetic** (`true − variation`). Default variation is **4.0°W** (`-4.0` on the slider; east is positive)
- RMC, VHW, and VTG also carry the same variation / magnetic course

Firmware treats HDG field 1 as magnetic. Leave HDT on for a true heading on the bus; mute is the way to stop the stream without dropping TCP.

### Rudder (RSA)

- Slider range: **-40° … +40°**
- Each tick emits `$IIRSA,<angle>,A,,*hh`
- Enable **RSA status V** to emit status `V` instead of `A`

### AIS (AIVDM / AIVDO)

When **Emit AIS** is enabled (default on), each tick emits synthetic targets offset from own-ship:

- Type 1 Class A position (`!AIVDM`) — MMSI `257000001`, ~0.3 NM ahead
- Type 18 Class B position (`!AIVDM`) — MMSI `257000002`, ~0.5 NM starboard
- Type 5 Class A static/voyage data every ~30 s as a 2-fragment `!AIVDM`
- Optional **Emit AIVDO own-ship** — type 1 `!AIVDO` from current lat/lon/COG/SOG/HDG

Payloads use a minimal 6-bit AIS encoder for message types **1**, **5**, and **18** only.

## Simulator Controls

- **Speed range (knots)** — 1–30 knots STW; varies smoothly within the range during simulation
- **Update Rate (Hz)** — 1–10 updates per second
- **Course Deviation (NM)** — Inject ±0.5 NM deviation to test autopilot corrections
- **Rudder angle (°)** — Direct RSA output; optional invalid status
- **Magnetic variation (°E)** — West is negative; drives HDG / RMC / VHW / VTG magnetic fields
- **Mute NMEA TX** — Simulation keeps running and TCP stays up, but no sentences are sent
- **GPS loss** — RMC/GLL status `V`, GGA quality `0`, VTG mode `N`
- **Wind** — Direction and speed ranges for MWV output (relative angle follows heading); optional MWV status V
- **Depth and Temperature** — Ranges for DBT/DPT/MTW output; values drift slowly like real sensors; optional blank depth
- **Current** — Direction and speed ranges used to derive COG/SOG from vessel STW
- **AIS** — Toggle Emit AIS / Emit AIVDO

Set min and max equal on any range slider to hold that value fixed.

## Build & Run

1. Open in **Android Studio** (Arctic Fox or newer)
2. Sync Gradle files
3. Build and run on device or emulator

**Minimum SDK:** 24 (Android 7.0)

## Usage for Testing

1. **Connect** — Enter your Nauti-Controller IP and port (default: `192.168.1.1:8091`)
2. **Start Simulation** — Vessel begins navigating between waypoints
3. **Adjust Deviation** — Use the course deviation slider to simulate off-track scenarios
4. **Monitor** — Watch heading, speed, XTE, and track visualization
5. **Test Autopilot** — ESP32 receives NMEA, converts to N2K/SeaTalk, autopilot should correct

## Project Structure

```
app/src/main/java/com/nauticontrol/nmeanavigationsimulator/
├── model/           # Data classes (GeoPoint, Waypoint, NavigationSnapshot)
├── network/         # TCP client for NMEA transmission
├── nmea/            # NMEA sentence generator, AIS encoder, checksum
├── simulation/      # Simulation engine and geo math
└── ui/              # MainActivity, ViewModel, TrackView
```

## License

MIT — Use for testing your Nauti Control projects.
