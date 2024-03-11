package supabase.domain

import kotlinx.serialization.Serializable

@Serializable
data class Command(
    val id: String,
    val created_at: String,
    val owner: String,
    val aircraft: String,
    val command: CommandType,
    val status: CommandStatus,
    val context: String
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
    FLY2CHECKPOINT,
    CAPTURE_IMAGE,
    LOITER,
    RTH,
    KILL,
    ELAND,
    CONTINUE
}