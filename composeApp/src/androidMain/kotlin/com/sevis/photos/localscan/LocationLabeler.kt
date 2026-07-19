package com.sevis.photos.localscan

import android.content.Context
import android.location.Geocoder
import android.util.Log
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Reverse-geocodes photo GPS coordinates into a place label (e.g. "Varanasi"),
 * clustering nearby coordinates first so a busy day of photos at one place
 * costs one Geocoder lookup, not one per photo.
 */
object LocationLabeler {
    private const val TAG = "LocationLabeler"

    // ~0.01 degrees of latitude is about 1.1km — coarse enough that one visit
    // to a place clusters together, fine enough not to blur distinct places.
    private const val CLUSTER_GRID = 100.0

    fun clusterKey(lat: Double, lon: Double): String {
        val gLat = (lat * CLUSTER_GRID).roundToInt()
        val gLon = (lon * CLUSTER_GRID).roundToInt()
        return "$gLat:$gLon"
    }

    @Suppress("DEPRECATION") // The async Geocoder API is API 33+ only; minSdk here is 26.
    fun resolve(context: Context, lat: Double, lon: Double): String? {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val results = geocoder.getFromLocation(lat, lon, 1)
            val address = results?.firstOrNull() ?: return null
            address.locality
                ?: address.subAdminArea
                ?: address.adminArea
                ?: address.countryName
        } catch (e: Exception) {
            Log.w(TAG, "Reverse geocode failed for ($lat,$lon): ${e.message}")
            null
        }
    }
}
