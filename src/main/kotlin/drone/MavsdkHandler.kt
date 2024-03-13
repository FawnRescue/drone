package drone

import com.github.sarxos.webcam.Webcam
import credentials.ConfigManager
import io.mavsdk.System
import io.mavsdk.mission.Mission
import io.mavsdk.mission.Mission.MissionPlan
import io.mavsdk.telemetry.Telemetry.FlightMode
import io.mavsdk.telemetry.Telemetry.LandedState
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import supabase.SupabaseMessageHandler
import supabase.domain.Command
import supabase.domain.CommandType
import supabase.domain.Image
import supabase.domain.LatLong
import java.awt.image.BufferedImage
import java.io.DataInputStream
import java.io.PrintWriter
import java.lang.Thread.sleep
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import kotlin.math.*

class MavsdkHandler(private val controller: DroneController, private val supabaseHandler: SupabaseMessageHandler) {
    private var drone: System? = null // Make 'drone' nullable
    private var statusReadJob: Job? = null
    private val mutex = Mutex() // Add this line

    // Drone stats
    private var armed: Boolean? = null
    private var inAir: Boolean? = null
    private var flightMode: FlightMode? = null
    private var landedState: LandedState? = null

    private var battery: Battery? = null

    private var numSatellites: Int? = null
    private var location: Location? = null
    private var altitude: Float? = null

    private fun readDroneStatus() {
        statusReadJob = CoroutineScope(Dispatchers.IO).launch {
            drone?.telemetry?.armed?.subscribe({ armed = it }, { runBlocking { reconnect() } })
            drone?.telemetry?.battery?.subscribe({
                battery = Battery(
                    remainingPercent = if (it.remainingPercent?.isFinite() == true) it.remainingPercent else null,
                    voltage = it.voltageV
                )
            }, { runBlocking { reconnect() } })
            drone?.telemetry?.position?.subscribe({
                location = Location(it.longitudeDeg, it.latitudeDeg)
                altitude = it.relativeAltitudeM
            }, { runBlocking { reconnect() } })
            drone?.telemetry?.gpsInfo?.subscribe({
                numSatellites = it.numSatellites
            }, { runBlocking { reconnect() } })
            drone?.telemetry?.inAir?.subscribe({
                inAir = it
            }, { runBlocking { reconnect() } })
            drone?.telemetry?.landedState?.subscribe({
                landedState = it
            }, { runBlocking { reconnect() } })
            drone?.telemetry?.flightMode?.subscribe({
                flightMode = it
            }, { runBlocking { reconnect() } })
        }
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val radiusEarth = 6371000
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val originLat = Math.toRadians(lat1)
        val destinationLat = Math.toRadians(lat2)

        val a = sin(dLat / 2).pow(2) + sin(dLon / 2).pow(2) * cos(originLat) * cos(destinationLat)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return radiusEarth * c
    }

    fun decodeFloat2D(byteArray: ByteArray, numRows: Int, numCols: Int): Array<FloatArray> {
        val byteBuffer = ByteBuffer.wrap(byteArray)
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN) // Adjust if your data is big-endian

        val floatValues = mutableListOf<Float>()
        while (byteBuffer.hasRemaining()) {
            floatValues.add(byteBuffer.float)
        }

        val floatArray2D = Array(numRows) { FloatArray(numCols) }
        for (row in 0 until numRows) {
            for (col in 0 until numCols) {
                floatArray2D[row][col] = floatValues[row * numCols + col]
            }
        }

        return floatArray2D
    }

    fun startCommunicating() = CoroutineScope(Dispatchers.IO).launch {
        println("Connecting to ${ConfigManager.getDronePath()}:${ConfigManager.getDronePort()}")
        try {
            drone = System(ConfigManager.getDronePath(), ConfigManager.getDronePort())
        } catch (e: Exception) {
            println("Cant Connect!")
        }
        readDroneStatus()
        while (isActive) {
            val status = DroneStatus(
                state = when (armed) {
                    true -> when (inAir) {
                        true -> DroneState.IN_FLIGHT
                        false -> DroneState.ARMED
                        null -> DroneState.NOT_CONNECTED
                    }

                    false -> DroneState.IDLE
                    null -> DroneState.NOT_CONNECTED
                }, battery, location, altitude, numSatellites
            )
            sendDroneStatusToBackend(status)
            sleep(100)
        }
    }

    fun executeCommand(command: Command) {
        try {
            when (command.command) {
                CommandType.ARM -> drone?.action?.arm()?.blockingAwait()
                CommandType.DISARM -> drone?.action?.disarm()?.blockingAwait()
                CommandType.TAKEOFF -> drone?.action?.takeoff()?.blockingAwait()
                CommandType.LAND -> drone?.action?.land()?.blockingAwait()
                CommandType.FLY2CHECKPOINT -> TODO()
                CommandType.CAPTURE_IMAGE -> TODO()
                CommandType.LOITER -> drone?.action?.takeoff()?.blockingAwait()
                CommandType.RTH -> drone?.action?.returnToLaunch()?.blockingAwait()
                CommandType.KILL -> drone?.action?.kill()?.blockingAwait()
                CommandType.ELAND -> drone?.action?.land()?.blockingAwait()
                CommandType.CONTINUE -> CoroutineScope(Dispatchers.IO).launch { continueMission(command.context) }
            }
        } catch (e: Exception) {
            println("Error executing Command: $command,\n Error: $e")
        }
    }


    private suspend fun continueMission(flightDateID: String) {
        val acceptanceRadius = 0.5f

        var webcam: Webcam? = null
        try {
            webcam = Webcam.getDefault()
            webcam.open() // Open the webcam
        } catch (e: Exception) {
            println("Error: Couldn't open RGB Camera!")
        }

        val flightPlan = controller.supabaseHandler.getFlightPlan(flightDateID) ?: return
        val missionPlan = MissionPlan(flightPlan.checkpoints?.map {
            Mission.MissionItem(
                it.latitude,
                it.longitude,
                10f,
                5f,
                false,
                0f,
                0f,
                Mission.MissionItem.CameraAction.TAKE_PHOTO,
                2f,
                0.0,
                acceptanceRadius,
                0f,
                0f,
                Mission.MissionItem.VehicleAction.NONE
            )
        } ?: emptyList())
        drone?.mission?.uploadMission(missionPlan)?.blockingAwait()
        drone?.mission?.setReturnToLaunchAfterMission(true)?.blockingAwait()
        drone?.mission?.setCurrentMissionItem(0)?.blockingAwait() // TODO: use actual checkpoint
        drone?.mission?.startMission()?.blockingAwait()

        var currentCheckpoint = missionPlan.missionItems[0]
        var checkpointReached = false
        drone?.mission?.missionProgress?.subscribe {
            if (currentCheckpoint == missionPlan.missionItems[it.current]) {
                return@subscribe
            }
            currentCheckpoint = missionPlan.missionItems[it.current]
            checkpointReached = false
        }
        drone?.telemetry?.position?.subscribe {
            if (checkpointReached) {
                return@subscribe
            }
            val distanceM = haversine(
                currentCheckpoint.latitudeDeg, currentCheckpoint.longitudeDeg, it.latitudeDeg, it.longitudeDeg
            )
            if (distanceM < acceptanceRadius) {
                checkpointReached = true
                runBlocking {
                    println("Checkpoint Reached")
                    delay(1000)
                    println("Photo")

                    try {
                        val name = UUID.randomUUID().toString()

                        val imageThermal: BufferedImage? = captureThermalImage()
                        val imageRGB: BufferedImage? = webcam?.image

                        val imagMetaData = Image(
                            thermal_path = if (imageThermal != null) "${name}-thermal.png" else null,
                            rgb_path = if (imageRGB != null) "${name}-rgb.png" else null,
                            location = LatLong(
                                location?.latitude ?: currentCheckpoint.latitudeDeg,
                                location?.longitude ?: currentCheckpoint.longitudeDeg
                            ),
                            flight_date = flightDateID
                        )
                        controller.supabaseHandler.uploadImage(
                            dataRGB = imageRGB, dataThermal = imageThermal, image = imagMetaData
                        )

                    } catch (e: Exception) {
                        println("Error: Couldn't upload photo")
                    }
                }
            }
        }
    }

    private fun captureThermalImage(hostName: String = "127.0.0.1", portNumber: Int = 15555): BufferedImage? {
        Socket(hostName, portNumber).use { socket ->
            PrintWriter(socket.getOutputStream(), true).use { out ->
                DataInputStream(socket.getInputStream()).use { dis ->
                    out.print("capture")
                    out.flush()
                    val imageSize = 320 * 240 * 4 // 4 bytes per float
                    val imageData = ByteArray(imageSize)
                    dis.readFully(imageData)
                    try {
                        val array = decodeFloat2D(byteArray = imageData, 240, 320)
                        val height = array.size
                        val width = array[0].size
                        val image = BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY)

                        for (y in array.indices) {
                            for (x in array[y].indices) {
                                val color = (array[y][x] * 20).toInt()
                                val rgb = color shl 16 or (color shl 8) or color
                                image.setRGB(x, y, rgb)
                            }
                        }
                        return image
                    } catch (e: Exception) {
                        return null
                    }

                }
            }
        }
    }

    private suspend fun sendDroneStatusToBackend(data: DroneStatus) {
        // Logic to send data directly to Supabase
        supabaseHandler.sendDroneStatus(data)
    }

    private suspend fun reconnect() {
        // Try to acquire the lock without suspending. Proceed if successful, otherwise cancel the call.
        if (mutex.tryLock()) {
            try {
                println("Connection lost. Attempting to reconnect...")
                statusReadJob?.cancelAndJoin() // Cancel any running status reading
                drone = null // Clear out the old drone object

                // Assuming sleep is a suspend function from kotlinx.coroutines package
                // If not, replace with delay(100) which is the correct way to delay in coroutines
                delay(1000)

                drone = System(ConfigManager.getDronePath(), ConfigManager.getDronePort())
                readDroneStatus()
            } finally {
                // Always release the lock when done.
                mutex.unlock()
            }
        }
    }
}
