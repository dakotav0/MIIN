# Dynamic NPC Generation Plan

**Date:** January 20, 2025 (22:45)
**Priority:** HIGH
**Context:** NPC duplication issue + Opportunity for generative experience

---

## Current Problem

NPCs are duplicating on world load, and duplicates have:
- Name: "NPC" (generic)
- ID: "unknown" in logs
- Missing personality/dialogue data
- Broken NBT persistence

**Root Cause:** Hardcoded NPC data in Kotlin isn't persisting properly to NBT

---

## Opportunity: Dynamic NPC Generation

Instead of fixing the hardcoded system, **evolve it** into a fully dynamic, LLM-driven NPC generation system.

### Vision

**Template NPCs** (marina, kira, rowan, etc.) become **archetypes** that spawn **variants**:

- **Marina Tidecaller** → "Marina Tidecaller", "Finn Saltwhisper", "Coral Deepcaster"
- **Kira Shadowhunter** → "Kira Shadowhunter", "Rex Nightblade", "Shadow the Silent"
- **Rowan Coinpurse** → "Rowan Coinpurse", "Jasper Goldhand", "Merchant Vex"

Each variant has:
- Unique name (LLM-generated)
- Personality variations (based on archetype)
- Custom dialogue style
- Persistent identity

---

## Architecture: Three-Tier NPC System

### Tier 1: Archetypes (Templates)
**File:** `npc_archetypes.json`

```json
{
  "fisher": {
    "base_personality": "patient, weathered, superstitious",
    "interests": ["fishing", "weather", "ocean lore"],
    "dialogue_style": "speaks in fishing metaphors",
    "questTypes": ["gathering", "fishing", "exploration"],
    "model": "llama3.1:8b"
  },
  "hunter": {
    "base_personality": "brave, direct, protective",
    "interests": ["combat", "monster behavior", "survival"],
    "dialogue_style": "tactical and direct",
    "questTypes": ["combat", "protection", "monster_slaying"],
    "model": "deepseek-r1:latest"
  },
  "merchant": {
    "base_personality": "shrewd, friendly, calculating",
    "interests": ["trading", "rare items", "negotiation"],
    "dialogue_style": "always thinking about value",
    "questTypes": ["gathering", "trading", "delivery"],
    "model": "llama3.2:latest"
  }
}
```

### Tier 2: Instances (Spawned NPCs)
**File:** `npc_instances.json`

Generated when NPC spawns, stored persistently:

```json
{
  "uuid": "550e8400-e29b-41d4-a716-446655440000",
  "archetype": "fisher",
  "generated_name": "Finn Saltwhisper",
  "personality": "patient, gruff, loves tall tales, afraid of storms",
  "backstory": "Lost his boat to a kraken 20 years ago. Now teaches others from the shore.",
  "dialogue_style": "Gruff but warm. References 'the old days'. Warns about sea dangers.",
  "location": {"x": 15, "y": 64, "z": 10, "dimension": "overworld"},
  "skin": "marina.png",  // Uses archetype skin
  "relationships": {},
  "quests_given": [],
  "generation_timestamp": "2025-01-20T22:45:00Z"
}
```

### Tier 3: Runtime State (In-Memory)
**Managed by NpcManager.kt:**

```kotlin
data class NpcRuntimeState(
    val uuid: String,
    val entity: MIINNpcEntity,
    val activeDialogue: Map<String, DialogueState>,
    val currentBehavior: String  // "roaming", "following", "stay"
)
```

---

## Implementation Plan

### Phase 1: NPC Instance Registry (Foundation)

**File:** `npc_instance_service.py` (NEW)

```python
class NpcInstanceService:
    """Manages dynamic NPC instances"""

    def generate_npc(self, archetype: str, location: dict) -> dict:
        """Generate a new NPC from archetype"""
        # 1. Load archetype template
        # 2. Use LLM to generate unique name + personality variant
        # 3. Create instance JSON
        # 4. Save to npc_instances.json
        # 5. Return instance data

    def get_instance(self, uuid: str) -> dict:
        """Get existing NPC instance"""

    def get_or_create(self, uuid: str, archetype: str, location: dict) -> dict:
        """Get existing or generate new"""

    def cleanup_duplicates(self) -> list:
        """Find and mark duplicate NPCs for removal"""
```

**MCP Tool:** `minecraft_npc_generate`

```typescript
server.setRequestHandler(CallToolRequestSchema, async (request) => {
  if (request.params.name === 'minecraft_npc_generate') {
    const { archetype, location } = request.params.arguments;

    // Call npc_instance_service.py
    const instance = await generateNpcInstance(archetype, location);

    return {
      content: [{
        type: "text",
        text: JSON.stringify(instance, null, 2)
      }]
    };
  }
});
```

---

### Phase 2: Kotlin NBT Persistence Fix

**File:** `NpcManager.kt`

**Current Issue:** NPCs spawn with data but lose it on reload

**Solution:** Store UUID in NBT, load instance data from `npc_instances.json`

```kotlin
// On spawn
fun spawnNpc(archetype: String, location: BlockPos, world: ServerWorld): MIINNpcEntity {
    val uuid = UUID.randomUUID().toString()

    // Call MCP to generate instance
    val instanceData = callMcpTool("minecraft_npc_generate", mapOf(
        "archetype" to archetype,
        "location" to mapOf("x" to location.x, "y" to location.y, "z" to location.z)
    ))

    val entity = NpcRegistry.MIIN_NPC.create(world)!!
    entity.refreshPositionAndAngles(location.x + 0.5, location.y.toDouble(), location.z + 0.5, 0f, 0f)

    // Store UUID in NBT
    entity.nbt.putString("MIIN_npc_uuid", uuid)
    entity.nbt.putString("MIIN_npc_name", instanceData["generated_name"])
    entity.nbt.putString("MIIN_npc_archetype", archetype)

    world.spawnEntity(entity)
    return entity
}

// On load (from disk)
fun onEntityLoad(entity: MIINNpcEntity) {
    val uuid = entity.nbt.getString("MIIN_npc_uuid")

    if (uuid.isEmpty()) {
        LOGGER.warn("NPC has no UUID - marking for cleanup")
        entity.customName = Text.literal("NPC (Duplicate)")
        return
    }

    // Load instance data from npc_instances.json via MCP
    val instanceData = callMcpTool("minecraft_npc_get_instance", mapOf("uuid" to uuid))

    if (instanceData == null) {
        LOGGER.warn("NPC UUID $uuid not found in instance registry")
        entity.customName = Text.literal("NPC (Unknown)")
        return
    }

    // Apply instance data
    entity.customName = Text.literal(instanceData["generated_name"])
    entity.npc_archetype = instanceData["archetype"]
}
```

---

### Phase 3: LLM-Powered Instance Generation

**File:** `npc_instance_service.py`

```python
def generate_npc(self, archetype: str, location: dict) -> dict:
    """Generate unique NPC instance from archetype"""

    # Load archetype template
    archetype_data = self.archetypes[archetype]

    # Build LLM prompt
    prompt = f"""Generate a unique NPC character based on this archetype:

Archetype: {archetype}
Personality: {archetype_data['base_personality']}
Interests: {', '.join(archetype_data['interests'])}
Dialogue Style: {archetype_data['dialogue_style']}

Location: {location.get('biome', 'unknown')} biome at coordinates ({location['x']}, {location['y']}, {location['z']})

Create a JSON object with:
- "generated_name": A unique, fitting name (2-3 words)
- "personality": Expanded personality (3-5 traits, include quirks)
- "backstory": 1-2 sentence backstory
- "dialogue_style": Specific speech patterns or catchphrases

Make them memorable and distinct, but true to the archetype.

Respond with ONLY valid JSON."""

    # Call LLM
    response = requests.post(
        f"{self.ollama_url}/api/generate",
        json={
            "model": archetype_data['model'],
            "prompt": prompt,
            "stream": False,
            "format": "json"
        },
        timeout=30
    )

    generated = json.loads(response.json()['response'])

    # Combine with archetype template
    instance = {
        "uuid": str(uuid.uuid4()),
        "archetype": archetype,
        "generated_name": generated['generated_name'],
        "personality": generated['personality'],
        "backstory": generated['backstory'],
        "dialogue_style": generated['dialogue_style'],
        "base_personality": archetype_data['base_personality'],
        "interests": archetype_data['interests'],
        "questTypes": archetype_data['questTypes'],
        "model": archetype_data['model'],
        "location": location,
        "skin": f"{archetype}.png",  # Use archetype skin
        "relationships": {},
        "generation_timestamp": datetime.now(timezone.utc).isoformat()
    }

    # Save to instances file
    self.instances[instance['uuid']] = instance
    self.save_instances()

    return instance
```

**Example Output:**

```json
{
  "uuid": "550e8400-e29b-41d4-a716-446655440000",
  "archetype": "fisher",
  "generated_name": "Finn Saltwhisper",
  "personality": "patient, gruff, loves tall tales, superstitious about storms, afraid of the deep",
  "backstory": "Lost his boat to a kraken 20 years ago. Now teaches others to fish from the safety of shore.",
  "dialogue_style": "Gruff but warm. References 'the old days at sea'. Always warns about dangers. Says 'mark my words' often.",
  "base_personality": "patient, weathered, superstitious",
  "interests": ["fishing", "weather", "ocean lore"],
  "questTypes": ["gathering", "fishing", "exploration"],
  "model": "llama3.1:8b",
  "location": {"x": 15, "y": 64, "z": 10, "dimension": "overworld", "biome": "beach"},
  "skin": "fisher.png",
  "generation_timestamp": "2025-01-20T22:45:00Z"
}
```

---

## Migration Strategy

### Option A: Clean Slate (Recommended)

1. Delete all existing NPCs with `/npc cleanup`
2. Manually spawn new NPCs with `/npc spawn <archetype>`
3. System generates unique instances for each

### Option B: Migrate Existing

1. Scan existing NPCs
2. If name matches template (e.g., "Marina Tidecaller"), assign UUID and create instance
3. If name is "NPC" or missing data, delete as duplicate

---

## Benefits

### For Players
- ✅ Every NPC feels unique and memorable
- ✅ Multiple NPCs of same archetype have different personalities
- ✅ World feels more alive and populated
- ✅ No more "clone" feeling

### For Developers
- ✅ No more hardcoded NPC data
- ✅ Easy to add new archetypes (just JSON)
- ✅ Scalable to hundreds/thousands of NPCs
- ✅ NPCs persist correctly (UUID-based)

### For AI
- ✅ Each NPC has distinct personality for dialogue
- ✅ Archetypes ensure consistent behavior
- ✅ LLM creativity within guardrails

---

## File Structure

```
MIIN/
├── npc_archetypes.json          # NEW: Archetype templates
├── npc_instances.json            # NEW: Generated NPC instances
├── npc_instance_service.py       # NEW: Instance management
├── npc_config.json               # DEPRECATED: Old hardcoded NPCs
├── npc_service.py                # UPDATED: Uses instances
└── src/index.ts                  # NEW: minecraft_npc_generate tool

MIIN/
└── src/main/kotlin/MIIN/listener/entity/
    └── NpcManager.kt             # UPDATED: UUID-based spawning
```

---

## Next Steps

### Immediate (Fix Duplication)
1. Implement `/npc cleanup` to remove "NPC" entities
2. Add UUID check on entity load
3. Mark unknown NPCs for player cleanup

### Short-Term (Dynamic Generation)
1. Create `npc_archetypes.json` from current `npc_config.json`
2. Implement `npc_instance_service.py`
3. Add `minecraft_npc_generate` MCP tool
4. Update `NpcManager.kt` to use UUIDs

### Long-Term (Full System)
1. Add `/npc spawn <archetype>` command
2. Implement automatic despawn/respawn for unused NPCs
3. Add NPC migration from villages/structures
4. Dynamic NPC behavior based on world events

---

## Rollout Plan

**Week 1: Foundation**
- Archetypes JSON
- Instance service (Python)
- MCP tools

**Week 2: Kotlin Integration**
- UUID-based spawning
- NBT persistence fix
- Cleanup command

**Week 3: Testing & Polish**
- Generate 20+ unique NPCs
- Test persistence across restarts
- Balance personality generation

**Week 4: Advanced Features**
- NPC variants (different skins per archetype)
- Relationship inheritance (children remember parent's friends)
- Dynamic spawning based on biome/structure

---

## Example Usage (Future)

```bash
# Spawn a fisher archetype NPC
/npc spawn fisher

# LLM generates:
# - Name: "Coral Deepcaster"
# - Personality: "mysterious, quiet, claims to hear fish singing"
# - Backstory: "Grew up in an underwater temple. Can hold breath for 10 minutes."

# Spawn another fisher
/npc spawn fisher

# LLM generates:
# - Name: "Old Salt"
# - Personality: "cheerful, loves jokes, terrible at fishing"
# - Backstory: "Former pirate turned fisher. Still has treasure maps."

# Both are fishers, both unique!
```

---

## Summary

**Current Issue:** NPC duplication, "unknown" NPCs, broken NBT persistence

**Solution:** Dynamic NPC generation with archetypes + instances

**Benefits:** Unique NPCs, scalable system, proper persistence

**Effort:** 2-3 days for foundation, 1-2 weeks for full system

**Priority:** HIGH - Solves current bug + unlocks major feature

---

**Ready to implement?** Let me know if you want to:
1. Start with quick duplication fix
2. Jump straight to dynamic generation
3. Hybrid approach (fix now, dynamic later)
