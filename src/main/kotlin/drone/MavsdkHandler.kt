package drone

import GeoTIFFReader
import credentials.ConfigManager
import io.mavsdk.System
import io.mavsdk.mission.Mission.MissionItem
import io.mavsdk.telemetry.Telemetry.FlightMode
import io.mavsdk.telemetry.Telemetry.LandedState
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import supabase.SupabaseMessageHandler
import supabase.domain.Command
import supabase.domain.CommandType
import supabase.domain.Image
import supabase.domain.LatLong
import java.io.DataInputStream
import java.io.PrintWriter
import java.lang.Thread.sleep
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import kotlin.math.*


class MavsdkHandler(private val controller: DroneController, private val supabaseHandler: SupabaseMessageHandler) {
    private val tiffPath = "height_data.tif"
    private var heightReader: GeoTIFFReader? = null
    private var drone: System? = null // Make 'drone' nullable
    private var statusReadJob: Job? = null
    private var statusSendJob: Job? = null
    private val mutex = Mutex() // Add this line

    // Drone stats
    private var armed: Boolean? = null
    private var inAir: Boolean? = null
    private var flightMode: FlightMode? = null
    private var landedState: LandedState? = null

    private var battery: Battery? = null

    private var numSatellites: Int? = null
    private var currentMissionItem: Int? = null
    private var numMissionItems: Int? = null
    private var location: Location? = null
    private var homeLocation: Location? = null
    private var altitude: Float? = null
    private var heading: Double? = null

    private var missionPlan: List<Checkpoint> = emptyList()
    private var currentCheckpoint: Checkpoint? = null
    private var checkpointReached: Boolean = false
    private var flightDateID: String? = null
    private var homeAltitude: Float? = null

    private var idleCounter: Int = 0

    init {
        try {
            heightReader = GeoTIFFReader(tiffPath)
        } catch (_: Exception) {
            println("Exception initializing heightReader from $tiffPath")
        }
    }

    private suspend fun startReadDroneStatusJob() {
        statusReadJob?.cancelAndJoin()
        statusReadJob = CoroutineScope(Dispatchers.IO).launch {
            drone?.telemetry?.armed?.subscribe({
                armed = it
                if (!it) {
                    currentMissionItem = null
                    numMissionItems = null
                }
            }, { runBlocking { reconnect() } })
            drone?.telemetry?.battery?.subscribe({
                battery = Battery(
                    remainingPercent = if (it.remainingPercent?.isFinite() == true) it.remainingPercent else null,
                    voltage = it.voltageV
                )
            }, { runBlocking { reconnect() } })
            drone?.telemetry?.position?.subscribe({
                location = Location(it.longitudeDeg, it.latitudeDeg)
                altitude = it.relativeAltitudeM
                if (currentCheckpoint == null || checkpointReached) {
                    return@subscribe
                }
                currentCheckpoint?.let { checkpoint ->
                    val distanceM = haversine(
                        checkpoint.latitude, checkpoint.longitude, it.latitudeDeg, it.longitudeDeg
                    )
                    if (distanceM < checkpoint.acceptanceRadius) {
                        checkpointReached = true
                        CoroutineScope(Dispatchers.IO).launch {
                            println("Checkpoint Reached")
                            sleep(500)
                            println("Photo")

                            try {
                                val name = UUID.randomUUID().toString()

                                val images = captureImages()

                                val imagMetaData = Image(
                                    thermal_path = if (images?.thermalGray != null) "${name}-thermal.png" else null,
                                    rgb_path = if (images?.rgbImage != null) "${name}-rgb.png" else null,
                                    binary_path = if (images?.thermalFloat != null) "${name}-float.bin" else null,
                                    location = LatLong(
                                        location?.latitude ?: it.latitudeDeg,
                                        location?.longitude ?: it.longitudeDeg
                                    ),
                                    flight_date = flightDateID!!
                                )
                                controller.supabaseHandler.uploadImage(
                                    dataRGB = images?.rgbImage,
                                    dataThermal = images?.thermalGray,
                                    image = imagMetaData,
                                    dataFloat = images?.thermalFloat
                                )

                            } catch (e: Exception) {
                                e.printStackTrace()
                                println("Error: Couldn't upload photo")
                            }
                            currentMissionItem?.let { index ->
                                if (index == numMissionItems) {
                                    currentMissionItem = null
                                    currentCheckpoint = null
                                    numMissionItems = null
                                    drone?.action?.returnToLaunch()?.blockingAwait()
                                }
                                currentMissionItem = index + 1
                                currentCheckpoint = missionPlan[index]
                                checkpointReached = false
                                drone?.action?.setCurrentSpeed(checkpoint.speedMS)?.blockingAwait()
                                currentCheckpoint?.let { checkpoint ->
                                    drone?.action?.gotoLocation(
                                        checkpoint.latitude,
                                        checkpoint.longitude,
                                        checkpoint.absoluteHeight,
                                        checkpoint.yawDeg
                                    )?.blockingAwait()
                                }
                            }
                        }
                    }
                }


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
            drone?.telemetry?.home?.subscribe({
                homeLocation = Location(it.longitudeDeg, it.latitudeDeg)
                homeAltitude = it.absoluteAltitudeM
            }, { runBlocking { reconnect() } })
            drone?.telemetry?.heading?.subscribe({
                heading = it.headingDeg
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

    private fun decodeFloat2D(byteArray: ByteArray, numRows: Int, numCols: Int): Array<FloatArray> {
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
        startReadDroneStatusJob()
    }

    suspend fun startSendDroneStatusJob() {
        statusSendJob?.cancelAndJoin()
        statusSendJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    val status = DroneStatus(
                        state = when (armed) {
                            true -> when (inAir) {
                                true -> DroneState.IN_FLIGHT
                                false -> DroneState.ARMED
                                null -> DroneState.NOT_CONNECTED
                            }

                            false -> DroneState.IDLE
                            null -> DroneState.NOT_CONNECTED
                        },
                        battery,
                        location,
                        homeLocation,
                        altitude,
                        numSatellites,
                        currentMissionItem,
                        numMissionItems,
                        heading
                    )
                    if (idleCounter == 0) {
                        sendDroneStatusToBackend(status)
                    }
                    if (status.state == DroneState.IDLE) {
                        if (idleCounter >= 10) {
                            idleCounter = 0
                        } else {
                            idleCounter++
                        }
                    } else {
                        idleCounter = 0
                    }
                    sleep(300)
                } catch (e: Exception) {
                    println("Error: Cant send status to Supabase!")
                    sleep(500)
                }
            }
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
        this.flightDateID = flightDateID
        val acceptanceRadius = 0.5f
        val missionHeight = 15f
        val flightPlan = controller.supabaseHandler.getFlightPlan(flightDateID) ?: return
        missionPlan = flightPlan.checkpoints?.map {
            var height = homeAltitude ?: 0f
            heightReader?.let { reader ->
                try {
                    height = reader.getHeightAtCoordinates(it.longitude, it.latitude).toFloat()
                } catch (_: Exception) {
                    println("Error reading height: $it")
                }
            }
            height += missionHeight

            Checkpoint(
                latitude = it.latitude,
                longitude = it.longitude,
                yawDeg = 0f,
                absoluteHeight = missionHeight,
                speedMS = 10f,
                acceptanceRadius = acceptanceRadius
            )
        } ?: emptyList()

        currentMissionItem = 0
        numMissionItems = missionPlan.size
        currentCheckpoint = missionPlan[0]
        checkpointReached = false
        currentCheckpoint?.let { checkpoint ->
            drone?.action?.setCurrentSpeed(checkpoint.speedMS)?.blockingAwait()
            drone?.action?.gotoLocation(
                checkpoint.latitude,
                checkpoint.longitude,
                checkpoint.absoluteHeight,
                checkpoint.yawDeg
            )?.blockingAwait()
        }
    }


    private fun captureImages(hostName: String = "127.0.0.1", portNumber: Int = 15555): ImagePacket? {
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

                    return ImagePacket(floatData, thermalImageData, rgbImageData)
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
                drone = null // Clear out the old drone object

                // Assuming sleep is a suspend function from kotlinx.coroutines package
                // If not, replace with delay(100) which is the correct way to delay in coroutines
                delay(1000)

                drone = System(ConfigManager.getDronePath(), ConfigManager.getDronePort())
                startReadDroneStatusJob()
            } finally {
                // Always release the lock when done.
                mutex.unlock()
            }
        }
    }
}

data class ImagePacket(val thermalFloat: ByteArray?, val thermalGray: ByteArray?, val rgbImage: ByteArray?)
