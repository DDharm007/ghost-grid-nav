package com.example.ghostgrid.search

enum class AmenityCategory(
    val dbKey: String,
    val label: String
) {
    FOOD("food", "Food Junctions"),
    HOSPITAL("hospital", "Hospitals & Clinics"),
    PHARMACY("pharmacy", "Pharmacies"),
    FUEL("fuel", "Fuel"),
    TRANSIT("transit", "Transit"),
    SAVED("saved", "Saved Places")
}

data class PlaceResult(
    val id: String,
    val name: String,
    val category: String,
    val subcategory: String,
    val address: String,
    val lat: Double,
    val lon: Double,
    val distanceMeters: Double,
    val regionId: String,
    val isSaved: Boolean = false,
    val savedListName: String? = null
) {
    val stableEnrichmentKey: String
        get() = "${name.lowercase().trim()}_${String.format("%.4f", lat)}_${String.format("%.4f", lon)}"
}
