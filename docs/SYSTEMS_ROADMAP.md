# MIIN Minecraft MCP - Systems Roadmap

**Version**: 1.0
**Date**: 2025-01-21
**Status**: PLANNING PHASE

---

## 📋 Executive Summary

This roadmap outlines the implementation plan for incomplete systems in MIIN Minecraft MCP. The focus is on dialogue optimization, context awareness, NPC persistence, and merchant/faction systems.

**Total Core Implementation**: ~29.5 hours (Phases 1-6)
**Total with Advanced Features**: ~46.5 hours

---

## 🎯 PHASE 1: DIALOGUE OPTIMIZATION & META-AWARENESS

**Priority**: CRITICAL
**Total Time**: 3.5 hours
**Dependencies**: None

### Problem Statement

Current dialogue system has no safeguards against:
- System prompt leakage ("As an AI...")
- Meta-commentary breaking immersion
- NPCs offering trades without checking inventory
- Generic responses that don't reference nearby context

### 1.1 Anti-Meta Filter Implementation

**Time Estimate**: 1 hour

**Problem**: NPCs might leak system prompts or break character with AI-awareness

**Solution**: Add sanitization layer in dialogue service

**Files to Modify**:
- `dialogue/service.py:491-495`

**Implementation**:
```python
def _sanitize_npc_response(self, response: str) -> str:
    """
    Remove meta-awareness and system prompt leakage from NPC responses

    Filters:
    - AI self-references
    - Technical jargon
    - Meta-commentary in parentheses/brackets
    """
    # Patterns to remove
    meta_patterns = [
        r'(As an AI|According to my training|I cannot|I don\'t have access)',
        r'(I am a language model|I was created by|My purpose is)',
        r'\[.*?\]',  # Remove bracketed meta-commentary
        r'\(Note:.*?\)',  # Remove notes in parentheses
    ]

    for pattern in meta_patterns:
        response = re.sub(pattern, '', response, flags=re.IGNORECASE)

    # Clean up extra whitespace
    response = re.sub(r'\s+', ' ', response).strip()

    return response
```

**Testing**:
- Input: "As an AI, I think you should buy wheat. [This is a good deal]"
- Expected: "I think you should buy wheat."

---

### 1.2 System Prompt Hardening

**Time Estimate**: 30 minutes

**Problem**: Current prompts don't explicitly forbid meta-awareness

**Solution**: Restructure prompts with "Director/Actor" separation

**Files to Modify**:
- `npc/scripts/service.py:459-527` (`build_system_prompt()`)

**Implementation**:

**BEFORE** (current):
```python
prompt = f"""You are {npc['name']}, a character in Minecraft.
PERSONALITY: {npc['personality']}
...
GUIDELINES:
1. Stay in character
2. Don't break fourth wall
...
"""
```

**AFTER** (hardened):
```python
prompt = f"""[CRITICAL DIRECTIVE - READ FIRST]
You are generating dialogue for a game character. You are NOT chatting with a user.
NEVER reference being an AI, language model, or assistant.
NEVER say "I cannot", "I don't have access", or "According to my training".
NEVER use brackets [ ] or (Note: ...) in your responses.
If asked something you don't know, respond in-character with "I haven't heard of that."

[CHARACTER DEFINITION]
Name: {npc['name']}
Personality: {npc['personality']}
Backstory: {npc['backstory']}
Dialogue Style: {npc['dialogue_style']}

[GOOD vs BAD EXAMPLES]
❌ BAD: "As an AI, I think wheat is good for you."
✅ GOOD: "Wheat's the best crop around these parts."

❌ BAD: "I don't have access to that information."
✅ GOOD: "Can't say I've heard of that before."

[CURRENT SITUATION]
{context_info}

[YOUR RESPONSE]
Speak ONLY as {npc['name']}. 2-4 sentences. Stay in character.
"""
```

**Key Changes**:
1. Critical directive at START (not buried in guidelines)
2. Explicit forbidden phrases
3. Good vs Bad examples
4. Stronger "in-character only" framing

---

### 1.3 NPC Inventory Awareness

**Time Estimate**: 2 hours

**Problem**: NPCs offer trades without checking if they have items

**Solution**: Inject merchant inventory into dialogue context

**Files to Create**:
- `npc/merchant/inventory.json`

**Structure**:
```json
{
  "rowan": {
    "stock": [
      {
        "item": "minecraft:wheat",
        "quantity": 64,
        "price_buy": 5,
        "price_sell": 3
      },
      {
        "item": "minecraft:bread",
        "quantity": 32,
        "price_buy": 10,
        "price_sell": 6
      }
    ],
    "currency": "minecraft:emerald",
    "last_restock": "2025-01-21T10:00:00Z"
  }
}
```

**Files to Modify**:
- `dialogue/service.py:_build_options_prompt()`

**Integration**:
```python
def _build_options_prompt(self, npc_id: str, player_name: str, context: Dict) -> str:
    # Load merchant inventory if NPC is a merchant
    inventory = self._load_merchant_inventory(npc_id)

    if inventory:
        inventory_context = self._format_inventory_for_prompt(inventory)
        prompt += f"\n\n[MERCHANT INVENTORY]\n{inventory_context}\n"
        prompt += "IMPORTANT: Only offer items you have in stock. Check quantities.\n"

    return prompt

def _format_inventory_for_prompt(self, inventory: Dict) -> str:
    """Format inventory for LLM context"""
    items = []
    for item in inventory.get('stock', []):
        items.append(f"- {item['item']}: {item['quantity']} available @ {item['price_buy']} emeralds")

    return "\n".join(items)
```

**Testing**:
- Merchant with 0 wheat should NOT offer wheat trade
- Merchant with 64 wheat should offer trade
- Out-of-stock items removed from options

---

## 🎯 PHASE 2: CONTEXT AWARENESS - "FIELD OF VIEW"

**Priority**: HIGH
**Total Time**: 3 hours
**Dependencies**: None

### Problem Statement

NPCs operate in a vacuum - they don't know:
- Who's standing nearby
- What other NPCs are present
- If guards/hostile mobs are watching

This breaks immersion when NPC should whisper secrets or adjust tone based on witnesses.

### 2.1 Nearby Entities Detection (Client-side)

**Time Estimate**: 2 hours

**Problem**: NPCs don't know who's nearby (can't say "Quiet, the guard is listening")

**Solution**: Scan entities within 16 blocks when player talks to NPC

**Files to Modify**:
- `MIIN/src/main/kotlin/MIIN/listener/dialogue/DialogueManager.kt`

**Implementation**:

```kotlin
// In DialogueManager.kt
fun getNearbyEntities(player: ServerPlayerEntity, radius: Double = 16.0): List<Map<String, Any>> {
    val world = player.world
    val playerPos = player.pos
    val box = Box.of(playerPos, radius, radius, radius)

    val nearbyEntities = mutableListOf<Map<String, Any>>()

    // Scan for entities
    world.getOtherEntities(player, box).forEach { entity ->
        when {
            // MIIN NPCs
            entity is MIINNpcEntity -> {
                nearbyEntities.add(mapOf(
                    "type" to "npc",
                    "id" to entity.npcId,
                    "name" to entity.npcName,
                    "distance" to String.format("%.1f", player.distanceTo(entity))
                ))
            }
            // Other players
            entity is ServerPlayerEntity -> {
                nearbyEntities.add(mapOf(
                    "type" to "player",
                    "name" to entity.name.string,
                    "distance" to String.format("%.1f", player.distanceTo(entity))
                ))
            }
            // Vanilla mobs (guards, golems, hostiles)
            entity is LivingEntity -> {
                val entityType = entity.type.toString()
                nearbyEntities.add(mapOf(
                    "type" to "mob",
                    "mob_type" to entityType,
                    "hostile" to (entity is HostileEntity),
                    "distance" to String.format("%.1f", player.distanceTo(entity))
                ))
            }
        }
    }

    return nearbyEntities
}

// Update startDialogue to include nearby context
fun startDialogue(player: ServerPlayerEntity, npc: MIINNpcEntity) {
    val nearbyEntities = getNearbyEntities(player)

    val payload = mapOf(
        "npc" to npc.npcId,
        "player" to player.name.string,
        "nearby_entities" to nearbyEntities
    )

    // Send to Python backend via HTTP
    httpClient.post("/dialogue/start", payload)
}
```

**Payload Example**:
```json
{
  "npc": "rowan",
  "player": "vDakota",
  "nearby_entities": [
    {"type": "npc", "id": "guard_01", "name": "Guard", "distance": "3.5"},
    {"type": "mob", "mob_type": "minecraft:iron_golem", "hostile": false, "distance": "8.0"},
    {"type": "player", "name": "Alice", "distance": "12.3"}
  ]
}
```

---

### 2.2 Context Integration (Server-side)

**Time Estimate**: 1 hour

**Problem**: Backend doesn't receive or use proximity data

**Solution**: Parse nearby entities and inject into system prompt

**Files to Modify**:
- `npc/scripts/service.py:269-374` (`get_player_context()`)
- `npc/scripts/service.py:build_system_prompt()`

**Implementation**:

```python
# In get_player_context()
def get_player_context(self, player_name: str, nearby_entities: List[Dict] = None) -> Dict:
    """
    Get comprehensive player context including nearby entities

    Args:
        player_name: Player's name
        nearby_entities: List of entities within proximity (from Kotlin)
    """
    context = {
        "location": {...},  # Existing
        "health": {...},    # Existing
        "recent_building": {...},  # Existing
        "nearby_entities": nearby_entities or []  # NEW
    }

    return context

# In build_system_prompt()
def build_system_prompt(self, npc: Dict, player_name: str, context: Dict) -> str:
    prompt = f"""[CHARACTER DEFINITION]
    {npc['name']} - {npc['personality']}
    ...
    """

    # Add proximity awareness
    if context.get('nearby_entities'):
        nearby = self._format_nearby_entities(context['nearby_entities'])
        prompt += f"\n\n[NEARBY ENTITIES]\n{nearby}\n"
        prompt += "IMPORTANT: Adjust your tone if guards, hostile mobs, or other witnesses are present.\n"
        prompt += "Use this context to inform your dialogue (e.g., whisper secrets, avoid guards).\n"

    return prompt

def _format_nearby_entities(self, entities: List[Dict]) -> str:
    """Format nearby entities for LLM context"""
    if not entities:
        return "You are alone with the player."

    formatted = []
    for entity in entities:
        if entity['type'] == 'npc':
            formatted.append(f"- {entity['name']} ({entity['distance']}m away)")
        elif entity['type'] == 'mob':
            hostile = "HOSTILE" if entity.get('hostile') else "passive"
            formatted.append(f"- {entity['mob_type']} ({hostile}, {entity['distance']}m)")
        elif entity['type'] == 'player':
            formatted.append(f"- Player: {entity['name']} ({entity['distance']}m)")

    return "\n".join(formatted)
```

**Example Prompt Injection**:
```
[NEARBY ENTITIES]
- Guard (3.5m away)
- minecraft:iron_golem (passive, 8.0m)

IMPORTANT: Adjust your tone if guards, hostile mobs, or other witnesses are present.
```

**Expected Behavior**:
- Merchant near guard: *"(lowers voice) Not so loud, the guard is right there..."*
- NPC alone with player: Normal dialogue
- Hostile mob nearby: *"Watch out, there's a zombie behind you!"*

---

## 🎯 PHASE 3: NPC PERSISTENCE - "THE SOUL"

**Priority**: HIGH
**Total Time**: 7 hours
**Dependencies**: Phase 2

### Problem Statement

NPCs are ephemeral - if the server restarts or the chunk unloads:
- NPCs lose all state (inventory, reputation, quest progress)
- Conversation memory survives but isn't linked to entity UUID
- Multiple instances of same archetype can collide in memory

This breaks the illusion of a living world where NPCs "remember" and persist.

### 3.1 UUID Registry Database

**Time Estimate**: 3 hours

**Problem**: NPCs don't persist state across server restarts

**Solution**: Database linking entity UUID → NPC identity + state

**Files to Create**:
- `npc/registry/uuid_manager.py`
- `npc/registry/npc_registry.db`

**Database Schema**:
```sql
CREATE TABLE npc_instances (
    uuid TEXT PRIMARY KEY,
    npc_id TEXT NOT NULL,           -- "marina", "rowan", etc.
    archetype_id TEXT,               -- "merchant", "fisher", etc.
    spawn_location TEXT,             -- JSON: {"x": 50, "y": 64, "z": -30}
    spawn_time TEXT,                 -- ISO timestamp
    last_seen TEXT,                  -- Last interaction timestamp
    status TEXT DEFAULT 'alive',     -- 'alive', 'dead', 'despawned'
    custom_data TEXT,                -- JSON blob for extra state
    FOREIGN KEY (npc_id) REFERENCES npcs(id)
);

CREATE INDEX idx_npc_id ON npc_instances(npc_id);
CREATE INDEX idx_status ON npc_instances(status);
```

**Implementation**:

```python
# npc/registry/uuid_manager.py
import sqlite3
import json
from pathlib import Path
from typing import Dict, Optional, List
from datetime import datetime

class UUIDManager:
    """Manages NPC UUID registry and persistence"""

    def __init__(self, db_path: str = None):
        self.root = Path(__file__).parent.parent.parent
        self.db_path = db_path or str(self.root / 'npc' / 'registry' / 'npc_registry.db')
        self.conn = None
        self.init_database()

    def init_database(self):
        """Initialize database with schema"""
        self.conn = sqlite3.connect(self.db_path)
        self.conn.execute('''
            CREATE TABLE IF NOT EXISTS npc_instances (
                uuid TEXT PRIMARY KEY,
                npc_id TEXT NOT NULL,
                archetype_id TEXT,
                spawn_location TEXT,
                spawn_time TEXT,
                last_seen TEXT,
                status TEXT DEFAULT 'alive',
                custom_data TEXT
            )
        ''')
        self.conn.execute('CREATE INDEX IF NOT EXISTS idx_npc_id ON npc_instances(npc_id)')
        self.conn.commit()

    def register_npc(self, uuid: str, npc_id: str, archetype_id: str,
                     spawn_location: Dict, custom_data: Dict = None) -> bool:
        """
        Register a new NPC instance

        Args:
            uuid: Minecraft entity UUID
            npc_id: NPC identifier (e.g., "marina")
            archetype_id: Archetype template (e.g., "fisher")
            spawn_location: {x, y, z, dimension}
            custom_data: Additional state data

        Returns:
            True if registered successfully
        """
        try:
            now = datetime.now().isoformat()
            self.conn.execute('''
                INSERT INTO npc_instances
                (uuid, npc_id, archetype_id, spawn_location, spawn_time, last_seen, custom_data)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            ''', (
                uuid,
                npc_id,
                archetype_id,
                json.dumps(spawn_location),
                now,
                now,
                json.dumps(custom_data or {})
            ))
            self.conn.commit()
            return True
        except sqlite3.IntegrityError:
            # UUID already exists
            return False

    def get_npc_by_uuid(self, uuid: str) -> Optional[Dict]:
        """Retrieve NPC instance by UUID"""
        cursor = self.conn.execute('''
            SELECT * FROM npc_instances WHERE uuid = ?
        ''', (uuid,))

        row = cursor.fetchone()
        if row:
            return {
                'uuid': row[0],
                'npc_id': row[1],
                'archetype_id': row[2],
                'spawn_location': json.loads(row[3]),
                'spawn_time': row[4],
                'last_seen': row[5],
                'status': row[6],
                'custom_data': json.loads(row[7])
            }
        return None

    def update_last_seen(self, uuid: str):
        """Update last_seen timestamp"""
        self.conn.execute('''
            UPDATE npc_instances SET last_seen = ? WHERE uuid = ?
        ''', (datetime.now().isoformat(), uuid))
        self.conn.commit()

    def mark_dead(self, uuid: str):
        """Mark NPC as dead"""
        self.conn.execute('''
            UPDATE npc_instances SET status = 'dead' WHERE uuid = ?
        ''', (uuid,))
        self.conn.commit()

    def get_alive_npcs(self) -> List[Dict]:
        """Get all alive NPC instances"""
        cursor = self.conn.execute('''
            SELECT * FROM npc_instances WHERE status = 'alive'
        ''')

        return [self._row_to_dict(row) for row in cursor.fetchall()]

    def _row_to_dict(self, row) -> Dict:
        """Convert DB row to dict"""
        return {
            'uuid': row[0],
            'npc_id': row[1],
            'archetype_id': row[2],
            'spawn_location': json.loads(row[3]),
            'spawn_time': row[4],
            'last_seen': row[5],
            'status': row[6],
            'custom_data': json.loads(row[7])
        }
```

**Integration with NPC Service**:

```python
# npc/scripts/service.py
from npc.registry.uuid_manager import UUIDManager

class NPCService:
    def __init__(self, ...):
        # ... existing code ...
        self.uuid_manager = UUIDManager()  # NEW

    def spawn_npc(self, npc_id: str, location: Dict, entity_uuid: str):
        """Register spawned NPC in UUID registry"""
        npc = self.npcs.get(npc_id)
        if npc:
            self.uuid_manager.register_npc(
                uuid=entity_uuid,
                npc_id=npc_id,
                archetype_id=npc.get('archetype', 'generic'),
                spawn_location=location
            )
```

---

### 3.2 NBT Persistence (Kotlin)

**Time Estimate**: 2 hours

**Problem**: MIINNpcEntity doesn't save custom data to NBT

**Solution**: Implement `writeNbt()` and `readNbt()` methods

**Files to Modify**:
- `MIIN/src/main/kotlin/MIIN/listener/entity/MIINNpcEntity.kt`

**Implementation**:

```kotlin
// In MIINNpcEntity.kt
override fun writeNbt(nbt: NbtCompound): NbtCompound {
    super.writeNbt(nbt)

    // Save MIIN-specific data
    nbt.putString("MIIN_npc_id", this.npcId)
    nbt.putString("MIIN_npc_name", this.npcName)
    nbt.putString("MIIN_skin_path", this.skinPath)
    nbt.putString("MIIN_archetype", this.archetype)

    // Save behavior state
    nbt.putString("MIIN_behavior_mode", this.behaviorMode.name)
    nbt.putDouble("MIIN_roam_radius", this.roamRadius)

    // Save merchant inventory if applicable
    if (this.merchantInventory != null) {
        val inventoryNbt = NbtList()
        this.merchantInventory.forEach { item ->
            val itemNbt = NbtCompound()
            itemNbt.putString("item", item.item)
            itemNbt.putInt("quantity", item.quantity)
            itemNbt.putInt("price", item.price)
            inventoryNbt.add(itemNbt)
        }
        nbt.put("MIIN_merchant_inventory", inventoryNbt)
    }

    // Save faction affiliation
    if (this.faction != null) {
        nbt.putString("MIIN_faction", this.faction)
    }

    return nbt
}

override fun readNbt(nbt: NbtCompound) {
    super.readNbt(nbt)

    // Restore MIIN-specific data
    this.npcId = nbt.getString("MIIN_npc_id")
    this.npcName = nbt.getString("MIIN_npc_name")
    this.skinPath = nbt.getString("MIIN_skin_path")
    this.archetype = nbt.getString("MIIN_archetype")

    // Restore behavior state
    this.behaviorMode = BehaviorMode.valueOf(
        nbt.getString("MIIN_behavior_mode") ?: "STATIONARY"
    )
    this.roamRadius = nbt.getDouble("MIIN_roam_radius")

    // Restore merchant inventory
    if (nbt.contains("MIIN_merchant_inventory")) {
        val inventoryNbt = nbt.getList("MIIN_merchant_inventory", 10)
        this.merchantInventory = inventoryNbt.map { itemNbt ->
            val compound = itemNbt as NbtCompound
            MerchantItem(
                item = compound.getString("item"),
                quantity = compound.getInt("quantity"),
                price = compound.getInt("price")
            )
        }.toMutableList()
    }

    // Restore faction
    if (nbt.contains("MIIN_faction")) {
        this.faction = nbt.getString("MIIN_faction")
    }

    // Sync to client
    this.syncToClient()
}
```

**Benefits**:
- NPC state survives chunk unload/reload
- Server restarts don't reset NPC data
- Merchant inventory persists
- Faction affiliations maintained

---

### 3.3 Memory System Migration

**Time Estimate**: 2 hours

**Problem**: Memory keyed by `npc_id` (string), not UUID - collision risk

**Solution**: Add UUID → npc_id mapping layer

**Current Structure** (`npc/config/memory.json`):
```json
{
  "marina:vDakota": [
    {"role": "user", "content": "...", "timestamp": "..."}
  ]
}
```

**Collision Scenario**:
- NPC 1: marina (UUID: abc-123)
- NPC 2: marina (UUID: def-456) - different instance, SAME npc_id
- Both use key "marina:vDakota" → memory collision!

**Migrated Structure**:
```json
{
  "abc-123:vDakota": [
    {"role": "user", "content": "...", "timestamp": "..."}
  ],
  "def-456:vDakota": [
    {"role": "user", "content": "...", "timestamp": "..."}
  ]
}
```

**Files to Modify**:
- `npc/scripts/service.py:add_to_memory()`
- `npc/scripts/service.py:get_npc_memory()`
- Create migration script: `npc/scripts/migrate_memory_to_uuid.py`

**Implementation**:

```python
# npc/scripts/service.py
def add_to_memory(self, npc_uuid: str, player_name: str, role: str, content: str):
    """
    Add message to NPC-player conversation memory

    Args:
        npc_uuid: Entity UUID (not npc_id!)
        player_name: Player's name
        role: 'user' or 'assistant'
        content: Message content
    """
    key = f"{npc_uuid}:{player_name}"

    if key not in self.memory:
        self.memory[key] = []

    self.memory[key].append({
        "role": role,
        "content": content,
        "timestamp": datetime.now().isoformat()
    })

    # Prune to last 20 messages
    if len(self.memory[key]) > 20:
        self.memory[key] = self.memory[key][-20:]

    self.save_memory()

def get_npc_memory(self, npc_uuid: str, player_name: str) -> List[Dict]:
    """Get conversation history for NPC-player pair"""
    key = f"{npc_uuid}:{player_name}"
    return self.memory.get(key, [])
```

**Migration Script**:

```python
# npc/scripts/migrate_memory_to_uuid.py
import json
from pathlib import Path
from npc.registry.uuid_manager import UUIDManager

def migrate_memory():
    """
    Migrate memory.json from npc_id:player to uuid:player format

    WARNING: This requires UUID registry to be populated!
    Run this AFTER all NPCs have been spawned and registered.
    """
    root = Path(__file__).parent.parent.parent
    memory_path = root / 'npc' / 'config' / 'memory.json'
    backup_path = root / 'npc' / 'config' / 'memory_backup.json'

    # Load existing memory
    with open(memory_path, 'r') as f:
        old_memory = json.load(f)

    # Backup
    with open(backup_path, 'w') as f:
        json.dump(old_memory, f, indent=2)

    # Load UUID registry
    uuid_manager = UUIDManager()
    alive_npcs = uuid_manager.get_alive_npcs()

    # Build npc_id → uuid mapping
    npc_id_to_uuid = {}
    for npc in alive_npcs:
        npc_id = npc['npc_id']
        uuid = npc['uuid']
        # For multiple instances, use first UUID (or handle collision)
        if npc_id not in npc_id_to_uuid:
            npc_id_to_uuid[npc_id] = uuid

    # Migrate memory
    new_memory = {}
    for old_key, messages in old_memory.items():
        # Parse old key: "marina:vDakota"
        parts = old_key.split(':', 1)
        if len(parts) != 2:
            continue

        npc_id, player = parts
        uuid = npc_id_to_uuid.get(npc_id)

        if uuid:
            new_key = f"{uuid}:{player}"
            new_memory[new_key] = messages
        else:
            print(f"WARNING: No UUID found for npc_id '{npc_id}'. Skipping.")

    # Save migrated memory
    with open(memory_path, 'w') as f:
        json.dump(new_memory, f, indent=2)

    print(f"Migration complete. Migrated {len(new_memory)}/{len(old_memory)} entries.")
    print(f"Backup saved to {backup_path}")

if __name__ == "__main__":
    migrate_memory()
```

**Migration Process**:
1. Ensure all NPCs spawned and registered in UUID registry
2. Run migration script: `python npc/scripts/migrate_memory_to_uuid.py`
3. Verify `memory_backup.json` created
4. Test dialogue to confirm memory works
5. If issues, restore from backup

---

## 🎯 PHASE 4: GAME STATE SERVICE

**Priority**: MEDIUM
**Total Time**: 6 hours
**Dependencies**: Phase 3

### Problem Statement

No centralized tracking of:
- World events (dragon killed, village raided)
- Faction territory control
- Dynamic economy/market prices
- Global quest state

This limits ability to create reactive world where events propagate and affect NPC behavior.

### 4.1 World State Tracker

**Time Estimate**: 4 hours

**Problem**: No centralized tracking of world state

**Solution**: Create game state service with SQLite backend

**Files to Create**:
- `game_state/service.py`
- `game_state/world_state.db`

**Database Schema**:

```sql
-- Global world events
CREATE TABLE world_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    event_type TEXT NOT NULL,        -- 'dragon_killed', 'village_raided', 'boss_defeated'
    location TEXT,                    -- JSON: {x, y, z, dimension}
    timestamp TEXT NOT NULL,
    player_involved TEXT,
    details TEXT,                     -- JSON blob
    propagated BOOLEAN DEFAULT 0      -- Has this spread to NPCs yet?
);

CREATE INDEX idx_event_type ON world_events(event_type);
CREATE INDEX idx_timestamp ON world_events(timestamp);

-- Faction territory control
CREATE TABLE faction_territories (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    faction_id TEXT NOT NULL,
    biome TEXT,                       -- 'minecraft:plains', etc.
    center_location TEXT,             -- JSON: {x, y, z}
    radius INTEGER,                   -- Territory radius in blocks
    control_strength INTEGER,         -- 0-100 (how dominant)
    last_updated TEXT
);

CREATE INDEX idx_faction ON faction_territories(faction_id);

-- Dynamic economy pricing
CREATE TABLE economy_prices (
    item_id TEXT PRIMARY KEY,         -- 'minecraft:wheat'
    base_price INTEGER,               -- Base emerald cost
    current_price INTEGER,            -- Current market price
    supply INTEGER DEFAULT 100,       -- 0-200 (affects price)
    demand INTEGER DEFAULT 100,       -- 0-200 (affects price)
    last_updated TEXT
);

-- Quest states (global)
CREATE TABLE quest_states (
    quest_id TEXT PRIMARY KEY,
    player_name TEXT,
    npc_giver TEXT,                   -- NPC UUID
    status TEXT,                      -- 'active', 'completed', 'failed'
    objectives TEXT,                  -- JSON array
    start_time TEXT,
    completion_time TEXT,
    rewards TEXT                      -- JSON
);

CREATE INDEX idx_player_quests ON quest_states(player_name);
CREATE INDEX idx_quest_status ON quest_states(status);
```

**Service Implementation**:

```python
# game_state/service.py
import sqlite3
import json
from pathlib import Path
from typing import Dict, List, Optional
from datetime import datetime, timedelta

class GameStateService:
    """Centralized world state management"""

    def __init__(self, db_path: str = None):
        self.root = Path(__file__).parent.parent
        self.db_path = db_path or str(self.root / 'game_state' / 'world_state.db')
        self.conn = None
        self.init_database()

    def init_database(self):
        """Initialize database with schema"""
        self.conn = sqlite3.connect(self.db_path)

        # Create tables (see schema above)
        self.conn.executescript('''
            CREATE TABLE IF NOT EXISTS world_events (...);
            CREATE TABLE IF NOT EXISTS faction_territories (...);
            CREATE TABLE IF NOT EXISTS economy_prices (...);
            CREATE TABLE IF NOT EXISTS quest_states (...);
        ''')
        self.conn.commit()

    # === WORLD EVENTS ===

    def log_world_event(self, event_type: str, location: Dict,
                        player_involved: str = None, details: Dict = None):
        """
        Log a significant world event

        Args:
            event_type: 'dragon_killed', 'village_raided', etc.
            location: {x, y, z, dimension}
            player_involved: Player name (optional)
            details: Additional event data
        """
        self.conn.execute('''
            INSERT INTO world_events
            (event_type, location, timestamp, player_involved, details)
            VALUES (?, ?, ?, ?, ?)
        ''', (
            event_type,
            json.dumps(location),
            datetime.now().isoformat(),
            player_involved,
            json.dumps(details or {})
        ))
        self.conn.commit()

    def get_recent_events(self, event_type: str = None,
                          hours: int = 24) -> List[Dict]:
        """Get recent world events"""
        cutoff = (datetime.now() - timedelta(hours=hours)).isoformat()

        if event_type:
            cursor = self.conn.execute('''
                SELECT * FROM world_events
                WHERE event_type = ? AND timestamp > ?
                ORDER BY timestamp DESC
            ''', (event_type, cutoff))
        else:
            cursor = self.conn.execute('''
                SELECT * FROM world_events
                WHERE timestamp > ?
                ORDER BY timestamp DESC
            ''', (cutoff,))

        return [self._event_row_to_dict(row) for row in cursor.fetchall()]

    def get_unpropagated_events(self) -> List[Dict]:
        """Get events that haven't spread to NPCs yet"""
        cursor = self.conn.execute('''
            SELECT * FROM world_events
            WHERE propagated = 0
            ORDER BY timestamp ASC
        ''')

        return [self._event_row_to_dict(row) for row in cursor.fetchall()]

    def mark_event_propagated(self, event_id: int):
        """Mark event as propagated to NPCs"""
        self.conn.execute('''
            UPDATE world_events SET propagated = 1 WHERE id = ?
        ''', (event_id,))
        self.conn.commit()

    # === ECONOMY ===

    def update_market_price(self, item_id: str, supply_delta: int = 0,
                           demand_delta: int = 0):
        """
        Update market price based on supply/demand

        Price formula: base_price * (demand/100) / (supply/100)
        """
        cursor = self.conn.execute('''
            SELECT * FROM economy_prices WHERE item_id = ?
        ''', (item_id,))

        row = cursor.fetchone()
        if not row:
            # Initialize item if not exists
            self.conn.execute('''
                INSERT INTO economy_prices
                (item_id, base_price, current_price, supply, demand, last_updated)
                VALUES (?, 10, 10, 100, 100, ?)
            ''', (item_id, datetime.now().isoformat()))
            self.conn.commit()
            return

        # Update supply/demand
        supply = max(10, min(200, row[3] + supply_delta))
        demand = max(10, min(200, row[4] + demand_delta))

        # Calculate new price
        base_price = row[1]
        new_price = int(base_price * (demand / 100.0) / (supply / 100.0))

        self.conn.execute('''
            UPDATE economy_prices
            SET current_price = ?, supply = ?, demand = ?, last_updated = ?
            WHERE item_id = ?
        ''', (new_price, supply, demand, datetime.now().isoformat(), item_id))
        self.conn.commit()

    def get_market_price(self, item_id: str) -> int:
        """Get current market price for item"""
        cursor = self.conn.execute('''
            SELECT current_price FROM economy_prices WHERE item_id = ?
        ''', (item_id,))

        row = cursor.fetchone()
        return row[0] if row else 10  # Default price

    # === QUESTS ===

    def start_quest(self, quest_id: str, player_name: str,
                    npc_giver: str, objectives: List[Dict],
                    rewards: Dict):
        """Start a quest for player"""
        self.conn.execute('''
            INSERT INTO quest_states
            (quest_id, player_name, npc_giver, status, objectives, start_time, rewards)
            VALUES (?, ?, ?, 'active', ?, ?, ?)
        ''', (
            quest_id,
            player_name,
            npc_giver,
            json.dumps(objectives),
            datetime.now().isoformat(),
            json.dumps(rewards)
        ))
        self.conn.commit()

    def complete_quest(self, quest_id: str):
        """Mark quest as completed"""
        self.conn.execute('''
            UPDATE quest_states
            SET status = 'completed', completion_time = ?
            WHERE quest_id = ?
        ''', (datetime.now().isoformat(), quest_id))
        self.conn.commit()

    def get_active_quests(self, player_name: str) -> List[Dict]:
        """Get player's active quests"""
        cursor = self.conn.execute('''
            SELECT * FROM quest_states
            WHERE player_name = ? AND status = 'active'
        ''', (player_name,))

        return [self._quest_row_to_dict(row) for row in cursor.fetchall()]

    # Helper methods
    def _event_row_to_dict(self, row) -> Dict:
        return {
            'id': row[0],
            'event_type': row[1],
            'location': json.loads(row[2]),
            'timestamp': row[3],
            'player_involved': row[4],
            'details': json.loads(row[5]),
            'propagated': bool(row[6])
        }

    def _quest_row_to_dict(self, row) -> Dict:
        return {
            'quest_id': row[0],
            'player_name': row[1],
            'npc_giver': row[2],
            'status': row[3],
            'objectives': json.loads(row[4]),
            'start_time': row[5],
            'completion_time': row[6],
            'rewards': json.loads(row[7])
        }
```

**Integration Example**:

```python
# When player kills Ender Dragon
from game_state.service import GameStateService

game_state = GameStateService()

game_state.log_world_event(
    event_type='dragon_killed',
    location={'x': 0, 'y': 64, 'z': 0, 'dimension': 'the_end'},
    player_involved='vDakota',
    details={'dragon_name': 'Ender Dragon'}
)

# Later, NPCs can reference this:
recent_events = game_state.get_recent_events(hours=48)
for event in recent_events:
    if event['event_type'] == 'dragon_killed':
        # NPC dialogue: "Did you hear? vDakota slayed the dragon!"
```

---

### 4.2 Event Reactor Revival

**Time Estimate**: 2 hours

**Problem**: `events/reactor.py` was deleted, event processing unclear

**Solution**: Restore or recreate event monitoring system

**Files to Verify**:
- `events/minecraft_events.json` - Check if still being written to
- `src/index.ts` - Verify event reactor spawn

**Current Status** (from git):
```
D events/reactor.py  # Marked for deletion
M events/minecraft_events.json  # Modified (still in use!)
```

**Investigation Needed**:
1. Is `minecraft_events.json` still receiving events?
2. What writes to it? (Kotlin mod? MCP server?)
3. Is event processing needed?

**If Event Processing Needed**:

Create `events/processor.py`:

```python
# events/processor.py
import json
import time
from pathlib import Path
from typing import Dict, List
from game_state.service import GameStateService

class EventProcessor:
    """Background processor for Minecraft events"""

    def __init__(self):
        self.root = Path(__file__).parent.parent
        self.events_path = self.root / 'events' / 'minecraft_events.json'
        self.game_state = GameStateService()
        self.processed_events = set()

    def process_events(self):
        """Process new events from minecraft_events.json"""
        try:
            with open(self.events_path, 'r') as f:
                events = json.load(f)
        except FileNotFoundError:
            events = []

        for event in events:
            event_id = event.get('id')
            if event_id in self.processed_events:
                continue

            # Process based on event type
            event_type = event.get('type')

            if event_type == 'mob_killed':
                self._handle_mob_killed(event)
            elif event_type == 'build_complete':
                self._handle_build_complete(event)
            elif event_type == 'player_death':
                self._handle_player_death(event)

            self.processed_events.add(event_id)

    def _handle_mob_killed(self, event: Dict):
        """Handle mob killed event"""
        mob_type = event.get('mob_type')

        # Log significant kills to game state
        if mob_type in ['ender_dragon', 'wither']:
            self.game_state.log_world_event(
                event_type=f'{mob_type}_killed',
                location=event.get('location'),
                player_involved=event.get('player'),
                details={'mob_type': mob_type}
            )

    def _handle_build_complete(self, event: Dict):
        """Handle build complete event"""
        # Update builder reputation, faction standing, etc.
        pass

    def _handle_player_death(self, event: Dict):
        """Handle player death event"""
        # Log death location, cause, update world state
        pass

    def run(self, interval: int = 5):
        """Run event processor in loop"""
        print("[EventProcessor] Starting...")
        while True:
            self.process_events()
            time.sleep(interval)

if __name__ == "__main__":
    processor = EventProcessor()
    processor.run()
```

**Integration**:
- Run as background process: `python events/processor.py &`
- OR spawn from MCP server: `src/index.ts` (like old reactor)

---

## 🎯 PHASE 5: MERCHANT SYSTEM MVP

**Priority**: MEDIUM
**Total Time**: 6 hours
**Dependencies**: Phase 3 (UUID registry)

### Problem Statement

No trading functionality exists. NPCs mention being merchants but can't:
- Display inventory
- Accept purchases
- Price items
- Track stock

This breaks immersion when merchant NPCs exist but can't trade.

### 5.1 Static Merchant Inventory

**Time Estimate**: 4 hours

**Problem**: No trade functionality exists

**Solution**: Implement basic buy/sell with fixed prices

**Files to Create**:
- `npc/merchant/service.py`
- `npc/merchant/inventory.json`
- `npc/merchant/trades.db` (transaction history)

**Inventory Structure** (`npc/merchant/inventory.json`):

```json
{
  "rowan": {
    "role": "general_merchant",
    "stock": [
      {
        "item": "minecraft:wheat",
        "quantity": 64,
        "price_buy": 5,
        "price_sell": 3,
        "restock_rate": 16,
        "max_stock": 128
      },
      {
        "item": "minecraft:bread",
        "quantity": 32,
        "price_buy": 10,
        "price_sell": 6,
        "restock_rate": 8,
        "max_stock": 64
      },
      {
        "item": "minecraft:iron_sword",
        "quantity": 3,
        "price_buy": 50,
        "price_sell": 25,
        "restock_rate": 1,
        "max_stock": 5
      }
    ],
    "currency": "minecraft:emerald",
    "last_restock": "2025-01-21T10:00:00Z"
  },
  "marina": {
    "role": "fisher",
    "stock": [
      {
        "item": "minecraft:cod",
        "quantity": 48,
        "price_buy": 3,
        "price_sell": 1
      },
      {
        "item": "minecraft:fishing_rod",
        "quantity": 5,
        "price_buy": 15,
        "price_sell": 7
      }
    ],
    "currency": "minecraft:emerald",
    "last_restock": "2025-01-21T08:00:00Z"
  }
}
```

**Service Implementation**:

```python
# npc/merchant/service.py
import json
import sqlite3
from pathlib import Path
from typing import Dict, List, Optional
from datetime import datetime, timedelta

class MerchantService:
    """Handles NPC trading and inventory management"""

    def __init__(self, inventory_path: str = None, db_path: str = None):
        self.root = Path(__file__).parent.parent.parent
        self.inventory_path = inventory_path or str(self.root / 'npc' / 'merchant' / 'inventory.json')
        self.db_path = db_path or str(self.root / 'npc' / 'merchant' / 'trades.db')

        self.inventory = self.load_inventory()
        self.init_database()

    def load_inventory(self) -> Dict:
        """Load merchant inventories"""
        try:
            with open(self.inventory_path, 'r') as f:
                return json.load(f)
        except FileNotFoundError:
            return {}

    def save_inventory(self):
        """Save merchant inventories"""
        with open(self.inventory_path, 'w') as f:
            json.dump(self.inventory, f, indent=2)

    def init_database(self):
        """Initialize trades database"""
        self.conn = sqlite3.connect(self.db_path)
        self.conn.execute('''
            CREATE TABLE IF NOT EXISTS trades (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                merchant_npc_id TEXT,
                player_name TEXT,
                trade_type TEXT,  -- 'buy', 'sell'
                item_id TEXT,
                quantity INTEGER,
                price_per_item INTEGER,
                total_price INTEGER,
                timestamp TEXT
            )
        ''')
        self.conn.commit()

    # === BROWSING ===

    def get_merchant_inventory(self, npc_id: str) -> Optional[Dict]:
        """
        Get merchant's current inventory

        Returns:
            {
                "role": "general_merchant",
                "stock": [{"item": "...", "quantity": 64, "price_buy": 5}],
                "currency": "minecraft:emerald"
            }
        """
        merchant = self.inventory.get(npc_id)
        if not merchant:
            return None

        # Auto-restock if needed
        self._auto_restock(npc_id)

        return merchant

    def _auto_restock(self, npc_id: str):
        """Automatically restock merchant inventory over time"""
        merchant = self.inventory.get(npc_id)
        if not merchant:
            return

        last_restock = datetime.fromisoformat(merchant.get('last_restock', datetime.now().isoformat()))
        hours_since_restock = (datetime.now() - last_restock).total_seconds() / 3600

        if hours_since_restock < 1:
            return  # Too soon

        # Restock items
        restocked = False
        for item in merchant['stock']:
            restock_rate = item.get('restock_rate', 0)
            max_stock = item.get('max_stock', 999)
            current_quantity = item['quantity']

            if restock_rate > 0 and current_quantity < max_stock:
                # Restock based on hours passed
                restock_amount = int(hours_since_restock * restock_rate)
                item['quantity'] = min(max_stock, current_quantity + restock_amount)
                restocked = True

        if restocked:
            merchant['last_restock'] = datetime.now().isoformat()
            self.save_inventory()

    # === BUYING (Player buys from merchant) ===

    def buy_item(self, merchant_npc_id: str, player_name: str,
                 item_id: str, quantity: int) -> Dict:
        """
        Player buys item from merchant

        Returns:
            {
                "success": True,
                "item": "minecraft:wheat",
                "quantity": 16,
                "total_price": 80,
                "currency": "minecraft:emerald"
            }
        """
        merchant = self.inventory.get(merchant_npc_id)
        if not merchant:
            return {"success": False, "error": "Merchant not found"}

        # Find item in stock
        stock_item = None
        for item in merchant['stock']:
            if item['item'] == item_id:
                stock_item = item
                break

        if not stock_item:
            return {"success": False, "error": "Item not in stock"}

        # Check quantity available
        if stock_item['quantity'] < quantity:
            return {
                "success": False,
                "error": f"Not enough stock. Only {stock_item['quantity']} available."
            }

        # Calculate price
        price_per_item = stock_item['price_buy']
        total_price = price_per_item * quantity

        # TODO: Check player has enough currency (requires player inventory integration)

        # Update stock
        stock_item['quantity'] -= quantity
        self.save_inventory()

        # Log transaction
        self._log_trade(merchant_npc_id, player_name, 'buy', item_id,
                       quantity, price_per_item, total_price)

        return {
            "success": True,
            "item": item_id,
            "quantity": quantity,
            "price_per_item": price_per_item,
            "total_price": total_price,
            "currency": merchant['currency']
        }

    # === SELLING (Player sells to merchant) ===

    def sell_item(self, merchant_npc_id: str, player_name: str,
                  item_id: str, quantity: int) -> Dict:
        """
        Player sells item to merchant

        Returns:
            {
                "success": True,
                "item": "minecraft:wheat",
                "quantity": 16,
                "total_payment": 48,
                "currency": "minecraft:emerald"
            }
        """
        merchant = self.inventory.get(merchant_npc_id)
        if not merchant:
            return {"success": False, "error": "Merchant not found"}

        # Find item in stock (merchant must accept this item)
        stock_item = None
        for item in merchant['stock']:
            if item['item'] == item_id:
                stock_item = item
                break

        if not stock_item:
            return {"success": False, "error": "Merchant doesn't buy this item"}

        # Check max stock limit
        max_stock = stock_item.get('max_stock', 999)
        if stock_item['quantity'] + quantity > max_stock:
            return {"success": False, "error": "Merchant already has too much stock"}

        # Calculate payment
        price_per_item = stock_item['price_sell']
        total_payment = price_per_item * quantity

        # Update stock
        stock_item['quantity'] += quantity
        self.save_inventory()

        # Log transaction
        self._log_trade(merchant_npc_id, player_name, 'sell', item_id,
                       quantity, price_per_item, total_payment)

        return {
            "success": True,
            "item": item_id,
            "quantity": quantity,
            "price_per_item": price_per_item,
            "total_payment": total_payment,
            "currency": merchant['currency']
        }

    # === TRANSACTION LOGGING ===

    def _log_trade(self, merchant_npc_id: str, player_name: str,
                   trade_type: str, item_id: str, quantity: int,
                   price_per_item: int, total_price: int):
        """Log trade to database"""
        self.conn.execute('''
            INSERT INTO trades
            (merchant_npc_id, player_name, trade_type, item_id,
             quantity, price_per_item, total_price, timestamp)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ''', (
            merchant_npc_id,
            player_name,
            trade_type,
            item_id,
            quantity,
            price_per_item,
            total_price,
            datetime.now().isoformat()
        ))
        self.conn.commit()

    def get_trade_history(self, merchant_npc_id: str = None,
                         player_name: str = None, limit: int = 50) -> List[Dict]:
        """Get trade history"""
        query = 'SELECT * FROM trades'
        params = []

        if merchant_npc_id:
            query += ' WHERE merchant_npc_id = ?'
            params.append(merchant_npc_id)
        elif player_name:
            query += ' WHERE player_name = ?'
            params.append(player_name)

        query += ' ORDER BY timestamp DESC LIMIT ?'
        params.append(limit)

        cursor = self.conn.execute(query, params)

        trades = []
        for row in cursor.fetchall():
            trades.append({
                'id': row[0],
                'merchant_npc_id': row[1],
                'player_name': row[2],
                'trade_type': row[3],
                'item_id': row[4],
                'quantity': row[5],
                'price_per_item': row[6],
                'total_price': row[7],
                'timestamp': row[8]
            })

        return trades
```

**MCP Tool Integration**:

Add to `src/index.ts`:

```typescript
// minecraft_merchant_browse
server.tool(
  "minecraft_merchant_browse",
  "Browse merchant's inventory",
  {
    npc: z.string().describe("NPC identifier (e.g., 'rowan')"),
    player: z.string().describe("Player name")
  },
  async ({ npc, player }) => {
    const scriptPath = `${process.cwd()}/npc/merchant/browse.py`;
    const result = await exec(`python "${scriptPath}" "${npc}" "${player}"`);

    return {
      content: [{ type: "text", text: result.stdout }]
    };
  }
);

// minecraft_merchant_buy
server.tool(
  "minecraft_merchant_buy",
  "Purchase item from merchant",
  {
    npc: z.string().describe("Merchant NPC ID"),
    player: z.string().describe("Player name"),
    item: z.string().describe("Item ID (e.g., 'minecraft:wheat')"),
    quantity: z.number().describe("Quantity to buy")
  },
  async ({ npc, player, item, quantity }) => {
    const scriptPath = `${process.cwd()}/npc/merchant/buy.py`;
    const escapedItem = item.replace(/"/g, '\\"');
    const result = await exec(
      `python "${scriptPath}" "${npc}" "${player}" "${escapedItem}" ${quantity}`
    );

    return {
      content: [{ type: "text", text: result.stdout }]
    };
  }
);

// minecraft_merchant_sell
server.tool(
  "minecraft_merchant_sell",
  "Sell item to merchant",
  {
    npc: z.string().describe("Merchant NPC ID"),
    player: z.string().describe("Player name"),
    item: z.string().describe("Item ID"),
    quantity: z.number().describe("Quantity to sell")
  },
  async ({ npc, player, item, quantity }) => {
    const scriptPath = `${process.cwd()}/npc/merchant/sell.py`;
    const escapedItem = item.replace(/"/g, '\\"');
    const result = await exec(
      `python "${scriptPath}" "${npc}" "${player}" "${escapedItem}" ${quantity}`
    );

    return {
      content: [{ type: "text", text: result.stdout }]
    };
  }
);
```

**Python Entry Scripts**:

```python
# npc/merchant/browse.py
import sys
from npc.merchant.service import MerchantService

if __name__ == "__main__":
    npc_id = sys.argv[1]
    player = sys.argv[2]

    merchant_service = MerchantService()
    inventory = merchant_service.get_merchant_inventory(npc_id)

    if not inventory:
        print(f"ERROR: {npc_id} is not a merchant")
        sys.exit(1)

    # Format for display
    print(f"=== {npc_id.upper()}'S SHOP ===")
    print(f"Currency: {inventory['currency']}\n")

    for item in inventory['stock']:
        print(f"{item['item']}: {item['quantity']} in stock")
        print(f"  Buy: {item['price_buy']} emeralds")
        print(f"  Sell: {item['price_sell']} emeralds")
        print()
```

```python
# npc/merchant/buy.py
import sys
import json
from npc.merchant.service import MerchantService

if __name__ == "__main__":
    npc_id = sys.argv[1]
    player = sys.argv[2]
    item_id = sys.argv[3]
    quantity = int(sys.argv[4])

    merchant_service = MerchantService()
    result = merchant_service.buy_item(npc_id, player, item_id, quantity)

    print(json.dumps(result, indent=2))
```

---

### 5.2 Reputation-Based Pricing

**Time Estimate**: 2 hours

**Problem**: All players pay same price

**Solution**: Integrate relationship system with merchant pricing

**Files to Modify**:
- `npc/merchant/service.py`
- `npc/config/relationships.json` (existing)

**Implementation**:

```python
# npc/merchant/service.py
from pathlib import Path
import json

class MerchantService:
    def __init__(self, ...):
        # ... existing code ...
        self.relationships_path = str(self.root / 'npc' / 'config' / 'relationships.json')
        self.relationships = self.load_relationships()

    def load_relationships(self) -> Dict:
        """Load player-NPC relationships"""
        try:
            with open(self.relationships_path, 'r') as f:
                return json.load(f)
        except FileNotFoundError:
            return {}

    def calculate_price(self, base_price: int, merchant_npc_id: str,
                       player_name: str, trade_type: str) -> int:
        """
        Calculate price with reputation discount/markup

        Args:
            base_price: Base item price
            merchant_npc_id: Merchant NPC ID
            player_name: Player name
            trade_type: 'buy' or 'sell'

        Returns:
            Adjusted price
        """
        # Get relationship level
        relationship_key = f"{merchant_npc_id}:{player_name}"
        relationship = self.relationships.get(relationship_key, {})
        relationship_level = relationship.get('level', 0)  # -100 to +100

        # Calculate discount percentage
        # -100 (Enemy): +50% markup (worse prices)
        # 0 (Stranger): No change
        # +100 (Trusted Ally): -50% discount (better prices)

        discount_percentage = relationship_level / 200.0  # -0.5 to +0.5

        if trade_type == 'buy':
            # Buying: negative discount = lower price
            final_price = int(base_price * (1 - discount_percentage))
        else:  # sell
            # Selling: positive discount = higher payment
            final_price = int(base_price * (1 + discount_percentage))

        # Ensure minimum price of 1
        return max(1, final_price)

    def buy_item(self, merchant_npc_id: str, player_name: str,
                 item_id: str, quantity: int) -> Dict:
        """Player buys item from merchant (WITH REPUTATION PRICING)"""
        # ... existing validation code ...

        # Calculate price WITH reputation
        base_price = stock_item['price_buy']
        adjusted_price = self.calculate_price(
            base_price,
            merchant_npc_id,
            player_name,
            'buy'
        )
        total_price = adjusted_price * quantity

        # ... rest of buy logic ...

        return {
            "success": True,
            "item": item_id,
            "quantity": quantity,
            "base_price": base_price,
            "adjusted_price": adjusted_price,  # Show discount!
            "total_price": total_price,
            "reputation_discount": base_price - adjusted_price
        }
```

**Relationship Tiers and Pricing**:

| Relationship Level | Title | Buy Price | Sell Price | Example |
|-------------------|-------|-----------|------------|---------|
| -100 | Enemy | +50% | -50% | 10 emeralds → 15 |
| -50 | Distrusted | +25% | -25% | 10 emeralds → 12 |
| 0 | Stranger | Base | Base | 10 emeralds → 10 |
| +50 | Friend | -25% | +25% | 10 emeralds → 7 |
| +100 | Trusted Ally | -50% | +50% | 10 emeralds → 5 |

**Testing**:
1. New player (0 relationship) → Base prices
2. Help merchant complete quest (+30 relationship) → Prices improve
3. Attack merchant (-50 relationship) → Prices worsen
4. Reach Trusted Ally (+100) → 50% discount

---

## 🎯 PHASE 6: FACTION SYSTEM

**Priority**: LOW
**Total Time**: 4 hours
**Dependencies**: Phase 5 (merchant system)

### Problem Statement

Helping one NPC doesn't affect others. No concept of:
- Faction membership (Traders Guild, Builders Union)
- Faction-wide reputation (help one merchant → all merchants like you)
- Faction conflicts (Villagers vs Pillagers)
- Faction perks (guild discounts, special quests)

This limits depth of social simulation.

### 6.1 Faction Configuration

**Time Estimate**: 1 hour

**Problem**: No faction affiliations exist

**Solution**: Define factions and assign NPCs

**Files to Create**:
- `factions/config.json`

**Structure**:

```json
{
  "factions": {
    "traders_guild": {
      "name": "Traders Guild",
      "description": "Merchants and traders who value fair deals and commerce",
      "members": ["rowan", "merchant_wealthy_01", "merchant_wealthy_02"],
      "allies": ["builders_union"],
      "enemies": ["outcast_bandits"],
      "neutral": ["fishers_lodge"],
      "perks": {
        "25": {
          "name": "Known Trader",
          "benefit": "5% discount at all guild shops"
        },
        "50": {
          "name": "Valued Customer",
          "benefit": "10% discount + access to rare items"
        },
        "100": {
          "name": "Guild Honorary Member",
          "benefit": "20% discount + exclusive quests"
        }
      }
    },
    "builders_union": {
      "name": "Builders Union",
      "description": "Craftsmen and architects who value skill and creativity",
      "members": ["builder_01", "architect_npc"],
      "allies": ["traders_guild"],
      "enemies": [],
      "neutral": ["fishers_lodge", "outcast_bandits"],
      "perks": {
        "50": {
          "name": "Apprentice Builder",
          "benefit": "Access to advanced building quests"
        },
        "100": {
          "name": "Master Builder",
          "benefit": "Unique building materials and blueprints"
        }
      }
    },
    "fishers_lodge": {
      "name": "Fishers Lodge",
      "description": "Fishermen and sailors who value the sea and its bounty",
      "members": ["marina", "fisher_02"],
      "allies": [],
      "enemies": [],
      "neutral": ["traders_guild", "builders_union"],
      "perks": {
        "50": {
          "name": "Sea Friend",
          "benefit": "Better fishing rod enchantments"
        }
      }
    },
    "outcast_bandits": {
      "name": "Outcast Bandits",
      "description": "Outlaws and thieves who operate outside the law",
      "members": ["bandit_king", "thief_npc"],
      "allies": [],
      "enemies": ["traders_guild", "village_guards"],
      "neutral": [],
      "perks": {
        "50": {
          "name": "Known Fence",
          "benefit": "Buy stolen goods at black market prices"
        }
      }
    },
    "village_guards": {
      "name": "Village Guards",
      "description": "Protectors of villages and law enforcement",
      "members": ["guard_01", "guard_02"],
      "allies": ["traders_guild", "builders_union"],
      "enemies": ["outcast_bandits"],
      "neutral": [],
      "perks": {
        "50": {
          "name": "Deputy",
          "benefit": "Access to guard equipment and quests"
        }
      }
    }
  }
}
```

**NPC Configuration Update**:

Add `faction` field to `npc/config/npcs.json`:

```json
{
  "npcs": [
    {
      "id": "rowan",
      "name": "Rowan",
      "faction": "traders_guild",  // NEW
      ...
    },
    {
      "id": "marina",
      "name": "Marina",
      "faction": "fishers_lodge",  // NEW
      ...
    }
  ]
}
```

---

### 6.2 Faction Reputation Service

**Time Estimate**: 3 hours

**Problem**: Helping one NPC doesn't affect faction standing

**Solution**: Track per-faction reputation with cascade effects

**Files to Create**:
- `factions/service.py`
- `factions/player_standings.db`

**Database Schema**:

```sql
CREATE TABLE faction_standings (
    player_name TEXT,
    faction_id TEXT,
    reputation INTEGER DEFAULT 0,  -- -100 to +100
    last_updated TEXT,
    PRIMARY KEY (player_name, faction_id)
);

CREATE TABLE reputation_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_name TEXT,
    faction_id TEXT,
    change INTEGER,  -- +5, -10, etc.
    reason TEXT,     -- "Helped merchant", "Attacked member"
    timestamp TEXT
);

CREATE INDEX idx_player_standings ON faction_standings(player_name);
CREATE INDEX idx_faction_events ON reputation_events(faction_id);
```

**Service Implementation**:

```python
# factions/service.py
import json
import sqlite3
from pathlib import Path
from typing import Dict, List, Optional
from datetime import datetime

class FactionService:
    """Manages faction affiliations and reputation"""

    def __init__(self, config_path: str = None, db_path: str = None):
        self.root = Path(__file__).parent.parent
        self.config_path = config_path or str(self.root / 'factions' / 'config.json')
        self.db_path = db_path or str(self.root / 'factions' / 'player_standings.db')

        self.factions = self.load_factions()
        self.init_database()

    def load_factions(self) -> Dict:
        """Load faction configuration"""
        try:
            with open(self.config_path, 'r') as f:
                data = json.load(f)
                return data.get('factions', {})
        except FileNotFoundError:
            return {}

    def init_database(self):
        """Initialize faction standings database"""
        self.conn = sqlite3.connect(self.db_path)
        self.conn.executescript('''
            CREATE TABLE IF NOT EXISTS faction_standings (
                player_name TEXT,
                faction_id TEXT,
                reputation INTEGER DEFAULT 0,
                last_updated TEXT,
                PRIMARY KEY (player_name, faction_id)
            );

            CREATE TABLE IF NOT EXISTS reputation_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                player_name TEXT,
                faction_id TEXT,
                change INTEGER,
                reason TEXT,
                timestamp TEXT
            );
        ''')
        self.conn.commit()

    # === REPUTATION MANAGEMENT ===

    def get_npc_faction(self, npc_id: str) -> Optional[str]:
        """
        Get NPC's faction affiliation

        This would ideally load from npc/config/npcs.json
        For now, return None (to be integrated)
        """
        # TODO: Integrate with NPC config
        return None

    def adjust_reputation(self, player_name: str, faction_id: str,
                         change: int, reason: str):
        """
        Adjust player's reputation with faction

        Args:
            player_name: Player name
            faction_id: Faction identifier
            change: Reputation change (+5, -10, etc.)
            reason: Why reputation changed
        """
        # Get current reputation
        current_rep = self.get_faction_standing(player_name, faction_id)
        new_rep = max(-100, min(100, current_rep + change))

        # Update database
        self.conn.execute('''
            INSERT OR REPLACE INTO faction_standings
            (player_name, faction_id, reputation, last_updated)
            VALUES (?, ?, ?, ?)
        ''', (player_name, faction_id, new_rep, datetime.now().isoformat()))

        # Log event
        self.conn.execute('''
            INSERT INTO reputation_events
            (player_name, faction_id, change, reason, timestamp)
            VALUES (?, ?, ?, ?, ?)
        ''', (player_name, faction_id, change, reason, datetime.now().isoformat()))

        self.conn.commit()

    def cascade_npc_relationship_change(self, player_name: str, npc_id: str,
                                       relationship_change: int):
        """
        Cascade NPC relationship change to faction reputation

        When player helps/harms an NPC, their faction reputation also changes
        (at a reduced rate: 50% of NPC relationship change)

        Args:
            player_name: Player name
            npc_id: NPC who was helped/harmed
            relationship_change: How much NPC relationship changed
        """
        npc_faction = self.get_npc_faction(npc_id)
        if not npc_faction:
            return  # NPC has no faction

        # Cascade at 50% rate
        faction_change = int(relationship_change * 0.5)

        if faction_change != 0:
            reason = f"Interaction with {npc_id} ({relationship_change:+d})"
            self.adjust_reputation(player_name, npc_faction, faction_change, reason)

            # Also affect allies/enemies
            self._cascade_to_allies_enemies(player_name, npc_faction, faction_change)

    def _cascade_to_allies_enemies(self, player_name: str, faction_id: str,
                                   base_change: int):
        """
        Cascade reputation changes to allied/enemy factions

        - Allies: +25% of change
        - Enemies: -50% of change (opposite direction)
        """
        faction = self.factions.get(faction_id)
        if not faction:
            return

        # Allies benefit slightly
        for ally_faction_id in faction.get('allies', []):
            ally_change = int(base_change * 0.25)
            if ally_change != 0:
                self.adjust_reputation(
                    player_name,
                    ally_faction_id,
                    ally_change,
                    f"Allied with {faction_id}"
                )

        # Enemies suffer opposite effect
        for enemy_faction_id in faction.get('enemies', []):
            enemy_change = int(-base_change * 0.5)
            if enemy_change != 0:
                self.adjust_reputation(
                    player_name,
                    enemy_faction_id,
                    enemy_change,
                    f"Enemy of {faction_id}"
                )

    def get_faction_standing(self, player_name: str, faction_id: str) -> int:
        """Get player's reputation with faction (-100 to +100)"""
        cursor = self.conn.execute('''
            SELECT reputation FROM faction_standings
            WHERE player_name = ? AND faction_id = ?
        ''', (player_name, faction_id))

        row = cursor.fetchone()
        return row[0] if row else 0

    def get_all_faction_standings(self, player_name: str) -> Dict[str, int]:
        """Get player's reputation with ALL factions"""
        cursor = self.conn.execute('''
            SELECT faction_id, reputation FROM faction_standings
            WHERE player_name = ?
        ''', (player_name,))

        standings = {}
        for row in cursor.fetchall():
            standings[row[0]] = row[1]

        return standings

    def get_unlocked_perks(self, player_name: str, faction_id: str) -> List[Dict]:
        """Get faction perks player has unlocked"""
        reputation = self.get_faction_standing(player_name, faction_id)
        faction = self.factions.get(faction_id)

        if not faction:
            return []

        unlocked = []
        for threshold, perk in faction.get('perks', {}).items():
            if reputation >= int(threshold):
                unlocked.append({
                    'threshold': int(threshold),
                    'name': perk['name'],
                    'benefit': perk['benefit']
                })

        return unlocked
```

**Integration with Relationship System**:

Modify `npc/config/relationships.json` handling to trigger faction cascade:

```python
# In dialogue/service.py (or wherever relationships are updated)
from factions.service import FactionService

def update_relationship(npc_id: str, player_name: str, change: int):
    """Update NPC relationship AND cascade to faction"""
    # ... existing relationship update code ...

    # Cascade to faction (NEW)
    faction_service = FactionService()
    faction_service.cascade_npc_relationship_change(player_name, npc_id, change)
```

**Example Flow**:

```python
# Player helps Rowan (traders_guild merchant) with quest
# +10 relationship with Rowan

# Cascades:
# 1. Traders Guild: +5 reputation (50% of +10)
# 2. Builders Union (ally): +1 reputation (25% of +5)
# 3. Outcast Bandits (enemy): -2 reputation (opposite, 50% of +5)

# Player now has:
# - Rowan relationship: +10
# - Traders Guild reputation: +5
# - Builders Union reputation: +1
# - Outcast Bandits reputation: -2
```

**Merchant Pricing Integration**:

Update `npc/merchant/service.py` to check BOTH relationship AND faction:

```python
def calculate_price(self, base_price: int, merchant_npc_id: str,
                   player_name: str, trade_type: str) -> int:
    """Calculate price with BOTH relationship and faction discounts"""
    # Individual relationship discount
    relationship_discount = self._get_relationship_discount(merchant_npc_id, player_name)

    # Faction discount (NEW)
    faction_discount = self._get_faction_discount(merchant_npc_id, player_name)

    # Combined discount (additive)
    total_discount = relationship_discount + faction_discount

    # Apply to price
    if trade_type == 'buy':
        final_price = int(base_price * (1 - total_discount))
    else:
        final_price = int(base_price * (1 + total_discount))

    return max(1, final_price)

def _get_faction_discount(self, merchant_npc_id: str, player_name: str) -> float:
    """Get discount based on faction standing"""
    from factions.service import FactionService

    faction_service = FactionService()
    npc_faction = faction_service.get_npc_faction(merchant_npc_id)

    if not npc_faction:
        return 0.0

    reputation = faction_service.get_faction_standing(player_name, npc_faction)

    # Reputation → discount conversion
    # 0 rep = 0% discount
    # 50 rep = 10% discount
    # 100 rep = 20% discount (cap at faction level)

    return min(0.2, reputation / 500.0)  # Max 20% faction discount
```

**Testing**:
1. Help 3 traders → Traders Guild reputation rises
2. Check all traders → Prices improve at ALL shops
3. Attack bandit → Traders Guild improves, Bandits worsen
4. Reach faction perk threshold → Unlock special items/quests

---

## 🎯 PHASE 7: ADVANCED FEATURES (FUTURE)

**Priority**: LOW
**Total Time**: 17+ hours
**Dependencies**: Phases 1-6

These are stretch goals for future development. Implement only after core systems are stable.

### 7.1 Beast Speech System

**Time Estimate**: 3 hours

**Concept**: Talk to mobs (cows, spiders, zombies) with skill gating

**Implementation**:
1. Add `player_skills.beast_speech` to game state
2. In dialogue router, check entity type:
   - Villager → Allow chat
   - Mob → Check beast_speech skill
3. Failure: Static response ("The spider hisses...")
4. Success: Generate mob personality prompt

**Mob Personalities**:
- Cow: "Peaceful grazer. Simple thoughts. Worried about wolves."
- Spider: "Predator. Hungry. Curious about shiny items."
- Zombie: "Driven by hunger. Vague memories of being human. Hates light."

---

### 7.2 Roll Checking System

**Time Estimate**: 4 hours

**Concept**: D&D-style skill checks with advantage/disadvantage

**Implementation**:
1. Parse `roll_check` from dialogue option
2. Execute dice roll: `d20 + modifier`
3. Compare to DC (difficulty class)
4. Success/failure triggers different dialogue branch

**Skill Modifiers**:
- Persuasion: Charisma-based
- Insight: Wisdom-based
- Stealth: Dexterity-based

---

### 7.3 Dynamic Stat Sheet (Bestiary)

**Time Estimate**: 4 hours

**Concept**: Progressive mob knowledge system

**Implementation**:
1. Database: `known_mobs.skeleton.discovery_level` (0-5)
2. Level 0: "You don't know yet"
3. Level 1 (1 kill): "Undead creature"
4. Level 3 (10 kills): "Weak to sunlight"
5. Level 5 (50 kills): "Vulnerable to Smite enchantment, blunt damage"

---

### 7.4 Rumor Mill (Information Propagation)

**Time Estimate**: 6 hours

**Concept**: Events spread across world over time

**Implementation**:
1. Event: "Dragon killed in Eastern Mountains"
2. Day 1: NPCs within 100 blocks know
3. Day 3: NPCs within 500 blocks know (via "traveler" NPCs)
4. Use RAG database to store/retrieve rumors
5. NPCs mention rumors in greetings: "Did you hear about the dragon?"

---

## 📊 IMPLEMENTATION PRIORITY MATRIX

| Phase | System | Priority | Effort | Impact | Dependencies |
|-------|--------|----------|--------|--------|--------------|
| 1.1 | Anti-Meta Filter | CRITICAL | 1h | HIGH | None |
| 1.2 | System Prompt Hardening | CRITICAL | 30m | HIGH | None |
| 1.3 | Inventory Awareness | CRITICAL | 2h | HIGH | None |
| 2.1 | Nearby Entities (Client) | HIGH | 2h | HIGH | None |
| 2.2 | Context Integration (Server) | HIGH | 1h | HIGH | 2.1 |
| 3.1 | UUID Registry | HIGH | 3h | MEDIUM | None |
| 3.2 | NBT Persistence | HIGH | 2h | MEDIUM | 3.1 |
| 3.3 | Memory Migration | HIGH | 2h | MEDIUM | 3.1 |
| 4.1 | Game State Service | MEDIUM | 4h | MEDIUM | 3.1 |
| 4.2 | Event Reactor | MEDIUM | 2h | LOW | 4.1 |
| 5.1 | Merchant System MVP | MEDIUM | 4h | HIGH | 3.1 |
| 5.2 | Reputation Pricing | MEDIUM | 2h | HIGH | 5.1 |
| 6.1 | Faction Config | LOW | 1h | MEDIUM | 5.1 |
| 6.2 | Faction Reputation | LOW | 3h | MEDIUM | 6.1 |
| 7.1 | Beast Speech | FUTURE | 3h | LOW | 1-6 |
| 7.2 | Roll Checking | FUTURE | 4h | LOW | 1-6 |
| 7.3 | Dynamic Bestiary | FUTURE | 4h | LOW | 4.1 |
| 7.4 | Rumor Mill | FUTURE | 6h | LOW | 4.1 |

---

## 🚀 SUGGESTED EXECUTION ORDER

### Sprint 1 (Week 1): Foundation - Dialogue Quality
**Goal**: Eliminate meta-awareness, add context awareness

**Tasks**:
1. Phase 1.1 - Anti-Meta Filter (1h)
2. Phase 1.2 - System Prompt Hardening (30m)
3. Phase 2.1 - Nearby Entities Detection (2h)
4. Phase 2.2 - Context Integration (1h)

**Total**: 4.5 hours

**Deliverables**:
- ✅ NPCs never break character
- ✅ NPCs aware of nearby entities
- ✅ Dialogue references context (guards watching, mobs nearby)

**Testing**:
- Talk to merchant near guard → Should whisper
- Talk to NPC alone → Normal dialogue
- NPC references hostile mob nearby

---

### Sprint 2 (Week 2): Persistence - "The Soul"
**Goal**: NPCs persist state across server restarts

**Tasks**:
5. Phase 3.1 - UUID Registry (3h)
6. Phase 3.2 - NBT Persistence (2h)
7. Phase 3.3 - Memory Migration (2h)

**Total**: 7 hours

**Deliverables**:
- ✅ UUID → NPC identity mapping
- ✅ NPC state saved to NBT
- ✅ Memory system uses UUIDs
- ✅ NPCs remember conversations after restart

**Testing**:
- Talk to NPC, restart server, talk again → Memory preserved
- Spawn 2 instances of same archetype → Separate memories
- Check `npc_registry.db` for UUID entries

---

### Sprint 3 (Week 3): Commerce - Merchant System
**Goal**: Functional trading with reputation-based pricing

**Tasks**:
8. Phase 1.3 - Inventory Awareness (2h)
9. Phase 5.1 - Merchant System MVP (4h)
10. Phase 5.2 - Reputation Pricing (2h)

**Total**: 8 hours

**Deliverables**:
- ✅ Browse merchant inventory
- ✅ Buy/sell items
- ✅ Stock management and restocking
- ✅ Reputation affects prices

**Testing**:
- Browse Rowan's shop → See inventory
- Buy wheat → Stock decreases, player pays emeralds
- Help merchant (+30 relationship) → Prices improve
- Attack merchant (-50 relationship) → Prices worsen

---

### Sprint 4 (Week 4): World State & Factions
**Goal**: Centralized world state and faction system

**Tasks**:
11. Phase 4.1 - Game State Service (4h)
12. Phase 4.2 - Event Reactor (2h)
13. Phase 6.1 - Faction Config (1h)
14. Phase 6.2 - Faction Reputation (3h)

**Total**: 10 hours

**Deliverables**:
- ✅ World events database
- ✅ Faction configuration
- ✅ Faction reputation tracking
- ✅ Cascade effects (help NPC → faction improves)

**Testing**:
- Help trader → Traders Guild reputation increases
- Check all traders → Prices improve at ALL shops
- Attack bandit → Traders improve, Bandits worsen
- Reach faction threshold → Unlock perks

---

### Future Sprints: Advanced Features
**Goal**: Beast speech, roll checks, bestiary, rumor mill

**Tasks**:
15. Phase 7.1 - Beast Speech (3h)
16. Phase 7.2 - Roll Checking (4h)
17. Phase 7.3 - Dynamic Bestiary (4h)
18. Phase 7.4 - Rumor Mill (6h)

**Total**: 17 hours

**Note**: Only implement after core systems (Phases 1-6) are stable and tested.

---

## 🔧 OPTIMIZATION OPPORTUNITIES

### Dialogue System
- **Token usage**: Already optimized via LLM Router (Phase 2: Context Optimization) ✅
- **Response caching**: Cache common greetings per NPC archetype (save 2-5s)
- **Parallel generation**: Generate options while player reads greeting
- **Prompt compression**: Use abbreviations in system prompt (save 20% tokens)

### Database Performance
- **Index UUID fields** for fast lookups
- **Batch writes** for event logging (reduce I/O)
- **In-memory cache** for active NPC states (reduce DB queries)
- **Connection pooling** for concurrent requests

### Network Optimization
- **Delta updates**: Only send changed nearby entities, not full list every time
- **Proximity zones**: Update nearby entities every 5 blocks, not every tick
- **Debouncing**: Don't send player_state events more than once per second

### Kotlin Performance
- **Async HTTP calls**: Use `CompletableFuture.runAsync()` for all HTTP requests (prevent thread blocking)
- **Entity caching**: Cache nearby entity scans for 2 seconds
- **Lazy loading**: Only load merchant inventory when browsing (not on spawn)

---

## 🚨 RISK AREAS

### 1. Thread Blocking (from TASK_LIST notes)
**Problem**: Kotlin POST requests blocking game tick

**Solution**: Use `CompletableFuture.runAsync()` for all HTTP calls

**Implementation**:
```kotlin
// BAD (blocks game tick)
val response = httpClient.post("/dialogue/start", payload)

// GOOD (async)
CompletableFuture.runAsync {
    val response = httpClient.post("/dialogue/start", payload)
    // Handle response
}.exceptionally { error ->
    logger.error("HTTP request failed: $error")
    null
}
```

---

### 2. JSON Corruption
**Problem**: No transaction safety in save operations

**Solution**: Use atomic writes (temp file + rename)

**Implementation**:
```python
import tempfile
import os

def save_json_atomic(data: dict, file_path: str):
    """Save JSON with atomic write"""
    temp_fd, temp_path = tempfile.mkstemp(dir=os.path.dirname(file_path))

    try:
        with os.fdopen(temp_fd, 'w') as f:
            json.dump(data, f, indent=2)

        # Atomic rename
        os.replace(temp_path, file_path)
    except Exception as e:
        os.unlink(temp_path)  # Clean up temp file
        raise e
```

**Apply to**:
- `npc/config/memory.json`
- `npc/config/relationships.json`
- `npc/merchant/inventory.json`

---

### 3. Memory Leaks
**Problem**: Unbounded growth of event logs

**Solution**: Implement log rotation

**Implementation**:
```python
def rotate_events(events_path: str, max_events: int = 1000):
    """Keep only last N events"""
    with open(events_path, 'r') as f:
        events = json.load(f)

    if len(events) > max_events:
        events = events[-max_events:]  # Keep last 1000

        with open(events_path, 'w') as f:
            json.dump(events, f, indent=2)
```

**Apply to**:
- `events/minecraft_events.json`
- `npc/config/memory.json` (per-key rotation already implemented ✅)

---

### 4. UUID Collisions
**Problem**: If using custom UUID generation

**Solution**: Use Minecraft's built-in `UUID.randomUUID()`

**Kotlin**:
```kotlin
import java.util.UUID

val entityUUID = UUID.randomUUID().toString()
// Example: "550e8400-e29b-41d4-a716-446655440000"
```

**Never** implement custom UUID generation!

---

### 5. Database Locking
**Problem**: SQLite can lock if multiple processes access simultaneously

**Solution**: Use WAL mode + connection pooling

**Implementation**:
```python
def init_database(self):
    self.conn = sqlite3.connect(self.db_path)

    # Enable WAL mode (Write-Ahead Logging)
    self.conn.execute('PRAGMA journal_mode=WAL')

    # Set busy timeout (wait up to 5 seconds for lock)
    self.conn.execute('PRAGMA busy_timeout=5000')

    self.conn.commit()
```

**Apply to**:
- `npc/registry/npc_registry.db`
- `game_state/world_state.db`
- `factions/player_standings.db`
- `npc/merchant/trades.db`

---

## 🎯 SUCCESS CRITERIA

After completion of Phases 1-6, the system should achieve:

### Dialogue Quality
✅ NPCs never break character or mention being AI
✅ NPCs aware of nearby entities and adjust dialogue
✅ No meta-commentary in NPC responses
✅ Merchants only offer items in stock

### Context Awareness
✅ NPCs reference nearby guards, players, mobs
✅ NPCs whisper secrets when appropriate
✅ NPCs warn about nearby hostiles

### Persistence
✅ NPCs persist state across server restarts
✅ Conversation memory survives chunk unload
✅ Multiple instances of same archetype have separate memories
✅ Merchant inventory persists

### Commerce
✅ Functional browse/buy/sell system
✅ Stock management with auto-restocking
✅ Reputation affects prices (up to 50% discount)
✅ Transaction history logged

### World State
✅ Global events tracked in database
✅ Event propagation to NPCs
✅ Quest state tracking
✅ Dynamic economy prices

### Factions
✅ Faction affiliations defined and assigned
✅ Faction reputation tracks separately from NPC relationships
✅ Cascade effects (help NPC → faction improves)
✅ Allied/enemy factions affected by actions
✅ Faction perks unlock at thresholds

### Technical
✅ No thread blocking or JSON corruption issues
✅ Atomic writes for all JSON saves
✅ Database WAL mode enabled
✅ Event log rotation implemented

---

## 📚 DOCUMENTATION REFERENCES

### Existing Documentation
- **TASK_LIST.md** - Original task list and ideas
- **MIIN_MC_SPEC.md** - System specification
- **ROUTER_IMPLEMENTATION_ROADMAP.md** - LLM router phases
- **CLAUDE.md** - AI assistant guide for codebase

### Files to Reference During Implementation
- **dialogue/service.py** - Dialogue system implementation
- **npc/scripts/service.py** - NPC service and memory
- **npc/config/relationships.json** - Relationship tracking
- **MIIN/.../MIINNpcEntity.kt** - NPC entity class
- **MIIN/.../DialogueManager.kt** - Dialogue handling

---

## 🔄 VERSIONING

**Roadmap Version**: 1.0
**Date Created**: 2025-01-21
**Last Updated**: 2025-01-21

**Change Log**:
- v1.0 (2025-01-21): Initial roadmap based on TASK_LIST.md analysis

---

## 📧 NOTES

### Development Principles
1. **Implement incrementally** - Complete each phase before moving to next
2. **Test thoroughly** - Manual testing after each sprint
3. **Document as you go** - Update this roadmap with lessons learned
4. **Refactor when needed** - Don't accumulate technical debt
5. **Prioritize core features** - Advanced features are optional

### Known Limitations
- Beast speech requires player skill system (not yet implemented)
- Roll checking needs dice mechanics integration
- Rumor mill requires RAG database integration
- Event reactor status unclear (was deleted, needs investigation) <- look in events/reactor

### Future Considerations
- Multi-player support (faction PvP?)
- Server-wide economy simulation
- Dynamic quest generation based on world state
- NPC-to-NPC relationships (friendships, rivalries)
- NPC aging/death/succession system

---

**END OF ROADMAP**
