import credentials.CredentialManager
import drone.DroneController
import kotlinx.coroutines.runBlocking
import java.lang.Thread.sleep

fun main(): Unit = runBlocking {
    val credentialManager = CredentialManager()

    if (!credentialManager.areCredentialsAvailable()) {
        //TODO
    }
    sleep(20000)
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
