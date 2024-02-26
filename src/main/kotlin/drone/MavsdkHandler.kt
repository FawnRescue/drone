package drone

import credentials.ConfigManager
import io.mavsdk.System
import kotlinx.coroutines.*
import supabase.SupabaseMessageHandler
import java.lang.Thread.sleep

class MavsdkHandler(private val controller: DroneController, private val supabaseHandler: SupabaseMessageHandler) {
    private var drone: System? = null // Make 'drone' nullable
    private var statusReadJob: Job? = null

    // Drone stats
    private var armed: Boolean? = null

    private var battery: Battery? = null

    private var numSatellites: Int? = null
    private var location: Location? = null
    private var altitude: Float? = null

    private fun readDroneStatus() {
        statusReadJob = CoroutineScope(Dispatchers.IO).launch {
            drone?.telemetry?.armed?.doOnError { runBlocking { reconnect() } }?.forEach {
                armed = it
            }
            drone?.telemetry?.battery?.doOnError { runBlocking { reconnect() } }
                ?.forEach {
                    battery = Battery(
                        remainingPercent = if (it.remainingPercent?.isFinite() == true) it.remainingPercent else -1f,
                        voltage = it.voltageV
                    )
                }
            drone?.telemetry?.position?.doOnError { runBlocking { reconnect() } }
                ?.forEach {
                    location = Location(it.longitudeDeg, it.latitudeDeg)
                    altitude = it.relativeAltitudeM
                }
            drone?.telemetry?.gpsInfo?.doOnError { runBlocking { reconnect() } }
                ?.forEach {
                    numSatellites = it.numSatellites
                }

        }
    }

    fun startCommunicating() = CoroutineScope(Dispatchers.IO).launch {
        drone = System(ConfigManager.getDronePath(), ConfigManager.getDronePort())
        readDroneStatus()
        drone?.action?.arm()?.blockingAwait()
        while (isActive) {
            val status = DroneStatus(
                state = when (armed) {
                    true -> DroneState.IN_FLIGHT
                    false -> DroneState.IDLE
                    null -> DroneState.NOT_CONNECTED
                },
                battery,
                location,
                altitude,
                numSatellites
            )
            sendDroneStatusToBackend(status)
            sleep(100)
        }
    }

    fun executeCommand(command: String) {
        // Logic to execute commands on the drone
    }

    private suspend fun sendDroneStatusToBackend(data: DroneStatus) {
        // Logic to send data directly to Supabase
        supabaseHandler.sendDroneStatus(data)
    }

    private suspend fun reconnect() {
        println("Connection lost. Attempting to reconnect...")
        statusReadJob?.cancelAndJoin() // Cancel any running status reading
        drone = null // Clear out the old drone object

        drone = System(ConfigManager.getDronePath(), ConfigManager.getDronePort())
        readDroneStatus()
    }
}
