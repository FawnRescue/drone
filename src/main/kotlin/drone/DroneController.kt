package drone

import credentials.CredentialManager
import supabase.SupabaseMessageHandler

class DroneController(val credentials: CredentialManager) {
    private var currentState = DroneState.IDLE

    val supabaseHandler = SupabaseMessageHandler(this)
    val mavsdkHandler = MavsdkHandler(this, supabaseHandler)

    suspend fun updateState(newState: DroneState) {
        currentState = newState
        // Send state update to Supabase
        supabaseHandler.sendDroneState(newState)
    }

    fun handleMavsdkError(error: String) {
        // Handle MAVSDK errors
    }

    fun sendCommandToDrone(command: String) {
        mavsdkHandler.executeCommand(command)
    }

    // Additional logic for controlling the drone and handling states
}
