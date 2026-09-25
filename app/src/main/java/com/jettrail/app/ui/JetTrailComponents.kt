package com.jettrail.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

@Composable
fun JetCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .16f))
    ) { Box(Modifier.padding(18.dp)) { content() } }
}

@Composable
fun Eyebrow(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = color)
}

@Composable
fun StatusPill(text: String, positive: Boolean = true, modifier: Modifier = Modifier) {
    val color = if (positive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    Row(
        modifier.background(color.copy(alpha = .12f), RoundedCornerShape(50)).border(1.dp, color.copy(alpha = .35f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
fun WarningBanner(title: String, message: String, action: String? = null, onAction: () -> Unit = {}) {
    val tint = MaterialTheme.colorScheme.tertiary
    JetCard(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(13.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(30.dp).background(tint.copy(alpha = .16f), CircleShape), contentAlignment = Alignment.Center) {
                Text("!", color = tint, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (action != null) {
                    androidx.compose.material3.TextButton(onClick = onAction, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        Text(action.uppercase(), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
fun InstrumentCard(value: InstrumentValue, modifier: Modifier = Modifier) {
    JetCard(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Eyebrow(value.label)
                val dot = when (value.confidence) {
                    ValueConfidence.MEASURED -> MaterialTheme.colorScheme.primary
                    ValueConfidence.ESTIMATED -> MaterialTheme.colorScheme.tertiary
                    ValueConfidence.UNAVAILABLE -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Box(Modifier.size(7.dp).background(dot, CircleShape))
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(value.value, style = MaterialTheme.typography.displaySmall, maxLines = 1)
                Text(value.unit, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 5.dp))
            }
            Text(
                when (value.confidence) {
                    ValueConfidence.MEASURED -> "MEASURED • ${value.hint}"
                    ValueConfidence.ESTIMATED -> "ESTIMATED • ${value.hint}"
                    ValueConfidence.UNAVAILABLE -> "WAITING FOR ${value.hint}"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private data class MapRegion(val name: String, val points: List<TrackPoint>)

private fun loadEuropeMap(context: android.content.Context): List<MapRegion> =
    context.assets.open("europe_map.txt").bufferedReader().useLines { lines ->
        lines.mapNotNull { line ->
            val split = line.indexOf('|')
            if (split < 1) return@mapNotNull null
            val points = line.substring(split + 1).split(';').mapNotNull { pair ->
                val values = pair.split(',')
                if (values.size == 2) TrackPoint(values[1].toDouble(), values[0].toDouble()) else null
            }
            MapRegion(line.substring(0, split), points).takeIf { points.size > 2 }
        }.toList()
    }

@Composable
fun OfflineRouteMap(route: List<TrackPoint>, modifier: Modifier = Modifier, compact: Boolean = false) {
    val context = LocalContext.current
    val regions = remember(context) { loadEuropeMap(context) }
    val primary = MaterialTheme.colorScheme.primary
    val destination = MaterialTheme.colorScheme.tertiary
    val land = MaterialTheme.colorScheme.surfaceVariant
    val border = MaterialTheme.colorScheme.onSurface.copy(alpha = .24f)
    val water = MaterialTheme.colorScheme.background
    val mapShape = RoundedCornerShape(18.dp)
    Canvas(modifier.clip(mapShape).background(water, mapShape)) {
        var minLon: Double
        var maxLon: Double
        var minLat: Double
        var maxLat: Double
        if (route.isEmpty()) {
            minLon = -12.0; maxLon = 35.0; minLat = 34.0; maxLat = 72.0
        } else {
            minLon = route.minOf { it.longitude } - 3.0; maxLon = route.maxOf { it.longitude } + 3.0
            minLat = route.minOf { it.latitude } - 2.5; maxLat = route.maxOf { it.latitude } + 2.5
            val lonCenter = (minLon + maxLon) / 2; val latCenter = (minLat + maxLat) / 2
            if (maxLon - minLon < 10) { minLon = lonCenter - 5; maxLon = lonCenter + 5 }
            if (maxLat - minLat < 7) { minLat = latCenter - 3.5; maxLat = latCenter + 3.5 }
            minLon = max(-18.0, minLon); maxLon = min(42.0, maxLon)
            minLat = max(31.0, minLat); maxLat = min(73.0, maxLat)
        }
        fun project(lon: Double, lat: Double) = Offset(
            (((lon - minLon) / (maxLon - minLon)) * size.width).toFloat(),
            (((maxLat - lat) / (maxLat - minLat)) * size.height).toFloat(),
        )
        for (lon in -10..40 step 10) drawLine(border.copy(alpha = .14f), project(lon.toDouble(), maxLat), project(lon.toDouble(), minLat), 1f)
        for (lat in 35..70 step 5) drawLine(border.copy(alpha = .14f), project(minLon, lat.toDouble()), project(maxLon, lat.toDouble()), 1f)
        regions.forEach { region ->
            val path = Path()
            region.points.forEachIndexed { index, point ->
                val p = project(point.longitude, point.latitude)
                if (index == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            path.close()
            drawPath(path, land.copy(alpha = .82f))
            drawPath(path, border, style = Stroke(if (compact) .8f else 1.4f))
        }
        if (route.size > 1) {
            val routePath = Path()
            route.forEachIndexed { index, point ->
                val p = project(point.longitude, point.latitude)
                if (index == 0) routePath.moveTo(p.x, p.y) else routePath.lineTo(p.x, p.y)
            }
            drawPath(routePath, Color.Black.copy(alpha = .45f), style = Stroke(if (compact) 7f else 10f, cap = StrokeCap.Round))
            drawPath(routePath, primary, style = Stroke(if (compact) 3f else 4.5f, cap = StrokeCap.Round))
            val first = project(route.first().longitude, route.first().latitude)
            val last = project(route.last().longitude, route.last().latitude)
            drawCircle(water, if (compact) 7f else 10f, first); drawCircle(primary, if (compact) 4f else 6f, first)
            drawCircle(destination.copy(alpha = .25f), if (compact) 11f else 16f, last)
            drawCircle(water, if (compact) 7f else 10f, last); drawCircle(destination, if (compact) 4.5f else 7f, last)
            val aircraft = Path().apply {
                moveTo(last.x, last.y - if (compact) 11f else 16f)
                lineTo(last.x - if (compact) 6f else 9f, last.y + if (compact) 7f else 11f)
                lineTo(last.x + if (compact) 6f else 9f, last.y + if (compact) 7f else 11f)
                close()
            }
            drawPath(aircraft, destination)
        }
    }
}

@Composable
fun MetricChart(series: List<Float>, label: String, unit: String, color: Color, modifier: Modifier = Modifier) {
    JetCard(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column { Eyebrow(label, color); Text("PROFILE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text("${series.maxOrNull()?.toInt() ?: 0} $unit", style = MaterialTheme.typography.titleMedium)
            }
            val grid = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f)
            Canvas(Modifier.fillMaxWidth().height(130.dp)) {
                repeat(4) { i -> drawLine(grid, Offset(0f, size.height * i / 3f), Offset(size.width, size.height * i / 3f), 1f) }
                if (series.size > 1) {
                    val high = max(1f, series.maxOrNull() ?: 1f)
                    val p = Path()
                    series.forEachIndexed { i, v ->
                        val x = size.width * i / (series.size - 1)
                        val y = size.height - (v / high * size.height * .9f)
                        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                    }
                    drawPath(p, color, style = Stroke(4f, cap = StrokeCap.Round))
                }
            }
        }
    }
}

@Composable
fun QualityBar(value: Int, modifier: Modifier = Modifier) {
    val color = when { value >= 85 -> MaterialTheme.colorScheme.primary; value >= 60 -> MaterialTheme.colorScheme.tertiary; else -> MaterialTheme.colorScheme.error }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Eyebrow("Data quality", color)
            Text("$value%", style = MaterialTheme.typography.labelLarge, color = color)
        }
        Box(Modifier.fillMaxWidth().height(5.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)) {
            Box(Modifier.fillMaxWidth((value.coerceIn(0, 100) / 100f)).fillMaxHeight().background(color, CircleShape))
        }
    }
}
