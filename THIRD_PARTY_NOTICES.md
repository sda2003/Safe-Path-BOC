# Map data & third-party notices (SafePath)

## OpenStreetMap
Map data © OpenStreetMap contributors, available under the
[Open Database License (ODbL)](https://opendatacommons.org/licenses/odbl/).

## Online basemap tiles
Online mode uses **Esri World Street Map** (no SafePath API key).
Attribution: Tiles © Esri — Source: Esri, TomTom, FAO, NOAA, USGS, © OpenStreetMap.

SafePath does **not** use Carto Voyager without an API key (those tiles display
an “API KEY REQUIRED” watermark).

Offline packs are OSM-derived MBTiles (HOT / OpenStreetMap), not Esri tiles
redistributed into the APK.

## Online routing
Online evacuation routes use the public
[OSRM](http://project-osrm.org/) demo endpoint (`router.project-osrm.org`)
for educational / competition demos. For production, host your own OSRM
instance and set `OSRM_BASE_URL` in `local.properties`.

## Offline basemap
Bundled `assets/tiles/srilanka_map.mbtiles` — OpenStreetMap-derived rasters
packaged for Sri Lanka (z6–z12). Attribution: © OpenStreetMap contributors.

## Offline routing
Hazard-aware Dijkstra over `res/raw/road_network.geojson` inside SafePath.
No external navigation app is launched.

## Organic Maps
SafePath does **not** embed the Organic Maps engine and does **not** require
the Organic Maps app for core navigation. Optional deep-link helpers may still
exist in the codebase for users who already have Organic Maps installed;
primary evacuation routing stays in-app.

If Organic Maps is used separately by the end user, attribution applies:
[Organic Maps Project](https://organicmaps.app/).

## OsmDroid
Map rendering: [osmdroid](https://github.com/osmdroid/osmdroid) (Apache 2.0).
