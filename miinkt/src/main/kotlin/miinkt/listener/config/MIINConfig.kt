package miinkt.listener.config

data class MIINConfig(
    val serverUrl: String = "http://localhost:8080",
    val timeout: Long = 30000,
    val maxRetries: Int = 3,
    val enableContextSharing: Boolean = true
)