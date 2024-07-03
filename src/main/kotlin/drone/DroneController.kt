package drone

import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import supabase.SupabaseMessageHandler
import supabase.domain.Command
import supabase.domain.CommandType
import supabase.domain.Image
import java.io.DataInputStream
import java.io.PrintWriter
import java.net.Socket
import java.util.*

class DroneController(
    token: String,
    supabase: SupabaseClient
) {
    private val imageQueue: Channel<ImagePacket> = Channel()
    private var flightDateID: String? = null

    private val supabaseHandler = SupabaseMessageHandler(token, supabase, {
        println("Send drone status!")
        gatherStatusUpdates()
        println("Upload images!")
        gatherImages()
    }, {
        when (it.command) {
            CommandType.CONTINUE -> continueMission(it)
            else -> mavsdkHandler.executeCommand(it)
        }
    })

    fun start() {
        val supabase = CoroutineScope(Dispatchers.IO).launch {
            supabaseHandler.startListening()
        }

        // Start MAVSDK communication
        val mavsdk = CoroutineScope(Dispatchers.IO).launch {
            mavsdkHandler.connectToDrone()
        }

        runBlocking {
            mavsdk.join()
            supabase.join()
            supabaseHandler.cleanup()
        }
    }

    private fun continueMission(it: Command) {
        CoroutineScope(Dispatchers.IO).launch {
            val flightPlan = supabaseHandler.getFlightPlan(it.context) ?: return@launch
            flightDateID = flightPlan.id
            mavsdkHandler.updateCheckpoints(flightPlan.checkpoints ?: emptyList()) //TODO: Handle checkpoints = null
        }
    }

    private fun gatherStatusUpdates() {
        CoroutineScope(Dispatchers.IO).launch {
            mavsdkHandler.produceStatusUpdates().consumeEach {
                supabaseHandler.sendDroneStatus(it)
            }
        }
    }

    private fun gatherImages() {
        CoroutineScope(Dispatchers.IO).launch {
            imageQueue.consumeEach {
                try {
                    supabaseHandler.uploadImage(
                        it
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    println("Error: Couldn't upload photo")
                }
            }
        }
    }

    private val mavsdkHandler = MavsdkHandler {
        CoroutineScope(Dispatchers.IO).launch {
            Thread.sleep(500)
            println("Photo")
            val name = UUID.randomUUID().toString()
            val images = captureImages() ?: return@launch
            val metadata = Image(
                thermal_path = if (images.thermalGray != null) "${name}-thermal.png" else null,
                rgb_path = if (images.rgbImage != null) "${name}-rgb.png" else null,
                binary_path = if (images.thermalFloat != null) "${name}-float.bin" else null,
                location = it,
                flight_date = flightDateID!!
            )
            imageQueue.send(ImagePacket(images, metadata))
        }

    }


    private fun captureImages(hostName: String = "127.0.0.1", portNumber: Int = 15555): ImagesData? {
        Socket(hostName, portNumber).use { socket ->
            PrintWriter(socket.getOutputStream(), true).use { out ->
                DataInputStream(socket.getInputStream()).use { dis ->
                    out.print("capture")
                    out.flush()
                    val status = dis.readInt()
                    if (status != 1) {
                        return null
                    }

                    var floatData: ByteArray? = null
                    try {
                        out.print("transferFloat")
                        out.flush()
                        val floatSize = dis.readInt()
                        floatData = ByteArray(floatSize)
                        dis.readFully(floatData)
                    } catch (_: Exception) {
                        println("Failed to retrieve Float data")
                    }

                    var thermalImageData: ByteArray? = null
                    try {
                        out.print("transferThermal")
                        out.flush()
                        val thermalImageSize = dis.readInt()
                        thermalImageData = ByteArray(thermalImageSize)
                        dis.readFully(thermalImageData)
                    } catch (_: Exception) {
                        println("Failed to retrieve thermal image data")
                    }

                    var rgbImageData: ByteArray? = null
                    try {
                        out.print("transferRGB")
                        out.flush()
                        val rgbImageSize = dis.readInt()
                        rgbImageData = ByteArray(rgbImageSize)
                        dis.readFully(rgbImageData)
                    } catch (_: Exception) {
                        println("Failed to retrieve rgb image data")
                    }

                    return ImagesData(floatData, thermalImageData, rgbImageData)
                }
            }
        }
    }
}
