import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.realtime.*
import io.mavsdk.System
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.forEach
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds

@Serializable
data class PresenceState(val username: String)

@OptIn(DelicateCoroutinesApi::class)
suspend fun main() {

    val supabase = createSupabaseClient(
        supabaseUrl = "https://irvsopidchmqfxbdpxqt.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImlydnNvcGlkY2htcWZ4YmRweHF0Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3MDE3MDI4NDgsImV4cCI6MjAxNzI3ODg0OH0.oaKgHBwqw5WsYhM1_nYNJKGyidmEkIO6GaqjEWtVHI8"

    ) {
        install(Realtime) {
            reconnectDelay = 5.seconds // Default: 7 seconds
        }
    }

    val roomOne = supabase.channel("command_test")
    val roomTwo = supabase.channel("command_test1")
    val roomThree = supabase.channel("command_test2")
    val presenceFlow: Flow<PresenceAction> = roomOne.presenceChangeFlow()

    println("Subscribing...")
    roomOne.subscribe(blockUntilSubscribed = true)
    roomTwo.subscribe(blockUntilSubscribed = true)
    roomThree.subscribe(blockUntilSubscribed = true)
    println("Subscribed!")
    roomOne.track(PresenceState(username = "John"))
    println("Collecting...")
    presenceFlow.collect {
        println("Leave: " + it.leaves)
        println("Join: " + it.joins)
    }
    println("Collected!")


    roomOne.broadcast("test", PresenceState(username = "test"))
    runBlocking {

        val drone = System()

        drone.telemetry.flightMode
        drone.action.arm()

    }
}