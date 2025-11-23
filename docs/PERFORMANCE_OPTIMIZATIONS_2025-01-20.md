# Dialogue Performance Optimizations

**Date:** January 20, 2025 (22:00)
**Status:** ✅ COMPLETE - Ready for Testing

---

## Problem Summary

Dialogue system was experiencing severe slowdowns:
- **Initial greeting:** 30+ seconds (timeout)
- **Follow-up responses:** 30+ seconds (timeout)
- **Expected performance:** 2-5 seconds

**Root Cause:** Massive context overload
- `get_player_context()` was loading **ALL events from last HOUR**
- player_state events (position updates) happen **every few seconds**
- This sent **hundreds/thousands of events** to LLM
- LLM took 30+ seconds to process bloated context → timeout

---

## Optimizations Implemented

### 1. ✅ Context Filtering (MAJOR WIN)

**File:** `npc_service.py:130-157`

**Changes:**
1. Reduced event lookback: **1 hour → 15 minutes**
2. Capped event count: **unlimited → 20 events max**
3. Deduplicated player_state events: **Keep only most recent**

**Code:**
```python
# Filter events for this player (last 15 minutes, max 20 events)
# OPTIMIZATION: Reduced from 1 hour to 15 minutes to prevent massive context
cutoff = datetime.now(timezone.utc) - timedelta(minutes=15)
player_events = [
    e for e in events
    if e.get('data', {}).get('playerName') == player_name
    and datetime.fromisoformat(e['timestamp'].replace('Z', '+00:00')) > cutoff
]

# Limit to most recent 20 events to prevent LLM slowdown
player_events = player_events[-20:] if len(player_events) > 20 else player_events

# Filter out redundant player_state events (only keep last one)
# player_state events happen every few seconds and bloat context
filtered_events = []
last_player_state = None

for event in player_events:
    if event.get('eventType') == 'player_state':
        last_player_state = event  # Keep updating to most recent
    else:
        filtered_events.append(event)

# Add the final player_state at the end
if last_player_state:
    filtered_events.append(last_player_state)

player_events = filtered_events
```

**Expected Impact:**
- Context size: Thousands of events → max 20 events
- LLM processing: 30s → 3-8s
- **This is the main fix that solves the timeout issue**

---

### 2. ✅ Increased Ollama Timeout (SAFETY NET)

**File:** `dialogue_service.py:168`

**Change:** 30 seconds → 90 seconds

**Code:**
```python
response = requests.post(
    f"{self.ollama_url}/api/generate",
    json={
        "model": "llama3.2:latest",
        "prompt": prompt,
        "stream": False,
        "format": "json"
    },
    timeout=90  # Increased from 30s to allow LLM to complete
)
```

**Expected Impact:**
- Prevents timeout errors if LLM occasionally takes longer
- Gives breathing room for complex dialogue
- With context filtering, should rarely hit this limit

---

### 3. ✅ Response Template System (INSTANT GREETINGS)

**File:** `dialogue_service.py:140-148, 303-402`

**What it does:**
- Detects first-time greetings (no memory, no quests)
- Returns instant template response **without calling LLM**
- Personality-based greetings for 7 NPCs (marina, vex, rowan, kira, sage, thane, lyra)
- Relationship-aware (neutral/friendly/close tones)

**Code:**
```python
# OPTIMIZATION: Use template for first-time greeting (no memory, no quests)
# This avoids LLM call for simple "Hello" interactions
memory = self.npc_service.get_npc_memory(npc_id, player_name)
player_quests = self.npc_service.get_player_quests(player_name)
npc_quests = [q for q in player_quests['active'] if q.get('npc_id') == npc_id]

if context_type == "greeting" and len(memory) == 0 and len(npc_quests) == 0:
    print(f"[Dialogue] Using greeting template for {npc_id} (no history)", file=sys.stderr)
    return self._get_greeting_template(npc, player_name, relationship)
```

**Template Examples:**

**Marina (fisher):**
- Neutral: "Marina Tidecaller looks up from mending nets. The sea's calm today."
- Friendly: "Ahoy, vDakota! The tides brought you here at the right time."
- Close: "vDakota, my friend! The ocean whispers your name today."

**Kira (monster hunter):**
- Neutral: "Kira Shadowhunter nods curtly. Dusk falls—dangerous creatures stir."
- Friendly: "vDakota. Good timing. I could use someone who knows how to fight."
- Close: "vDakota! Perfect timing. Got a hunt planned that needs two swords."

**Vex (voidwalker):**
- Neutral: "Vex the Voidwalker stares through you, seeing... something else."
- Friendly: "You again. The dimensions align when you're near, vDakota."
- Close: "vDakota... I've seen you in seventeen realities. This one feels... real."

**Expected Impact:**
- First-time greetings: **30s → <1ms** (instant!)
- Only uses LLM for contextual dialogue (memory/quests exist)
- Players get immediate feedback on interaction

---

## Expected Performance After Optimizations

### Before Optimizations:
- **First greeting:** 30+ seconds → timeout error
- **Follow-up response:** 30+ seconds → timeout error
- **Context size:** Hundreds/thousands of events
- **User experience:** Frustrating, feels broken

### After Optimizations:
- **First greeting (template):** <1ms (instant)
- **Contextual greeting (LLM):** 3-8 seconds
- **Follow-up response:** 3-8 seconds
- **Complex multi-turn:** 5-12 seconds
- **Context size:** Max 20 relevant events
- **User experience:** Responsive, natural

---

## Architecture Pattern: Analyzer → Orchestrator

This follows the user's suggested **analyzer → orchestrator** pattern:

1. **Analyzer Stage** (dialogue_service.py:140-148)
   - Checks if this is a simple greeting (no memory/quests)
   - Decides: template (instant) vs LLM (contextual)

2. **Filter Stage** (npc_service.py:130-157)
   - Reduces context to 15 minutes, max 20 events
   - Deduplicates redundant player_state events
   - Only relevant data passes to LLM

3. **Orchestrator Stage** (dialogue_service.py:150-190)
   - Builds optimized prompt with filtered context
   - Calls LLM with 90s timeout safety net
   - Returns structured response

**Benefits:**
- Simple interactions bypass LLM entirely (instant)
- Complex interactions get filtered context (3-8s)
- No wasted LLM processing on redundant data
- Fail-safe timeout prevents hard errors

---

## Testing Instructions

### 1. Test First-Time Greeting (Template System)

**Steps:**
1. Clear NPC memory: Delete or rename `npc_memory.json`
2. Start game, right-click an NPC
3. Should see instant greeting (no delay)
4. Check logs for: `[Dialogue] Using greeting template for <npc_id> (no history)`

**Expected Result:**
- Response time: <1 second
- Greeting matches NPC personality
- 3 dialogue options displayed

---

### 2. Test Contextual Dialogue (Filtered LLM)

**Steps:**
1. After first greeting, select option 1 or 2
2. Continue conversation for 2-3 turns
3. Right-click same NPC again
4. Should now use LLM (memory exists)

**Expected Result:**
- Response time: 3-8 seconds
- NPC references previous conversation
- No timeout errors

---

### 3. Verify Context Filtering

**Check logs for:**
```
[NPC] Loaded X recent events for vDakota
```
X should be ≤20 even after hours of gameplay

**Check that player_state deduplication is working:**
- Only 1 player_state event in context
- Other event types (block_place, item_pickup, etc.) preserved

---

### 4. Performance Benchmarking

**Monitor these times:**

| Interaction Type | Before | Target | Actual |
|-----------------|---------|---------|---------|
| First greeting (template) | 30s timeout | <1s | ? |
| Contextual greeting (LLM) | 30s timeout | 3-8s | ? |
| Follow-up response | 30s timeout | 3-8s | ? |
| Complex multi-turn | N/A | 5-12s | ? |

Fill in "Actual" column during testing.

---

## Files Modified

### 1. npc_service.py
- **Lines 130-140:** Reduced lookback to 15 minutes, max 20 events
- **Lines 142-157:** Added player_state deduplication

### 2. dialogue_service.py
- **Lines 140-148:** Template system check (first-time greetings)
- **Line 165:** Increased timeout 30s → 90s
- **Lines 303-402:** New `_get_greeting_template()` method with personality templates

---

## Rollback Instructions

If optimizations cause issues, revert these changes:

**npc_service.py:130-157** - Context filtering
```python
# BEFORE (original):
cutoff = datetime.now(timezone.utc) - timedelta(hours=1)
player_events = [
    e for e in events
    if e.get('data', {}).get('playerName') == player_name
    and datetime.fromisoformat(e['timestamp'].replace('Z', '+00:00')) > cutoff
]
# No deduplication
```

**dialogue_service.py:168** - Timeout
```python
# BEFORE (original):
timeout=30
```

**dialogue_service.py:140-148** - Template check
```python
# BEFORE (original):
# Remove the entire template check block
# Jump directly to: player_context = self.npc_service.get_player_context(player_name)
```

---

## Future Enhancements (Optional)

### 1. Dynamic Template Expansion
- Add time-of-day variations ("Good morning" vs "Good evening")
- Weather-aware greetings (rain, clear, etc.)
- Biome-specific greetings

### 2. Smarter Context Filtering
- Weight events by importance (combat > movement)
- Decay old events exponentially
- Player-NPC interaction history takes priority

### 3. Response Caching
- Cache common responses (e.g., "What do you sell?")
- Invalidate on relationship change
- LRU cache with 100-item limit

### 4. Pre-warming
- Start Ollama model on server startup
- Keep model loaded in memory
- Further reduces first LLM call latency

---

## Summary

**What we did:**
1. ✅ Filtered context to 15 min, max 20 events, deduped player_state
2. ✅ Increased Ollama timeout 30s → 90s
3. ✅ Added template system for instant first-time greetings

**Expected improvement:**
- **30s timeouts → <1s templates or 3-8s LLM responses**
- **Massively reduced context → faster LLM processing**
- **Better user experience → responsive, natural dialogue**

**Ready for testing:** YES
**Requires rebuild:** NO (Python only, hot-reload)
**Requires restart:** YES (MCP server needs to reload dialogue_service.py)

---

**Next Step:** Test in-game and report performance metrics!
