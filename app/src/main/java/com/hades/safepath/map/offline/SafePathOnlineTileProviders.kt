package com.hades.safepath.map.offline

import android.content.Context
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.modules.NetworkAvailabliltyCheck
import org.osmdroid.tileprovider.tilesource.ITileSource

/**
 * Online tile provider that uses [SafePathRegisterReceiver] so it does not crash
 * on Android 14+ (targetSdk 34). Prefer this over [MapTileProviderBasic]’s
 * Context-only constructors, which wrap OsmDroid’s unsafe SimpleRegisterReceiver.
 */
object SafePathOnlineTileProviders {

    fun create(context: Context, tileSource: ITileSource): MapTileProviderBasic {
        val app = context.applicationContext
        return MapTileProviderBasic(
            SafePathRegisterReceiver(app),
            NetworkAvailabliltyCheck(app),
            tileSource,
            app,
            null
        ).also { it.setUseDataConnection(true) }
    }
}
