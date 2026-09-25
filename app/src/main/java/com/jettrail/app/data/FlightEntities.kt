package com.jettrail.app.data

import androidx.room.*

@Entity(tableName = "flights", indices = [Index("startedAtMillis"), Index("status")])
data class FlightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAtMillis: Long,
    val endedAtMillis: Long? = null,
    /** RECORDING, COMPLETED, or RECOVERED; a RECORDING row makes interrupted sessions recoverable. */
    val status: String = "RECORDING",
    val title: String? = null,
    val inferredOriginIdent: String? = null,
    val inferredDestinationIdent: String? = null,
    val correctedOriginIdent: String? = null,
    val correctedDestinationIdent: String? = null,
    val distanceM: Double = 0.0,
    val durationMillis: Long = 0,
    val averageGroundSpeedMps: Double? = null,
    val maximumGroundSpeedMps: Double? = null,
    val maximumGpsAltitudeM: Double? = null,
    val gpsCoverageFraction: Double = 0.0,
    val rejectedSampleCount: Int = 0,
    val isSimulation: Boolean = false,
)

@Entity(
    tableName = "samples",
    foreignKeys = [ForeignKey(
        entity = FlightEntity::class,
        parentColumns = ["id"],
        childColumns = ["flightId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["flightId", "timestampMillis"]), Index("flightId")],
)
data class FlightSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val flightId: Long,
    val timestampMillis: Long,
    // Raw measurements are always retained.
    val latitudeDeg: Double?,
    val longitudeDeg: Double?,
    val groundSpeedMps: Double?,
    val bearingDeg: Double?,
    val gpsAltitudeM: Double?,
    val horizontalAccuracyM: Double?,
    val verticalAccuracyM: Double?,
    val satelliteCount: Int?,
    val pressureHpa: Double?,
    val estimatedCabinAltitudeM: Double?,
    val linearAccelerationRmsMps2: Double?,
    val elapsedRealtimeNanos: Long? = null,
    val locationElapsedRealtimeNanos: Long? = null,
    val isNewLocationFix: Boolean = false,
    val speedAccuracyMps: Double? = null,
    val bearingAccuracyDeg: Double? = null,
    val satellitesVisible: Int? = null,
    val satellitesUsedInFix: Int? = null,
    val turbulencePeakMps2: Double? = null,
    val handheldMotionLikely: Boolean = false,
    val locationProvider: String? = null,
    val locationProvenance: String,
    val pressureProvenance: String,
    val isSimulated: Boolean,
    // Processing annotations control visible summaries without destroying raw evidence.
    val acceptedForStatistics: Boolean,
    val rejectionReasonsCsv: String,
    val distanceFromPreviousM: Double?,
    val verticalSpeedMps: Double?,
    val phase: String,
    val turbulence: String,
)

@Entity(tableName = "earned_badges")
data class EarnedBadgeEntity(
    @PrimaryKey val badgeId: String,
    val earnedAtMillis: Long,
)

data class FlightWithSamples(
    @Embedded val flight: FlightEntity,
    @Relation(parentColumn = "id", entityColumn = "flightId") val samples: List<FlightSampleEntity>,
)
