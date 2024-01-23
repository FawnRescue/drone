package drone

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
    fun startCommunicating() = CoroutineScope(Dispatchers.IO).launch {
        // Communication logic with MAVSDK
        // Report status and errors to the controller
        sleep(5000)
        println("The drone is ready to fly!")
        sendDataToBackend(DroneStatus(DroneState.IDLE, 100, "Berlin"))
    }

    fun executeCommand(command: String) {
        // Logic to execute commands on the drone
    }

    private suspend inline fun <reified T : Any> sendDataToBackend(data: T) {
        // Logic to send data directly to Supabase
        supabaseHandler.sendData(data)
    }
}
