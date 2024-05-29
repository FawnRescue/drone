package supabase.domain

import kotlinx.serialization.Serializable

@Serializable
data class Aircraft(
    val token: String,
    val owner: String,
    val name: String,
    val camera_fov: Float,
    val flight_height: Float,
    val description: String?,
    val created_at: String,
    val deleted: Boolean
)

@Serializable
data class InsertableAircraft(
    val token: String,
    val owner: String? = null,
    val name: String,
    val camera_fov: Float? = null,
    val flight_height: Float? = null,
    val description: String? = null,
    val created_at: String? = null,
    val deleted: Boolean? = null
)