# MIIN Minecraft MCP - Architecture Overview

**Version**: 1.0
**Last Updated**: 2025-01-21

---

## Table of Contents

- [System Overview](#system-overview)
- [Architecture Diagram](#architecture-diagram)
- [Core Components](#core-components)
- [Data Flow](#data-flow)
- [Technology Stack](#technology-stack)
- [Design Patterns](#design-patterns)
- [Security Considerations](#security-considerations)

---

## System Overview

MIIN Minecraft MCP is a **living world NPC system** that brings AI-powered characters to Minecraft. It consists of three main layers:

1. **Fabric Mod (Kotlin)** - Client-side entity management and event capture
2. **MCP Server (TypeScript)** - Protocol layer exposing tools via Model Context Protocol
3. **Backend Services (Python)** - AI/LLM integration, persistence, and business logic

### Key Features

- **LLM-Driven Dialogue** - NPCs converse naturally using local Ollama models
- **Persistent Memory** - NPCs remember conversations across server restarts
- **Dynamic NPCs** - Spawn NPCs with unique personalities, quests, and behaviors
- **Merchant System** - Functional trading with reputation-based pricing
- **Faction System** - Reputation cascades across allied/enemy factions
- **Quest Generation** - AI-generated quests based on player activity
- **Lore Discovery** - Progressive world knowledge system

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                        MINECRAFT CLIENT                          │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │         Fabric Mod (MIIN) - Kotlin             │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐ │ │
│  │  │ MIINListener │  │ NpcManager   │  │ DialogueManager  │ │ │
│  │  │   (Core)     │  │ (Entities)   │  │    (UI/Chat)     │ │ │
│  │  └──────┬───────┘  └──────┬───────┘  └────────┬─────────┘ │ │
│  │         │                  │                    │           │ │
│  │         └──────────────────┴────────────────────┘           │ │
│  │                            │                                │ │
│  │                      HTTP POST (JSON)                       │ │
│  └────────────────────────────┼────────────────────────────────┘ │
└─────────────────────────────────┼──────────────────────────────┬─┘
                                  │                              │
                                  ▼                              │
┌─────────────────────────────────────────────────────┐          │
│            MCP SERVER (TypeScript)                   │          │
│  ┌────────────────────────────────────────────────┐ │          │
│  │              src/index.ts                      │ │          │
│  │  ┌──────────────────────────────────────────┐ │ │          │
│  │  │  MCP Tools (30+ endpoints)               │ │ │          │
│  │  │  - minecraft_npc_talk                    │ │ │          │
│  │  │  - minecraft_dialogue_start_llm          │ │ │          │
│  │  │  - minecraft_dialogue_respond            │ │ │          │
│  │  │  - minecraft_merchant_browse             │ │ │          │
│  │  │  - minecraft_quest_request               │ │ │          │
│  │  │  - minecraft_party_invite                │ │ │          │
│  │  └──────────────┬───────────────────────────┘ │ │          │
│  └─────────────────┼───────────────────────────── │ │          │
│                    │ exec() Python scripts        │ │          │
└────────────────────┼──────────────────────────────┘ │          │
                     │                                │          │
                     ▼                                │          │
┌─────────────────────────────────────────────────────────────┐  │
│              PYTHON BACKEND SERVICES                         │  │
│                                                               │  │
│  ┌──────────────────┐  ┌──────────────────┐  ┌────────────┐ │  │
│  │  NPC Service     │  │ Dialogue Service │  │  LLM Router│ │  │
│  │  npc/scripts/    │  │  dialogue/       │  │  llm_router│ │  │
│  │  service.py      │  │  service.py      │  │  .py       │ │  │
│  │                  │  │                  │  │            │ │  │
│  │ - NPC registry   │  │ - BG3-style      │  │ - Task-    │ │  │
│  │ - Memory mgmt    │  │   dialogue wheel │  │   based    │ │  │
│  │ - Quest gen      │  │ - Relationship   │  │   routing  │ │  │
│  │ - Context build  │  │   tracking       │  │ - Fallback │ │  │
│  └────────┬─────────┘  └────────┬─────────┘  └─────┬──────┘ │  │
│           │                     │                   │        │  │
│           └─────────────────────┴───────────────────┘        │  │
│                                 │                            │  │
│  ┌──────────────────────────────┼───────────────────────────┐│  │
│  │           Ollama Integration (via HTTP)                  ││  │
│  │  ┌────────────────────────────────────────────────────┐ ││  │
│  │  │  http://localhost:11434/api/chat                   │ ││  │
│  │  │  - llama3.2:latest (fast, conversational)          │ ││  │
│  │  │  - llama3.1:8b (creative, reasoning)               │ ││  │
│  │  │  - deepseek-r1:latest (analytical, code)           │ ││  │
│  │  │  - Keep-alive: 10m (instant responses)             │ ││  │
│  │  └────────────────────────────────────────────────────┘ ││  │
│  └──────────────────────────────────────────────────────────┘│  │
│                                                               │  │
│  ┌──────────────────┐  ┌──────────────────┐  ┌────────────┐ │  │
│  │ Merchant Service │  │ Faction Service  │  │ Game State │ │  │
│  │ npc/merchant/    │  │  factions/       │  │ game_state/│ │  │
│  │ service.py       │  │  service.py      │  │ service.py │ │  │
│  │                  │  │                  │  │            │ │  │
│  │ - Inventory mgmt │  │ - Faction config │  │ - World    │ │  │
│  │ - Buy/sell logic │  │ - Reputation     │  │   events   │ │  │
│  │ - Reputation     │  │   cascades       │  │ - Economy  │ │  │
│  │   pricing        │  │ - Perks/bonuses  │  │ - Quest DB │ │  │
│  └──────────────────┘  └──────────────────┘  └────────────┘ │  │
│                                                               │  │
│  ┌──────────────────┐  ┌──────────────────┐  ┌────────────┐ │  │
│  │   Party Service  │  │   Lore Service   │  │   Events   │ │  │
│  │   party/         │  │    lore/         │  │   events/  │ │  │
│  │   service.py     │  │    service.py    │  │            │ │  │
│  └──────────────────┘  └──────────────────┘  └────────────┘ │  │
└─────────────────────────────────────────────────────────────┘  │
                                                                  │
┌─────────────────────────────────────────────────────────────┐  │
│                  PERSISTENT STORAGE                          │  │
│                                                               │  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐   │  │
│  │  JSON Files  │  │ SQLite DBs   │  │  NBT (Minecraft) │   │  │
│  ├──────────────┤  ├──────────────┤  ├──────────────────┤   │  │
│  │ - npcs.json  │  │ - npc_       │  │ - Entity NBT     │   │  │
│  │ - memory.    │  │   registry.  │  │ - PersistentData │   │  │
│  │   json       │  │   db         │  │   Container      │   │  │
│  │ - relation-  │  │ - world_     │  │                  │   │  │
│  │   ships.json │  │   state.db   │  │                  │   │  │
│  │ - inventory. │  │ - faction_   │  │                  │   │  │
│  │   json       │  │   standings  │  │                  │   │  │
│  │ - quests.    │  │   .db        │  │                  │   │  │
│  │   json       │  │ - trades.db  │  │                  │   │  │
│  └──────────────┘  └──────────────┘  └──────────────────┘   │  │
└─────────────────────────────────────────────────────────────┘  │
                                                                  │
  ┌─────────────────────────────────────────────────────────┐   │
  │                 EXTERNAL INTEGRATIONS                    │   │
  │                                                           │   │
  │  ┌────────────────────┐      ┌─────────────────────┐    │   │
  │  │   Claude Desktop   │      │   Ollama (Local)    │    │   │
  │  │   (via MCP)        │◄────►│   Port 11434        │    │   │
  │  └────────────────────┘      └─────────────────────┘    │   │
  └─────────────────────────────────────────────────────────┘   │
                                                                  │
                                                                  │
```

---

## Core Components

### 1. Fabric Mod (Kotlin) - Client Layer

**Location**: `MIIN/src/main/kotlin/MIIN/listener/`

#### MIINListener.kt
**Purpose**: Main entry point and event coordination

**Responsibilities**:
- Initialize mod on server start
- Register event handlers
- Coordinate between managers
- HTTP communication with MCP server

**Key Methods**:
```kotlin
fun onInitializeServer() {
    // Register event handlers
    ServerTickEvents.END_SERVER_TICK.register(this::onServerTick)
    ServerPlayConnectionEvents.JOIN.register(this::onPlayerJoin)
}

fun onPlayerChat(message: String, player: ServerPlayerEntity) {
    // Send player_chat event to MCP server
}
```

#### NpcManager.kt
**Purpose**: Manage NPC entities (spawning, tracking, behavior)

**Responsibilities**:
- Spawn NPCs from config at server start
- Track active NPCs (prevent duplicates)
- Manage NPC behavior modes (stationary, roaming, following)
- Handle NPC interactions (click, right-click)

**Key Features**:
- Ground-level detection for safe spawning
- Skin synchronization via DataTracker
- Ambient barks (contextual idle messages)
- NBT persistence (NEW - saves state across restarts)

#### DialogueManager.kt
**Purpose**: Handle player-NPC dialogue interactions

**Responsibilities**:
- Initiate dialogue when player talks to NPC
- Send messages to MCP server
- Display responses in chat
- Generate dialogue options (BG3-style wheel)

**Key Methods**:
```kotlin
fun startDialogue(player: ServerPlayerEntity, npc: MIINNpcEntity) {
    // Get nearby entities for context
    val nearby = getNearbyEntities(player)

    // Send to MCP server
    val payload = mapOf(
        "npc" to npc.npcId,
        "player" to player.name.string,
        "nearby_entities" to nearby
    )
    httpClient.post("/dialogue/start", payload)
}
```

#### MIINNpcEntity.kt
**Purpose**: Custom NPC entity class

**Extends**: `PathAwareEntity` (for pathfinding)

**Key Features**:
- Custom skin rendering
- Behavior modes (stationary, roaming, following)
- Ambient barks with cooldown system
- NBT persistence (saves npcId, archetype, merchant inventory, faction)

**Data Synced to Client**:
```kotlin
private val NPC_ID = DataTracker.registerData(MIINNpcEntity::class.java, TrackedDataHandlerRegistry.STRING)
private val NPC_NAME = DataTracker.registerData(MIINNpcEntity::class.java, TrackedDataHandlerRegistry.STRING)
private val SKIN_PATH = DataTracker.registerData(MIINNpcEntity::class.java, TrackedDataHandlerRegistry.STRING)
```

---

### 2. MCP Server (TypeScript) - Protocol Layer

**Location**: `src/`

#### index.ts
**Purpose**: MCP server implementation

**Architecture**:
- Implements Model Context Protocol (MCP)
- Exposes 30+ tools via stdio
- Each tool = Python script execution
- Returns JSON results to Claude Desktop (or other MCP clients)

**Tool Categories**:

**NPC Tools** (8):
- `minecraft_npc_talk` - Basic conversation
- `minecraft_npc_talk_with_suggestions` - Get dialogue options
- `minecraft_npc_generate` - Create dynamic NPC
- `minecraft_npc_list` - List all NPCs
- `minecraft_npc_spawn` - Spawn NPC at location
- `minecraft_npc_get_memory` - View conversation history
- `minecraft_npc_get_stats` - NPC statistics

**Dialogue Tools** (3):
- `minecraft_dialogue_start_llm` - Start LLM-driven dialogue
- `minecraft_dialogue_respond` - Respond to dialogue option
- `minecraft_dialogue_select` - Select option by ID

**Quest Tools** (4):
- `minecraft_quest_request` - Request quest from NPC
- `minecraft_quest_accept` - Accept quest
- `minecraft_quest_status` - Check quest progress
- `minecraft_quest_check_progress` - Update quest status

**Merchant Tools** (3):
- `minecraft_merchant_browse` - View shop inventory
- `minecraft_merchant_buy` - Purchase item
- `minecraft_merchant_sell` - Sell item to merchant

**Party Tools** (3):
- `minecraft_party_create` - Create party
- `minecraft_party_invite` - Invite NPC to party
- `minecraft_party_chat` - Party conversation

**Lore Tools** (2):
- `minecraft_lore_discover` - Discover lore entry
- `minecraft_lore_list` - List discovered lore

**Build Challenge Tools** (3):
- `minecraft_build_challenge_list` - List challenges
- `minecraft_build_challenge_request` - Request challenge
- `minecraft_build_challenge_validate` - Validate completion

**Milestone Tools** (3):
- `minecraft_milestone_list` - List milestones
- `minecraft_milestone_check_completion` - Check if complete
- `minecraft_milestone_celebrate` - Trigger celebration

#### Tool Handler Pattern

All tools follow this pattern:

```typescript
server.tool(
  "minecraft_npc_talk",
  "Have a conversation with an NPC",
  {
    npc: z.string().describe("NPC identifier"),
    player: z.string().describe("Player name"),
    message: z.string().describe("What to say to NPC")
  },
  async ({ npc, player, message }) => {
    // 1. Escape inputs (prevent shell injection)
    const escapedMessage = message.replace(/"/g, '\\"');

    // 2. Execute Python script
    const scriptPath = `${process.cwd()}/npc/scripts/talk.py`;
    const result = await exec(
      `python "${scriptPath}" "${npc}" "${player}" "${escapedMessage}"`
    );

    // 3. Return result
    return {
      content: [{ type: "text", text: result.stdout }]
    };
  }
);
```

---

### 3. Python Backend Services - Business Logic Layer

**Location**: Root directories (npc/, dialogue/, party/, etc.)

#### NPC Service (npc/scripts/service.py)

**Purpose**: Core NPC management and LLM integration

**Key Features**:
- NPC configuration loading (static + dynamic)
- Conversation memory (last 20 messages per NPC-player pair)
- Context building (player stats, location, recent activity)
- System prompt generation (personality, backstory, guidelines)
- LLM integration via router (task-based model selection)
- Quest generation
- Relationship tracking

**Public API**:
```python
class NPCService:
    def generate_npc_response(npc_id, player, message, context) -> str
    def get_npc_memory(npc_id, player) -> List[Dict]
    def add_to_memory(uuid, player, role, content)
    def build_system_prompt(npc, player, context) -> str
    def get_player_context(player, nearby_entities) -> Dict
```

**LLM Router Integration**:
```python
# Route request to appropriate model based on task type
response, error = self.llm_router.route_request(
    messages=messages,
    task_type="dialogue",  # quick_response, quest_generation, etc.
    npc_id=npc_id
)
```

#### Dialogue Service (dialogue/service.py)

**Purpose**: BG3-style dialogue wheel system

**Key Features**:
- Tone-based dialogue options (friendly, aggressive, curious, flirty, intimidating)
- Relationship delta preview (+1, -2, etc.)
- D&D-style skill checks (persuasion, insight, stealth)
- Lore integration (references discovered lore)
- Template-based greetings (instant, <1ms)
- LLM-generated options (2-5s)

**Dialogue Flow**:
```
1. Player talks to NPC
2. System checks relationship history
3. Generate greeting:
   - First time: Template greeting (instant)
   - Returning: LLM greeting with context (2-5s)
4. Generate 3-5 dialogue options with tones
5. Player selects option
6. NPC responds via LLM
7. Update relationship based on choice
8. Repeat from step 4 or end conversation
```

**Relationship System**:
```python
# Levels: -100 to +100
# Titles: Enemy → Distrusted → Stranger → Acquaintance → Friend → Trusted Ally
# Tracked: memorable actions (last 20), dialogue choices (last 50)

def update_relationship(npc_id, player, delta, reason):
    relationship['level'] = max(-100, min(100, current_level + delta))
    relationship['memorable_actions'].append({
        'action': reason,
        'delta': delta,
        'timestamp': datetime.now().isoformat()
    })
```

#### LLM Router (npc/scripts/llm_router.py)

**Purpose**: Intelligent model selection and request optimization

**Key Features**:
- **Task-based routing** - Different models for different tasks
- **Automatic fallback** - Try backup model if primary fails
- **Context optimization** - Keep only relevant messages (50-80% token reduction)
- **Keep-alive** - Models stay loaded for 10 minutes (instant responses)

**Task Types**:
```python
{
    "quick_response": {
        "preferred_model": "llama3.2:latest",
        "fallback": "llama3.1:8b",
        "memory_window": 3  # 6 messages
    },
    "dialogue": {
        "preferred_model": "llama3.1:8b",
        "fallback": "llama3.2:latest",
        "memory_window": 10  # 20 messages
    },
    "quest_generation": {
        "preferred_model": "deepseek-r1:latest",
        "fallback": "llama3.1:8b",
        "memory_window": 20  # 40 messages
    }
}
```

**Routing Flow**:
```python
def route_request(messages, task_type, npc_id):
    # 1. Select model based on task type
    model = self._select_model(task_type)

    # 2. Optimize context (keep last N exchanges)
    messages = self._optimize_context(messages, task_type)

    # 3. Call Ollama with keep-alive
    try:
        response = self._call_ollama(model, messages)
        return response, None
    except Exception as e:
        # 4. Try fallback model
        fallback = get_fallback(task_type)
        response = self._call_ollama(fallback, messages)
        return response, None
```

#### Merchant Service (npc/merchant/service.py)

**Purpose**: NPC trading system with reputation pricing

**Key Features**:
- Inventory management (stock, restocking)
- Buy/sell transactions
- Reputation-based pricing (up to 50% discount)
- Transaction history logging (SQLite)

**Pricing Formula**:
```python
# relationship_level: -100 to +100
discount_percentage = relationship_level / 200.0  # -0.5 to +0.5

if trade_type == 'buy':
    final_price = base_price * (1 - discount_percentage)
else:  # sell
    final_price = base_price * (1 + discount_percentage)

# Examples:
# Enemy (-100): 10 emeralds → 15 (50% markup)
# Stranger (0): 10 emeralds → 10 (no change)
# Friend (+50): 10 emeralds → 7 (30% discount)
# Trusted Ally (+100): 10 emeralds → 5 (50% discount)
```

#### Faction Service (factions/service.py)

**Purpose**: Faction reputation and cascade effects

**Key Features**:
- Faction affiliations for NPCs
- Reputation tracking per faction
- Cascade effects:
  - Help NPC → Faction +50% of relationship change
  - Allied factions → +25% of change
  - Enemy factions → -50% of change (opposite)
- Faction perks at thresholds (25, 50, 100)

**Example Cascade**:
```python
# Player helps Rowan (Traders Guild merchant) with quest: +10 relationship

# Cascades:
# 1. Rowan: +10 (direct)
# 2. Traders Guild: +5 (50% of +10)
# 3. Builders Union (ally): +1 (25% of +5)
# 4. Outcast Bandits (enemy): -2 (opposite, 50% of +5)

# Result:
# - All traders give better prices
# - Builders offer special quests
# - Bandits become more hostile
```

#### Game State Service (game_state/service.py)

**Purpose**: Centralized world state tracking

**Databases**:
- **world_events** - Dragon killed, village raided, boss defeated
- **faction_territories** - Territory control by biome
- **economy_prices** - Dynamic pricing based on supply/demand
- **quest_states** - Active/completed quests

**Event Propagation** (Future):
```python
# Event: Dragon killed in Eastern Mountains
# Day 1: NPCs within 100 blocks know
# Day 3: NPCs within 500 blocks know (via "traveler" NPCs)
# Day 7: All NPCs know (via RAG database rumor mill)
```

---

## Data Flow

### Player Talks to NPC (Complete Flow)

```
1. MINECRAFT CLIENT
   ├─ Player right-clicks NPC or types in chat
   ├─ DialogueManager.startDialogue() triggered
   ├─ Scan nearby entities (16 block radius)
   └─ HTTP POST to MCP server

2. MCP SERVER
   ├─ Receive minecraft_dialogue_start_llm tool call
   ├─ Parse parameters (npc, player, nearby_entities)
   ├─ Execute dialogue/start_llm.py
   └─ Return result to Kotlin

3. PYTHON BACKEND (Dialogue Service)
   ├─ Load NPC config from npcs.json
   ├─ Check relationship history (relationships.json)
   ├─ First time? Use template greeting (instant)
   └─ Returning? Generate LLM greeting (2-5s):
       ├─ Build system prompt (NPCService.build_system_prompt)
       │   ├─ NPC personality, backstory, dialogue style
       │   ├─ Player context (location, health, recent activity)
       │   └─ Nearby entities ("Guard_01 is 3m away")
       ├─ Get conversation memory (last 10 exchanges)
       ├─ Route to LLM (LLMRouter.route_request)
       │   ├─ Select model (llama3.1:8b for dialogue)
       │   ├─ Optimize context (keep last 20 messages)
       │   ├─ Call Ollama with keep-alive (instant if loaded)
       │   └─ Sanitize response (remove meta-awareness)
       └─ Generate dialogue options (3-5 with tones)

4. OLLAMA
   ├─ Receive chat request
   ├─ Generate NPC response (2-4 sentences)
   ├─ Model stays loaded for 10 minutes (keep-alive)
   └─ Return response

5. PYTHON BACKEND (Dialogue Service)
   ├─ Format response with dialogue options
   ├─ Save to memory (memory.json)
   └─ Return JSON to MCP server

6. MCP SERVER
   └─ Return formatted result to Kotlin

7. MINECRAFT CLIENT
   ├─ Display NPC greeting in chat
   ├─ Show dialogue options (clickable)
   ├─ Player selects option
   └─ REPEAT from step 2 (dialogue_respond tool)

8. AFTER CONVERSATION
   ├─ Update relationship (relationships.json)
   ├─ Update faction reputation (faction_standings.db)
   └─ Cascade to allied/enemy factions
```

### Merchant Transaction (Buy Item)

```
1. MINECRAFT CLIENT
   └─ Player talks to merchant NPC, says "I want to buy wheat"

2. MCP SERVER (minecraft_merchant_browse)
   └─ Execute npc/merchant/browse.py

3. PYTHON BACKEND (Merchant Service)
   ├─ Load merchant inventory (inventory.json)
   ├─ Auto-restock if 1+ hours passed
   ├─ Format inventory for display
   └─ Return stock list with prices

4. PLAYER DECIDES TO BUY
   └─ MCP server calls minecraft_merchant_buy

5. PYTHON BACKEND (Merchant Service)
   ├─ Validate item in stock (quantity check)
   ├─ Get relationship level (relationships.json)
   ├─ Calculate price with reputation discount:
   │   ├─ Base price: 5 emeralds
   │   ├─ Relationship: +50 (Friend)
   │   ├─ Discount: -25% (relationship / 200)
   │   └─ Final price: 3 emeralds
   ├─ Deduct from stock (64 → 48 wheat)
   ├─ Log transaction (trades.db)
   └─ Return success with price breakdown

6. MINECRAFT CLIENT
   ├─ Display transaction result
   ├─ Remove emeralds from player inventory
   └─ Add wheat to player inventory
```

### NPC Spawning (Server Start)

```
1. MINECRAFT SERVER START
   └─ MIINListener.onInitializeServer() triggered

2. NPC MANAGER
   ├─ Load npc/config/npcs.json
   ├─ Check MIIN_spawned_npcs.json (prevent duplicates)
   └─ For each NPC:
       ├─ Resolve spawn location (absolute or relative to spawn)
       ├─ Find ground level (raycast down from Y+20)
       ├─ Spawn MIINNpcEntity
       ├─ Set DataTracker fields (npcId, name, skin)
       ├─ Register in UUID registry (npc_registry.db)
       └─ Track in MIIN_spawned_npcs.json

3. MIINNpcEntity
   ├─ Load from NBT if exists (restore state)
   ├─ Sync to client (skin, name, behavior mode)
   ├─ Start behavior AI (stationary/roaming/following)
   └─ Schedule ambient barks (5 min cooldown)

4. PERSISTENT STORAGE
   ├─ Entity UUID → NPC ID mapping (npc_registry.db)
   ├─ Entity NBT (npcId, archetype, faction, merchantInventory)
   └─ Conversation memory linked to UUID (memory.json)
```

---

## Technology Stack

### Client (Kotlin)
- **Fabric 1.20.1** - Mod loader
- **Minecraft 1.20.1** - Game version
- **Kotlin 1.9+** - JVM language
- **Gradle 8+** - Build system

### MCP Server (TypeScript)
- **Node.js 18+** - Runtime
- **TypeScript 5+** - Type-safe JavaScript
- **@modelcontextprotocol/sdk** - MCP implementation
- **zod** - Schema validation

### Backend (Python)
- **Python 3.10+** - Runtime
- **requests** - HTTP client
- **sqlite3** - Database (built-in)
- **pathlib** - File path handling
- **json** - Data serialization

### AI/LLM
- **Ollama** - Local LLM inference
  - llama3.2:latest (3B, fast, conversational)
  - llama3.1:8b (8B, creative, reasoning)
  - deepseek-r1:latest (8B, analytical, code)

### Data Storage
- **JSON** - Configuration, memory, relationships
- **SQLite** - Registries, transactions, world state
- **NBT** - Minecraft entity persistence

---

## Design Patterns

### 1. Service Pattern
Each domain (NPC, Dialogue, Merchant) has a service class:
```python
class NPCService:
    def __init__(self, config_path, memory_path):
        self.load_config()
        self.load_memory()

    def generate_response(...) -> str
    def add_to_memory(...) -> None
```

### 2. Tool Handler Pattern (MCP)
Consistent tool implementation:
```typescript
server.tool(name, description, schema, async handler)
```

### 3. Repository Pattern
Data access abstraction:
```python
class UUIDManager:
    def register_npc(uuid, npc_id, ...)
    def get_npc_by_uuid(uuid) -> Dict
    def mark_dead(uuid)
```

### 4. Strategy Pattern (LLM Router)
Task-based model selection:
```python
router.route_request(messages, task_type="dialogue")
# Internally: select model based on task characteristics
```

### 5. Template Method Pattern (Dialogue)
```python
def start_dialogue():
    greeting = generate_greeting()  # Abstract
    options = generate_options()    # Abstract
    return format_response(greeting, options)
```

### 6. Observer Pattern (Events)
```kotlin
ServerTickEvents.END_SERVER_TICK.register(this::onServerTick)
```

### 7. Singleton Pattern (Services)
```python
# Single instance per service type
npc_service = NPCService()  # Shared across requests
```

---

## Security Considerations

### 1. Input Sanitization

**Shell Injection Prevention**:
```typescript
// CRITICAL: Always escape shell arguments
const escapedMessage = message.replace(/"/g, '\\"');
await exec(`python script.py "${escapedMessage}"`);
```

**SQL Injection Prevention**:
```python
# Use parameterized queries
cursor.execute('SELECT * FROM npcs WHERE uuid = ?', (uuid,))
# NEVER: f'SELECT * FROM npcs WHERE uuid = "{uuid}"'
```

### 2. File Access Control

**Path Traversal Prevention**:
```python
# Validate paths
from pathlib import Path
root = Path(__file__).parent.parent
config_path = root / 'npc' / 'config' / 'npcs.json'
config_path.resolve().relative_to(root)  # Raises error if outside root
```

### 3. Rate Limiting

**Future**: Implement rate limiting for:
- MCP tool calls (prevent spam)
- LLM requests (prevent abuse)
- HTTP endpoints (DDoS protection)

### 4. Data Privacy

**Local Only**:
- All data stored locally
- No cloud API calls (except optional)
- No telemetry or tracking

**Sensitive Data**:
- Conversation memory (player-specific)
- Relationship data (private)
- Transaction history (local only)

### 5. Atomic Writes

**Prevent JSON Corruption**:
```python
import tempfile, os

temp_path = f"{file_path}.tmp"
with open(temp_path, 'w') as f:
    json.dump(data, f)
os.replace(temp_path, file_path)  # Atomic operation
```

---

## Performance Optimizations

### 1. LLM Router

**Keep-Alive** (10 minutes):
- Models stay loaded
- First response: ~20s (load) + ~3s (generate) = 23s
- Subsequent: ~3s (instant load + generate)

**Context Optimization**:
- Keep only last N exchanges (task-dependent)
- 50-80% token reduction
- Faster inference (less data to process)

### 2. Database Indexing

```sql
CREATE INDEX idx_npc_id ON npc_instances(npc_id);
CREATE INDEX idx_uuid ON faction_standings(player_name);
```

### 3. Async Operations (Kotlin)

```kotlin
// Don't block game thread
CompletableFuture.runAsync {
    httpClient.post(url, payload)
}
```

### 4. Caching

- **NPC configs**: Loaded once at startup
- **Nearby entities**: Cached for 2 seconds
- **Merchant inventory**: Lazy-loaded when browsing

---

## Future Enhancements

### Planned Features (from SYSTEMS_ROADMAP.md)

**Phase 1: Dialogue Optimization** (3.5 hours)
- Anti-meta filter
- System prompt hardening
- Inventory awareness

**Phase 2: Context Awareness** (3 hours)
- Nearby entities detection
- Field-of-view awareness

**Phase 3: NPC Persistence** (7 hours)
- UUID registry database
- NBT persistence
- Memory system migration

**Phase 4: Game State Service** (6 hours)
- World state tracker
- Event propagation

**Phase 5: Merchant System** (6 hours)
- Functional trading
- Reputation pricing

**Phase 6: Faction System** (4 hours)
- Faction configuration
- Reputation cascades

**Phase 7: Advanced Features** (17+ hours)
- Beast speech (talk to mobs)
- Roll checking (D&D-style)
- Dynamic bestiary (progressive discovery)
- Rumor mill (event propagation)

---

## Troubleshooting Architecture

### Common Issues

**NPCs don't respond**:
- Check Ollama running: `ollama ps`
- Check MCP server running: `npm run start`
- Check Python scripts executable
- Check logs: `MIIN/runtimebuglog.txt`

**Memory not persisting**:
- Verify `npc/config/memory.json` exists
- Check atomic writes implemented
- Verify UUID registry populated

**Slow LLM responses**:
- Check keep-alive working: `ollama ps` (should show UNTIL)
- Verify context optimization enabled
- Check token counts (should be <1500)

---

**For more details, see:**
- [SYSTEMS_ROADMAP.md](SYSTEMS_ROADMAP.md) - Implementation roadmap
- [CONTRIBUTING.md](../CONTRIBUTING.md) - Development guide
- [BUILD_AND_TEST.md](BUILD_AND_TEST.md) - Build/test instructions
- [QUICKSTART.md](../QUICKSTART.md) - Quick setup guide

---

**Last Updated**: 2025-01-21
**Contributors**: Dakota V, Claude (AI Assistant)
**License**: MIT
