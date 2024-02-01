import credentials.CredentialManager
import drone.DroneController
import kotlinx.coroutines.runBlocking

fun main(): Unit = runBlocking {
    lateinit var credentialManager: CredentialManager
    lateinit var controller: DroneController
    do {
        credentialManager = CredentialManager()
        if (!credentialManager.areCredentialsAvailable()) {
            continue
        }
        controller = DroneController(credentialManager)
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
