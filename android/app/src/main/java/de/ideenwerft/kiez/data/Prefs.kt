package de.ideenwerft.kiez.data

import android.content.Context
import androidx.core.content.edit

// Kleine Einstellungen und das anonyme Token; alles bleibt auf dem Gerät
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("kiez", Context.MODE_PRIVATE)

    var token: String?
        get() = sp.getString("token", null)
        set(v) = sp.edit { putString("token", v) }

    var theme: String
        get() = sp.getString("theme", "auto") ?: "auto"
        set(v) = sp.edit { putString("theme", v) }

    var haptics: Boolean
        get() = sp.getBoolean("haptics", true)
        set(v) = sp.edit { putBoolean("haptics", v) }

    // Zuletzt benutzter Ort; gps = true heißt „automatisch per Standort“
    var place: SavedPlace?
        get() {
            if (!sp.contains("lat")) return null
            return SavedPlace(
                Coords(sp.getFloat("lat", 0f).toDouble(), sp.getFloat("lng", 0f).toDouble()),
                sp.getString("label", "") ?: "",
                sp.getBoolean("gps", true),
            )
        }
        set(v) = sp.edit {
            if (v == null) { remove("lat"); remove("lng"); remove("label"); remove("gps") }
            else {
                putFloat("lat", v.coords.lat.toFloat()); putFloat("lng", v.coords.lng.toFloat())
                putString("label", v.label); putBoolean("gps", v.gps)
            }
        }

    // Letzter geladener Feed, damit die App auch ohne Netz etwas zeigt
    var cachedFeed: String?
        get() = sp.getString("feed", null)
        set(v) = sp.edit { putString("feed", v) }
}

data class SavedPlace(val coords: Coords, val label: String, val gps: Boolean)
