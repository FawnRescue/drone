package credentials

import java.io.File
import com.google.gson.Gson
import java.io.BufferedReader
import java.io.InputStreamReader

class CredentialManager {
    private var credentialsFile = File("credentials.json")

    var key: String? = null
        private set
    var token: String? = null
        private set

    init {
        if (credentialsFile.exists()) {
            val credentials = Gson().fromJson(credentialsFile.readText(), Credentials::class.java)
            key = credentials.key
            token = credentials.token
            println("Credentials loaded.")
        } else {
            println("Credentials file not found.")
            fetchCredentialsFromNode()
            println("Credentials fetched.")
        }
    }

    fun areCredentialsAvailable(): Boolean = key != null && token != null

    fun storeCredentials(key: String, token: String) {
        this.key = key
        this.token = token
        val credentials = Credentials(key, token)
        credentialsFile.writeText(Gson().toJson(credentials))
    }

    fun getCredentials(): Pair<String, String> {
        if (!areCredentialsAvailable()) {
            throw IllegalStateException("Credentials are not available.")
        }
        return Pair(key!!, token!!)
    }

    fun deleteCredentials() {
        key = null
        token = null
        credentialsFile.delete()
    }

    private fun fetchCredentialsFromNode() {
        try {
            val command = listOf("node", "C:/Users/Larsk/PycharmProjects/bluetooth/bluetooth.js")
            println("Executing command: $command")
            val processBuilder = ProcessBuilder(command)
            val process = processBuilder.start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))

            val output = reader.readLine()?.split(",")
            println("Output: $output")
            if (output != null && output.size == 2) {
                storeCredentials(output[0], output[1])
            }

            process.waitFor()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    data class Credentials(val key: String, val token: String)
}
