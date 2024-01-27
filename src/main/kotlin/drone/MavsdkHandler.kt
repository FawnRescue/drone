package drone

import io.mavsdk.System
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import supabase.SupabaseMessageHandler
import java.lang.Thread.sleep

class MavsdkHandler(private val controller: DroneController, private val supabaseHandler: SupabaseMessageHandler) {
    private var drone = System("172.19.40.205", 50051)
    private var armed: Boolean? = null
    private var battery: Float? = null
    private var location: String? = null

    private fun readDroneStatus() = CoroutineScope(Dispatchers.IO).launch {
        drone.telemetry.armed.doOnError { reconnect() }.forEach {
            armed = it
        }
        drone.telemetry.battery.doOnError { reconnect() }.forEach {
            battery = it.remainingPercent
        }
        drone.telemetry.position.doOnError { reconnect() }.forEach {
            location = "\n${it.latitudeDeg.toFloat()},\n ${it.longitudeDeg.toFloat()}"
        }
    }

    fun startCommunicating() = CoroutineScope(Dispatchers.IO).launch {
        readDroneStatus()
        while (true) {
            val status = DroneStatus(
                state = when (armed) {
                    true -> DroneState.IN_FLIGHT
                    false -> DroneState.IDLE
                    null -> DroneState.NOT_CONNECTED
                },
                battery = battery,
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

    private fun reconnect() {
        println("Connection lost. Attempting to reconnect...")
        // Reinitialize the drone object or perform necessary steps to reconnect
        drone = System("172.19.40.205", 50051)
        readDroneStatus()
    }
}
