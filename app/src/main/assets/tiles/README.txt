Offline basemap tiles for SafePath (OsmDroid) — 100% in-APK, no network

================================================================================
PRIMARY
================================================================================
  app/src/main/assets/tiles/srilanka_map.mbtiles

Full Sri Lanka island, Carto Voyager rasters, zoom 6–12.
Copied at runtime to filesDir/osmdroid/srilanka_map.mbtiles

================================================================================
OPTIONAL ZIP FALLBACK
================================================================================
  python tools/build_offline_tiles.py --zip
  → app/src/main/assets/tiles/Mapnik.zip  (Mapnik/{z}/{x}/{y}.png)

================================================================================
OVERVIEW PNGs (optional, low zoom)
================================================================================
  app/src/main/assets/Mapnik/{z}/{x}/{y}.png   (z6–z9)

================================================================================
BUILD / REFRESH TILES
================================================================================
  python tools/build_offline_tiles.py

Downloads Carto Voyager tiles for the full island into srilanka_map.mbtiles.
Does NOT use tile.openstreetmap.org (avoids OSM 403 blocks).

MapView always uses setUseDataConnection(false).
