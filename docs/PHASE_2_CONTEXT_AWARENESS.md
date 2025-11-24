# Phase 2: Context Awareness ("Field of View")

**Priority**: HIGH  
**Scope**: Phase 2.1 Nearby Entities Detection (Kotlin) and Phase 2.2 Context Integration (Python)  
**Goal**: NPC dialogue adapts to nearby witnesses and threats.

## Deliverables
- Player→NPC dialogue requests include a `nearby_entities` payload built server-side.
- Backend injects proximity data into the system prompt and response context.
- NPCs adjust tone/content when guards, hostiles, or other players/NPCs are present.

## Implementation Plan
1) Kotlin (DialogueManager)
- Add `getNearbyEntities(player: ServerPlayerEntity, radius: Double = 16.0)` that returns lightweight maps for NPCs, players, and mobs (type, id/name, hostile flag, distance).
- Call the helper inside `startNpcDialogue` (after dialogue state is seeded) and include `nearby_entities` in the MCP payload.
- Guard against overcount: filter to living entities, skip spectators, and clamp list length (e.g., top 10 by distance).

2) Python (npc/scripts/service.py)
- Extend `get_player_context` to accept `nearby_entities` and store it.
- Update `build_system_prompt` to inject a `[NEARBY ENTITIES]` block via a formatter, plus guidance on tone changes around guards/hostiles/witnesses.
- Ensure dialogue calls (start/select) pass through the new context field.

3) Logging & Debug
- Log proximity snapshot sizes at debug level to watch for perf spikes.
- Consider sampling distance formatting to one decimal place to save tokens.

## Acceptance Scenarios
- Guard nearby: NPC lowers voice or defers risky info when an iron golem/guard is within 5–8m.
- Hostile nearby: NPC warns about zombies/creepers in range and keeps replies brief.
- Player crowd: NPC acknowledges onlookers or suggests privacy.
- Alone: NPC uses normal tone and does not mention witnesses.
- Payload sanity: `nearby_entities` list present, capped, and distances are sensible.

## Test Checklist
- Start dialogue next to a guard: response includes a caution/whisper note.
- Start dialogue with a zombie within 10m: NPC warns or shifts topic to safety.
- Start dialogue with two other players in range: NPC references the crowd or privacy.
- Start dialogue alone: no nearby-entity mention.
- Verify MCP request payload logs show `nearby_entities` with correct counts and types.
