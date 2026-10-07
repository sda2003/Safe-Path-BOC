package com.hades.safepath.map.mode

import org.junit.Assert.assertEquals
import org.junit.Test

class MapModeResolveTest {

    @Test
    fun auto_usesOnlineWhenNetworkUp() {
        assertEquals(EffectiveMapMode.ONLINE, resolve(MapMode.AUTO, online = true))
        assertEquals(EffectiveMapMode.OFFLINE, resolve(MapMode.AUTO, online = false))
    }

    @Test
    fun offlinePreference_stickyEvenWhenOnline() {
        assertEquals(EffectiveMapMode.OFFLINE, resolve(MapMode.OFFLINE, online = true))
        assertEquals(EffectiveMapMode.OFFLINE, resolve(MapMode.OFFLINE, online = false))
    }

    @Test
    fun onlinePreference_fallsBackWhenOffline() {
        assertEquals(EffectiveMapMode.ONLINE, resolve(MapMode.ONLINE, online = true))
        assertEquals(EffectiveMapMode.OFFLINE, resolve(MapMode.ONLINE, online = false))
    }

    private fun resolve(pref: MapMode, online: Boolean): EffectiveMapMode = when (pref) {
        MapMode.OFFLINE -> EffectiveMapMode.OFFLINE
        MapMode.ONLINE -> if (online) EffectiveMapMode.ONLINE else EffectiveMapMode.OFFLINE
        MapMode.AUTO -> if (online) EffectiveMapMode.ONLINE else EffectiveMapMode.OFFLINE
    }
}
