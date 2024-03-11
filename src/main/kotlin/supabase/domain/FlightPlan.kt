package supabase.domain

import kotlinx.serialization.Serializable

@Serializable
data class LatLong(val latitude: Double = 0.0, val longitude: Double = 0.0)
@Serializable
data class FlightPlan(
    val id: String,
    val checkpoints: List<LatLong>?,
    val boundary: List<LatLong>,
    val location: LatLong,
    val created_at: String,
)