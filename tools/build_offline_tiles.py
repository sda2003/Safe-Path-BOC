#!/usr/bin/env python3
"""
Download raster tiles for the full Sri Lanka island and install them as:

  app/src/main/assets/tiles/srilanka_map.mbtiles   (primary OsmDroid archive)
  app/src/main/assets/tiles/Mapnik.zip             (fallback zip)
  app/src/main/assets/Mapnik/{z}/{x}/{y}.png       (low-zoom overview only)

Uses OpenStreetMap.de raster tiles (no API key watermark).
Do NOT use Carto Voyager without an API key — tiles are watermarked.
"""
from __future__ import annotations

import math
import shutil
import sqlite3
import sys
import time
import urllib.request
import zipfile
from pathlib import Path

# Full Sri Lanka island (slight padding around coastline)
LAT_MIN, LAT_MAX = 5.85, 9.90
LON_MIN, LON_MAX = 79.50, 82.00
ZOOM_MIN, ZOOM_MAX = 6, 12
# Loose assets/Mapnik only for overview (keeps APK asset tree small)
ASSETS_PNG_ZOOM_MAX = 9
TILE_SOURCE = "Mapnik"
USER_AGENT = "SafePath/2.0 (offline disaster map; educational; contact=safepath-dev)"

# Watermark-free HOT / OSM France rasters (avoid Carto API-key watermarks)
TILE_URL = "https://a.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png"

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app" / "src" / "main" / "assets"
OUT_DIR = ASSETS / TILE_SOURCE
OUT_ZIP = ASSETS / "tiles" / "Mapnik.zip"
OUT_MBTILES = ASSETS / "tiles" / "srilanka_map.mbtiles"
TMP = ROOT / "tools" / "_tile_cache" / "osm_de_sl"


def deg2num(lat_deg: float, lon_deg: float, zoom: int) -> tuple[int, int]:
    lat_rad = math.radians(lat_deg)
    n = 2.0 ** zoom
    xtile = int((lon_deg + 180.0) / 360.0 * n)
    ytile = int((1.0 - math.asinh(math.tan(lat_rad)) / math.pi) / 2.0 * n)
    return xtile, ytile


def tiles_for_bbox(z: int) -> list[tuple[int, int, int]]:
    x0, y1 = deg2num(LAT_MIN, LON_MIN, z)
    x1, y0 = deg2num(LAT_MAX, LON_MAX, z)
    xs = range(min(x0, x1), max(x0, x1) + 1)
    ys = range(min(y0, y1), max(y0, y1) + 1)
    return [(z, x, y) for x in xs for y in ys]


def xyz_to_tms_row(z: int, y: int) -> int:
    """MBTiles stores TMS (flipped) Y."""
    return (2**z - 1) - y


def download_tile(z: int, x: int, y: int, dest: Path) -> bool:
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.exists() and dest.stat().st_size > 200:
        return True
    url = TILE_URL.format(z=z, x=x, y=y)
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            data = resp.read()
        if len(data) < 68:
            print(f"  bad tile {z}/{x}/{y} ({len(data)} bytes)", file=sys.stderr)
            return False
        # Accept tiny ocean placeholders (valid 1×1 PNGs ~100B) and full rasters.
        if data[:8] == b"\x89PNG\r\n\x1a\n" or data[:2] == b"\xff\xd8":
            dest.write_bytes(data)
            return True
        print(f"  bad tile {z}/{x}/{y} (not PNG/JPEG)", file=sys.stderr)
        return False
    except Exception as e:
        print(f"  fail {z}/{x}/{y}: {e}", file=sys.stderr)
        return False


def write_mbtiles(all_tiles: list[tuple[int, int, int]]) -> int:
    if OUT_MBTILES.exists():
        OUT_MBTILES.unlink()
    OUT_MBTILES.parent.mkdir(parents=True, exist_ok=True)

    conn = sqlite3.connect(OUT_MBTILES)
    try:
        conn.execute("PRAGMA journal_mode=OFF")
        conn.execute("PRAGMA synchronous=OFF")
        conn.execute(
            "CREATE TABLE tiles ("
            "zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, tile_data BLOB)"
        )
        conn.execute("CREATE TABLE metadata (name TEXT, value TEXT)")
        conn.execute(
            "CREATE UNIQUE INDEX tile_index ON tiles "
            "(zoom_level, tile_column, tile_row)"
        )
        meta = {
            "name": "SafePath Sri Lanka",
            "type": "baselayer",
            "version": "2",
            "description": "Offline Carto Voyager basemap for Sri Lanka (SafePath)",
            "format": "png",
            "bounds": f"{LON_MIN},{LAT_MIN},{LON_MAX},{LAT_MAX}",
            "center": f"{(LON_MIN + LON_MAX) / 2},{(LAT_MIN + LAT_MAX) / 2},{ZOOM_MIN + 1}",
            "minzoom": str(ZOOM_MIN),
            "maxzoom": str(ZOOM_MAX),
            "attribution": "© OpenStreetMap contributors © CARTO",
        }
        conn.executemany(
            "INSERT INTO metadata (name, value) VALUES (?, ?)",
            list(meta.items()),
        )

        packed = 0
        batch: list[tuple[int, int, int, bytes]] = []
        for z, x, y in all_tiles:
            src = TMP / str(z) / str(x) / f"{y}.png"
            if not src.exists():
                continue
            data = src.read_bytes()
            batch.append((z, x, xyz_to_tms_row(z, y), data))
            if len(batch) >= 200:
                conn.executemany(
                    "INSERT OR REPLACE INTO tiles "
                    "(zoom_level, tile_column, tile_row, tile_data) VALUES (?,?,?,?)",
                    batch,
                )
                packed += len(batch)
                batch.clear()
        if batch:
            conn.executemany(
                "INSERT OR REPLACE INTO tiles "
                "(zoom_level, tile_column, tile_row, tile_data) VALUES (?,?,?,?)",
                batch,
            )
            packed += len(batch)
        conn.commit()
        return packed
    finally:
        conn.close()


def main() -> int:
    all_tiles: list[tuple[int, int, int]] = []
    for z in range(ZOOM_MIN, ZOOM_MAX + 1):
        batch = tiles_for_bbox(z)
        print(f"z{z}: {len(batch)} tiles")
        all_tiles.extend(batch)

    print(f"Total tiles: {len(all_tiles)} (Sri Lanka {LAT_MIN}–{LAT_MAX}N, {LON_MIN}–{LON_MAX}E)")
    ok = 0
    for i, (z, x, y) in enumerate(all_tiles, 1):
        dest = TMP / str(z) / str(x) / f"{y}.png"
        was_cached = dest.exists() and dest.stat().st_size >= 68
        if download_tile(z, x, y, dest):
            ok += 1
        if not was_cached:
            if i % 50 == 0:
                print(f"  progress {i}/{len(all_tiles)} ok={ok}")
                time.sleep(0.25)
            else:
                time.sleep(0.05)
        elif i % 200 == 0:
            print(f"  progress {i}/{len(all_tiles)} ok={ok} (cached)")

    # Low-zoom overview PNGs in assets/Mapnik (fast first paint)
    overview = [(z, x, y) for z, x, y in all_tiles if z <= ASSETS_PNG_ZOOM_MAX]
    packed_png = 0
    for z, x, y in overview:
        src = TMP / str(z) / str(x) / f"{y}.png"
        if not src.exists():
            continue
        dest = OUT_DIR / str(z) / str(x) / f"{y}.png"
        dest.parent.mkdir(parents=True, exist_ok=True)
        try:
            shutil.copy2(src, dest)
        except PermissionError:
            time.sleep(0.2)
            shutil.copy2(src, dest)
        packed_png += 1

    keep = {OUT_DIR / str(z) / str(x) / f"{y}.png" for z, x, y in overview}
    if OUT_DIR.exists():
        for png in OUT_DIR.rglob("*.png"):
            if png not in keep:
                try:
                    png.unlink()
                except OSError:
                    pass

    # Optional zip fallback (pass --zip). Default APK ships MBTiles only to avoid
    # duplicating ~14 MB of the same rasters.
    write_zip = "--zip" in sys.argv
    if write_zip:
        OUT_ZIP.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(OUT_ZIP, "w", compression=zipfile.ZIP_DEFLATED) as zf:
            for z, x, y in all_tiles:
                src = TMP / str(z) / str(x) / f"{y}.png"
                if not src.exists():
                    continue
                arcname = f"{TILE_SOURCE}/{z}/{x}/{y}.png"
                zf.write(src, arcname)
        sri_zip = OUT_ZIP.parent / "srilanka_map.zip"
        shutil.copy2(OUT_ZIP, sri_zip)
        zip_mb = OUT_ZIP.stat().st_size / (1024 * 1024)
        print(f"Wrote {OUT_ZIP} ({zip_mb:.1f} MB) and {sri_zip.name}")
    else:
        for stale in (OUT_ZIP, OUT_ZIP.parent / "srilanka_map.zip"):
            if stale.exists():
                stale.unlink()
                print(f"Removed stale {stale.name} (MBTiles is primary)")

    mb_count = write_mbtiles(all_tiles)

    mb_mb = OUT_MBTILES.stat().st_size / (1024 * 1024)
    print(f"Overview PNGs: {packed_png} under {OUT_DIR} (z{ZOOM_MIN}–z{ASSETS_PNG_ZOOM_MAX})")
    print(f"Wrote {OUT_MBTILES} ({mb_mb:.1f} MB, {mb_count} tiles)")
    print(f"Downloaded ok={ok}/{len(all_tiles)}")
    return 0 if mb_count > 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
