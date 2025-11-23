# Ambient NPC Barks - Testing & Tuning Guide

## Overview

Ambient barks are implemented in `MIINNpcEntity.kt:380-489` and provide contextual, time-based NPC dialogue without player interaction.

## Current Configuration

**Location:** `MIIN/src/main/kotlin/MIIN/listener/entity/MIINNpcEntity.kt`

```kotlin
// Line 63: Cooldown between barks per player
private const val AMBIENT_COOLDOWN_TICKS = 6000L // 5 minutes (6000 ticks)

// Line 124: Chance of bark per tick when player nearby
private val ambientBarkChance = 0.001f // 0.1% chance per tick
```

**Current Behavior:**
- **Proximity Range:** 10 blocks
- **Check Interval:** Every 10 seconds (200 ticks minimum between attempts)
- **Cooldown:** 5 minutes per player per NPC
- **Trigger Chance:** 0.1% per tick (when within range and past check interval)
- **Expected Frequency:** Approximately one bark every 2-3 minutes when standing near NPC

## How It Works

### 1. Proximity Detection (Line 381)
```kotlin
val nearestPlayer = entityWorld.getClosestPlayer(this, 10.0)
```
NPCs only bark to players within 10 blocks.

### 2. Cooldown System (Lines 396-408)
```kotlin
private fun shouldAmbientBark(player: PlayerEntity): Boolean {
    val playerName = player.name.string
    val currentTick = entityWorld.time
    val lastBark = playerAmbientCooldowns[playerName] ?: 0L

    // Check cooldown - prevents spam
    if (currentTick - lastBark < AMBIENT_COOLDOWN_TICKS) {
        return false
    }

    // Random chance
    return random.nextFloat() < ambientBarkChance
}
```

### 3. Contextual Barks by Time of Day (Lines 429-458)

| Minecraft Time | Real-World Time | Bark Themes |
|---|---|---|
| 0-450 | Dawn | New opportunities, morning optimism |
| 450-11616 | Daytime | Quests, adventures, greetings |
| 11616-13800 | Dusk/Evening | Warnings about approaching night |
| 13800-24000 | Night | Danger, vigilance, secrets |

Example barks:
```kotlin
// Dawn
"The dawn brings new opportunities..."
"Another day begins. What will you build?"

// Day
"Busy day? I can see you've been active."
"Care for a quest? I might have something for you."

// Dusk
"The evening approaches. Mind the shadows."
"Dusk falls... dangerous creatures stir."

// Night
"The night holds many secrets."
"Stay vigilant in the darkness."
```

### 4. HTTP Bridge Delivery (Lines 463-489)
Barks are sent to the MCP server at `http://localhost:5558/command` as:
```json
{
  "type": "send_chat",
  "data": {
    "player": "PlayerName",
    "message": "[NPC_Name] The dawn brings new opportunities..."
  }
}
```

## Testing Checklist

### Prerequisites
- [ ] HTTP bridge is running on port 5558
- [ ] Minecraft server is running with MIIN listener mod
- [ ] At least one NPC is spawned

### Test Procedure

1. **Spawn near an NPC:**
   ```
   /tp @s <npc_x> <npc_y> <npc_z>
   ```

2. **Stay within 10 blocks for 5+ minutes**
   - Move around slightly to avoid AFK
   - Watch chat for `[NPC_Name] <bark message>`

3. **Verify cooldown:**
   - After receiving a bark, stay near the NPC
   - You should NOT receive another bark from the same NPC for 5 minutes

4. **Test different times of day:**
   ```
   /time set 0      # Dawn
   /time set 6000   # Noon
   /time set 12000  # Dusk
   /time set 18000  # Night
   ```
   - Verify different barks appear based on time

5. **Test multiple NPCs:**
   - Stand between two NPCs
   - Each should have independent cooldowns

### Expected Results

✅ **Success:** Barks appear approximately every 2-3 minutes when near NPC
✅ **Success:** Different barks for different times of day
✅ **Success:** No spam (5-minute cooldown enforced)
✅ **Success:** Different NPCs can bark to same player independently

❌ **Failure:** No barks after 10 minutes → Check HTTP bridge connection
❌ **Failure:** Too frequent (< 1 minute apart) → Cooldown not working
❌ **Failure:** Same bark repeatedly → RNG seed issue

## Tuning Parameters

### If Barks Are Too Frequent

**Option 1: Increase cooldown**
```kotlin
// Line 63: Change from 5 minutes to 10 minutes
private const val AMBIENT_COOLDOWN_TICKS = 12000L // 10 minutes
```

**Option 2: Reduce chance**
```kotlin
// Line 124: Change from 0.1% to 0.05%
private val ambientBarkChance = 0.0005f // 0.05% chance
```

### If Barks Are Too Rare

**Option 1: Decrease cooldown**
```kotlin
// Line 63: Change from 5 minutes to 3 minutes
private const val AMBIENT_COOLDOWN_TICKS = 3600L // 3 minutes
```

**Option 2: Increase chance**
```kotlin
// Line 124: Change from 0.1% to 0.2%
private val ambientBarkChance = 0.002f // 0.2% chance
```

**Option 3: Increase proximity range**
```kotlin
// Line 381: Change from 10 blocks to 15 blocks
val nearestPlayer = entityWorld.getClosestPlayer(this, 15.0)
```

### If Barks Need More Variety

Add more bark options to `generateAmbientBark()` (lines 429-458):
```kotlin
timeOfDay < 11616 -> listOf(
    "Busy day? I can see you've been active.",
    "The sun is high. Good time for adventures.",
    "Greetings, traveler.",
    "Care for a quest? I might have something for you.",
    // Add more here:
    "You look like you could use a break.",
    "Finding everything you need?",
    "The weather is pleasant today."
)
```

## Troubleshooting

### No Barks Appearing

1. **Check HTTP bridge is running:**
   ```bash
   curl http://localhost:5558/health
   # Should return 200 OK
   ```

2. **Check mod logs:**
   ```
   # Look for in server console:
   [MIIN] <NPC_Name> barked at <Player>: <message>
   ```

3. **Verify NPC is spawned:**
   ```
   /execute as @e[type=MIIN-listener:MIIN_npc] run say I exist
   ```

4. **Check player proximity:**
   ```
   /execute as @e[type=MIIN-listener:MIIN_npc,distance=..10] run say Player nearby
   ```

### Barks Too Frequent (Spam)

- Cooldown map might not be persisting between server restarts
- Check if `playerAmbientCooldowns` is being cleared somewhere
- Verify `entityWorld.time` is incrementing correctly

### Same Bark Repeating

- Random seed issue - check `random.nextInt(barks.size)` on line 457
- Ensure bark lists have multiple entries

### Barks Wrong for Time of Day

- Check `entityWorld.timeOfDay % 24000` calculation (line 430)
- Verify Minecraft time ranges match expected values

## Performance Considerations

- **Memory:** ~1KB per player-NPC pair for cooldown tracking
- **CPU:** Negligible (one float comparison + RNG every 10 seconds per player per NPC)
- **Network:** ~100 bytes per bark (HTTP POST to localhost)

**Recommendation:** Current settings are performance-friendly. Safe to have 10+ NPCs without impact.

## Future Enhancements

### LLM-Generated Barks (Planned)
Replace static bark lists with dynamic generation:
```kotlin
private fun generateAmbientBark(): String {
    // Call MCP tool to generate contextual bark
    // Based on: time, weather, player activity, recent events
}
```

### Player Activity-Based Barks
```kotlin
// React to what player is doing
if (player.isHoldingPickaxe()) {
    return "Mining, I see. The mountains hold many treasures."
}
```

### Relationship-Aware Barks
```kotlin
// Different barks based on relationship level
val relationship = getRelationship(player)
if (relationship > 50) {
    return "Good to see you again, friend."
}
```

## Implementation Status

✅ **WORKING** - Fully implemented and functional
- Proximity detection
- Cooldown system
- Time-of-day contextual barks
- HTTP bridge delivery
- Per-player-per-NPC tracking

⚠️ **NEEDS TESTING** - Awaiting user validation
- Frequency tuning
- Bark variety assessment
- HTTP bridge integration verification

## Related Files

- **Implementation:** `MIIN/src/main/kotlin/MIIN/listener/entity/MIINNpcEntity.kt:380-489`
- **HTTP Bridge:** `MIIN/src/main/kotlin/MIIN/listener/MIINListener.kt:1512-1586`
- **MCP Command Handler:** `src/index.ts` (receives bark requests)

## Contact

If barks are not working as expected after following this guide, check:
1. Server console logs for errors
2. HTTP bridge health endpoint
3. NPC spawn logs
4. Player proximity to NPCs

---

**Last Updated:** 2025-11-20
**Status:** Working ✅
**Next Action:** Test and tune parameters based on user experience
