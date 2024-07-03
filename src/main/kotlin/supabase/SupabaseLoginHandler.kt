package supabase

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.OtpType
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import supabase.domain.Aircraft
import supabase.domain.InsertableAircraft
import java.io.File

class SupabaseLoginHandler(private val supabase: SupabaseClient) {
    private val tokenFile = File("token")
    val authFlow = supabase.auth.sessionStatus

    suspend fun signout(){
            supabase.auth.signOut()
            tokenFile.delete()
    }

    suspend fun login(otp: String, email: String) {
        supabase.auth.verifyEmailOtp(type = OtpType.Email.MAGIC_LINK, email = email, token = otp)
    }

    suspend fun debugLogin(debugPassword: String, debugEmail: String) {
        supabase.auth.signInWith(Email) {
            email = debugEmail
            password = debugPassword
        }
    }

    fun isTokenAvailable(): Boolean {
        return tokenFile.exists()
    }

    fun saveToken(token: String) {
        tokenFile.writeText(token)
    }

    fun getToken(): String? {
        if (isTokenAvailable()) {
            return tokenFile.readText()
        }
        return null
    }

    suspend fun isAircraftAvailable(token: String): Boolean {
        val aircraft: List<Aircraft> = supabase.from("aircraft").select {
            filter {
                eq("token", token)
            }
        }.decodeList<Aircraft>()
        if (aircraft.isEmpty()) {
            supabase.from("aircraft")
                .insert(InsertableAircraft(name = "Aircraft-${token.subSequence(0, 4)}", token = token))
        } else if (aircraft.first().deleted) {
            return false
        }
        return true
    }

}
