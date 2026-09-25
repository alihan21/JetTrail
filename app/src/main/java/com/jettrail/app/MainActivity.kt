package com.jettrail.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val viewModel: JetTrailViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val live by viewModel.live.collectAsStateWithLifecycle()
            val flights by viewModel.flights.collectAsStateWithLifecycle()
            val explorer by viewModel.explorer.collectAsStateWithLifecycle()
            val simulation by viewModel.simulation.collectAsStateWithLifecycle()
            val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
                viewModel.refreshEnvironment()
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    viewModel.startFlight(this)
                }
            }
            JetTrailApp(live, flights, explorer, simulation, JetTrailCallbacks(
                onStartFlight = {
                    val missingPermissions = buildList {
                        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            add(Manifest.permission.ACCESS_FINE_LOCATION)
                            add(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                        if (Build.VERSION.SDK_INT >= 33 &&
                            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    if (missingPermissions.isEmpty()) viewModel.startFlight(this)
                    else permissionLauncher.launch(missingPermissions.toTypedArray())
                },
                onEndFlight = viewModel::endFlight,
                onLocationSettings = { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                onPermissionRequest = { permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
                onBatterySettings = { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
                onCorrectAirports = viewModel::correctAirports,
                onDeleteFlight = viewModel::deleteFlight,
                onStartSimulation = viewModel::startSimulation,
                onStopSimulation = viewModel::stopSimulation,
            ))
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppResumed(this)
    }
}
