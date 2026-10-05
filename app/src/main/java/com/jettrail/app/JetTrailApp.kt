package com.jettrail.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jettrail.app.ui.AppSection
import com.jettrail.app.ui.CabinTheme
import com.jettrail.app.ui.ExplorerScreen
import com.jettrail.app.ui.ExplorerUiState
import com.jettrail.app.ui.FlightSummary
import com.jettrail.app.ui.JetTrailTheme
import com.jettrail.app.ui.LiveDashboard
import com.jettrail.app.ui.LiveUiState
import com.jettrail.app.ui.LogbookScreen
import com.jettrail.app.ui.ValueConfidence
import com.jettrail.app.ui.InstrumentValue
import com.jettrail.app.ui.demoFlights
import com.jettrail.app.ui.demoRoute

data class JetTrailCallbacks(
    val onStartFlight: () -> Unit = {},
    val onEndFlight: () -> Unit = {},
    val onLocationSettings: () -> Unit = {},
    val onPermissionRequest: () -> Unit = {},
    val onBatterySettings: () -> Unit = {},
    val onCorrectAirports: (Long, String?, String?) -> Unit = { _, _, _ -> },
    val onDeleteFlight: (Long) -> Unit = {},
)

@Composable
fun JetTrailApp(
    liveState: LiveUiState = previewLiveState,
    flights: List<FlightSummary> = demoFlights,
    explorerState: ExplorerUiState = ExplorerUiState(),
    callbacks: JetTrailCallbacks = JetTrailCallbacks()
) {
    var theme by remember { mutableStateOf(CabinTheme.DARK) }
    var selectedSection by remember { mutableStateOf(AppSection.LIVE) }
    var selectedFlight by remember { mutableStateOf<FlightSummary?>(null) }

    JetTrailTheme(theme) {
        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            val expanded = maxWidth >= 700.dp
            if (expanded) {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.width(104.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.weight(1f)) {
                            NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                                Text("JT", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 22.dp))
                                AppSection.entries.forEach { section ->
                                    NavigationRailItem(
                                        selected = selectedSection == section,
                                        onClick = { selectedSection = section; if (section != AppSection.LOGBOOK) selectedFlight = null },
                                        icon = { NavGlyph(section, selectedSection == section) },
                                        label = { Text(section.label, style = MaterialTheme.typography.labelMedium) }
                                    )
                                }
                            }
                        }
                        ThemeToggle(theme) { theme = it }
                    }
                    Box(Modifier.weight(1f).fillMaxSize()) {
                        AppContent(selectedSection, liveState, flights, selectedFlight, { selectedFlight = it }, { selectedFlight = null }, explorerState, callbacks)
                    }
                }
            } else {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            AppSection.entries.forEach { section ->
                                NavigationBarItem(
                                    selected = selectedSection == section,
                                    onClick = { selectedSection = section; if (section != AppSection.LOGBOOK) selectedFlight = null },
                                    icon = { NavGlyph(section, selectedSection == section) },
                                    label = { Text(section.label, style = MaterialTheme.typography.labelMedium) }
                                )
                            }
                            NavigationBarItem(
                                selected = false,
                                onClick = { theme = if (theme == CabinTheme.DARK) CabinTheme.RED_AMBER else CabinTheme.DARK },
                                icon = { Text(if (theme == CabinTheme.DARK) "NGT" else "DRK", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary) },
                                label = { Text(if (theme == CabinTheme.DARK) "Night" else "Dark", style = MaterialTheme.typography.labelMedium) }
                            )
                        }
                    }
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        AppContent(selectedSection, liveState, flights, selectedFlight, { selectedFlight = it }, { selectedFlight = null }, explorerState, callbacks)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppContent(
    section: AppSection,
    liveState: LiveUiState,
    flights: List<FlightSummary>,
    selectedFlight: FlightSummary?,
    onSelectFlight: (FlightSummary) -> Unit,
    onBack: () -> Unit,
    explorerState: ExplorerUiState,
    callbacks: JetTrailCallbacks
) {
    when (section) {
        AppSection.LIVE -> LiveDashboard(liveState, callbacks.onStartFlight, callbacks.onEndFlight, callbacks.onLocationSettings, callbacks.onPermissionRequest, callbacks.onBatterySettings)
        AppSection.LOGBOOK -> LogbookScreen(flights, selectedFlight, onSelectFlight, onBack, callbacks.onCorrectAirports, callbacks.onDeleteFlight)
        AppSection.EXPLORER -> ExplorerScreen(explorerState)
    }
}

@Composable
private fun NavGlyph(section: AppSection, selected: Boolean) {
    Text(
        section.shortLabel.take(2),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ThemeToggle(current: CabinTheme, onChange: (CabinTheme) -> Unit) {
    TextButton(onClick = { onChange(if (current == CabinTheme.DARK) CabinTheme.RED_AMBER else CabinTheme.DARK) }) {
        Text(if (current == CabinTheme.DARK) "NIGHT" else "DARK", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
    }
}

private val previewLiveState = LiveUiState(
    isRecording = false,
    flightPhase = "Ready on ground",
    origin = "BRU",
    destination = "DUB",
    route = demoRoute.take(4),
    elapsed = "00:42:18",
    distance = "386.4 km",
    instruments = listOf(
        InstrumentValue("842", "km/h", "Ground speed", ValueConfidence.MEASURED),
        InstrumentValue("10,940", "m", "GPS altitude", ValueConfidence.MEASURED, "GNSS ±18 m"),
        InstrumentValue("+12", "m/min", "Vertical speed", ValueConfidence.ESTIMATED, "20 s trend"),
        InstrumentValue("287", "° WNW", "Track", ValueConfidence.MEASURED, "direction over ground")
    ),
    accuracyMetres = 18,
    satellites = 14,
    coveragePercent = 93,
    pressure = "812 hPa measured",
    cabinAltitude = "1,860 m estimated",
    turbulence = "Light • rough estimate",
    rawSamples = 2538,
    acceptedSamples = 2491
)
