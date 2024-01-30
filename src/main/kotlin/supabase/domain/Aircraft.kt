package supabase.domain

import kotlinx.serialization.Serializable

@Serializable
data class Aircraft(
    val token: String,
    val owner: String,
    val name: String,
    val description: String?,
    val created_at: String,
    val deleted: Boolean
)