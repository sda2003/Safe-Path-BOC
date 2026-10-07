package com.hades.safepath.map.offline

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.MapTileProviderBase
import org.osmdroid.tileprovider.modules.SqlTileWriter
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bundles offline basemap tiles shipped in the APK:
 * - Primary: `assets/tiles/srilanka_map.mbtiles` (full island z6–z12)
 * - Fallback: `assets/tiles/Mapnik.zip` / `srilanka_map.zip`
 * - Overview: `assets/Mapnik/{z}/{x}/{y}.png` (low zoom only)
 *
 * Never registers a network tile downloader — no OSM 403 tiles.
 */
@Singleton
class OfflineTileRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class TileSetup(
        val hasLocalArchive: Boolean,
        val offlineProvider: MapTileProviderBase?,
        val tileSource: ITileSource,
        val archiveNames: List<String>,
        val message: String
    )

    private val baseDir: File
        get() = File(context.filesDir, "osmdroid").also { it.mkdirs() }

    private val cacheDir: File
        get() = File(baseDir, "tiles").also { it.mkdirs() }

    private val mapnikArchive: File
        get() = File(baseDir, "$ARCHIVE_TILE_SOURCE_NAME.zip")

    private val mbtilesArchive: File
        get() = File(baseDir, "$CANONICAL_BASENAME.mbtiles")

    suspend fun prepare(): TileSetup = withContext(Dispatchers.IO) {
        configureCaches()
        purgeAllTileCaches()
        copyBundledArchives()

        val assetTileCount = countAssetTiles()
        val hasAssets = assetTileCount > 0
        val hasZip = mapnikArchive.exists() && mapnikArchive.length() > 0L
        val hasMbtiles = mbtilesArchive.exists() && mbtilesArchive.length() > 0L

        if (!hasAssets && !hasZip && !hasMbtiles) {
            Log.w(TAG, "No assets/Mapnik tiles and no bundled archive")
            return@withContext TileSetup(
                hasLocalArchive = false,
                offlineProvider = null,
                tileSource = offlineOnlyTileSource(),
                archiveNames = emptyList(),
                message = "No offline tiles bundled — vector overlays only."
            )
        }

        val source = offlineOnlyTileSource()
        val provider = runCatching {
            SafePathOfflineTileProvider(
                registerReceiver = SafePathRegisterReceiver(context),
                assets = context.assets,
                tileSource = source,
                archiveFiles = buildList {
                    if (hasMbtiles) add(mbtilesArchive)
                    if (hasZip) add(mapnikArchive)
                }
            )
        }.getOrElse {
            Log.e(TAG, "SafePathOfflineTileProvider failed", it)
            return@withContext TileSetup(
                hasLocalArchive = false,
                offlineProvider = null,
                tileSource = source,
                archiveNames = emptyList(),
                message = "Map provider failed — overlays only."
            )
        }

        val labels = buildList {
            if (hasMbtiles) add(mbtilesArchive.name)
            if (hasAssets) add("assets/Mapnik ($assetTileCount tiles)")
            if (hasZip) add(mapnikArchive.name)
        }

        Log.i(TAG, "Offline tiles ready: $labels")
        TileSetup(
            hasLocalArchive = true,
            offlineProvider = provider,
            tileSource = source,
            archiveNames = labels,
            message = "Offline Sri Lanka basemap · ${labels.joinToString()}"
        )
    }

    fun configureCaches() {
        val cfg = Configuration.getInstance()
        cfg.load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        cfg.osmdroidBasePath = baseDir
        cfg.osmdroidTileCache = cacheDir
        cfg.userAgentValue = context.packageName
        cfg.tileFileSystemCacheMaxBytes = DISK_CACHE_MAX_BYTES
        cfg.tileFileSystemCacheTrimBytes = DISK_CACHE_TRIM_BYTES
        cfg.tileDownloadThreads = 1
        cfg.tileFileSystemThreads = 2
        cfg.cacheMapTileCount = MEMORY_TILE_COUNT
        cfg.expirationOverrideDuration = 365L * 24 * 60 * 60 * 1000
        cfg.isMapTileDownloaderFollowRedirects = false
    }

    fun offlineOnlyTileSource(): ITileSource = object : XYTileSource(
        ARCHIVE_TILE_SOURCE_NAME,
        ZOOM_MIN,
        ZOOM_MAX,
        256,
        ".png",
        emptyArray()
    ) {}

    private fun countAssetTiles(): Int {
        return runCatching {
            var count = 0
            fun walk(path: String) {
                val kids = context.assets.list(path) ?: return
                if (kids.isEmpty()) {
                    if (path.endsWith(".png")) count++
                    return
                }
                for (kid in kids) {
                    val child = if (path.isEmpty()) kid else "$path/$kid"
                    if (kid.endsWith(".png")) count++
                    else walk(child)
                }
            }
            walk(ARCHIVE_TILE_SOURCE_NAME)
            count
        }.getOrDefault(0)
    }

    private fun purgeAllTileCaches() {
        runCatching {
            val writer = SqlTileWriter()
            writer.purgeCache()
            writer.purgeCache(ARCHIVE_TILE_SOURCE_NAME)
            writer.onDetach()
        }
        listOf("cache.db", "cache.db-journal", "cache.db-shm", "cache.db-wal").forEach { name ->
            File(cacheDir, name).delete()
        }
        if (cacheDir.exists()) {
            cacheDir.deleteRecursively()
            cacheDir.mkdirs()
        }
        context.getExternalFilesDir(null)?.let { ext ->
            File(ext, "osmdroid/tiles").deleteRecursively()
        }
    }

    private fun copyBundledArchives() {
        copyAssetIfNeeded(
            candidates = listOf(
                "tiles/$CANONICAL_BASENAME.mbtiles",
                "tiles/$ARCHIVE_TILE_SOURCE_NAME.mbtiles"
            ),
            dest = mbtilesArchive,
            marker = File(baseDir, ".srilanka_mbtiles_v3")
        )
        copyAssetIfNeeded(
            candidates = listOf(
                "tiles/$ARCHIVE_TILE_SOURCE_NAME.zip",
                "tiles/$CANONICAL_BASENAME.zip"
            ),
            dest = mapnikArchive,
            marker = File(baseDir, ".mapnik_zip_v5")
        )
    }

    private fun copyAssetIfNeeded(candidates: List<String>, dest: File, marker: File) {
        val assetPath = candidates.firstOrNull { assetExists(it) } ?: return
        if (dest.exists() && dest.length() > 0L && marker.exists()) return

        runCatching {
            if (dest.exists()) dest.delete()
            context.assets.open(assetPath).use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
            marker.writeText("ok:${dest.length()}")
            Log.i(TAG, "Copied $assetPath → ${dest.absolutePath}")
        }.onFailure {
            Log.e(TAG, "Archive copy failed for $assetPath", it)
        }
    }

    private fun assetExists(path: String): Boolean =
        runCatching { context.assets.open(path).close(); true }.getOrDefault(false)

    companion object {
        private const val TAG = "OfflineTileRepository"
        const val ASSETS_TILES_DIR = "tiles"
        const val CANONICAL_BASENAME = "srilanka_map"
        const val ARCHIVE_TILE_SOURCE_NAME = "Mapnik"
        /** Full-island overview through street-level (see tools/build_offline_tiles.py). */
        const val ZOOM_MIN = 6
        const val ZOOM_MAX = 12

        /** Sri Lanka scroll / focus bounds (lat/lon). */
        const val SL_LAT_MIN = 5.85
        const val SL_LAT_MAX = 9.90
        const val SL_LON_MIN = 79.50
        const val SL_LON_MAX = 82.00
        const val SL_CENTER_LAT = 7.8731
        const val SL_CENTER_LON = 80.7718
        const val DEFAULT_ISLAND_ZOOM = 7.5

        fun sriLankaBoundingBox(): BoundingBox =
            BoundingBox(SL_LAT_MAX, SL_LON_MAX, SL_LAT_MIN, SL_LON_MIN)

        private const val MEMORY_TILE_COUNT: Short = 160
        private const val DISK_CACHE_MAX_BYTES = 256L * 1024L * 1024L
        private const val DISK_CACHE_TRIM_BYTES = 192L * 1024L * 1024L
    }
}
