package supabase.domain

import kotlinx.serialization.Serializable

@Serializable
data class Image(
    val rgb_path: String? = null,
    val thermal_path: String? = null,
    val location: LatLong,
    val flight_date: String
)

