package credentials

import java.io.File
import com.google.gson.Gson
import java.io.BufferedReader
import java.io.InputStreamReader

class CredentialManager {
    private var credentialsFile = File("credentials.json")

    var accessToken: String? = null
        private set
    var refreshToken: String? = null
        private set
    var token: String? = null
        private set

    init {
        if (credentialsFile.exists()) {
            val credentials = Gson().fromJson(credentialsFile.readText(), Credentials::class.java)
            accessToken = credentials.accessToken
            refreshToken = credentials.refreshToken
            token = credentials.token
            println("Credentials loaded.")
        } else {
            println("Credentials file not found.")
            fetchCredentialsFromNode()
            println("Credentials fetched.")
        }
    }

    fun areCredentialsAvailable(): Boolean = accessToken != null && refreshToken != null && token != null

    private fun storeCredentials(accessToken: String, refreshToken: String, token: String) {
        this.accessToken = accessToken
        this.refreshToken = refreshToken
        this.token = token
        val credentials = Credentials(accessToken, refreshToken, token)
        credentialsFile.writeText(Gson().toJson(credentials))
    }

    fun deleteCredentials() {
        accessToken = null
        refreshToken = null
        token = null
        credentialsFile.delete()
    }

    private fun fetchCredentialsFromNode() {
        try {
            val command = listOf("sudo", "node", "/home/drone/bluetooth/bluetooth.js")
            println("Executing command: $command")
            val processBuilder = ProcessBuilder(command)
            val process = processBuilder.start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))

            val output = reader.readLine()?.removeSuffix("\n")?.split(",")
            println("Output: $output")
            if (output != null && output.size == 4) {
                storeCredentials(output[1], output[2], output[3])
            }

            process.waitFor()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    data class Credentials(val accessToken: String, val refreshToken: String, val token: String)
}
