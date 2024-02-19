package drone

import credentials.ConfigManager
import io.mavsdk.System
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import supabase.SupabaseMessageHandler
import java.lang.Thread.sleep

class MavsdkHandler(private val controller: DroneController, private val supabaseHandler: SupabaseMessageHandler) {
    private var drone: System? = null // Make 'drone' nullable
    private var statusReadJob: Job? = null
    private var armed: Boolean? = null
    private var battery: Float? = null
    private var location: String? = null

    private fun readDroneStatus() {
        statusReadJob = CoroutineScope(Dispatchers.IO).launch {
            drone?.telemetry?.armed?.doOnError { runBlocking { reconnect() } }?.forEach { armed = it }
            drone?.telemetry?.battery?.doOnError { runBlocking { reconnect() } }
                ?.forEach { battery = it.remainingPercent }
            drone?.telemetry?.position?.doOnError { runBlocking { reconnect() } }
                ?.forEach { location = "\n${it.latitudeDeg},\n ${it.longitudeDeg}" }

        }
    }

    fun startCommunicating() = CoroutineScope(Dispatchers.IO).launch {
        drone = System(ConfigManager.getDronePath(), ConfigManager.getDronePort())
        readDroneStatus()

        while (isActive) {
            val status = DroneStatus(
                state = when (armed) {
                    true -> DroneState.IN_FLIGHT
                    false -> DroneState.IDLE
                    null -> DroneState.NOT_CONNECTED
                },
                battery = if (battery?.isFinite() == true) battery else null,
                location = location
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
