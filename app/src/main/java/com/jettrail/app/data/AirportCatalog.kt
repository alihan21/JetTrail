package com.jettrail.app.data

import android.content.Context
import com.jettrail.app.domain.Airport

object AirportCatalog {
    fun load(context: Context): List<Airport> = context.assets.open("airports_compact.txt").bufferedReader().useLines { lines ->
        lines.mapNotNull { line ->
            val p = line.split('|', limit = 6)
            if (p.size != 6) null else Airport(p[0], p[5], p[3].toDouble(), p[4].toDouble(), p[2], p[1].ifBlank { null })
        }.toList()
    }
}
