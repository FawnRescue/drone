import credentials.ConfigManager
import credentials.Credentials
import credentials.TokenManager
import drone.DroneController
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import kotlinx.coroutines.runBlocking
import supabase.SupabaseLoginHandler
import kotlin.time.Duration.Companion.seconds

fun main(): Unit = runBlocking {
    val tokenManager = TokenManager()
    val supabase: SupabaseClient = createSupabaseClient(
        supabaseUrl = ConfigManager.get("supabase_url") ?: "", supabaseKey = ConfigManager.get("supabase_token") ?: ""
    ) {
        install(Realtime) {
            reconnectDelay = 5.seconds
        }
        install(Postgrest)
        install(Auth)
        install(Storage)
    }
    val supabaseLoginHandler = SupabaseLoginHandler(supabase)

    // These credentials are used for simulated drone communication
    val email = ConfigManager.get("email")
    val password = ConfigManager.get("password")
    val overrideToken = ConfigManager.get("token_override")
    if (password != null && email != null && overrideToken != null) {
        // Override token and login for debug purposes
        supabaseLoginHandler.debugLogin(password, email)
        waitForAuthentication(supabaseLoginHandler, overrideToken, supabase)
    }
    if (password != null || email != null || overrideToken != null) {
        println("All of 'email', 'password' and 'token_override' have to be set for the override to work")
    }

    // Normal login procedure
    val token = supabaseLoginHandler.getToken()
    if (token != null) {
        // We assume a session is cached
        waitForAuthentication(supabaseLoginHandler, token, supabase)
    } else {
        // We need to log in again by getting the info from the app via Bluetooth
        var loginData: Credentials?
        do {
            loginData = tokenManager.fetchCredentialsFromBT()
        } while (loginData == null)
        supabaseLoginHandler.saveToken(loginData.token)
        supabaseLoginHandler.login(loginData.otp, loginData.email)
        waitForAuthentication(supabaseLoginHandler, loginData.token, supabase)
    }
}

private suspend fun waitForAuthentication(
    supabaseLoginHandler: SupabaseLoginHandler,
    token: String,
    supabase: SupabaseClient
) {
    supabaseLoginHandler.authFlow.collect { status ->
        when (status) {
            is SessionStatus.Authenticated -> {
                if (!supabaseLoginHandler.isAircraftAvailable(token)) {
                    supabaseLoginHandler.signout()
                    println("Aircraft was deleted. Exiting...")
                    return@collect
                }
                val controller = DroneController(token, supabase)
                controller.start()
            }

            else -> {
                println(status)
            }
        }
    }
}

