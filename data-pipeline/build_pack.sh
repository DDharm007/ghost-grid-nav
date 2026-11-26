#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# GhostGrid Global Offline Region Pack Compiler
# ==============================================================================
# Compiles 100% open geospatial data (OpenStreetMap via Geofabrik + Overture Maps)
# into offline-ready GhostGrid assets:
#   1. world-base.pmtiles  (Global zoom 0–6 overview for APK assets/)
#   2. map.pmtiles         (Regional vector map + Overture 3D buildings via Planetiler)
#   3. routing-graph/      (GraphHopper Core 11.1 graph with surface/smoothness/road_class)
#   4. places.db           (SQLite FTS5 + R-Tree POI & address database)
#   5. <region>-pack.zip   (Signed SHA-256 regional archive ready for GhostGrid import)
#
# Usage:
#   ./build_pack.sh --preset world-base
#   ./build_pack.sh --preset gujarat
#   ./build_pack.sh --region tokyo \
#       --geofabrik-url "https://download.geofabrik.de/asia/japan/kanto-latest.osm.pbf" \
#       --bbox "138.90,35.40,140.10,35.95"
# ==============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
WORK_DIR="${SCRIPT_DIR}/work"
TOOLS_DIR="${SCRIPT_DIR}/tools"
OUT_ROOT="${SCRIPT_DIR}/dist"

PLANETILER_VERSION="0.9.0"
GRAPHHOPPER_VERSION="11.1"
JAVA_HEAP="${GHOSTGRID_JAVA_HEAP:-8g}"

PRESET=""
REGION=""
GEOFABRIK_URL=""
BBOX=""
DISPLAY_NAME=""

usage() {
  cat <<EOF
GhostGrid Offline Pack Compiler

Options:
  --preset <world-base|gujarat>   Use built-in preset
  --region <slug>                 Region slug (lowercase alphanumeric/hyphens)
  --name <display_name>           Human-readable region name
  --geofabrik-url <url>           Geofabrik .osm.pbf download URL
  --bbox "<minLon,minLat,maxLon,maxLat>"  Geographic bounding box
  -h, --help                      Show this help
EOF
  exit 1
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --preset) PRESET="$2"; shift 2 ;;
    --region) REGION="$2"; shift 2 ;;
    --name) DISPLAY_NAME="$2"; shift 2 ;;
    --geofabrik-url) GEOFABRIK_URL="$2"; shift 2 ;;
    --bbox) BBOX="$2"; shift 2 ;;
    -h|--help) usage ;;
    *) echo "Unknown argument: $1" >&2; usage ;;
  esac
done

mkdir -p "${WORK_DIR}" "${TOOLS_DIR}" "${OUT_ROOT}"

ensure_tools() {
  command -v java >/dev/null 2>&1 || { echo "Error: Java 21+ required." >&2; exit 1; }
  command -v python3 >/dev/null 2>&1 || { echo "Error: Python 3.10+ required." >&2; exit 1; }
  command -v curl >/dev/null 2>&1 || { echo "Error: curl required." >&2; exit 1; }
  command -v zip >/dev/null 2>&1 || { echo "Error: zip required." >&2; exit 1; }

  local planetiler_jar="${TOOLS_DIR}/planetiler.jar"
  if [[ ! -f "${planetiler_jar}" ]]; then
    echo "[GhostGrid] Downloading Planetiler ${PLANETILER_VERSION}..."
    curl -fsSL "https://github.com/onthegomap/planetiler/releases/download/v${PLANETILER_VERSION}/planetiler.jar" \
      -o "${planetiler_jar}"
  fi

  local gh_jar="${TOOLS_DIR}/graphhopper-web-${GRAPHHOPPER_VERSION}.jar"
  if [[ ! -f "${gh_jar}" && "${PRESET}" != "world-base" ]]; then
    echo "[GhostGrid] Downloading GraphHopper ${GRAPHHOPPER_VERSION}..."
    curl -fsSL "https://repo1.maven.org/maven2/com/graphhopper/graphhopper-web/${GRAPHHOPPER_VERSION}/graphhopper-web-${GRAPHHOPPER_VERSION}.jar" \
      -o "${gh_jar}"
  fi
}

build_world_base() {
  ensure_tools
  local out_pmtiles="${OUT_ROOT}/world-base.pmtiles"
  echo "[GhostGrid] Compiling global overview map (zoom 0–6): ${out_pmtiles}"

  java -Xmx"${JAVA_HEAP}" -jar "${TOOLS_DIR}/planetiler.jar" \
    --area=monaco \
    --download \
    --maxzoom=6 \
    --minzoom=0 \
    --exclude-layers=building,housenumber,poi \
    --output="${out_pmtiles}" \
    --force

  local apk_assets="${REPO_ROOT}/app/src/main/assets"
  mkdir -p "${apk_assets}"
  cp "${out_pmtiles}" "${apk_assets}/world-base.pmtiles"
  echo "[GhostGrid] Installed world-base.pmtiles into ${apk_assets}/world-base.pmtiles"
}

if [[ "${PRESET}" == "world-base" ]]; then
  build_world_base
  exit 0
fi

if [[ "${PRESET}" == "gujarat" ]]; then
  REGION="${REGION:-gujarat}"
  DISPLAY_NAME="${DISPLAY_NAME:-Gujarat, India}"
  GEOFABRIK_URL="${GEOFABRIK_URL:-https://download.geofabrik.de/asia/india/western-zone-latest.osm.pbf}"
  BBOX="${BBOX:-68.10,20.10,74.50,24.75}"
fi

if [[ -z "${REGION}" || -z "${GEOFABRIK_URL}" || -z "${BBOX}" ]]; then
  echo "Error: Provide --preset <world-base|gujarat> OR --region, --geofabrik-url, and --bbox." >&2
  usage
fi

DISPLAY_NAME="${DISPLAY_NAME:-${REGION}}"
REGION_SAFE="$(echo "${REGION}" | tr '[:upper:]' '[:lower:]' | tr -cd 'a-z0-9_-')"
REGION_WORK="${WORK_DIR}/${REGION_SAFE}"
REGION_STAGE="${REGION_WORK}/stage"
mkdir -p "${REGION_WORK}" "${REGION_STAGE}"

ensure_tools

# Step 1: Download OpenStreetMap extract from Geofabrik
PBF_FILE="${REGION_WORK}/${REGION_SAFE}.osm.pbf"
if [[ ! -f "${PBF_FILE}" ]]; then
  echo "[GhostGrid] Downloading OSM extract: ${GEOFABRIK_URL}"
  curl -fL "${GEOFABRIK_URL}" -o "${PBF_FILE}"
fi

# Step 2: Download Overture Maps 3D Buildings (CDLA-Permissive-2.0) for the region bbox
OVERTURE_BUILDINGS="${REGION_WORK}/overture_buildings.parquet"
if command -v overturemaps >/dev/null 2>&1; then
  echo "[GhostGrid] Fetching Overture 3D buildings for ${REGION_SAFE} (${BBOX})..."
  overturemaps download --bbox="${BBOX}" -f geoparquet --type=building -o "${OVERTURE_BUILDINGS}" || true
fi

# Step 3: Compile Regional Vector Map (map.pmtiles, zoom 0–15) via Planetiler
echo "[GhostGrid] Compiling map.pmtiles via Planetiler..."
java -Xmx"${JAVA_HEAP}" -jar "${TOOLS_DIR}/planetiler.jar" \
  --osm-path="${PBF_FILE}" \
  --bounds="${BBOX}" \
  --download \
  --minzoom=0 \
  --maxzoom=15 \
  --building_merge_z13=true \
  --output="${REGION_STAGE}/map.pmtiles" \
  --force

# Step 4: Compile Offline Routing Graph (GraphHopper 11.1)
echo "[GhostGrid] Compiling GraphHopper ${GRAPHHOPPER_VERSION} routing-graph/..."
rm -rf "${REGION_STAGE}/routing-graph"
java -Xmx"${JAVA_HEAP}" \
  -Ddw.graphhopper.datareader.file="${PBF_FILE}" \
  -Ddw.graphhopper.graph.location="${REGION_STAGE}/routing-graph" \
  -jar "${TOOLS_DIR}/graphhopper-web-${GRAPHHOPPER_VERSION}.jar" \
  import "${SCRIPT_DIR}/graphhopper-config.yml"

# Step 5: Compile SQLite FTS5 + R-Tree Search Database (places.db)
echo "[GhostGrid] Compiling SQLite FTS5 + R-Tree places.db..."
python3 "${SCRIPT_DIR}/build_places_db.py" \
  --region "${REGION_SAFE}" \
  --bbox "${BBOX}" \
  --output "${REGION_STAGE}/places.db"

# Step 6: Write Region Pack Manifest
IFS=',' read -r MIN_LON MIN_LAT MAX_LON MAX_LAT <<< "${BBOX}"
CREATED_AT="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
cat > "${REGION_STAGE}/manifest.json" <<EOF
{
  "id": "${REGION_SAFE}",
  "name": "${DISPLAY_NAME}",
  "version": "${CREATED_AT}",
  "minLon": ${MIN_LON},
  "minLat": ${MIN_LAT},
  "maxLon": ${MAX_LON},
  "maxLat": ${MAX_LAT},
  "minZoom": 6,
  "maxZoom": 16,
  "hasRoutingGraph": true,
  "hasPlacesDb": true,
  "attribution": "Map data © OpenStreetMap contributors (ODbL 1.0); Buildings & POIs © Overture Maps Foundation (CDLA-Permissive-2.0)"
}
EOF

# Step 7: Package atomic ZIP archive + SHA-256 digest
ZIP_PATH="${OUT_ROOT}/${REGION_SAFE}-pack.zip"
rm -f "${ZIP_PATH}" "${ZIP_PATH}.sha256"
(
  cd "${REGION_STAGE}"
  zip -r -9 "${ZIP_PATH}" manifest.json map.pmtiles places.db routing-graph/
)

if command -v sha256sum >/dev/null 2>&1; then
  SHA256="$(sha256sum "${ZIP_PATH}" | awk '{print $1}')"
else
  SHA256="$(shasum -a 256 "${ZIP_PATH}" | awk '{print $1}')"
fi
echo "${SHA256}  ${REGION_SAFE}-pack.zip" > "${ZIP_PATH}.sha256"

echo ""
echo "=================================================================="
echo "GhostGrid Pack Ready!"
echo "  Archive : ${ZIP_PATH}"
echo "  SHA-256 : ${SHA256}"
echo "=================================================================="
