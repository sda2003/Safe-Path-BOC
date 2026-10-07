# Offline + online maps (SafePath)

## Architecture

SafePath uses **OsmDroid** with a dual-mode controller:

| Mode | Tiles | Routing |
|------|-------|---------|
| Online | `tile.openstreetmap.de` (no API key) | Public OSRM GeoJSON geometry |
| Offline | `assets/tiles/srilanka_map.mbtiles` | Dijkstra on `road_network.geojson` |
| Auto | Network-aware | Same |

Carto Voyager is **not** used (watermarked without API key).

## Bundled offline archive

```
app/src/main/assets/tiles/srilanka_map.mbtiles  # Full island z6–z12
app/src/main/assets/Mapnik/{z}/{x}/{y}.png      # Low-zoom overview
```

Refresh (watermark-free OSM.de):

```bash
python tools/build_offline_tiles.py
```

## Legal

See [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md).
