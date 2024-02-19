import credentials.ConfigManager
import credentials.Credentials
import credentials.TokenManager
import drone.DroneController
import io.github.jan.supabase.gotrue.SessionStatus
import kotlinx.coroutines.runBlocking

fun main(): Unit = runBlocking {
    val tokenManager = TokenManager()
    val controller = DroneController()

    // These credentials are used for simulated drone communication
    val email = ConfigManager.get("email")
    val password = ConfigManager.get("password")
    val token = ConfigManager.get("token")
    if (password != null && email != null && token != null) {
        controller.supabaseHandler.debugLogin(password, email, token)
    }

    controller.supabaseHandler.authFlow.collect {
        when (it) {
            is SessionStatus.Authenticated -> authenticated(controller)
            SessionStatus.LoadingFromStorage -> {
                println("Loading")
            }

            SessionStatus.NetworkError -> {
                println("Network Error")
            }

            SessionStatus.NotAuthenticated -> notAuthenticated(tokenManager, controller)
        }
    }
}

fun authenticated(controller: DroneController) {
    runBlocking {
        val result = controller.supabaseHandler.setup()
        if (!result) {
            return@runBlocking
        }
        val supabase = controller.supabaseHandler.startListening()

        // Start MAVSDK communication
        val mavsdk = controller.mavsdkHandler.startCommunicating()

        // Additional main logic
        mavsdk.join()
        supabase.join()

        controller.supabaseHandler.cleanup()
    }
}

fun notAuthenticated(tokenManager: TokenManager, controller: DroneController) {
    runBlocking {
        var loginData: Credentials? = null
        do {
            loginData = tokenManager.fetchCredentialsFromNode()
        } while (loginData == null)
        println(loginData)
        controller.supabaseHandler.login(loginData.otp, loginData.email, loginData.token)
    }
}
