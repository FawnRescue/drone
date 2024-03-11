package supabase.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class FlightDate(
    val id: String,
    val created_at: Instant,
    val mission: String,
    val start_date: Instant,
    val end_date: Instant,
    val aircraft: String,
)

