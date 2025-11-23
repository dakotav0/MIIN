# Themed Build Challenges - User Guide

## Overview

Themed Build Challenges are structured quests where NPCs challenge players to create specific types of builds with clear requirements and validation. Unlike general building quests, challenges have:

- **Specific block requirements** (types and quantities)
- **Dimensional constraints** (height, total blocks)
- **Validation rules** (checks if build meets criteria)
- **Themed rewards** (lore, XP, items)

## Available Challenges

### 1. Medieval Tower (Medium Difficulty)
**NPCs:** Thane Ironforge, Lyra Starweaver

**Description:** Build a tall stone tower worthy of a castle keep

**Requirements:**
- Minimum 100 blocks total
- Minimum 20 blocks height
- 40+ stone bricks (main structure)
- 20+ oak planks (floors/supports)
- 10+ glass (windows)

**Rewards:**
- Ancient architectural lore
- 100 XP
- 2 diamonds + 16 iron ingots

---

### 2. Garden Sanctuary (Easy Difficulty)
**NPCs:** Sage Rootwhisper, Lyra Starweaver

**Description:** Design a peaceful garden with natural harmony

**Requirements:**
- Minimum 50 blocks total
- Minimum 3 blocks height
- 20+ grass blocks (foundation)
- 15+ flowers (any type)
- 10+ oak leaves (canopy)

**Rewards:**
- Nature gardening secrets
- 50 XP
- 32 bone meal + 8 saplings

---

### 3. Redstone Workshop (Hard Difficulty)
**NPCs:** Thane Ironforge

**Description:** Engineer a functional workshop with working redstone mechanisms

**Requirements:**
- Minimum 150 blocks total
- Minimum 10 blocks height
- 20+ redstone
- 5+ redstone torches
- 3+ pistons
- 10+ iron blocks
- 2+ crafting tables

**Rewards:**
- Engineering principles and redstone wisdom
- 200 XP
- 4 diamonds + 8 redstone blocks + 12 gold ingots

---

### 4. Underwater Haven (Hard Difficulty)
**NPCs:** Marina Tidecaller, Vex the Voidwalker

**Description:** Construct a breathable sanctuary beneath the waves

**Requirements:**
- Minimum 120 blocks total
- Minimum 8 blocks height
- 40+ glass (viewing)
- 30+ prismarine (aesthetics)
- 8+ sponges (water clearing)
- 6+ sea lanterns (lighting)
- Must be underwater at depth 10+

**Rewards:**
- Marina's ocean secrets
- 180 XP
- 3 diamonds + 1 trident + 1 heart of the sea

---

### 5. Artistic Sculpture (Medium Difficulty)
**NPCs:** Lyra Starweaver

**Description:** Create a statue or sculpture that tells a story

**Requirements:**
- Minimum 80 blocks total
- Minimum 12 blocks height
- 40+ colored blocks (terracotta/concrete/wool)
- 20+ contrast blocks
- At least 6 unique block types

**Rewards:**
- Color theory and artistic insights
- 120 XP
- 2 diamonds + 1 music disc + 16 firework rockets

---

### 6. Defensive Outpost (Medium Difficulty)
**NPCs:** Kira Shadowhunter, Thane Ironforge

**Description:** Fortify a defensive position capable of withstanding monster sieges

**Requirements:**
- Minimum 130 blocks total
- Minimum 15 blocks height
- 50+ cobblestone (walls)
- 30+ stone (reinforcement)
- 20+ torches (lighting)
- 2+ doors (entrances)

**Rewards:**
- Combat wisdom and defensive architecture
- 150 XP
- 1 diamond sword + 8 iron blocks + 2 shields

---

## How to Use Build Challenges

### Step 1: Request a Challenge

Talk to an NPC who offers build challenges (see "NPCs" under each challenge) and ask for a build challenge.

**Via MCP Tools:**
```typescript
// Request any suitable challenge from an NPC
minecraft_build_challenge_request({
  npc: "thane",
  player: "PlayerName"
})

// Request a specific challenge
minecraft_build_challenge_request({
  npc: "lyra",
  player: "PlayerName",
  challenge_id: "artistic_sculpture"
})
```

**In-Game:**
```
Player: "Thane, I'd like a building challenge."
Thane: *generates Medieval Tower or Redstone Workshop challenge*
```

### Step 2: Accept the Challenge

Once the NPC offers a challenge, accept it like any other quest:

```typescript
minecraft_quest_accept({
  npc: "thane",
  player: "PlayerName",
  quest_id: "thane_PlayerName_challenge_1234567890.123"
})
```

### Step 3: Build According to Requirements

Construct your build following the challenge requirements. Keep track of:
- Block types and quantities
- Total height
- Special requirements (underwater, working redstone, etc.)

**Tips:**
- Use F3 screen to check height (Y coordinate)
- Keep count of specific blocks as you place them
- Read the challenge description for hints about structure

### Step 4: Validate Your Build

When you think your build is complete, validate it against the challenge:

```typescript
minecraft_build_challenge_validate({
  player: "PlayerName",
  quest_id: "thane_PlayerName_challenge_1234567890.123",
  build_data: {
    blocks: {
      "stone_bricks": 45,
      "oak_planks": 25,
      "glass": 12,
      "cobblestone": 18
    },
    height: 22
  }
})
```

**Validation Response:**
```json
{
  "quest_id": "thane_PlayerName_challenge_1234567890.123",
  "challenge_id": "medieval_tower",
  "valid": true,
  "checks": {
    "min_blocks": {"pass": true, "required": 100, "actual": 100},
    "min_height": {"pass": true, "required": 20, "actual": 22},
    "stone_bricks": {"pass": true, "required": 40, "actual": 45},
    "oak_planks": {"pass": true, "required": 20, "actual": 25},
    "glass": {"pass": true, "required": 10, "actual": 12}
  },
  "statistics": {
    "total_blocks": 100,
    "unique_blocks": 4,
    "height": 22
  }
}
```

### Step 5: Return to NPC

If validation passes, return to the NPC to complete the quest and receive your reward:

```typescript
minecraft_quest_check_progress({
  player: "PlayerName"
})
```

The NPC will deliver the reward (lore, XP, items).

---

## How to Track Build Statistics

### Method 1: Manual Tracking

Keep notes as you build:
```
Stone Bricks: 45
Oak Planks: 25
Glass: 12
Total Height: 22 blocks
```

### Method 2: Build Session Tracking (Automatic)

The Minecraft MCP mod automatically tracks build sessions. When you finalize a build, it sends statistics to the MCP server, which can then validate challenges.

**From mod (MIINListener.kt):**
```kotlin
// Build session data structure
data class BuildSession(
    val playerName: String,
    val startTime: Long,
    val blocks: MutableMap<String, Int>,
    val minY: Int,
    val maxY: Int
)

// Finalize sends to MCP
finalizeBuildSession(playerName)
```

### Method 3: Using Build Analysis Tool

After completing a build, use the analyze tool:
```typescript
minecraft_analyze_build({
  buildName: "My Medieval Tower",
  blocks: ["stone_bricks", "oak_planks", "glass", "cobblestone"],
  blockCounts: {
    "stone_bricks": 45,
    "oak_planks": 25,
    "glass": 12,
    "cobblestone": 18
  }
})
```

---

## Listing Available Challenges

### List All Challenges
```typescript
minecraft_build_challenge_list({})
```

**Output:**
```json
{
  "total": 6,
  "challenges": [
    {
      "id": "medieval_tower",
      "title": "Construct a Medieval Tower",
      "description": "Build a tall stone tower worthy of a castle keep",
      "difficulty": "medium",
      "givers": ["thane", "lyra"],
      "requirements_summary": {
        "min_blocks": 100,
        "min_height": 20,
        "required_block_types": 3
      },
      "reward": {
        "type": "lore",
        "xp": 100,
        "items": ["diamond:2", "iron_ingot:16"]
      }
    },
    // ... more challenges
  ]
}
```

### List Challenges for Specific NPC
```typescript
minecraft_build_challenge_list({
  npc: "lyra"
})
```

Shows only challenges Lyra offers (Medieval Tower, Garden Sanctuary, Artistic Sculpture).

---

## NPC-Challenge Affinity

| NPC | Challenges Offered | Specialization |
|-----|-------------------|----------------|
| **Thane Ironforge** | Medieval Tower, Redstone Workshop, Defensive Outpost | Practical, functional builds |
| **Lyra Starweaver** | Medieval Tower, Garden Sanctuary, Artistic Sculpture | Aesthetic, creative builds |
| **Sage Rootwhisper** | Garden Sanctuary | Nature-themed builds |
| **Kira Shadowhunter** | Defensive Outpost | Combat/defensive structures |
| **Marina Tidecaller** | Underwater Haven | Ocean-related builds |
| **Vex the Voidwalker** | Underwater Haven | Unusual/dimensional builds |

---

## Troubleshooting

### Challenge Validation Fails

**Problem:** "min_blocks": {"pass": false, "required": 100, "actual": 85}

**Solution:** You need to add 15 more blocks of any required type.

**Problem:** "stone_bricks": {"pass": false, "required": 40, "actual": 35}

**Solution:** You need 5 more stone bricks specifically.

### NPC Doesn't Offer Challenges

**Check:**
1. NPC has challenges in their `giver_affinity` list
2. Challenge templates exist in `npcs.json`
3. NPC service is running

### Validation Script Fails

**Common Causes:**
- Malformed JSON in build_data
- Missing required fields (blocks, height)
- Quest ID doesn't exist or already completed

**Fix:** Check script output:
```bash
cd MIIN
python npc_build_challenge_validate.py "Player" "quest_id" '{"blocks": {"stone": 50}, "height": 10}'
```

---

## Advanced: Creating Custom Challenges

### 1. Add Challenge Template to npcs.json

```json
{
  "id": "custom_challenge",
  "title": "Your Challenge Title",
  "description": "What players need to build",
  "difficulty": "medium",
  "giver_affinity": ["npc_id"],
  "requirements": {
    "minBlocks": 100,
    "minHeight": 15,
    "requiredBlockTypes": {
      "block_type": {"min": 20, "description": "Purpose"}
    }
  },
  "reward": {
    "type": "lore",
    "content": "Lore text...",
    "xp": 100,
    "items": ["diamond:2"]
  },
  "validation": {
    "checkVerticalIntegrity": true,
    "minUniqueBlocks": 5
  }
}
```

### 2. Add NPC to giver_affinity

NPCs can only offer challenges they're listed under.

### 3. Test Validation

Use the validation script to ensure requirements are reasonable:
```bash
python npc_build_challenge_validate.py "TestPlayer" "test_quest" '{"blocks": {"test_block": 100}, "height": 20}'
```

---

## Integration with Event Reactor

The Event Reactor (`event_reactor.py`) can proactively suggest build challenges when it detects building activity:

```python
# In event_reactor.py
if player_building_large_structure:
    suggest_build_challenge(player, suitable_npc)
```

---

## Future Enhancements

### Planned Features
1. **Photo Validation:** Submit screenshot for AI analysis
2. **Structural Analysis:** Check for walls, rooms, roof presence
3. **Style Matching:** Validate architectural style (medieval, modern, etc.)
4. **Collaborative Challenges:** Multi-player build challenges
5. **Timed Challenges:** Speed-building competitions

---

## Files Modified/Created

### Created:
- `npc_build_challenge_request.py` - Request challenge script
- `npc_build_challenge_validate.py` - Validation script
- `npc_build_challenge_list.py` - List challenges script

### Modified:
- `npc_config.json` - Added `build_challenges` array with 6 challenges
- `npc_service.py` - Added 3 methods:
  - `load_build_challenges()`
  - `generate_build_challenge_quest()`
  - `validate_build_challenge()`
- `src/index.ts` - Added 3 MCP tools:
  - `minecraft_build_challenge_request`
  - `minecraft_build_challenge_validate`
  - `minecraft_build_challenge_list`

---

## Testing Checklist

- [ ] Request challenge from compatible NPC (Thane, Lyra, etc.)
- [ ] Accept challenge quest
- [ ] Build according to requirements
- [ ] Validate build with correct statistics
- [ ] Validation passes all checks
- [ ] Return to NPC for reward delivery
- [ ] Receive lore, XP, and items
- [ ] List all challenges works
- [ ] Filter challenges by NPC works

---

**Last Updated:** 2025-11-20
**Status:** Implemented and Ready for Testing ✅
**Next Steps:** Test in-game, tune difficulty, add more challenge templates
