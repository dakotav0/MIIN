package miinkt.listener.service

import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.URI

class MIINClient {
    private val logger = LoggerFactory.getLogger("MIINkt-client")
    private val httpClient = HttpClient.newHttpClient()
    private val MIINktBaseUrl = "http://localhost:8080" // Adjust based on your MIINkt setup
    
    suspend fun sendMessage(message: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val request = HttpRequest.newBuilder()
                    .uri(URI.create("$MIINktBaseUrl/chat"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("""{"message": "$message"}"""))
                    .build()
                
                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                if (response.statusCode() == 200) {
                    response.body()
                } else {
                    logger.warn("MIINkt responded with status: ${response.statusCode()}")
                    null
                }
            } catch (e: Exception) {
                logger.error("Failed to communicate with MIINkt", e)
                null
            }
        }
    }
}