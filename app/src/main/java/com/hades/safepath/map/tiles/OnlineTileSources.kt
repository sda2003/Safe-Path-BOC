package com.hades.safepath.map.tiles

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.MapTileIndex

/**
 * Key-free online basemap.
 *
 * Carto Voyager is banned here: without an API key every tile is watermarked
 * "API KEY REQUIRED".
 *
 * Esri World Street Map is used for Online mode (JPEG tiles, professional look).
 * Attribution must remain visible. Offline packs use OSM-derived MBTiles separately.
 */
object OnlineTileSources {

    fun openStreetMapDe(): OnlineTileSourceBase = esriWorldStreet()

    /** Esri World Street Map — z/y/x URL order. */
    fun esriWorldStreet(): OnlineTileSourceBase = object : XYTileSource(
        "EsriStreet",
        0,
        19,
        256,
        ".jpg",
        arrayOf(
            "https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/"
        ),
        "Tiles © Esri — Source: Esri, TomTom, FAO, NOAA, USGS, © OpenStreetMap"
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val z = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            // Esri REST: .../tile/{z}/{y}/{x}
            return baseUrl + "$z/$y/$x"
        }
    }

    const val ATTRIBUTION_ONLINE =
        "Tiles © Esri · © OpenStreetMap"
    const val ATTRIBUTION_OFFLINE =
        "© OpenStreetMap contributors · SafePath offline Sri Lanka"
}
