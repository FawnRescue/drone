package credentials

import java.io.FileInputStream
import java.util.*

object ConfigManager {
    private val properties = Properties()

    init {
        loadProperties("secrets.properties") // Load your properties file
    }

    private fun loadProperties(filename: String) {
        try {
            FileInputStream(filename).use { fileInputStream ->
                properties.load(fileInputStream)
            }
        } catch (e: Exception) {
            // Handle exceptions if config file isn't found (e.g., log/throw or use defaults)
            println("Error loading config file: ${e.message}")
        }
    }

    fun get(key: String): String? = properties.getProperty(key)
}