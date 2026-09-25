package com.jettrail.app

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jettrail.app.data.FlightEntity
import com.jettrail.app.data.toDomain
import com.jettrail.app.data.toEntity
import com.jettrail.app.domain.FlightProcessor
import com.jettrail.app.domain.ProcessedFlightSample
import com.jettrail.app.domain.FlightSimulator
import com.jettrail.app.domain.FlightStatisticsCalculator
import com.jettrail.app.domain.SimulationScenario
import com.jettrail.app.recording.RecordingController
import com.jettrail.app.recording.RecordingEnvironment
import com.jettrail.app.recording.RecordingRuntime
import com.jettrail.app.recording.RecordingSample
import com.jettrail.app.recording.RecordingStartResult
import com.jettrail.app.recording.RecordingState
import com.jettrail.app.recording.toDomainRawSample
import com.jettrail.app.ui.ExplorerUiState
import com.jettrail.app.ui.FlightSummary
import com.jettrail.app.ui.InstrumentValue
import com.jettrail.app.ui.LiveUiState
import com.jettrail.app.ui.TrackPoint
import com.jettrail.app.ui.ValueConfidence
import com.jettrail.app.ui.BadgeUi
import com.jettrail.app.ui.SimulationUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

class JetTrailViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as JetTrailApplication
    private val dao = app.database.flightDao()
    private val _live = MutableStateFlow(LiveUiState())
    val live: StateFlow<LiveUiState> = _live.asStateFlow()
    private val liveProcessor = FlightProcessor()
    private val liveRoute = ArrayDeque<TrackPoint>()
    private var liveStartedAt = 0L
    private var liveDistanceM = 0.0
    private var liveMaxSpeedMps: Double? = null
    private var liveMaxAltitudeM: Double? = null
    private var liveLongestDropoutSamples = 0
    private var liveCurrentDropoutSamples = 0
    private var recoveredFlightId: Long? = null
    private var simulationJob: Job? = null
    private var simulationFlightId: Long? = null
    private val _simulation = MutableStateFlow(SimulationUiState())
    val simulation: StateFlow<SimulationUiState> = _simulation.asStateFlow()

    val flights: StateFlow<List<FlightSummary>> = dao.observeFlights().map { rows ->
        rows.mapNotNull { flight ->
            val samples = dao.getSamples(flight.id).map { it.toDomain() }
            if (samples.isEmpty()) return@mapNotNull null
            val stats = FlightStatisticsCalculator.calculate(samples)
            FlightSummary(
                id = flight.id,
                origin = flight.correctedOriginIdent ?: flight.inferredOriginIdent ?: "—",
                destination = flight.correctedDestinationIdent ?: flight.inferredDestinationIdent ?: "—",
                date = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(flight.startedAtMillis)),
                duration = formatDuration(stats.durationMillis),
                distanceKm = (stats.distanceM / 1000).roundToInt(),
                maxSpeedKmh = ((stats.maximumGroundSpeedMps ?: 0.0) * 3.6).roundToInt(),
                maxGpsAltitudeM = (stats.maximumGpsAltitudeM ?: 0.0).roundToInt(),
                averageSpeedKmh = ((stats.averageGroundSpeedMps ?: 0.0) * 3.6).roundToInt(),
                qualityPercent = (stats.gpsCoverageFraction * 100).roundToInt(),
                phases = stats.phaseDurationsMillis.entries.joinToString(" • ") { "${it.key.name.lowercase().replaceFirstChar(Char::uppercase)} ${formatDuration(it.value)}" },
                route = samples.filter { it.acceptedForStatistics }.mapNotNull { s ->
                    s.raw.latitudeDeg?.let { lat -> s.raw.longitudeDeg?.let { TrackPoint(lat, it) } }
                },
                speedSeries = samples.mapNotNull { it.raw.groundSpeedMps?.times(3.6)?.toFloat() },
                altitudeSeries = samples.mapNotNull { it.raw.gpsAltitudeM?.toFloat() },
                isSimulation = flight.isSimulation,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val explorer: StateFlow<ExplorerUiState> = flights.map { all ->
        val list = all.filterNot { it.isSimulation }
        val airportCodes = list.flatMap { listOf(it.origin, it.destination) }.filter { it != "—" }.toSet()
        val countryByCode = app.airports.flatMap { airport -> listOfNotNull(airport.ident to airport.countryCode, airport.iataCode?.let { it to airport.countryCode }) }.toMap()
        val countries = airportCodes.mapNotNull(countryByCode::get).toSet()
        val routes = list.mapNotNull { if (it.origin == "—" || it.destination == "—") null else listOf(it.origin, it.destination).sorted().joinToString("-") }.toSet()
        val xp = list.sumOf { 100 + (it.distanceKm / 100).coerceAtMost(100) + if (it.qualityPercent >= 80) 25 else 0 }
        val unlocked = buildSet {
            if (list.isNotEmpty()) add("First lift-off")
            if (countries.size >= 5) add("Five countries")
            if (airportCodes.size >= 10) add("Airport collector")
            if (list.any { it.distanceKm >= 5_000 }) add("Long haul")
            if (list.any { it.qualityPercent >= 95 }) add("Clean signal")
        }
        val badges = listOf(
            BadgeUi("First lift-off", "Record a complete real flight", "First lift-off" in unlocked, "01"),
            BadgeUi("Clean signal", "Reach 95% GNSS coverage", "Clean signal" in unlocked, "◎"),
            BadgeUi("Five countries", "Visit five countries", "Five countries" in unlocked, "05"),
            BadgeUi("Airport collector", "Visit ten airports", "Airport collector" in unlocked, "10"),
            BadgeUi("Long haul", "Record a real flight over 5,000 km", "Long haul" in unlocked, "∞"),
        )
        val level = 1 + xp / 500
        ExplorerUiState(level = level, title = when { level >= 10 -> "Jetstream Cartographer"; level >= 5 -> "Stratosphere Scout"; level >= 2 -> "Runway Rover"; else -> "New Explorer" },
            xp = xp, nextLevelXp = level * 500, flights = list.size, airports = airportCodes.size, countries = countries.size, routes = routes.size,
            longestDistanceKm = list.maxOfOrNull { it.distanceKm } ?: 0,
            highestGroundSpeedKmh = list.maxOfOrNull { it.maxSpeedKmh } ?: 0,
            highestGpsAltitudeM = list.maxOfOrNull { it.maxGpsAltitudeM } ?: 0,
            bestGnssCoveragePercent = list.maxOfOrNull { it.qualityPercent } ?: 0,
            badges = badges)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExplorerUiState(level = 1, xp = 0, nextLevelXp = 500, flights = 0, airports = 0, countries = 0, routes = 0))

    init {
        refreshEnvironment()
        viewModelScope.launch { recoverActiveRecording() }
        viewModelScope.launch {
            RecordingRuntime.rawSamples.collect { sample -> updateLive(sample) }
        }
        viewModelScope.launch {
            RecordingRuntime.state.collect { state ->
                _live.value = _live.value.copy(isRecording = state is RecordingState.Active || state is RecordingState.Starting)
                if (state is RecordingState.Active && state.flightId != recoveredFlightId && _live.value.rawSamples == 0) {
                    recoverActiveRecording()
                }
            }
        }
    }

    fun refreshEnvironment() {
        val warnings = RecordingEnvironment.warnings(getApplication())
        _live.value = _live.value.copy(
            permissionReady = warnings.none { it.code == "fine_location_permission" },
            locationEnabled = warnings.none { it.code == "location_disabled" },
            batteryOptimized = warnings.any { it.code == "battery_saver" || it.code == "location_power_save" },
        )
    }

    fun onAppResumed(activity: Activity) {
        refreshEnvironment()
        viewModelScope.launch {
            val flight = dao.findRecoverableRecording(recoverableCutoffMillis()) ?: return@launch
            recoverActiveRecording(flight)
            if (!RecordingController.serviceHeartbeatIsFresh(activity, flight.id)) {
                RecordingController.resumeFromVisibleActivity(activity, flight.id)
            }
        }
    }

    fun startFlight(activity: Activity) = viewModelScope.launch {
        resetLiveRecordingState()
        liveStartedAt = System.currentTimeMillis()
        val id = dao.insertFlight(FlightEntity(startedAtMillis = liveStartedAt))
        when (val result = RecordingController.startFromVisibleActivity(activity, id)) {
            RecordingStartResult.Started -> _live.value = _live.value.copy(isRecording = true)
            is RecordingStartResult.Blocked -> { dao.finishRecording(id, System.currentTimeMillis(), "ABORTED"); refreshEnvironment() }
            is RecordingStartResult.Failed -> dao.finishRecording(id, System.currentTimeMillis(), "ABORTED")
        }
    }

    fun endFlight() {
        RecordingController.end(getApplication())
        viewModelScope.launch {
            val activeId = (RecordingRuntime.state.value as? RecordingState.Active)?.flightId
                ?: recoveredFlightId
                ?: dao.findRecoverableRecording(recoverableCutoffMillis())?.id
            if (activeId != null) {
                finishRecordingFromDatabase(activeId, System.currentTimeMillis())
                recoveredFlightId = null
                resetLiveRecordingState()
                _live.value = LiveUiState()
            }
        }
    }

    fun correctAirports(flightId: Long, origin: String?, destination: String?) = viewModelScope.launch {
        dao.correctAirports(flightId, origin?.trim()?.uppercase()?.ifBlank { null }, destination?.trim()?.uppercase()?.ifBlank { null })
    }

    fun deleteFlight(flightId: Long) = viewModelScope.launch { dao.deleteFlightById(flightId) }

    fun startSimulation(speed: Float, dropouts: Boolean, outliers: Boolean) {
        simulationJob?.cancel()
        simulationJob = viewModelScope.launch {
            _simulation.value = SimulationUiState(running = true, progress = 0f)
            liveProcessor.reset(); liveRoute.clear(); liveStartedAt = System.currentTimeMillis()
            val id = dao.insertFlight(FlightEntity(startedAtMillis = liveStartedAt, isSimulation = true, title = "Simulation Lab", inferredOriginIdent = "BRU", inferredDestinationIdent = "LHR"))
            simulationFlightId = id
            val processor = FlightProcessor()
            val count = 181
            FlightSimulator.generate(SimulationScenario(seed = 7, sampleCount = count)).forEachIndexed { index, raw ->
                var adjusted = raw
                if (!dropouts && raw.latitudeDeg == null) adjusted = raw.copy(latitudeDeg = 51.2, longitudeDeg = 2.0)
                if (!outliers && (raw.groundSpeedMps ?: 0.0) > 500) adjusted = raw.copy(groundSpeedMps = 240.0, latitudeDeg = 51.2, longitudeDeg = 2.0, gpsAltitudeM = 10_000.0)
                val processed = processor.process(adjusted)
                dao.insertSample(processed.toEntity(id))
                updateLiveDomain(processed)
                _simulation.value = SimulationUiState(true, (index + 1f) / count)
                delay((10_000 / speed.coerceIn(5f, 120f)).toLong())
            }
            dao.finishRecording(id, System.currentTimeMillis())
            _live.value = _live.value.copy(isRecording = false, flightPhase = "Simulation complete")
            _simulation.value = SimulationUiState(running = false, progress = 1f)
            simulationFlightId = null
        }
    }

    fun stopSimulation() {
        simulationJob?.cancel(); simulationJob = null
        simulationFlightId?.let { id -> viewModelScope.launch { dao.deleteFlightById(id) } }
        simulationFlightId = null
        _simulation.value = SimulationUiState(); _live.value = _live.value.copy(isRecording = false)
    }

    private fun updateLive(sample: RecordingSample) {
        val processed = liveProcessor.process(sample.toDomainRawSample())
        updateLiveDomain(processed)
    }

    private suspend fun recoverActiveRecording(
        knownFlight: FlightEntity? = null,
    ) {
        val flight = knownFlight ?: dao.findRecoverableRecording(recoverableCutoffMillis()) ?: return
        val sampleEntities = dao.getSamples(flight.id)
        val samples = sampleEntities.map { it.toDomain() }
        recoveredFlightId = flight.id
        resetLiveRecordingState()
        liveStartedAt = flight.startedAtMillis
        if (samples.isEmpty()) {
            _live.value = _live.value.copy(isRecording = true, elapsed = formatDuration(System.currentTimeMillis() - flight.startedAtMillis))
            return
        }
        liveProcessor.processAll(samples.map { it.raw })
        val stats = FlightStatisticsCalculator.calculate(samples)
        liveDistanceM = stats.distanceM
        liveMaxSpeedMps = stats.maximumGroundSpeedMps
        liveMaxAltitudeM = stats.maximumGpsAltitudeM
        samples.filter { it.acceptedForStatistics }.mapNotNullTo(liveRoute) { sample ->
            val lat = sample.raw.latitudeDeg
            val lon = sample.raw.longitudeDeg
            if (lat != null && lon != null) TrackPoint(lat, lon) else null
        }
        var currentDropout = 0
        samples.forEach { sample ->
            if (sample.raw.latitudeDeg == null || sample.raw.longitudeDeg == null) {
                currentDropout += 1
                liveLongestDropoutSamples = maxOf(liveLongestDropoutSamples, currentDropout)
            } else {
                currentDropout = 0
            }
        }
        val latest = samples.last()
        var recoveredInstruments = LiveUiState().instruments
        samples.forEach { recoveredInstruments = liveInstruments(it, recoveredInstruments) }
        val latestAccuracy = samples.asReversed().firstNotNullOfOrNull { it.raw.horizontalAccuracyM }?.roundToInt()
        val latestSatellites = samples.asReversed().firstNotNullOfOrNull { it.raw.satelliteCount }
        val latestPressure = samples.asReversed().firstNotNullOfOrNull { it.raw.pressureHpa }
        val latestCabinAltitude = samples.asReversed().firstNotNullOfOrNull { it.raw.estimatedCabinAltitudeM }
        val latestMotion = samples.asReversed().firstNotNullOfOrNull { it.raw.linearAccelerationRmsMps2 }
        _live.value = LiveUiState(
            isRecording = true,
            flightPhase = samples.asReversed().firstOrNull {
                it.raw.groundSpeedMps != null || it.raw.gpsAltitudeM != null
            }?.phase?.name?.lowercase()?.replaceFirstChar(Char::uppercase) ?: "Waiting for GNSS",
            origin = flight.correctedOriginIdent ?: flight.inferredOriginIdent ?: "—",
            destination = flight.correctedDestinationIdent ?: flight.inferredDestinationIdent ?: "—",
            elapsed = formatDuration((System.currentTimeMillis() - flight.startedAtMillis).coerceAtLeast(0)),
            distance = formatDistance(liveDistanceM),
            instruments = recoveredInstruments,
            route = liveRoute.toList(),
            accuracyMetres = latestAccuracy,
            satellites = latestSatellites,
            coveragePercent = (stats.gpsCoverageFraction * 100).roundToInt(),
            pressure = latestPressure?.let { "${it.roundToInt()} hPa" },
            cabinAltitude = latestCabinAltitude?.let { "${it.roundToInt()} m cabin pressure altitude" },
            turbulence = latestMotion?.let { formatPhoneMotion(it) } ?: "Unavailable",
            rawSamples = samples.size,
            acceptedSamples = stats.acceptedSamples,
            rejectedSamples = stats.rejectedSamples,
            averageSpeed = stats.averageGroundSpeedMps?.let { "${(it * 3.6).roundToInt()} km/h" } ?: "—",
            maxSpeed = liveMaxSpeedMps?.let { "${(it * 3.6).roundToInt()} km/h" } ?: "—",
            maxAltitude = liveMaxAltitudeM?.let { "${it.roundToInt()} m" } ?: "—",
            signalStatus = if (latest.raw.latitudeDeg == null) "GNSS unavailable • holding last values" else signalStatus((stats.gpsCoverageFraction * 100).roundToInt()),
            longestDropout = formatDropout(liveLongestDropoutSamples),
        )
    }

    private fun updateLiveDomain(processed: ProcessedFlightSample) {
        val raw = processed.raw
        if (processed.acceptedForStatistics && raw.latitudeDeg != null && raw.longitudeDeg != null) {
            liveRoute.addLast(TrackPoint(raw.latitudeDeg, raw.longitudeDeg))
        }
        if (processed.acceptedForStatistics) {
            liveDistanceM += processed.distanceFromPreviousM ?: 0.0
            raw.groundSpeedMps?.takeIf(Double::isFinite)?.let { liveMaxSpeedMps = maxOf(liveMaxSpeedMps ?: it, it) }
            raw.gpsAltitudeM?.takeIf(Double::isFinite)?.let { liveMaxAltitudeM = maxOf(liveMaxAltitudeM ?: it, it) }
        }
        if (raw.latitudeDeg == null || raw.longitudeDeg == null) {
            liveCurrentDropoutSamples += 1
            liveLongestDropoutSamples = maxOf(liveLongestDropoutSamples, liveCurrentDropoutSamples)
        } else {
            liveCurrentDropoutSamples = 0
        }
        val current = _live.value
        val rawCount = current.rawSamples + 1
        val elapsed = if (liveStartedAt == 0L) 0 else (System.currentTimeMillis() - liveStartedAt).coerceAtLeast(0)
        val coverage = (((current.coveragePercent * (rawCount - 1)) + if (raw.latitudeDeg != null) 100 else 0) / rawCount)
        val hasLiveGnss = raw.latitudeDeg != null || raw.groundSpeedMps != null || raw.gpsAltitudeM != null
        _live.value = current.copy(
            isRecording = true,
            flightPhase = if (hasLiveGnss) processed.phase.name.lowercase().replaceFirstChar(Char::uppercase) else current.flightPhase,
            elapsed = formatDuration(elapsed),
            distance = formatDistance(liveDistanceM),
            instruments = liveInstruments(processed, current.instruments),
            route = liveRoute.toList(),
            accuracyMetres = raw.horizontalAccuracyM?.roundToInt() ?: current.accuracyMetres,
            satellites = raw.satelliteCount ?: current.satellites,
            coveragePercent = coverage,
            pressure = raw.pressureHpa?.let { "${it.roundToInt()} hPa" } ?: current.pressure,
            cabinAltitude = raw.estimatedCabinAltitudeM?.let { "${it.roundToInt()} m cabin pressure altitude" } ?: current.cabinAltitude,
            turbulence = raw.linearAccelerationRmsMps2?.let { formatPhoneMotion(it) } ?: current.turbulence,
            rawSamples = rawCount,
            acceptedSamples = current.acceptedSamples + if (processed.acceptedForStatistics) 1 else 0,
            rejectedSamples = current.rejectedSamples + if (processed.acceptedForStatistics) 0 else 1,
            averageSpeed = raw.groundSpeedMps?.let { "${(it * 3.6).roundToInt()} km/h now" } ?: current.averageSpeed,
            maxSpeed = liveMaxSpeedMps?.let { "${(it * 3.6).roundToInt()} km/h" } ?: "—",
            maxAltitude = liveMaxAltitudeM?.let { "${it.roundToInt()} m" } ?: "—",
            signalStatus = if (hasLiveGnss) signalStatus(coverage) else "GNSS unavailable • holding last values",
            longestDropout = formatDropout(liveLongestDropoutSamples),
        )
    }

    private fun resetLiveRecordingState() {
        liveProcessor.reset()
        liveRoute.clear()
        liveDistanceM = 0.0
        liveMaxSpeedMps = null
        liveMaxAltitudeM = null
        liveLongestDropoutSamples = 0
        liveCurrentDropoutSamples = 0
    }

    private fun liveInstruments(
        processed: ProcessedFlightSample,
        previous: List<InstrumentValue> = emptyList(),
    ): List<InstrumentValue> {
        val raw = processed.raw
        val verticalMpm = processed.verticalSpeedMps?.times(60.0)
        val current = listOf(
            instrument(raw.groundSpeedMps?.times(3.6), "km/h", "Ground speed", 0),
            instrument(raw.gpsAltitudeM, "m", "GPS altitude", 0),
            verticalInstrument(verticalMpm),
            trackInstrument(raw.bearingDeg),
        )
        return current.mapIndexed { index, value ->
            if (value.confidence != ValueConfidence.UNAVAILABLE) value
            else previous.getOrNull(index)?.takeIf { it.confidence != ValueConfidence.UNAVAILABLE }
                ?.copy(confidence = ValueConfidence.ESTIMATED, hint = "last valid GNSS value")
                ?: value
        }
    }

    private fun verticalInstrument(valueMpm: Double?): InstrumentValue {
        if (valueMpm == null || !valueMpm.isFinite()) {
            return InstrumentValue("—", "m/min", "Vertical speed", ValueConfidence.UNAVAILABLE, "altitude trend")
        }
        if (abs(valueMpm) < 15.0) {
            return InstrumentValue("0", "m/min", "Vertical speed", ValueConfidence.ESTIMATED, "approximately level")
        }
        val signed = if (valueMpm > 0) "+${valueMpm.roundToInt()}" else valueMpm.roundToInt().toString()
        val trend = if (valueMpm > 0) "climb" else "descent"
        return InstrumentValue(signed, "m/min", "Vertical speed", ValueConfidence.ESTIMATED, trend)
    }

    private fun trackInstrument(bearingDeg: Double?): InstrumentValue {
        if (bearingDeg == null || !bearingDeg.isFinite()) return InstrumentValue("—", "°", "Track", ValueConfidence.UNAVAILABLE, "GNSS course")
        return InstrumentValue(bearingDeg.roundToInt().mod(360).toString(), "° ${compassPoint(bearingDeg)}", "Track", ValueConfidence.MEASURED, "direction over ground")
    }

    private fun formatDistance(distanceM: Double): String = String.format(Locale.US, "%.1f km", distanceM / 1000.0)

    private fun formatPhoneMotion(value: Double): String = when {
        value < .35 -> "Smooth phone motion"
        value < .85 -> "Light phone motion"
        value < 1.6 -> "Moderate phone motion"
        else -> "Rough phone motion"
    }

    private fun signalStatus(coverage: Int): String = when {
        coverage >= 90 -> "Excellent signal"
        coverage >= 70 -> "Usable signal"
        coverage >= 35 -> "Patchy signal"
        else -> "Weak or blocked"
    }

    private fun formatDropout(samples: Int): String = if (samples <= 0) "None" else formatDuration(samples * 1000L)

    private fun compassPoint(degrees: Double): String {
        val points = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
        return points[((degrees.mod(360.0) / 22.5) + 0.5).toInt() % points.size]
    }

    private suspend fun finishRecordingFromDatabase(flightId: Long, endedAtMillis: Long) {
        val flight = dao.getFlight(flightId) ?: return
        val samples = dao.getSamples(flightId).map { it.toDomain() }
        if (samples.isEmpty()) {
            dao.finishRecording(flightId, endedAtMillis, "ABORTED")
            return
        }
        val stats = FlightStatisticsCalculator.calculate(samples)
        val (origin, destination) = com.jettrail.app.domain.AirportInferenceEngine.inferRoute(samples, app.airports)
        dao.updateFlight(flight.copy(
            endedAtMillis = endedAtMillis,
            status = "COMPLETED",
            inferredOriginIdent = origin.airport?.ident,
            inferredDestinationIdent = destination.airport?.ident,
            distanceM = stats.distanceM,
            durationMillis = stats.durationMillis,
            averageGroundSpeedMps = stats.averageGroundSpeedMps,
            maximumGroundSpeedMps = stats.maximumGroundSpeedMps,
            maximumGpsAltitudeM = stats.maximumGpsAltitudeM,
            gpsCoverageFraction = stats.gpsCoverageFraction,
            rejectedSampleCount = stats.rejectedSamples,
        ))
    }

    private fun recoverableCutoffMillis(): Long = System.currentTimeMillis() - MAX_RECOVERABLE_RECORDING_AGE_MILLIS

    private fun instrument(value: Double?, unit: String, label: String, decimals: Int) = InstrumentValue(
        value = value?.let { if (decimals == 0) it.roundToInt().toString() else String.format(Locale.US, "%.1f", it) } ?: "—",
        unit = unit, label = label, confidence = if (value == null) ValueConfidence.UNAVAILABLE else ValueConfidence.MEASURED,
    )

    companion object {
        private const val MAX_RECOVERABLE_RECORDING_AGE_MILLIS = 18L * 60L * 60L * 1000L

        fun formatDuration(ms: Long): String {
            val seconds = ms / 1000
            return "%02d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
        }
    }
}
