package com.jettrail.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
private fun ScreenHeader(kicker: String, title: String, subtitle: String, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Eyebrow(kicker)
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) { Spacer(Modifier.width(12.dp)); trailing() }
    }
}

@Composable
fun LiveDashboard(
    state: LiveUiState,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    onLocationSettings: () -> Unit,
    onPermissionRequest: () -> Unit,
    onBatterySettings: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ScreenHeader("Private flight recorder", if (state.isRecording) "Recording in progress" else "Ready for departure", "Offline • device-only • one-second sampling") {
                StatusPill(if (state.isRecording) "REC ${state.elapsed}" else "STANDBY", state.isRecording)
            }
        }
        if (!state.permissionReady) item {
            WarningBanner("Location permission needed", "Allow precise location and background access so recording can continue when the Fold is locked.", "Review permissions", onPermissionRequest)
        }
        if (!state.locationEnabled) item {
            WarningBanner("Location is switched off", "GNSS samples cannot be captured until Location is enabled.", "Open Location settings", onLocationSettings)
        }
        if (state.batteryOptimized) item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    "Power settings",
                    modifier = Modifier.clickable(onClick = onBatterySettings).padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            BoxWithConstraints {
                val expanded = maxWidth >= 720.dp
                if (expanded) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        FlightHero(state, onStart, onEnd, Modifier.weight(1.15f))
                        SignalPanel(state, Modifier.weight(.85f))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        FlightHero(state, onStart, onEnd, Modifier.fillMaxWidth())
                        SignalPanel(state, Modifier.fillMaxWidth())
                    }
                }
            }
        }
        item { InstrumentGrid(state.instruments) }
        item { LiveStatsPanel(state) }
        item {
            BoxWithConstraints {
                val expanded = maxWidth >= 720.dp
                if (expanded) Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    EnvironmentPanel(state, Modifier.weight(1f))
                    SamplePanel(state, Modifier.weight(1f))
                } else Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    EnvironmentPanel(state, Modifier.fillMaxWidth())
                    SamplePanel(state, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun FlightHero(state: LiveUiState, onStart: () -> Unit, onEnd: () -> Unit, modifier: Modifier) {
    JetCard(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column { Eyebrow("Live track"); Text("${state.origin}  →  ${state.destination}", style = MaterialTheme.typography.headlineMedium) }
                StatusPill(state.flightPhase, state.isRecording)
            }
            OfflineRouteMap(state.route, Modifier.fillMaxWidth().height(290.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) { Eyebrow("Elapsed"); Text(state.elapsed, style = MaterialTheme.typography.titleLarge) }
                Column(Modifier.weight(1f)) { Eyebrow("Distance"); Text(state.distance, style = MaterialTheme.typography.titleLarge) }
            }
            if (!state.isRecording) {
                Button(onClick = onStart, enabled = state.locationEnabled && state.permissionReady, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
                    Text("START FLIGHT", style = MaterialTheme.typography.labelLarge)
                }
            } else {
                OutlinedButton(onClick = onEnd, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("END FLIGHT", style = MaterialTheme.typography.labelLarge)
                }
            }
            Text("Recording starts only from this control. A persistent notification remains visible while active.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SignalPanel(state: LiveUiState, modifier: Modifier) {
    JetCard(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Eyebrow("GNSS quality"); Text(state.signalStatus, style = MaterialTheme.typography.titleLarge) }
                Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
                    Text("${state.coveragePercent}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            QualityBar(state.coveragePercent)
            InfoRow("Satellites", state.satellites?.toString() ?: "Unavailable", state.satellites != null)
            InfoRow("Reported accuracy", state.accuracyMetres?.let { "±$it m" } ?: "Unavailable", state.accuracyMetres != null)
            InfoRow("Longest dropout", state.longestDropout, state.longestDropout == "None")
            InfoRow("Sample interval", if (state.isRecording) "1 second" else "Inactive", state.isRecording)
            Text("Coverage is computed from fresh confirmed GNSS fixes. Impossible readings are retained raw but excluded from visible statistics.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LiveStatsPanel(state: LiveUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MiniMetric(state.maxSpeed, "TOP SPEED", Modifier.weight(1f))
        MiniMetric(state.maxAltitude, "MAX GPS ALT", Modifier.weight(1f))
        MiniMetric(state.acceptedSamples.toString(), "GOOD FIXES", Modifier.weight(1f))
    }
}

@Composable
private fun InstrumentGrid(values: List<InstrumentValue>) {
    BoxWithConstraints {
        val columns = if (maxWidth >= 720.dp) 4 else 2
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            values.chunked(columns).forEach { rowValues ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowValues.forEach { InstrumentCard(it, Modifier.weight(1f)) }
                    repeat(columns - rowValues.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun EnvironmentPanel(state: LiveUiState, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    JetCard(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column { Eyebrow("Advanced sensors"); Text(if (expanded) "Cabin and phone motion" else "Hidden by default", style = MaterialTheme.typography.titleMedium) }
                TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) { Text(if (expanded) "HIDE" else "SHOW") }
            }
            if (expanded) {
                InfoRow("Cabin pressure", state.pressure ?: "Not available", state.pressure != null)
                InfoRow("Cabin pressure altitude", state.cabinAltitude ?: "Not available", state.cabinAltitude != null, estimated = state.cabinAltitude != null)
                InfoRow("Phone motion", state.turbulence, false, estimated = true)
                Text("These are context clues, not aircraft instruments. Cabin pressure altitude is not aircraft altitude, and phone motion can come from your hand or tray table.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("Useful for curiosity/debugging, but not reliable enough to headline the flight.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SamplePanel(state: LiveUiState, modifier: Modifier) {
    JetCard(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Eyebrow("Processing pipeline")
            InfoRow("Raw samples retained", state.rawSamples.toString(), true)
            InfoRow("Confirmed GNSS fixes", state.acceptedSamples.toString(), true)
            InfoRow("Rejected as outliers", state.rejectedSamples.toString(), false)
            Text("Measured values come from device sensors. Estimated values are explicitly labelled throughout the logbook.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, positive: Boolean, estimated: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(Modifier.size(6.dp).background(if (estimated) MaterialTheme.colorScheme.tertiary else if (positive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
            Text(value, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun LogbookScreen(flights: List<FlightSummary>, selected: FlightSummary?, onSelect: (FlightSummary) -> Unit, onBack: () -> Unit, onCorrect: (Long, String?, String?) -> Unit, onDelete: (Long) -> Unit) {
    if (selected != null) FlightDetailScreen(selected, onBack, onCorrect, onDelete) else LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item { ScreenHeader("Local history", "Flight logbook", "${flights.size} flights • stored only on this device") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniMetric("${flights.size}", "FLIGHTS", Modifier.weight(1f)); MiniMetric("${flights.sumOf { it.distanceKm }}", "TOTAL KM", Modifier.weight(1f)); MiniMetric("${flights.maxOfOrNull { it.maxSpeedKmh } ?: 0}", "TOP KM/H", Modifier.weight(1f))
            }
        }
        item { Eyebrow("Flights") }
        if (flights.isEmpty()) item { Text("No flights recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(flights, key = { it.id }) { flight -> FlightRow(flight) { onSelect(flight) } }
    }
}

@Composable
private fun FlightRow(flight: FlightSummary, onClick: () -> Unit) {
    JetCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        BoxWithConstraints {
            val expanded = maxWidth > 560.dp
            if (expanded) Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                OfflineRouteMap(flight.route, Modifier.width(190.dp).height(100.dp), true)
                FlightRowText(flight, Modifier.weight(1f))
            } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlightRowText(flight, Modifier.fillMaxWidth())
                OfflineRouteMap(flight.route, Modifier.fillMaxWidth().height(105.dp), true)
            }
        }
    }
}

@Composable private fun FlightRowText(f: FlightSummary, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${f.origin}  →  ${f.destination}", style = MaterialTheme.typography.titleLarge)
            StatusPill("${f.qualityPercent}% DATA", f.qualityPercent >= 85)
        }
        Text(f.date, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(f.duration, style = MaterialTheme.typography.labelLarge); Text("${f.distanceKm} km", style = MaterialTheme.typography.labelLarge); Text("${f.maxSpeedKmh} km/h max", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun MiniMetric(value: String, label: String, modifier: Modifier) {
    JetCard(modifier) { Column { Text(value, style = MaterialTheme.typography.titleLarge); Eyebrow(label) } }
}

@Composable
fun FlightDetailScreen(flight: FlightSummary, onBack: () -> Unit, onCorrect: (Long, String?, String?) -> Unit, onDelete: (Long) -> Unit) {
    var correcting by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var origin by remember(flight.id) { mutableStateOf(flight.origin.takeUnless { it == "—" }.orEmpty()) }
    var destination by remember(flight.id) { mutableStateOf(flight.destination.takeUnless { it == "—" }.orEmpty()) }
    if (correcting) androidx.compose.material3.AlertDialog(
        onDismissRequest = { correcting = false },
        title = { Text("Correct airports") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            androidx.compose.material3.OutlinedTextField(origin, { origin = it.take(4) }, label = { Text("Origin IATA/ICAO") }, singleLine = true)
            androidx.compose.material3.OutlinedTextField(destination, { destination = it.take(4) }, label = { Text("Destination IATA/ICAO") }, singleLine = true)
        } },
        confirmButton = { TextButton(onClick = { onCorrect(flight.id, origin, destination); correcting = false }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { correcting = false }) { Text("Cancel") } },
    )
    if (confirmingDelete) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirmingDelete = false },
        title = { Text("Delete flight?") },
        text = { Text("This permanently removes the session and all of its locally stored raw samples.") },
        confirmButton = { TextButton(onClick = { onDelete(flight.id); confirmingDelete = false; onBack() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") } },
    )
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text("←  LOGBOOK", style = MaterialTheme.typography.labelLarge) }
            ScreenHeader(flight.date, "${flight.origin}  →  ${flight.destination}", "${flight.duration} • ${flight.distanceKm} km • ${flight.phases}") { StatusPill("${flight.qualityPercent}% QUALITY", flight.qualityPercent >= 85) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { correcting = true }, contentPadding = PaddingValues(0.dp)) { Text("Correct airports") }
                TextButton(onClick = { confirmingDelete = true }, contentPadding = PaddingValues(0.dp)) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            }
        }
        item { OfflineRouteMap(flight.route, Modifier.fillMaxWidth().height(260.dp)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniMetric("${flight.averageSpeedKmh}", "AVG KM/H", Modifier.weight(1f)); MiniMetric("${flight.maxSpeedKmh}", "MAX KM/H", Modifier.weight(1f)); MiniMetric("${flight.maxGpsAltitudeM}", "MAX GPS M", Modifier.weight(1f))
            }
        }
        item {
            BoxWithConstraints {
                if (maxWidth >= 720.dp) Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    MetricChart(flight.speedSeries, "Ground speed", "km/h", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    MetricChart(flight.altitudeSeries, "GPS altitude", "m", MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                } else Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    MetricChart(flight.speedSeries, "Ground speed", "km/h", MaterialTheme.colorScheme.primary, Modifier.fillMaxWidth())
                    MetricChart(flight.altitudeSeries, "GPS altitude", "m", MaterialTheme.colorScheme.tertiary, Modifier.fillMaxWidth())
                }
            }
        }
        item { JetCard(Modifier.fillMaxWidth()) { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { QualityBar(flight.qualityPercent); Text("Statistics omit impossible outliers while raw samples remain stored. Ground speed is not airspeed; altitude shown is GNSS altitude, not cabin altitude.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
    }
}

@Composable
fun ExplorerScreen(state: ExplorerUiState) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { ScreenHeader("Explorer profile", "Level ${state.level} • ${state.title}", "Personal milestones from completed flights — no interaction needed in the air") { StatusPill("${state.xp} XP") } }
        item {
            JetCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Eyebrow("Next level"); Text("${state.xp} / ${state.nextLevelXp} XP", style = MaterialTheme.typography.labelLarge) }
                    Box(Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)) { Box(Modifier.fillMaxWidth(state.xp.toFloat() / state.nextLevelXp).height(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape)) }
                    Text("XP is awarded after a session ends. JetTrail never prompts for game actions during takeoff, landing, or turbulence.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { MiniMetric("${state.flights}", "FLIGHTS", Modifier.weight(1f)); MiniMetric("${state.airports}", "AIRPORTS", Modifier.weight(1f)); MiniMetric("${state.countries}", "COUNTRIES", Modifier.weight(1f)); MiniMetric("${state.routes}", "ROUTES", Modifier.weight(1f)) } }
        item { Text("BADGE CABINET", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
        items(state.badges.chunked(2)) { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { BadgeCard(it, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item { JetCard(Modifier.fillMaxWidth()) { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Eyebrow("Personal records"); InfoRow("Longest flight", "${state.longestDistanceKm} km", state.flights > 0); InfoRow("Highest ground speed", "${state.highestGroundSpeedKmh} km/h", state.flights > 0); InfoRow("Highest GPS altitude", "${state.highestGpsAltitudeM} m", state.flights > 0); InfoRow("Best GNSS coverage", "${state.bestGnssCoveragePercent}%", state.flights > 0); Text("Only completed flights are included.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
    }
}

@Composable private fun BadgeCard(badge: BadgeUi, modifier: Modifier) {
    val tint = if (badge.unlocked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
    JetCard(modifier) { Column(verticalArrangement = Arrangement.spacedBy(9.dp)) { Box(Modifier.size(42.dp).background(tint.copy(alpha = .13f), CircleShape), contentAlignment = Alignment.Center) { Text(badge.glyph, style = MaterialTheme.typography.labelLarge, color = tint) }; Text(badge.name, style = MaterialTheme.typography.titleMedium, color = if (badge.unlocked) MaterialTheme.colorScheme.onSurface else tint); Text(badge.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Eyebrow(if (badge.unlocked) "Unlocked" else "Locked", tint) } }
}
