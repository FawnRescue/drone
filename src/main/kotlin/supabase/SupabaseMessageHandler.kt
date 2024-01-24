package supabase

import drone.DroneController
import drone.DroneState
import drone.DroneStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.realtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.lang.Thread.sleep
import kotlin.time.Duration.Companion.seconds

class SupabaseMessageHandler(private val controller: DroneController) {
    private val supabase = createSupabaseClient(
        supabaseUrl = "https://irvsopidchmqfxbdpxqt.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImlydnNvcGlkY2htcWZ4YmRweHF0Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3MDE3MDI4NDgsImV4cCI6MjAxNzI3ODg0OH0.oaKgHBwqw5WsYhM1_nYNJKGyidmEkIO6GaqjEWtVHI8"

    ) {
        install(Realtime) {
            reconnectDelay = 5.seconds
        }
    }
    val channel = supabase.channel("channel_test")
    var isSubscribed = false

    fun startListening() = CoroutineScope(Dispatchers.IO).launch {
        // Logic to listen to Supabase messages
        println("Subscribing...")
        channel.subscribe(blockUntilSubscribed = true)
        isSubscribed = true
        println("Subscribed!")
    }

    suspend fun sendDroneStatus(status: DroneStatus) {
        // Logic to send drone status to Supabase
        sendData(status)
    }

    suspend fun sendDroneState(status: DroneState) {
        // Logic to send drone status to Supabase
        sendData(status)
    }

    suspend inline fun <reified T : Any> sendData(data: T) {
        // Logic to send arbitrary data to the backend
        while (!isSubscribed) {
            delay(100)
        }
        channel.broadcast("event", data)
    }

    suspend fun cleanup() {
        supabase.realtime.removeAllChannels()
    }
}
