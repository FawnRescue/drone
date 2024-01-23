package drone

import supabase.SupabaseMessageHandler

class DroneController {
    var currentState = DroneState.IDLE
        private set

    val supabaseHandler = SupabaseMessageHandler(this)
    val mavsdkHandler = MavsdkHandler(this, supabaseHandler)

    fun updateState(newState: DroneState) {
        currentState = newState
        // Send state update to Supabase
        supabaseHandler.sendDroneStatus(newState)
    }

    fun handleMavsdkError(error: String) {
        // Handle MAVSDK errors
    }

    fun sendCommandToDrone(command: String) {
        mavsdkHandler.executeCommand(command)
    }

    // Additional logic for controlling the drone and handling states
}
