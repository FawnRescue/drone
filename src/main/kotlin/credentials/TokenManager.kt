package credentials

import java.io.File
import com.google.gson.Gson
import java.io.BufferedReader
import java.io.InputStreamReader

class TokenManager {
    fun fetchCredentialsFromNode(): Credentials? {
        try {
            val command = listOf("sudo", "node", "/home/drone/bluetooth/bluetooth.js")
            println("Executing command: $command")
            val processBuilder = ProcessBuilder(command)
            val process = processBuilder.start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))

            val output = reader.readLine()?.removeSuffix("\n")?.split(",")
            println("Output: $output")
            if (output != null && output.size == 4) {
                return Credentials(output[1], output[2], output[3])
            }
            process.waitFor()
            return null
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}

data class Credentials(val otp: String, val email: String, val token: String)