import drone.DroneController
import kotlinx.coroutines.runBlocking

fun main(): Unit = runBlocking {
    val controller = DroneController()

    // Start Supabase message handling
    val supabase = controller.supabaseHandler.startListening()

    // Start MAVSDK communication
    val mavsdk = controller.mavsdkHandler.startCommunicating()

    // Additional main logic
    mavsdk.join()
    supabase.join()

    controller.supabaseHandler.cleanup()
}
