package drone

import kotlinx.serialization.Serializable

@Serializable
data class DroneStatus(val state: DroneState, val battery: Int?, val location: String?)
