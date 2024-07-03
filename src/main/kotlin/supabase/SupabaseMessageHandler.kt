package supabase

import drone.DroneStatus
import drone.ImagePacket
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.*
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import supabase.domain.*
import java.io.File

class SupabaseMessageHandler(
    private val token: String,
    private val supabase: SupabaseClient,
    val onConnected: () -> Unit,
    val onCommand: (Command) -> Unit
) {


    val channel: RealtimeChannel = supabase.channel(token)
    var isSubscribed = false


    suspend fun startListening() {
        println("Subscribing...")
        channel.subscribe(blockUntilSubscribed = true)
        supabase.realtime.status.collect {
            when (it) {
                Realtime.Status.DISCONNECTED -> {
                    isSubscribed = false
                    println(it)
                }

                Realtime.Status.CONNECTING -> println(it)
                Realtime.Status.CONNECTED -> {
                    isSubscribed = true
                    println("Subscribed!")
                    onConnected()
                    collectCommands()
                }
            }
        }
    }

    private suspend fun collectCommands() {
        val commandFlow = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "command"
        }
        println("Collect drone commands!")
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
                onCommand(command)
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

    suspend fun getFlightPlan(id: String): FlightPlan? {
        val flightDate: FlightDate = supabase.from("flightdate").select {
            filter {
                eq("id", id)
            }
        }.decodeSingle<FlightDate>()
        val mission: Mission = supabase.from("mission").select {
            filter {
                eq("id", flightDate.mission)
            }
        }.decodeSingle<Mission>()
        if (mission.plan == null) {
            return null
        }
        val flightPlan: FlightPlan = supabase.from("flightplan").select {
            filter {
                eq("id", mission.plan)
            }
        }.decodeSingle<FlightPlan>()
        return flightPlan
    }

    suspend fun uploadImage(imagesPacket: ImagePacket) {
        val bucket = supabase.storage.from("images")
        if (imagesPacket.images.rgbImage != null) {
            bucket.upload(imagesPacket.metadata.rgb_path ?: "", imagesPacket.images.rgbImage, upsert = false)
        }
        if (imagesPacket.images.thermalGray != null) {
            bucket.upload(imagesPacket.metadata.thermal_path ?: "", imagesPacket.images.thermalGray, upsert = false)
        }
        if (imagesPacket.images.thermalFloat != null) {
            bucket.upload(imagesPacket.metadata.binary_path ?: "", imagesPacket.images.thermalFloat, upsert = false)
        }
        supabase.postgrest.from("image").insert(imagesPacket.metadata)
    }

    suspend inline fun <reified T : Any> sendData(event: String, data: T) {
        // Logic to send arbitrary data to the backend
        while (!isSubscribed) {
            delay(100)
        }
        channel.broadcast(event, data)
    }

    suspend fun cleanup() {
        supabase.realtime.removeAllChannels()
    }
}
