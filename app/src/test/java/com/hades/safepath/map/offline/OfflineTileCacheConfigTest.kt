package com.hades.safepath.map.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies offline tile cache / zoom band constants used by
 * [OfflineTileRepository] and [OfflineMapFragment].
 */
class OfflineTileCacheConfigTest {

    @Test
    fun zoomBand_coversFullSriLankaIsland() {
        assertEquals(6, OfflineTileRepository.ZOOM_MIN)
        assertEquals(12, OfflineTileRepository.ZOOM_MAX)
        assertTrue(OfflineTileRepository.ZOOM_MAX > OfflineTileRepository.ZOOM_MIN)
    }

    @Test
    fun sriLankaBounds_areSensible() {
        assertTrue(OfflineTileRepository.SL_LAT_MIN < OfflineTileRepository.SL_LAT_MAX)
        assertTrue(OfflineTileRepository.SL_LON_MIN < OfflineTileRepository.SL_LON_MAX)
        assertTrue(OfflineTileRepository.SL_CENTER_LAT in OfflineTileRepository.SL_LAT_MIN..OfflineTileRepository.SL_LAT_MAX)
        assertTrue(OfflineTileRepository.SL_CENTER_LON in OfflineTileRepository.SL_LON_MIN..OfflineTileRepository.SL_LON_MAX)
        val box = OfflineTileRepository.sriLankaBoundingBox()
        assertEquals(OfflineTileRepository.SL_LAT_MAX, box.latNorth, 0.0001)
        assertEquals(OfflineTileRepository.SL_LON_MAX, box.lonEast, 0.0001)
    }

    @Test
    fun assetsTilesDir_isBundledRelativePath() {
        assertEquals("tiles", OfflineTileRepository.ASSETS_TILES_DIR)
        assertEquals("srilanka_map", OfflineTileRepository.CANONICAL_BASENAME)
    }
}
