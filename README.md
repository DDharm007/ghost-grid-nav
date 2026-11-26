# GhostGrid — Offline-First, Privacy-Hardened Android Navigation

> **Zero cloud, zero tracking, pure navigation.**  
> Powered by **MapLibre Native**, **PMTiles**, **GraphHopper Core 11.1**, **SQLite FTS5 + R-Tree**, and **Android Keystore + BiometricPrompt**.
> Includes a built-in **Curfew & Blackout Survival Toolkit** (Calculator, Encrypted Notes, SOS LED Torch, Magnetic Compass, Medical ID, Emergency Contacts, Unit Converter) and **No-Login Local Identity** with optional personal user-server sync.

`android` `offline-maps` `privacy` `maplibre` `pmtiles` `graphhopper` `sqlcipher` `openstreetmap` `overture-maps` `jetpack-compose` `zero-cloud`

---

## Architecture Overview

GhostGrid is structured as a reproducible monorepo:

```text
.
├── data-pipeline/                  # Global PC compiler (OpenStreetMap + Overture Maps)
│   ├── build_pack.sh               # Compiles world-base.pmtiles & <region>-pack.zip
│   ├── build_places_db.py          # Overture Parquet -> SQLite FTS5 + R-Tree places.db
│   ├── graphhopper-config.yml      # GraphHopper 11.1 encoded values + LM profiles
│   └── requirements.txt
└── app/                            # Jetpack Compose + MapLibre Native Android App
    └── src/main/
        ├── assets/
        │   ├── ghostgrid_dark_style.json
        │   ├── world_land.geojson  # Instant offline Earth vector layer (0–6)
        │   └── world-base.pmtiles  # (Optional) Planetiler global PMTiles
        └── java/com/example/
            ├── MainActivity.kt
            └── ghostgrid/
                ├── security/       # Android Keystore 256-bit key + Biometric gate
                ├── data/           # SQLCipher-encrypted Room DB + DataStore prefs
                ├── map/            # MapLibre Native + PMTiles layering + native LocationManager
                ├── downloader/     # SAF .zip importer + HTTPS SHA-256 downloader + Zip-Slip guard
                ├── search/         # Multi-region SQLite FTS5 + R-Tree + Haversine ranker
                ├── routing/        # GraphHopper 11.1 CustomModel + Off-route reroute + Road Block
                ├── tracking/       # ForegroundService GPS recorder + GPX/GeoJSON exporter
                ├── hybrid/         # Strict Offline gate + on-demand Places enricher & SQLite cache
                └── ui/             # Jet-black dark theme, Poppins typography, Compose screens
```

---

## Privacy & Security Guarantees

1. **Zero Cloud Backup:** `android:allowBackup="false"` and strict `data_extraction_rules.xml` exclusions ensure GPS logs, saved lists, and keys never leave the device.
2. **At-Rest SQLCipher Encryption:** All personal GPS route history, live location breadcrumbs, saved place lists, and cached POI enrichments live in an AES-256 SQLCipher Room database keyed via Android Keystore (`AES/GCM/NoPadding`).
3. **App-Private Storage Only:** Offline `.pmtiles`, routing graphs, and `places.db` archives live exclusively inside `context.noBackupFilesDir`—never on public `/sdcard/`.
4. **Biometric Route Vault:** Route History and `.gpx`/`.geojson` exports are gated behind Android `BiometricPrompt`.
5. **Transport-Level Strict Offline Mode:** Defaults to **ON**. An OkHttp `StrictOfflineInterceptor` blocks every outbound packet unless the user explicitly toggles Strict Offline Mode OFF.
6. **Release Log Stripping:** R8 (`proguard-rules.pro`) strips all `android.util.Log` calls in release builds so GPS coordinates can never leak to `logcat`.

---

## Quick-Start: Compiling Global & Regional Offline Packs

### 1. Install Pipeline Prerequisites (Desktop / CI)
```bash
pip install -r data-pipeline/requirements.txt
# Requires Java 21+, curl, and zip
chmod +x data-pipeline/build_pack.sh
```

### 2. Build Global Base Map (`world-base.pmtiles`, Zoom 0–6)
```bash
./data-pipeline/build_pack.sh --preset world-base
# Automatically copies world-base.pmtiles -> app/src/main/assets/world-base.pmtiles
```

### 3. Build Any Country, State, or Bounding Box Pack
Default Gujarat preset:
```bash
./data-pipeline/build_pack.sh --preset gujarat
```
Custom region worldwide (e.g., Tokyo / Kanto):
```bash
./data-pipeline/build_pack.sh \
  --region tokyo \
  --name "Tokyo & Kanto, Japan" \
  --geofabrik-url "https://download.geofabrik.de/asia/japan/kanto-latest.osm.pbf" \
  --bbox "138.90,35.40,140.10,35.95"
```
Transfer `data-pipeline/dist/<region>-pack.zip` to your Android phone and tap **Regions -> Import Local .zip Pack** (or host it on GitHub Releases and paste your manifest URL).

---

## Open Geospatial Data Attribution

- **Map data © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors**, licensed under the **Open Database License (ODbL 1.0)**.
- **3D buildings, street addresses, and places © [Overture Maps Foundation](https://overturemaps.org/)**, licensed under **CDLA-Permissive-2.0**.
- **Map rendering:** [MapLibre Native Android](https://maplibre.org/) & [PMTiles](https://protomaps.com/docs/pmtiles).
- **Offline turn-by-turn routing:** [GraphHopper Core](https://www.graphhopper.com/).

Licensed under the [Apache License 2.0](LICENSE).
