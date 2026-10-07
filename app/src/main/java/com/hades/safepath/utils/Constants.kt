package com.hades.safepath.utils

import com.hades.safepath.R
import com.hades.safepath.data.model.PackItem
import com.hades.safepath.data.model.SurvivalArticle
import com.hades.safepath.data.model.SurvivalTopic

object Constants {
    val DEFAULT_PACK_ITEMS = listOf(
        PackItem("water", "Drinking Water (1L per person)", "critical"),
        PackItem("firstaid", "First Aid Kit", "critical"),
        PackItem("documents", "Important Documents (Waterproof)", "critical"),
        PackItem("powerbank", "Battery / Power Bank", "important"),
        PackItem("flashlight", "Flashlight", "important"),
        PackItem("food", "Dry Food (3 Meals)", "important"),
        PackItem("whistle", "Whistle", "important"),
        PackItem("clothes", "Change of Clothes", "optional"),
        PackItem("cash", "Cash (Small Notes)", "optional"),
        PackItem("meds", "Personal Medications", "optional"),
        PackItem("map", "Physical Map of Area", "optional")
    )

    val SURVIVAL_TOPICS = emptyList<SurvivalTopic>()
}

object PackData {
    fun getItemsForDisaster(disasterType: String): List<PackItem> {
        return when (disasterType.lowercase()) {
            "flood", "tsunami" -> floodPack
            "earthquake" -> earthquakePack
            "fire" -> firePack
            "landslide" -> landslidePack
            else -> generalPack
        }
    }

    val floodPack = listOf(
        PackItem("water",      "Drinking Water (1L per person)",      "critical"),
        PackItem("firstaid",   "First Aid Kit",                       "critical"),
        PackItem("food",       "Dry Food (3 Days)",                   "critical"),
        PackItem("documents",  "Important Documents (Waterproof Bag)","critical"),
        PackItem("powerbank",  "Fully Charged Power Bank",            "important"),
        PackItem("flashlight", "Waterproof Flashlight",               "important"),
        PackItem("whistle",    "Emergency Whistle",                   "important"),
        PackItem("rope",       "Rope (5 meters)",                     "important"),
        PackItem("clothes",    "Change of Clothes (in sealed bag)",   "optional"),
        PackItem("cash",       "Cash (Small Notes, Waterproof)",      "optional"),
        PackItem("meds",       "Personal Medications",                "optional"),
    )

    val earthquakePack = listOf(
        PackItem("helmet",     "Protective Helmet or Hard Hat",       "critical"),
        PackItem("firstaid",   "First Aid Kit",                       "critical"),
        PackItem("water",      "Drinking Water (3L per person)",      "critical"),
        PackItem("food",       "Dry Food (3 Days)",                   "critical"),
        PackItem("gloves",     "Heavy Work Gloves",                   "important"),
        PackItem("flashlight", "Flashlight + Extra Batteries",        "important"),
        PackItem("whistle",    "Emergency Whistle",                   "important"),
        PackItem("powerbank",  "Charged Power Bank",                  "important"),
        PackItem("documents",  "Important Documents",                 "optional"),
        PackItem("cash",       "Cash (Small Notes)",                  "optional"),
        PackItem("meds",       "Personal Medications",                "optional"),
    )

    val firePack = listOf(
        PackItem("mask",       "N95 Mask or Wet Cloth",               "critical"),
        PackItem("water",      "Drinking Water (2L per person)",      "critical"),
        PackItem("firstaid",   "First Aid Kit with Burn Gel",         "critical"),
        PackItem("food",       "Dry Food (3 Days)",                   "critical"),
        PackItem("flashlight", "Flashlight",                          "important"),
        PackItem("powerbank",  "Charged Power Bank",                  "important"),
        PackItem("whistle",    "Emergency Whistle",                   "important"),
        PackItem("blanket",    "Emergency Fire Blanket",              "important"),
        PackItem("clothes",    "Change of Clothes",                   "optional"),
        PackItem("cash",       "Cash (Small Notes)",                  "optional"),
        PackItem("meds",       "Personal Medications",                "optional"),
    )

    val landslidePack = listOf(
        PackItem("firstaid",   "First Aid Kit",                       "critical"),
        PackItem("water",      "Drinking Water (2L per person)",      "critical"),
        PackItem("food",       "Dry Food (3 Days)",                   "critical"),
        PackItem("flashlight", "Flashlight",                          "important"),
        PackItem("whistle",    "Emergency Whistle",                   "important"),
        PackItem("rope",       "Rope (5 meters)",                     "important"),
        PackItem("powerbank",  "Charged Power Bank",                  "important"),
        PackItem("documents",  "Important Documents",                 "optional"),
        PackItem("clothes",    "Change of Clothes",                   "optional"),
        PackItem("cash",       "Cash (Small Notes)",                  "optional"),
        PackItem("meds",       "Personal Medications",                "optional"),
    )

    val generalPack = listOf(
        PackItem("water",      "Drinking Water (2L per person)",      "critical"),
        PackItem("firstaid",   "First Aid Kit",                       "critical"),
        PackItem("food",       "Dry Food (3 Days)",                   "critical"),
        PackItem("flashlight", "Flashlight",                          "important"),
        PackItem("powerbank",  "Charged Power Bank",                  "important"),
        PackItem("whistle",    "Emergency Whistle",                   "important"),
        PackItem("documents",  "Important Documents",                 "optional"),
        PackItem("clothes",    "Change of Clothes",                   "optional"),
        PackItem("cash",       "Cash (Small Notes)",                  "optional"),
        PackItem("meds",       "Personal Medications",                "optional"),
    )
}
