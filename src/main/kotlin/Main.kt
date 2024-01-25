import credentials.CredentialManager
import drone.DroneController
import kotlinx.coroutines.runBlocking
import java.lang.Thread.sleep

fun main(): Unit = runBlocking {
    val credentialManager = CredentialManager()

    if (!credentialManager.areCredentialsAvailable()) {
        TODO("Get tokens from App")
    }

    val controller = DroneController(token = credentialManager.token!!, key = credentialManager.key!!)

    // Start Supabase message handling
    val supabase = controller.supabaseHandler.startListening()

    // Start MAVSDK communication
    val mavsdk = controller.mavsdkHandler.startCommunicating()

    // Additional main logic
    mavsdk.join()
    supabase.join()

    controller.supabaseHandler.cleanup()
}
