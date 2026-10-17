package de.ideenwerft.kiez.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

// Aktuellen Standort einmal abfragen, ohne Google-Dienste (klappt auch auf Geräten ohne Play Store)
@SuppressLint("MissingPermission")
suspend fun currentLocation(context: Context): Coords? {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
    // Eine frische letzte Position reicht für einen 10-km-Umkreis
    val last = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
    if (last != null && System.currentTimeMillis() - last.time < 10 * 60_000) return Coords(last.latitude, last.longitude)
    val provider = providers.firstOrNull { it != LocationManager.PASSIVE_PROVIDER } ?: return last?.let { Coords(it.latitude, it.longitude) }
    val fresh = withTimeoutOrNull(12_000) {
        suspendCancellableCoroutine<Location?> { cont ->
            if (Build.VERSION.SDK_INT >= 30) {
                lm.getCurrentLocation(provider, null, context.mainExecutor) { loc -> if (cont.isActive) cont.resume(loc) }
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        lm.removeUpdates(this)
                        if (cont.isActive) cont.resume(location)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }
                lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                cont.invokeOnCancellation { lm.removeUpdates(listener) }
            }
        }
    }
    val best = fresh ?: last ?: return null
    return Coords(best.latitude, best.longitude)
}
