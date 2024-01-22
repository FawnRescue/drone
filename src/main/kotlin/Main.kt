import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.realtime.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds
import io.mavsdk.System
import io.mavsdk.mission.Mission.MissionItem

@OptIn(DelicateCoroutinesApi::class)
fun main() {

    val supabase = createSupabaseClient(
        supabaseUrl = "https://irvsopidchmqfxbdpxqt.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImlydnNvcGlkY2htcWZ4YmRweHF0Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3MDE3MDI4NDgsImV4cCI6MjAxNzI3ODg0OH0.oaKgHBwqw5WsYhM1_nYNJKGyidmEkIO6GaqjEWtVHI8"

    ) {
        install(Realtime) {
            reconnectDelay = 5.seconds // Default: 7 seconds
        }
    }

    val roomOne = supabase.channel("command_test")
    val presenceFlow: Flow<PresenceAction> = roomOne.presenceChangeFlow()
    val flow = presenceFlow
        .onEach {
            println(it.joins) //You can also use it.decodeJoinsAs<YourType>()
            println(it.leaves) //You can also use it.decodeLeavesAs<YourType>()
        }


    runBlocking {
        val drone = System()

        drone.telemetry.flightMode
        drone.action.arm()

    }
}