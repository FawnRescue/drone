package drone

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
import java.lang.Thread.sleep
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
            drone?.telemetry?.armed?.subscribe(
                { armed = it },
                { runBlocking { reconnect() } })
            drone?.telemetry?.battery?.subscribe(
                {
                    battery = Battery(
                        remainingPercent = if (it.remainingPercent?.isFinite() == true) it.remainingPercent else null,
                        voltage = it.voltageV
                    )
                },
                { runBlocking { reconnect() } })
            drone?.telemetry?.position?.subscribe(
                {
                    location = Location(it.longitudeDeg, it.latitudeDeg)
                    altitude = it.relativeAltitudeM
                },
                { runBlocking { reconnect() } })
            drone?.telemetry?.gpsInfo?.subscribe(
                {
                    numSatellites = it.numSatellites
                },
                { runBlocking { reconnect() } })
            drone?.telemetry?.inAir?.subscribe(
                {
                    inAir = it
                },
                { runBlocking { reconnect() } })
            drone?.telemetry?.landedState?.subscribe(
                {
                    landedState = it
                },
                { runBlocking { reconnect() } })
            drone?.telemetry?.flightMode?.subscribe(
                {
                    flightMode = it
                },
                { runBlocking { reconnect() } })
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
                },
                battery,
                location,
                altitude,
                numSatellites
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


    private suspend fun continueMission(id: String) {
        val flightPlan = controller.supabaseHandler.getFlightPlan(id) ?: return
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
                5f,
                0.0,
                1f,
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
            currentCheckpoint = missionPlan.missionItems[it.current]
            checkpointReached = false
        }
        drone?.telemetry?.position?.subscribe {
            if (checkpointReached) {
                return@subscribe
            }
            val distanceM = haversine(
                currentCheckpoint.latitudeDeg,
                currentCheckpoint.longitudeDeg,
                it.latitudeDeg,
                it.longitudeDeg
            )
            if (distanceM < 1) {
                checkpointReached = true
                println("Checkpoint")
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
