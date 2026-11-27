package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.resume

object LocationTimeHelper {

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun getCurrentTimeZoneId(): String = TimeZone.getDefault().id

    fun formatLocalTime(timeZoneId: String? = null, nowMillis: Long = System.currentTimeMillis()): String {
        val tz = if (!timeZoneId.isNullOrBlank()) TimeZone.getTimeZone(timeZoneId) else TimeZone.getDefault()
        val sdf = SimpleDateFormat("HH:mm · EEE, MMM d", Locale.getDefault()).apply {
            timeZone = tz
        }
        val shortTz = tz.getDisplayName(false, TimeZone.SHORT, Locale.getDefault())
        return "${sdf.format(Date(nowMillis))} ($shortTz)"
    }

    fun formatShortClock(timeZoneId: String? = null, nowMillis: Long = System.currentTimeMillis()): String {
        val tz = if (!timeZoneId.isNullOrBlank()) TimeZone.getTimeZone(timeZoneId) else TimeZone.getDefault()
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
            timeZone = tz
        }
        return sdf.format(Date(nowMillis))
    }

    /**
     * Parses yyyy-MM-dd and returns accurate age in years (0..130).
     */
    fun calculateAgeFromDob(dobIso: String): Int {
        val parts = dobIso.trim().split("-")
        if (parts.size != 3) return 0
        val year = parts[0].toIntOrNull() ?: return 0
        val month = parts[1].toIntOrNull() ?: return 0
        val day = parts[2].toIntOrNull() ?: return 0
        if (year < 1900 || month !in 1..12 || day !in 1..31) return 0

        val today = Calendar.getInstance()
        val dob = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
        }
        if (dob.after(today)) return 0

        var age = today.get(Calendar.YEAR) - dob.get(Calendar.YEAR)
        val todayDay = today.get(Calendar.DAY_OF_YEAR)
        val dobDay = dob.get(Calendar.DAY_OF_YEAR)
        if (todayDay < dobDay) {
            age--
        }
        return age.coerceIn(0, 130)
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentCoordinates(context: Context): Pair<Double, Double>? {
        if (!hasLocationPermission(context)) return null
        val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)
        val location = suspendCancellableCoroutine<Location?> { cont ->
            val cts = CancellationTokenSource()
            cont.invokeOnCancellation { cts.cancel() }
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        cont.resume(loc)
                    } else {
                        client.lastLocation
                            .addOnSuccessListener { last -> cont.resume(last) }
                            .addOnFailureListener { cont.resume(null) }
                    }
                }
                .addOnFailureListener {
                    client.lastLocation
                        .addOnSuccessListener { last -> cont.resume(last) }
                        .addOnFailureListener { cont.resume(null) }
                }
        } ?: return null
        return location.latitude to location.longitude
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentLocationLabel(context: Context): String? {
        val (lat, lng) = fetchCurrentCoordinates(context) ?: return null
        val location = Location("fused").apply {
            latitude = lat
            longitude = lng
        }

        return withContext(Dispatchers.IO) {
            val coords = String.format(Locale.US, "%.4f, %.4f", location.latitude, location.longitude)
            try {
                @Suppress("DEPRECATION")
                val addresses = Geocoder(context.applicationContext, Locale.getDefault())
                    .getFromLocation(location.latitude, location.longitude, 1)
                val addr = addresses?.firstOrNull()
                val city = addr?.locality ?: addr?.subAdminArea ?: addr?.adminArea
                val country = addr?.countryCode ?: addr?.countryName
                when {
                    !city.isNullOrBlank() && !country.isNullOrBlank() -> "$city, $country"
                    !city.isNullOrBlank() -> city
                    !country.isNullOrBlank() -> country
                    else -> coords
                }
            } catch (_: Exception) {
                coords
            }
        }
    }
}
