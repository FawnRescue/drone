import credentials.CredentialManager
import drone.DroneController
import kotlinx.coroutines.runBlocking
import java.lang.Thread.sleep

fun main(): Unit = runBlocking {
    lateinit var credentialManager: CredentialManager
    lateinit var controller: DroneController
    do {
        credentialManager = CredentialManager()
        controller = DroneController(token = credentialManager.token!!, key = credentialManager.key!!)
        if (!controller.supabaseHandler.checkToken()) {
            println("No valid Credentials!")
            credentialManager.deleteCredentials()
        }

    } while (!credentialManager.areCredentialsAvailable())


    // Start Supabase message handling
    val supabase = controller.supabaseHandler.startListening()

    // Start MAVSDK communication
    val mavsdk = controller.mavsdkHandler.startCommunicating()

    // Additional main logic
    mavsdk.join()
    supabase.join()

    controller.supabaseHandler.cleanup()
}
