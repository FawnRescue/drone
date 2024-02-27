package supabase.domain

import kotlinx.serialization.Serializable

@Serializable
data class Command(
    val id: String,
    val created_at: String,
    val owner: String,
    val aircraft: String,
    val command: CommandType,
    val status: CommandStatus
)

enum class CommandStatus {
    PENDING,
    EXECUTED,
    FAILED
}

enum class CommandType {
    ARM,
    DISARM,
    TAKEOFF,
    LAND,
    RETURN
}