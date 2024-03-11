package drone

import supabase.SupabaseMessageHandler
import supabase.domain.CommandType

class DroneController() {
    private var currentState = DroneState.IDLE

    val supabaseHandler = SupabaseMessageHandler(this)
    val mavsdkHandler = MavsdkHandler(this, supabaseHandler)

    fun sendCommandToDrone(command: CommandType) {
        mavsdkHandler.executeCommand(command)
    }

    // Additional logic for controlling the drone and handling states
}
