package com.jettrail.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FlightDao {
    @Insert suspend fun insertFlight(flight: FlightEntity): Long
    @Update suspend fun updateFlight(flight: FlightEntity)
    @Insert suspend fun insertSample(sample: FlightSampleEntity): Long
    @Insert suspend fun insertSamples(samples: List<FlightSampleEntity>)

    @Query("SELECT * FROM flights ORDER BY startedAtMillis DESC")
    fun observeFlights(): Flow<List<FlightEntity>>

    @Query("SELECT * FROM flights WHERE id = :flightId")
    suspend fun getFlight(flightId: Long): FlightEntity?

    @Transaction
    @Query("SELECT * FROM flights WHERE id = :flightId")
    fun observeFlight(flightId: Long): Flow<FlightWithSamples?>

    @Transaction
    @Query("SELECT * FROM flights WHERE id = :flightId")
    suspend fun getFlightWithSamples(flightId: Long): FlightWithSamples?

    @Query("SELECT * FROM samples WHERE flightId = :flightId ORDER BY timestampMillis")
    suspend fun getSamples(flightId: Long): List<FlightSampleEntity>

    @Query("SELECT * FROM flights WHERE status = 'RECORDING' AND startedAtMillis >= :oldestStartedAtMillis ORDER BY startedAtMillis DESC LIMIT 1")
    suspend fun findRecoverableRecording(oldestStartedAtMillis: Long): FlightEntity?

    @Query("UPDATE flights SET status = :status, endedAtMillis = :endedAtMillis WHERE id = :flightId")
    suspend fun finishRecording(flightId: Long, endedAtMillis: Long, status: String = "COMPLETED")

    @Query("UPDATE flights SET correctedOriginIdent = :origin, correctedDestinationIdent = :destination WHERE id = :flightId")
    suspend fun correctAirports(flightId: Long, origin: String?, destination: String?)

    @Delete suspend fun deleteFlight(flight: FlightEntity)

    @Query("DELETE FROM flights WHERE id = :flightId")
    suspend fun deleteFlightById(flightId: Long)
}

@Dao
interface BadgeDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(badge: EarnedBadgeEntity)
    @Query("SELECT * FROM earned_badges ORDER BY earnedAtMillis") fun observeAll(): Flow<List<EarnedBadgeEntity>>
}
