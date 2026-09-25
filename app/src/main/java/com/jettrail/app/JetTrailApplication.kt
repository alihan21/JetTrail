package com.jettrail.app

import android.app.Application
import com.jettrail.app.data.JetTrailDatabase
import com.jettrail.app.data.AirportCatalog
import com.jettrail.app.recording.ProcessingRecordingSink
import com.jettrail.app.recording.RecordingRuntime

class JetTrailApplication : Application() {
    val database by lazy { JetTrailDatabase.get(this) }
    val airports by lazy { AirportCatalog.load(this) }

    override fun onCreate() {
        super.onCreate()
        RecordingRuntime.installSink(ProcessingRecordingSink(database.flightDao(), airports))
    }
}
