# GHOSTGRID
### Zero cloud, zero tracking, pure navigation.

<p align="center">
  <strong>An offline-first, privacy-hardened Android navigation & survival companion built for people who move off the grid.</strong>
</p>

<p align="center">
  <a href="https://github.com/DDharm007/GhostGrid/raw/refs/heads/main/APK/GhostGrid.apk">
    <img src="https://img.shields.io/badge/⬇_DOWNLOAD_GHOSTGRID-ANDROID_APK-black?style=for-the-badge&logo=android" alt="Download GhostGrid APK" />
  </a>
</p>

<p align="center">
  <strong>Get GhostGrid. Navigate off-grid. Leave zero trace.</strong>
  <br/>
  <sub>Direct Android APK Download · Free & Open-Source Installation</sub>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-black?style=for-the-badge&logo=android" />
  <img src="https://img.shields.io/badge/Kotlin-Native-purple?style=for-the-badge&logo=kotlin" />
  <img src="https://img.shields.io/badge/Jetpack_Compose-UI-4285F4?style=for-the-badge&logo=jetpackcompose" />
  <img src="https://img.shields.io/badge/Vault-SQLCipher_AES--256-black?style=for-the-badge" />
  <img src="https://img.shields.io/badge/Maps-MapLibre_+_PMTiles-green?style=for-the-badge&logo=openstreetmap" />
  <img src="https://img.shields.io/badge/Routing-GraphHopper_11-black?style=for-the-badge" />
</p>

---

## What is GhostGrid?

**GhostGrid** is an offline-first, privacy-hardened navigation and survival toolkit designed for people who demand reliable maps, celestial orientation, and total data sovereignty — with or without a network connection.

It transforms your Android device into a self-contained off-grid command deck, bringing multi-layer offline maps, turn-by-turn routing, an interactive Constellation Detector sky dome, live location-aware Weather & Moon Phase forecasting, global time zones, an encrypted biometric Route Vault, and a full Survival Toolkit together in one focused monochrome experience.

Whether you're crossing remote highways without cell coverage, navigating city streets without cloud telemetry, or finding your bearing by the stars and Moon, GhostGrid keeps you oriented while keeping every coordinate strictly on your device.

The philosophy is simple:

**Zero cloud. Zero tracking. Pure navigation.**

### Built for off-grid navigation

- **Offline-First Multi-Layer Maps** — Access Dark, Map, Satellite, Terrain, 3D, and Pure Offline views seamlessly across all zoom levels with pre-bundled global & India tiles, automatic background tile caching, and pyramid upscaling.
- **Interactive Constellation Detector & Sky Dome** — Drag to spin the 360° sky dome, zoom in/out, lock to live magnetic/true-north compass, scan the sky by camera, and save constellations with live Moon phase & bearing alignment.
- **Dynamic Weather, Forecast & Moon Phase HUD** — Live Weather and Moon Phase pill directly above the World Time HUD that updates automatically as you move, with one-tap `°C / °F / K` unit switching plus hourly and 6-day forecasts.
- **Global World Time Zones** — Second-accurate bottom-left HUD clock synced to any world time zone selected from the Tools -> Time selector.
- **Hybrid & Offline Place Search** — Instant local SQLite FTS5 + R-Tree place search with optional live Google & Bing search integration when Strict Offline Mode is off.
- **On-Device Turn-by-Turn Routing** — Local GraphHopper Core routing engine with offline TTS voice guidance, off-route rerouting, and one-tap Road Blocked Ahead avoidance.
- **Biometric Encrypted Route Vault** — SQLCipher AES-256 Room database keyed with Android Keystore (`AES/GCM/NoPadding`), gated by `BiometricPrompt`, with GPX & GeoJSON export.
- **Curfew & Blackout Survival Toolkit** — Precision Magnetic Compass, SOS LED Torch & Morse transmitter, Encrypted Notes, Scientific Calculator, Unit & Currency Converter, Medical ID, and Emergency Contacts.
- **System Language Changer (180+ Languages)** — Switch instantly between every world language with native script display, search, locale sync, and full RTL layout support.
- **Strict Offline Kill Switch** — Hard-blocks all outbound HTTPS packets at the OkHttp transport layer when enabled.

## Why GhostGrid?

Navigation shouldn't require trading your location history to the cloud.

GhostGrid follows a different approach: a calm, smoked-glass monochromatic interface built around offline resilience and hardware-backed privacy.

It focuses on three principles:

1. **Privacy over surveillance** — Zero mandatory accounts, `allowBackup="false"`, Keystore-encrypted SQLCipher storage, and zero cloud tracking.
2. **Offline over dependency** — Pre-bundled tiles, local PMTiles/routing packs, on-device astronomy, and transport-level offline locking.
3. **Clarity over clutter** — A focused black, grey, and white instrument layout that keeps critical navigation and survival telemetry readable day or night.

Every map tile, waypoint, and bearing stays yours.

---

## Download & Installation

Download the latest Android APK directly from GitHub.

<p align="center">
  <a href="https://github.com/DDharm007/GhostGrid/raw/refs/heads/main/APK/GhostGrid.apk">
    <img src="https://img.shields.io/badge/DOWNLOAD-GHOSTGRID.APK-black?style=for-the-badge&logo=android" alt="Download GhostGrid" />
  </a>
</p>

**Installation steps**

1. Tap the **Download GhostGrid APK** button above.
2. Your Android device will download `GhostGrid.apk`.
3. Open the downloaded file from your Downloads folder.
4. Allow installation from this source if Android requests permission.
5. Complete the installation and launch GhostGrid.

**Direct download link:** [GhostGrid.apk](https://github.com/DDharm007/GhostGrid/raw/refs/heads/main/APK/GhostGrid.apk)  
**SHA-256 checksum file:** [GhostGrid.apk.sha256](https://github.com/DDharm007/GhostGrid/raw/refs/heads/main/APK/GhostGrid.apk.sha256)

---

## Built with Purpose

| Technology | Purpose |
|---|---|
| Kotlin | Native Android development |
| Jetpack Compose | Modern declarative UI |
| Material 3 | Adaptive UI components |
| MapLibre Native + PMTiles | Multi-layer offline & online map rendering |
| GraphHopper Core 11.1 | On-device turn-by-turn routing |
| SQLCipher + Room (SQLite) | AES-256 encrypted local database & FTS5 search |
| Android Keystore + BiometricPrompt | Hardware-backed encryption keys & biometric vault lock |
| Native Android LocationManager | GPS & location tracking with zero Play Services dependency |
| OkHttp + Retrofit + Moshi | Transport-gated hybrid networking & tile vault interceptor |
| Jetpack DataStore | Local preferences, time zone, units & language state |
| Kotlin Coroutines & Flow | Reactive asynchronous operations |
| Gradle Kotlin DSL | Build configuration |

## Architecture

GhostGrid follows a modular native Android MVVM architecture with strict transport-layer offline isolation and encrypted local storage.

```text
GhostGrid
   |
   ├── Android Application
   |      ├── Kotlin
   |      └── Jetpack Compose (Monochrome Smoked-Glass M3)
   |
   ├── Mapping & Tile Vault Layer
   |      ├── MapLibre Native + PMTiles
   |      └── OfflineTileVault (Pre-bundled + Disk Cache + Pyramid Upscaler)
   |
   ├── Navigation & Search Layer
   |      ├── GraphHopper Core 11.1 (Offline Routing + TTS)
   |      └── SQLite FTS5 + R-Tree & Optional Hybrid Search
   |
   ├── Sky, Weather & Sensor Layer
   |      ├── CelestialNavigatorEngine (Interactive Constellation Dome & Moon)
   |      ├── Native LocationManager & Magnetic/True-North Compass
   |      └── Dynamic Weather, Hourly & 6-Day Forecast Engine
   |
   ├── Encrypted Data & Vault Layer
   |      ├── SQLCipher AES-256 Room Database
   |      ├── Android Keystore + BiometricPrompt Gate
   |      └── Jetpack DataStore Preferences
   |
   └── System & Localization Layer
          └── 180+ World Language Engine (RTL + System Locale Sync)
```

## Getting Started

### Prerequisites

- Android Studio
- JDK 17 or compatible configured Gradle runtime
- Android SDK 36
- Android device or emulator (API 26+)

### Run Locally

**1. Clone the repository**

```bash
git clone https://github.com/DDharm007/GhostGrid.git
```

**2. Open the project**

Open the cloned `GhostGrid` directory in Android Studio.

**3. Sync Gradle**

Allow Android Studio to download and configure the required dependencies.

**4. Configure the environment**

The project includes an `.env.example` file. Copy it to `.env` if your local setup requires optional keys.

**5. Run the application**

Select an Android device or emulator (API 26+) and click **Run**.

### Optional: Compile Custom Regional Offline Packs

Build your own offline region packs from OpenStreetMap & Overture Maps:

```bash
chmod +x data-pipeline/build_pack.sh
./data-pipeline/build_pack.sh --preset gujarat
```

Import the generated `.zip` directly inside **Maps -> Import Local .zip Pack**.

## Permissions

GhostGrid uses Android permissions strictly for:

- Coarse & fine location (`ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`) for GPS positioning and compass declination.
- Foreground location service & notifications (`FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS`) for encrypted background track recording.
- Optional internet & network state (`INTERNET`, `ACCESS_NETWORK_STATE`), hard-blocked when Strict Offline Mode is enabled.
- Biometric hardware (`USE_BIOMETRIC`) for unlocking the encrypted Route Vault.
- Optional camera (`CAMERA`) for Constellation Detector sky photo scanning.

## Project Structure

```text
GhostGrid/
│
├── APK/
│   ├── GhostGrid.apk
│   ├── GhostGrid.apk.sha256
│   └── README.md
│
├── app/
│   └── src/
│       └── main/
│           ├── assets/
│           │   ├── offline_tiles/
│           │   ├── world_land.geojson
│           │   └── ghostgrid_dark_style.json
│           ├── java/com/example/
│           ├── res/
│           └── AndroidManifest.xml
│
├── data-pipeline/
│   ├── build_pack.sh
│   ├── build_places_db.py
│   └── graphhopper-config.yml
│
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── metadata.json
└── .env.example
```

## Open Data Attribution

- **Map data © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors** (ODbL 1.0).
- **3D buildings & places © [Overture Maps Foundation](https://overturemaps.org/)** (CDLA-Permissive-2.0).
- **Rendering & Routing:** [MapLibre Native](https://maplibre.org/), [PMTiles](https://protomaps.com/docs/pmtiles), and [GraphHopper Core](https://www.graphhopper.com/).

## Roadmap

- [ ] Expand regional high-detail offline pack presets worldwide.
- [ ] Add deeper offline topographic contour & elevation profiling.
- [ ] Expand full native string packs across additional regional dialects.
- [ ] Introduce encrypted peer-to-peer offline waypoint sharing.
- [ ] Enhance night-sky catalog with deep-sky objects & planetary ephemerides.

*The roadmap represents development directions, not necessarily currently implemented features.*

## Contributing

Contributions, suggestions, bug reports, and feature ideas are welcome.

1. Fork the repository.
2. Create a feature branch.
3. Commit your changes.
4. Open a pull request.

For major changes, open an issue first to discuss your proposal.

## Developer

**Dharm Patel**

Founder of Craftorā · Full-Stack Developer · Product Builder

Building software at the intersection of engineering, design, and practical utility.

<p align="center">
  <a href="https://github.com/DDharm007">GitHub</a> ·
  <a href="https://craftoraa.com">Craftorā</a>
</p>

---

<p align="center">
  <strong>GHOSTGRID</strong>
  <br/>
  Zero cloud. Zero tracking. Pure navigation.
  <br/><br/>
  <i>Built for the ones who navigate off the grid.</i>
  <br/><br/>
  Every coordinate stays yours.
</p>
