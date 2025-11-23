package miinkt.listener.parser

sealed class MIINktCommand {
    data class Chat(val message: String) : MIINktCommand()
    data class Analyze(val target: String) : MIINktCommand()
    data class Help(val topic: String?) : MIINktCommand()
}

class CommandParser {
    fun parseCommand(input: String): MIINktCommand {
        val parts = input.trim().split(" ", limit = 2)
        val command = parts[0].lowercase()
        val args = if (parts.size > 1) parts[1] else ""
        
        return when (command) {
            "analyze" -> MIINktCommand.Analyze(args)
            "help" -> MIINktCommand.Help(args.ifEmpty { null })
            else -> MIINktCommand.Chat(input)
        }
    }
}