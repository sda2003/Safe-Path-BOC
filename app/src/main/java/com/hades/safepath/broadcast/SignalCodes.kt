package com.hades.safepath.broadcast

/**
 * SafePath 3-digit signal encoding scheme.
 *
 * Disaster type  = floor(code / 10): 11=Flood, 12=Landslide, 13=Cyclone
 * Severity level = code mod 10:      1=Advisory, 2=Alert, 3=Imminent
 */
object SignalCodes {
    const val FLOOD_ADVISORY    = 111
    const val FLOOD_ALERT       = 112
    const val FLOOD_IMMINENT    = 113

    const val LANDSLIDE_ADVISORY = 121
    const val LANDSLIDE_ALERT    = 122
    const val LANDSLIDE_IMMINENT = 123

    const val CYCLONE_ADVISORY  = 131
    const val CYCLONE_ALERT     = 132
    const val CYCLONE_IMMINENT  = 133

    /** All valid codes for iteration / validation. */
    val ALL_VALID = setOf(
        FLOOD_ADVISORY, FLOOD_ALERT, FLOOD_IMMINENT,
        LANDSLIDE_ADVISORY, LANDSLIDE_ALERT, LANDSLIDE_IMMINENT,
        CYCLONE_ADVISORY, CYCLONE_ALERT, CYCLONE_IMMINENT
    )
}
