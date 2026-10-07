package com.hades.safepath

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hades.safepath.data.SafePathDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies that the Room database is correctly seeded on first launch.
 *
 * R7 criterion 6: DB must be fully functional with zero network calls from first launch.
 * R14 criterion 2: at least 3 verified Shelter records per pilot district.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseSeedTest {

    private lateinit var db: SafePathDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = SafePathDatabase.getInstance(context)
    }

    /**
     * R7 criterion 6 — FLOOD/IMMINENT must have at least 5 guidance steps.
     */
    @Test
    fun testDatabaseSeeded_floodImminentSteps() = runBlocking {
        val steps = db.guidanceStepDao().getSteps("FLOOD", "IMMINENT")
        assertTrue(
            "Expected at least 5 FLOOD/IMMINENT guidance steps, got ${steps.size}",
            steps.size >= 5
        )
    }

    /**
     * All 9 disaster/severity combinations must have at least 1 step each.
     */
    @Test
    fun testDatabaseSeeded_allNineCombinations() = runBlocking {
        val combinations = listOf(
            "FLOOD" to "IMMINENT",
            "FLOOD" to "ALERT",
            "FLOOD" to "ADVISORY",
            "LANDSLIDE" to "IMMINENT",
            "LANDSLIDE" to "ALERT",
            "LANDSLIDE" to "ADVISORY",
            "CYCLONE" to "IMMINENT",
            "CYCLONE" to "ALERT",
            "CYCLONE" to "ADVISORY"
        )

        for ((type, severity) in combinations) {
            val steps = db.guidanceStepDao().getSteps(type, severity)
            assertTrue(
                "Expected at least 1 step for $type/$severity, got ${steps.size}",
                steps.isNotEmpty()
            )
        }
    }

    /**
     * R14 criterion 2 — at least 3 shelters must be seeded in the database.
     */
    @Test
    fun testDatabaseSeeded_sheltersExist() = runBlocking {
        val shelters = db.shelterDao().getAllShelters()
        assertTrue(
            "Expected at least 3 shelters in database, got ${shelters.size}",
            shelters.size >= 3
        )
    }

    /**
     * R14 criterion 2 — at least 3 verified shelters for Ratnapura pilot district.
     */
    @Test
    fun testDatabaseSeeded_ratnapuraShelters() = runBlocking {
        val shelters = db.shelterDao().getSheltersByDistrict("Ratnapura")
        assertTrue(
            "Expected at least 3 Ratnapura shelters, got ${shelters.size}",
            shelters.size >= 3
        )
        assertTrue(
            "All Ratnapura shelters should be verified",
            shelters.all { it.isVerified }
        )
    }

    /**
     * Pack items must be seeded for all 3 disaster types.
     */
    @Test
    fun testDatabaseSeeded_packItemsExist() = runBlocking {
        for (type in listOf("FLOOD", "LANDSLIDE", "CYCLONE")) {
            val items = db.packItemDao().getItemsByDisasterType(type)
            assertTrue(
                "Expected pack items for $type, got ${items.size}",
                items.isNotEmpty()
            )
        }
    }
}
