package supabase

import drone.DroneController
import drone.DroneState
import drone.DroneStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.decodeIfNotEmptyOrDefault
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.OtpType
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import supabase.domain.Aircraft
import supabase.domain.Command
import supabase.domain.CommandStatus
import supabase.domain.InsertableAircraft
import java.io.File
import javax.management.Query.eq
import kotlin.time.Duration.Companion.seconds

class SupabaseMessageHandler(private val controller: DroneController) {
    private val supabase = createSupabaseClient(
        supabaseUrl = "https://irvsopidchmqfxbdpxqt.supabase.co",   // TODO Use environment variables
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImlydnNvcGlkY2htcWZ4YmRweHF0Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3MDE3MDI4NDgsImV4cCI6MjAxNzI3ODg0OH0.oaKgHBwqw5WsYhM1_nYNJKGyidmEkIO6GaqjEWtVHI8" // TODO Use environment variables
    ) {
        install(Realtime) {
            reconnectDelay = 5.seconds
        }
        install(Postgrest)
        install(Auth)
    } // TODO Move to constructor
    private val tokenFile = File("token")
    private lateinit var token: String
    lateinit var channel: RealtimeChannel
    var isSubscribed = false
    val authFlow = supabase.auth.sessionStatus

    suspend fun login(otp: String, email: String, token: String) {
        this.token = token
        supabase.auth.verifyEmailOtp(type = OtpType.Email.MAGIC_LINK, email = email, token = otp)
    }

    suspend fun debugLogin(debugPassword: String, debugEmail: String, token: String) {
        this.token = token
        supabase.auth.signInWith(Email) {
            email = debugEmail
            password = debugPassword
        }
    }

    suspend fun setup(): Boolean {
        if (!tokenFile.exists()) {
            tokenFile.writeText(token)
        } else {
            token = tokenFile.readText()
        }
        if (!checkDatabase()) {
            return false
        }

        channel = supabase.channel(token)
        return true
    }

    private suspend fun checkDatabase(): Boolean {
        val aircraft: List<Aircraft> = supabase.from("aircraft").select {
            filter {
                eq("token", token)
            }
        }.decodeList<Aircraft>()
        if (aircraft.isEmpty()) {
            supabase.from("aircraft")
                .insert(InsertableAircraft(name = "Aircraft-${token.subSequence(0, 4)}", token = token))
        } else if (aircraft.first().deleted) {
            supabase.auth.signOut()
            tokenFile.delete()
            return false
        }
        return true
    }

    fun startListening() = CoroutineScope(Dispatchers.IO).launch {
        // Logic to listen to Supabase messages
        println("Subscribing...")
        val commandFlow = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "command"
        }
        channel.subscribe(blockUntilSubscribed = true)
        isSubscribed = true
        println("Subscribed!")
        commandFlow.collect {
            try {
                val command = Json.decodeFromJsonElement<Command>(it.record)
                if (command.aircraft != token) {
                    return@collect
                }
                if (command.status != CommandStatus.PENDING) {
                    return@collect
                }
                println("Received command: ${command.command}")
                controller.sendCommandToDrone(command.command)
                supabase.from("command").update({
                    set("status", CommandStatus.EXECUTED)
                }
                ) {
                    filter {
                        eq("id", command.id)
                    }
                }
            } catch (e: Exception) {
                println("Error executing command: ${it.record}")
            }
        }
    }

    suspend fun sendDroneStatus(status: DroneStatus) {
        // Logic to send drone status to Supabase
        sendData("aircraft_status", status)
    }

    fun getMission(id: String) {
        /*val aircraft: List<> = supabase.from("").select {
            filter {
                eq("token", token)
            }
        }.decodeList<Aircraft>()*/
    }

    suspend inline fun <reified T : Any> sendData(event: String, data: T) {
        // Logic to send arbitrary data to the backend
        while (!isSubscribed) {
            yield()
            delay(100)
        }
        channel.broadcast(event, data)
    }

    suspend fun cleanup() {
        supabase.realtime.removeAllChannels()
    }
}
