package supabase.domain

import kotlinx.serialization.Serializable

@Serializable
data class Command(val id: String, val created_at: String, val owner: String, val command: String, val status: String)