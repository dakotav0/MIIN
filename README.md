# Minecraft MCP Server & Mod

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE.md)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-green.svg)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric-0.15.11-orange.svg)](https://fabricmc.net/)
[![Node](https://img.shields.io/badge/node-18%2B-brightgreen.svg)](https://nodejs.org/)
[![Python](https://img.shields.io/badge/python-3.10%2B-blue.svg)](https://www.python.org/)

**AI-powered NPC system for Minecraft using local LLMs.**

This project integrates Minecraft with local Large Language Models (LLMs) via the Model Context Protocol (MCP) to create dynamic, intelligent NPCs. It features a Fabric mod for the client/server and a set of MCP servers for handling AI logic, dialogue, and game state.

## Features

*   **LLM-Driven Dialogue:** NPCs have unique personalities, memories, and can engage in open-ended conversations.
*   **Dynamic Spawning:** Create NPCs on the fly with generated backstories and traits.
*   **Merchant System:** Trading system where prices fluctuate based on reputation and relationships.
*   **Faction System:** Dynamic reputation system with multiple factions.
*   **Quest Generation:** Procedural quests based on player activity and NPC needs.
*   **Party System:** Recruit NPCs to join your party and coordinate actions.
*   **Lore Discovery:** Procedural lore generation and discovery system.

## Architecture

The system consists of three main components:

1.  **Minecraft Mod (Fabric):** Handles in-game rendering, events, and communication with the MCP server.
2.  **MCP Server (TypeScript):** Acts as the bridge between Minecraft and the AI services.
3.  **AI Services (Python):** Specialized services for NPC logic, dialogue, and game state management.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for a detailed overview.

## Quick Start

See [QUICKSTART.md](QUICKSTART.md) for a 5-minute setup guide.

**Prerequisites:**
*   Minecraft Java Edition 1.20.1
*   Fabric Loader
*   Node.js 18+
*   Python 3.10+
*   Ollama (for local LLMs)

## Documentation

*   [Prerequisites & Installation](PREREQUISITES.md)
*   [Architecture Overview](docs/ARCHITECTURE.md)
*   [Build & Test Guide](docs/BUILD_AND_TEST.md)
*   [Systems Roadmap](docs/SYSTEMS_ROADMAP.md)

## Contributing

We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines and our code of conduct.

## License

This project is licensed under the MIT License - see the [LICENSE.md](LICENSE.md) file for details.
