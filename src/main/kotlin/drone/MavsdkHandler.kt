package drone

import credentials.ConfigManager
import io.mavsdk.System
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import supabase.SupabaseMessageHandler
import java.lang.Thread.sleep

class MavsdkHandler(private val controller: DroneController, private val supabaseHandler: SupabaseMessageHandler) {
    private var drone: System? = null // Make 'drone' nullable
    private var statusReadJob: Job? = null
    private val mutex = Mutex() // Add this line

    // Drone stats
    private var armed: Boolean? = null

    private var battery: Battery? = null

    private var numSatellites: Int? = null
    private var location: Location? = null
    private var altitude: Float? = null

    private fun readDroneStatus() {
        statusReadJob = CoroutineScope(Dispatchers.IO).launch {
            drone?.telemetry?.armed?.subscribe(
                { armed = it },
                { runBlocking { reconnect() } })
            drone?.telemetry?.battery?.subscribe(
                {
                    battery = Battery(
                        remainingPercent = if (it.remainingPercent?.isFinite() == true) it.remainingPercent else null,
                        voltage = it.voltageV
                    )
                },
                { runBlocking { reconnect() } })
            drone?.telemetry?.position?.subscribe(
                {
                    location = Location(it.longitudeDeg, it.latitudeDeg)
                    altitude = it.relativeAltitudeM
                },
                { runBlocking { reconnect() } })
            drone?.telemetry?.gpsInfo?.subscribe(
                {
                    numSatellites = it.numSatellites
                },
                { runBlocking { reconnect() } })
        }
    }

    fun startCommunicating() = CoroutineScope(Dispatchers.IO).launch {
        println("Connecting to ${ConfigManager.getDronePath()}:${ConfigManager.getDronePort()}")
        try {
            drone = System(ConfigManager.getDronePath(), ConfigManager.getDronePort())
        } catch (e: Exception) {
            println("Cant Connect!")
        }
        readDroneStatus()
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
        // Try to acquire the lock without suspending. Proceed if successful, otherwise cancel the call.
        if (mutex.tryLock()) {
            try {
                println("Connection lost. Attempting to reconnect...")
                statusReadJob?.cancelAndJoin() // Cancel any running status reading
                drone = null // Clear out the old drone object

                // Assuming sleep is a suspend function from kotlinx.coroutines package
                // If not, replace with delay(100) which is the correct way to delay in coroutines
                delay(100)

                drone = System(ConfigManager.getDronePath(), ConfigManager.getDronePort())
                readDroneStatus()
            } finally {
                // Always release the lock when done.
                mutex.unlock()
            }
        }
    }
}
