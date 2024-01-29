import credentials.CredentialManager
import drone.DroneController
import kotlinx.coroutines.runBlocking
import java.lang.Thread.sleep

fun main(): Unit = runBlocking {
    lateinit var credentialManager: CredentialManager
    do {
        credentialManager = CredentialManager()
    } while (!credentialManager.areCredentialsAvailable())


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
