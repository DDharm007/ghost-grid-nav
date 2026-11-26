#!/usr/bin/env python3
"""
GhostGrid Offline Places & Address Compiler (Overture Maps -> SQLite FTS5 + R-Tree)

Downloads and compiles 100% open Overture Maps Foundation Parquet datasets
(CDLA-Permissive-2.0) for any global bounding box into a compact, read-only
SQLite database indexed with:
  - FTS5 prefix trigram/unicode61 full-text search table (`places_fts`)
  - R-Tree 2D spatial bounding-box index (`places_rtree`)

Schema:
  places(id, name, category, subcategory, address, lat, lon, min_lat, max_lat, min_lon, max_lon)

Usage:
  python3 build_places_db.py --region gujarat --bbox "68.10,20.10,74.50,24.75" --output ./out/gujarat/places.db
"""

from __future__ import annotations

import argparse
import os
import shutil
import sqlite3
import subprocess
import sys
import tempfile
from pathlib import Path
from typing import Tuple

import duckdb

# Normalized GhostGrid category mapping from Overture Maps primary taxonomy
CATEGORY_MAPPING = {
    # Food Junctions
    "restaurant": "food",
    "cafe": "food",
    "coffee_shop": "food",
    "fast_food_restaurant": "food",
    "bakery": "food",
    "street_food": "food",
    "food_court": "food",
    "diner": "food",
    "bar": "food",
    "ice_cream_shop": "food",
    "tea_room": "food",
    # Hospitals & Clinics
    "hospital": "hospital",
    "clinic": "hospital",
    "medical_center": "hospital",
    "urgent_care_clinic": "hospital",
    "doctor": "hospital",
    "dentist": "hospital",
    "emergency_room": "hospital",
    # Pharmacies
    "pharmacy": "pharmacy",
    "drugstore": "pharmacy",
    "medical_supply": "pharmacy",
    # Fuel
    "gas_station": "fuel",
    "fuel_station": "fuel",
    "ev_charging_station": "fuel",
    "petrol_station": "fuel",
    # Transit
    "train_station": "transit",
    "bus_station": "transit",
    "metro_station": "transit",
    "subway_station": "transit",
    "airport": "transit",
    "ferry_terminal": "transit",
    "transit_station": "transit",
    "light_rail_station": "transit",
}


def parse_bbox(bbox_str: str) -> Tuple[float, float, float, float]:
    parts = [p.strip() for p in bbox_str.split(",")]
    if len(parts) != 4:
        raise ValueError("bbox must be 'minLon,minLat,maxLon,maxLat'")
    min_lon, min_lat, max_lon, max_lat = map(float, parts)
    if not (-180.0 <= min_lon < max_lon <= 180.0 and -90.0 <= min_lat < max_lat <= 90.0):
        raise ValueError(f"Invalid geographic bounds: {bbox_str}")
    return min_lon, min_lat, max_lon, max_lat


def download_overture_parquet(dataset_type: str, bbox_str: str, out_file: Path) -> bool:
    """Invoke the official `overturemaps` CLI to extract GeoParquet for the bbox."""
    cmd = [
        "overturemaps",
        "download",
        f"--bbox={bbox_str}",
        "-f",
        "geoparquet",
        f"--type={dataset_type}",
        "-o",
        str(out_file),
    ]
    print(f"[GhostGrid] Fetching Overture '{dataset_type}' Parquet for bbox {bbox_str}...")
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        print(f"[GhostGrid] Warning: Overture '{dataset_type}' download failed: {result.stderr.strip()}", file=sys.stderr)
        return False
    return out_file.exists() and out_file.stat().st_size > 0


def init_sqlite_schema(conn: sqlite3.Connection) -> None:
    cur = conn.cursor()
    cur.executescript(
        """
        PRAGMA journal_mode = OFF;
        PRAGMA synchronous = OFF;
        PRAGMA temp_store = MEMORY;
        PRAGMA page_size = 4096;

        CREATE TABLE IF NOT EXISTS metadata (
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL
        );

        CREATE TABLE IF NOT EXISTS places (
            rowid INTEGER PRIMARY KEY AUTOINCREMENT,
            id TEXT NOT NULL UNIQUE,
            name TEXT NOT NULL,
            category TEXT NOT NULL,
            subcategory TEXT NOT NULL,
            address TEXT NOT NULL,
            lat REAL NOT NULL,
            lon REAL NOT NULL,
            min_lat REAL NOT NULL,
            max_lat REAL NOT NULL,
            min_lon REAL NOT NULL,
            max_lon REAL NOT NULL
        );

        CREATE INDEX IF NOT EXISTS idx_places_category ON places(category);
        CREATE INDEX IF NOT EXISTS idx_places_lat_lon ON places(lat, lon);

        -- Full-text search index with fast prefix token support (2, 3, 4 chars)
        CREATE VIRTUAL TABLE IF NOT EXISTS places_fts USING fts5(
            name,
            category,
            subcategory,
            address,
            content='places',
            content_rowid='rowid',
            tokenize='unicode61 remove_diacritics 2',
            prefix='2 3 4'
        );

        -- 2D spatial R-Tree index for instant bounding-box proximity queries
        CREATE VIRTUAL TABLE IF NOT EXISTS places_rtree USING rtree(
            id,
            min_lon, max_lon,
            min_lat, max_lat
        );
        """
    )
    conn.commit()


def normalize_category(raw_primary: str | None) -> str:
    if not raw_primary:
        return "landmark"
    key = raw_primary.strip().lower()
    if key in CATEGORY_MAPPING:
        return CATEGORY_MAPPING[key]
    if any(k in key for k in ("restaurant", "cafe", "food", "bakery", "coffee", "pizza", "burger", "dining")):
        return "food"
    if any(k in key for k in ("hospital", "clinic", "medical", "doctor", "dentist", "health")):
        return "hospital"
    if "pharm" in key or "chemist" in key or "drug" in key:
        return "pharmacy"
    if "fuel" in key or "gas_station" in key or "petrol" in key or "charging" in key:
        return "fuel"
    if any(k in key for k in ("station", "airport", "transit", "bus", "rail", "metro", "ferry")):
        return "transit"
    return "landmark"


def ingest_places_parquet(duck: duckdb.DuckDBPyConnection, parquet_path: Path, sqlite_conn: sqlite3.Connection) -> int:
    print(f"[GhostGrid] Indexing Overture Places from {parquet_path.name}...")
    query = f"""
        SELECT
            id,
            COALESCE(names.primary, '') AS name,
            COALESCE(categories.primary, 'landmark') AS raw_category,
            COALESCE(
                NULLIF(
                    concat_ws(', ',
                        addresses[1].freeform,
                        addresses[1].locality,
                        addresses[1].region,
                        addresses[1].postcode
                    ),
                    ''
                ),
                ''
            ) AS address,
            ST_Y(ST_Centroid(geometry)) AS lat,
            ST_X(ST_Centroid(geometry)) AS lon,
            bbox.ymin AS min_lat,
            bbox.ymax AS max_lat,
            bbox.xmin AS min_lon,
            bbox.xmax AS max_lon
        FROM read_parquet('{parquet_path.as_posix()}')
        WHERE names.primary IS NOT NULL
          AND length(trim(names.primary)) > 0
          AND geometry IS NOT NULL
    """
    rows = duck.execute(query).fetchall()
    cur = sqlite_conn.cursor()
    inserted = 0
    batch = []

    for row in rows:
        place_id, name, raw_cat, address, lat, lon, min_lat, max_lat, min_lon, max_lon = row
        if lat is None or lon is None:
            continue
        category = normalize_category(raw_cat)
        subcat = (raw_cat or "landmark").replace("_", " ").strip()
        lat_f = float(lat)
        lon_f = float(lon)
        batch.append(
            (
                str(place_id),
                str(name).strip(),
                category,
                subcat,
                str(address or "").strip(),
                lat_f,
                lon_f,
                float(min_lat if min_lat is not None else lat_f),
                float(max_lat if max_lat is not None else lat_f),
                float(min_lon if min_lon is not None else lon_f),
                float(max_lon if max_lon is not None else lon_f),
            )
        )
        if len(batch) >= 5000:
            cur.executemany(
                """
                INSERT OR IGNORE INTO places
                (id, name, category, subcategory, address, lat, lon, min_lat, max_lat, min_lon, max_lon)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                batch,
            )
            inserted += len(batch)
            batch.clear()

    if batch:
        cur.executemany(
            """
            INSERT OR IGNORE INTO places
            (id, name, category, subcategory, address, lat, lon, min_lat, max_lat, min_lon, max_lon)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            batch,
        )
        inserted += len(batch)

    sqlite_conn.commit()
    return inserted


def ingest_addresses_parquet(duck: duckdb.DuckDBPyConnection, parquet_path: Path, sqlite_conn: sqlite3.Connection) -> int:
    print(f"[GhostGrid] Indexing Overture Street Addresses from {parquet_path.name}...")
    query = f"""
        SELECT
            id,
            trim(concat_ws(' ', number, street)) AS name,
            concat_ws(', ',
                trim(concat_ws(' ', number, street)),
                address_levels[1].value,
                postcode,
                country
            ) AS full_address,
            ST_Y(ST_Centroid(geometry)) AS lat,
            ST_X(ST_Centroid(geometry)) AS lon
        FROM read_parquet('{parquet_path.as_posix()}')
        WHERE street IS NOT NULL
          AND length(trim(street)) > 0
          AND geometry IS NOT NULL
    """
    rows = duck.execute(query).fetchall()
    cur = sqlite_conn.cursor()
    inserted = 0
    batch = []

    for row in rows:
        addr_id, name, full_addr, lat, lon = row
        if lat is None or lon is None or not name:
            continue
        lat_f = float(lat)
        lon_f = float(lon)
        batch.append(
            (
                f"addr:{addr_id}",
                str(name).strip(),
                "address",
                "street address",
                str(full_addr or name).strip(),
                lat_f,
                lon_f,
                lat_f,
                lat_f,
                lon_f,
                lon_f,
            )
        )
        if len(batch) >= 5000:
            cur.executemany(
                """
                INSERT OR IGNORE INTO places
                (id, name, category, subcategory, address, lat, lon, min_lat, max_lat, min_lon, max_lon)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                batch,
            )
            inserted += len(batch)
            batch.clear()

    if batch:
        cur.executemany(
            """
            INSERT OR IGNORE INTO places
            (id, name, category, subcategory, address, lat, lon, min_lat, max_lat, min_lon, max_lon)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            batch,
        )
        inserted += len(batch)

    sqlite_conn.commit()
    return inserted


def build_indexes_and_optimize(conn: sqlite3.Connection, region: str, bbox_str: str) -> int:
    print("[GhostGrid] Building SQLite FTS5 & R-Tree spatial indexes...")
    cur = conn.cursor()
    cur.execute(
        """
        INSERT INTO places_fts(rowid, name, category, subcategory, address)
        SELECT rowid, name, category, subcategory, address FROM places
        """
    )
    cur.execute(
        """
        INSERT INTO places_rtree(id, min_lon, max_lon, min_lat, max_lat)
        SELECT rowid, min_lon, max_lon, min_lat, max_lat FROM places
        """
    )
    total = cur.execute("SELECT COUNT(*) FROM places").fetchone()[0]
    cur.executemany(
        "INSERT OR REPLACE INTO metadata(key, value) VALUES (?, ?)",
        [
            ("region", region),
            ("bbox", bbox_str),
            ("record_count", str(total)),
            ("schema_version", "1"),
            ("attribution", "Overture Maps Foundation (CDLA-Permissive-2.0)"),
        ],
    )
    cur.execute("INSERT INTO places_fts(places_fts) VALUES('optimize')")
    conn.commit()
    cur.execute("VACUUM")
    cur.execute("ANALYZE")
    conn.commit()
    return total


def main() -> None:
    parser = argparse.ArgumentParser(description="Compile Overture Maps POIs & Addresses into SQLite FTS5 + R-Tree.")
    parser.add_argument("--region", required=True, help="Region slug (e.g., gujarat, california, tokyo)")
    parser.add_argument("--bbox", required=True, help="Bounding box: minLon,minLat,maxLon,maxLat")
    parser.add_argument("--output", required=True, help="Output SQLite database path (places.db)")
    parser.add_argument("--places-parquet", default=None, help="Optional pre-downloaded Overture places GeoParquet")
    parser.add_argument("--addresses-parquet", default=None, help="Optional pre-downloaded Overture addresses GeoParquet")
    parser.add_argument("--skip-addresses", action="store_true", help="Index POIs only without street addresses")
    args = parser.parse_args()

    parse_bbox(args.bbox)
    out_path = Path(args.output).resolve()
    out_path.parent.mkdir(parents=True, exist_ok=True)
    if out_path.exists():
        out_path.unlink()

    with tempfile.TemporaryDirectory(prefix="ghostgrid_places_") as tmp_dir:
        tmp = Path(tmp_dir)
        places_pq = Path(args.places_parquet) if args.places_parquet else tmp / "places.parquet"
        addresses_pq = Path(args.addresses_parquet) if args.addresses_parquet else tmp / "addresses.parquet"

        if not args.places_parquet:
            download_overture_parquet("place", args.bbox, places_pq)
        if not args.skip_addresses and not args.addresses_parquet:
            download_overture_parquet("address", args.bbox, addresses_pq)

        duck = duckdb.connect()
        duck.execute("INSTALL spatial; LOAD spatial;")

        conn = sqlite3.connect(str(out_path))
        try:
            init_sqlite_schema(conn)
            poi_count = ingest_places_parquet(duck, places_pq, conn) if places_pq.exists() else 0
            addr_count = ingest_addresses_parquet(duck, addresses_pq, conn) if (not args.skip_addresses and addresses_pq.exists()) else 0
            total = build_indexes_and_optimize(conn, args.region, args.bbox)
            print(f"[GhostGrid] Compiled {out_path} ({total:,} records: {poi_count:,} POIs, {addr_count:,} addresses)")
        finally:
            conn.close()
            duck.close()


if __name__ == "__main__":
    main()
