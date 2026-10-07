package com.hades.safepath.map.offline

import android.content.res.AssetManager
import org.osmdroid.tileprovider.IRegisterReceiver
import org.osmdroid.tileprovider.MapTileProviderArray
import org.osmdroid.tileprovider.modules.ArchiveFileFactory
import org.osmdroid.tileprovider.modules.MapTileApproximater
import org.osmdroid.tileprovider.modules.MapTileAssetsProvider
import org.osmdroid.tileprovider.modules.MapTileFileArchiveProvider
import org.osmdroid.tileprovider.tilesource.ITileSource
import java.io.File

/**
 * Tile provider that **cannot** hit the network.
 *
 * Chain:
 * 1. APK assets (`assets/Mapnik/z/x/y.png`) — low-zoom overview
 * 2. Local archives (`.mbtiles` / `.zip`) — full island pack
 * 3. Approximater (blur from nearby zoom) — fill gaps when zooming past max tile zoom
 *
 * No [org.osmdroid.tileprovider.modules.MapTileDownloader], no SQL cache of OSM 403s.
 */
class SafePathOfflineTileProvider(
    registerReceiver: IRegisterReceiver,
    assets: AssetManager,
    tileSource: ITileSource,
    archiveFiles: List<File> = emptyList()
) : MapTileProviderArray(tileSource, registerReceiver) {

    @Deprecated("Use archiveFiles list")
    constructor(
        registerReceiver: IRegisterReceiver,
        assets: AssetManager,
        tileSource: ITileSource,
        archiveFile: File?
    ) : this(
        registerReceiver,
        assets,
        tileSource,
        listOfNotNull(archiveFile)
    )

    init {
        val assetsProvider = MapTileAssetsProvider(registerReceiver, assets, tileSource)
        mTileProviderList.add(assetsProvider)

        val approximater = MapTileApproximater()
        approximater.addProvider(assetsProvider)

        val opened = archiveFiles
            .filter { it.exists() && it.length() > 0L }
            .mapNotNull { file ->
                ArchiveFileFactory.getArchiveFile(file)?.also { archive ->
                    archive.setIgnoreTileSource(true)
                }
            }
            .toTypedArray()

        if (opened.isNotEmpty()) {
            val archiveProvider = MapTileFileArchiveProvider(
                registerReceiver,
                tileSource,
                opened,
                true
            )
            mTileProviderList.add(archiveProvider)
            approximater.addProvider(archiveProvider)
        }

        mTileProviderList.add(approximater)
        setUseDataConnection(false)
    }

    override fun isDowngradedMode(pMapTileIndex: Long): Boolean = true
}
