package drone

import credentials.ConfigManager
import io.mavsdk.System
import io.mavsdk.telemetry.Telemetry
import io.mavsdk.telemetry.Telemetry.FlightMode
import io.mavsdk.telemetry.Telemetry.LandedState
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.sync.Mutex
import supabase.SupabaseMessageHandler
import supabase.domain.Command
import supabase.domain.CommandType
import supabase.domain.Image
import supabase.domain.LatLong
import utils.haversine
import java.io.DataInputStream
import java.io.PrintWriter
import java.lang.Thread.sleep
import java.net.Socket
import java.util.*


class MavsdkHandler(private val controller: DroneController, private val supabaseHandler: SupabaseMessageHandler) {
    private val tiffPath = "output_SRTMGL1.asc"
    private var heightGrid: GridData? = null
    private var drone: System? = null // Make 'drone' nullable
    private var statusReadJob: Job? = null
    private var statusSendJob: Job? = null
    private var imageUploadJob: Job? = null
    private val connectionRetryMutex = Mutex()
    private val imageQueue: Channel<ImagePacket> = Channel()

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
            heightGrid = readAscFile(tiffPath)
        } catch (e: Exception) {
            println("Exception initializing heightReader from $tiffPath")
            e.printStackTrace()
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
                if (currentCheckpoint == null || checkpointReached) {
                    return@subscribe
                }
                handleDronePosition(it)
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

    private fun handleDronePosition(it: Telemetry.Position) {
        location = Location(it.longitudeDeg, it.latitudeDeg)
        altitude = it.relativeAltitudeM
        currentCheckpoint?.let { checkpoint ->
            // Check if we reached a checkpoint
            val distanceM = haversine(
                checkpoint.latitude, checkpoint.longitude, it.latitudeDeg, it.longitudeDeg
            )
            if (distanceM >= checkpoint.acceptanceRadius) {
                return@let
            }
            checkpointReached = true
            CoroutineScope(Dispatchers.IO).launch {
                checkpointReached(it, checkpoint)
            }
        }
    }

    private suspend fun checkpointReached(position: Telemetry.Position, checkpoint: Checkpoint) {
        println("Checkpoint Reached")
        sleep(500)

        println("Photo")
        val name = UUID.randomUUID().toString()
        val images = captureImages() ?: return
        val metadata = Image(
            thermal_path = if (images.thermalGray != null) "${name}-thermal.png" else null,
            rgb_path = if (images.rgbImage != null) "${name}-rgb.png" else null,
            binary_path = if (images.thermalFloat != null) "${name}-float.bin" else null,
            location = LatLong(
                location?.latitude ?: position.latitudeDeg,
                location?.longitude ?: position.longitudeDeg
            ),
            flight_date = flightDateID!!
        )
        imageQueue.send(ImagePacket(images, metadata))
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

    fun startCommunicating() = CoroutineScope(Dispatchers.IO).launch {
        println("Connecting to ${ConfigManager.getDronePath()}:${ConfigManager.getDronePort()}")
        try {
            drone = System(ConfigManager.getDronePath(), ConfigManager.getDronePort())
        } catch (e: Exception) {
            println("Cant Connect!")
        }
        startReadDroneStatusJob()
    }

    suspend fun startUploadImagesJob() {
        imageUploadJob?.cancelAndJoin()
        imageUploadJob = CoroutineScope(Dispatchers.IO).launch {
            imageQueue.consumeEach {
                try {
                    controller.supabaseHandler.uploadImage(
                        it
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    println("Error: Couldn't upload photo")
                }
            }
        }
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
                        // Logic to send data directly to Supabase
                        supabaseHandler.sendDroneStatus(status)
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
                CommandType.RTH -> {
                    resetMission()
                    drone?.action?.returnToLaunch()?.blockingAwait()
                }

                CommandType.KILL -> {
                    resetMission()
                    drone?.action?.kill()?.blockingAwait()
                }

                CommandType.ELAND -> {
                    resetMission()
                    drone?.action?.land()?.blockingAwait()
                }

                CommandType.CONTINUE -> CoroutineScope(Dispatchers.IO).launch { continueMission(command.context) }
            }
        } catch (e: Exception) {
            println("Error executing Command: $command,\n Error: $e")
        }
    }


    private suspend fun continueMission(flightDateID: String) {
        this.flightDateID = flightDateID
        val acceptanceRadius = 0.5f
        val missionHeight = 15f //TODO: Load flightHeight from drone db
        this.drone?.action?.setReturnToLaunchAltitude(missionHeight)?.blockingAwait()
        // Reload Flight Plan
        val flightPlan = controller.supabaseHandler.getFlightPlan(flightDateID) ?: return
        missionPlan = flightPlan.checkpoints?.map {
            var height = homeAltitude ?: 0f
            heightGrid?.let { grid ->
                try {
                    height = getElevationAtCoordinate(grid, it.latitude, it.longitude)!!.toFloat()
                    println("Height: $height")
                } catch (_: Exception) {
                    println("Error reading height: $it")
                }
            }
            height += missionHeight

            Checkpoint(
                latitude = it.latitude,
                longitude = it.longitude,
                yawDeg = 0f,
                absoluteHeight = height,
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
            homeLocation?.let {
                homeAltitude?.let { altitude ->
                    println("Raising Altitude...")
                    drone?.action?.gotoLocation(
                        it.latitude, it.longitude,
                        altitude + missionHeight, 0f
                    )?.blockingAwait()
                }
            }
            sleep(2000)
            drone?.action?.gotoLocation(
                checkpoint.latitude,
                checkpoint.longitude,
                checkpoint.absoluteHeight,
                checkpoint.yawDeg
            )?.blockingAwait()
        }
    }

    private fun resetMission() {
        currentMissionItem = null
        numMissionItems = null
        currentCheckpoint = null
        checkpointReached = false
        missionPlan = emptyList()
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

    private suspend fun reconnect() {
        // Try to acquire the lock without suspending. Proceed if successful, otherwise cancel the call.
        if (connectionRetryMutex.tryLock()) {
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
                connectionRetryMutex.unlock()
            }
        }
    }
}

