#!/usr/bin/env python3
"""
NPC Service - Manages NPC dialogue, memory, and quest generation

Integrates with MIIN's local Ollama LLMs to create living, breathing NPCs
that remember conversations, generate quests, and deliver narrative rewards.
"""

import json
import tempfile
from pathlib import Path
from typing import Dict, List, Optional, Any
from datetime import datetime, timedelta, timezone
import requests
import random

# Add project root to path for absolute imports (npc, dialogue, etc.)
import sys, os
ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

# Import LLM Router (Phase 5: Integration)
from npc.scripts.llm_router import SimpleLLMRouter


class NPCService:
    """
    NPC Service - Chat-based NPC system with memory and quest generation

    Features:
    - LLM-powered NPC dialogue (uses local Ollama models)
    - Persistent memory per NPC
    - Context-aware responses (player activity, location, inventory)
    - Quest generation based on player behavior
    - Narrative reward delivery
    """

    def __init__(
        self,
        npc_config_path: str = None,
        memory_path: str = None,
        quest_path: str = None,
        ollama_url: str = "http://localhost:11434"
    ):
        """Initialize NPC service"""
        # Paths
        self.root = Path(__file__).parent.parent.parent  # Go up to MIIN root
        self.npc_config_path = npc_config_path or str(self.root / 'npc' / 'config' / 'npcs.json')
        self.memory_path = memory_path or str(self.root / 'npc' / 'config' / 'memory.json')
        self.quest_path = quest_path or str(self.root / 'npc' / 'config' / 'quests.json')
        self.events_path = str(self.root / 'events' / 'minecraft_events.json')
        self.dynamic_npc_path = str(self.root / 'npc' / 'config' / 'dynamic_npcs.json')

        # Ollama connection
        self.ollama_url = ollama_url

        # Initialize LLM Router (Phase 5: Integration)
        self.llm_router = SimpleLLMRouter()

        # Load data
        self.templates = self.load_templates()
        self.npcs = self.load_npcs()
        self.memory = self.load_memory()
        self.quests = self.load_quests()

        print(f"[NPC] Service initialized with {len(self.npcs)} NPCs", file=sys.stderr)

        # HOTLOADING: Send a dummy request to force the model into VRAM immediately
        print("[NPC] Hotloading LLM models...", file=sys.stderr)
        try:
            # Use a dummy NPC ID if available, otherwise skip
            if self.npcs:
                dummy_id = next(iter(self.npcs))
                self.generate_npc_response(dummy_id, "system", "warmup")
                print("[NPC] Models hotloaded and ready.", file=sys.stderr)
            else:
                print("[NPC] No NPCs loaded, skipping hotload.", file=sys.stderr)
        except Exception as e:
            print(f"[NPC] Hotloading failed (will load on first chat): {e}", file=sys.stderr)

    def load_templates(self) -> Dict:
        """Load NPC templates"""
        try:
            with open(self.npc_config_path, 'r') as f:
                config = json.load(f)
                return config.get('npc_templates', {})
        except FileNotFoundError:
            return {}

    def load_npcs(self) -> Dict:
        """Load NPC configurations (static + dynamic)"""
        npcs = {}
        
        # Load static NPCs
        try:
            with open(self.npc_config_path, 'r') as f:
                config = json.load(f)
                for npc in config.get('npcs', []):
                    npcs[npc['id']] = npc
        except FileNotFoundError:
            print(f"[NPC] Warning: NPC config not found at {self.npc_config_path}", file=sys.stderr)

        # Load dynamic NPCs
        try:
            if os.path.exists(self.dynamic_npc_path):
                with open(self.dynamic_npc_path, 'r') as f:
                    dynamic_data = json.load(f)
                    for npc in dynamic_data.get('npcs', []):
                        npcs[npc['id']] = npc
        except Exception as e:
            print(f"[NPC] Error loading dynamic NPCs: {e}", file=sys.stderr)
            
        return npcs

    def save_dynamic_npcs(self):
        """Save dynamic NPCs to file"""
        try:
            # Filter out static NPCs (those present in config)
            # For simplicity, we'll just check if they are in the original config list
            # But since we merged them, we need a way to distinguish.
            # Better approach: Keep dynamic NPCs in a separate list in memory or check against static IDs.
            # For now, let's assume any NPC with 'is_dynamic': True is dynamic.
            
            dynamic_list = [npc for npc in self.npcs.values() if npc.get('is_dynamic')]
            
            with open(self.dynamic_npc_path, 'w') as f:
                json.dump({"npcs": dynamic_list}, f, indent=2)
        except Exception as e:
            print(f"[NPC] Error saving dynamic NPCs: {e}", file=sys.stderr)

    def load_memory(self) -> Dict:
        """Load NPC conversation memories"""
        try:
            with open(self.memory_path, 'r') as f:
                return json.load(f)
        except FileNotFoundError:
            return {}

    def save_memory(self):
        """Save NPC memories with error logging and pruning"""
        try:
            # Prune all memories to max 50 items to prevent bloat
            for key in self.memory:
                if isinstance(self.memory[key], list) and len(self.memory[key]) > 50:
                    self.memory[key] = self.memory[key][-50:]

            with open(self.memory_path, 'w') as f:
                json.dump(self.memory, f, indent=2)
        except Exception as e:
            print(f"[NPC] Error saving memory to {self.memory_path}: {e}", file=sys.stderr)

    def load_quests(self) -> Dict:
        """Load active quests"""
        try:
            with open(self.quest_path, 'r') as f:
                return json.load(f)
        except FileNotFoundError:
            return {"active": [], "completed": []}

    def save_quests(self):
        """Save quests with atomic write to prevent corruption"""
        try:
            # Write to temp file first
            dir_name = os.path.dirname(self.quest_path) or '.'
            fd, temp_path = tempfile.mkstemp(dir=dir_name, suffix='.tmp')
            try:
                with os.fdopen(fd, 'w') as f:
                    json.dump(self.quests, f, indent=2)
                # Atomic rename (works on POSIX systems)
                os.replace(temp_path, self.quest_path)
            except:
                # Clean up temp file on error
                if os.path.exists(temp_path):
                    os.unlink(temp_path)
                raise
        except Exception as e:
            print(f"[NPC] Error saving quests: {e}", file=sys.stderr)

    def create_npc(
        self,
        template_id: str,
        location: Dict,
        name: str = None
    ) -> Optional[Dict]:
        """
        Create a new dynamic NPC from a template
        
        Args:
            template_id: ID of the template to use (e.g., 'villager')
            location: Dict with x, y, z, dimension, biome
            name: Optional specific name
            
        Returns:
            New NPC dict or None
        """
        template = self.templates.get(template_id)
        if not template:
            print(f"[NPC] Template '{template_id}' not found", file=sys.stderr)
            return None
            
        # Generate details using LLM if needed
        details = self.generate_npc_details(template, location, name)
        if not details:
            return None
            
        npc_id = f"{template_id}_{int(datetime.now().timestamp())}_{random.randint(1000, 9999)}"
        
        new_npc = {
            "id": npc_id,
            "name": details['name'],
            "personality": details['personality'],
            "backstory": details['backstory'],
            "model": random.choice(template['models']),
            "location": location,
            "interests": template['interests'],
            "questTypes": template['quest_types'],
            "appearance": "player_model", # Could be dynamic later
            "skin": f"{template_id}.png", # Placeholder
            "dialogue_style": template['dialogue_style'],
            "is_dynamic": True,
            "template_id": template_id,
            "created_at": datetime.now().isoformat()
        }
        
        # Register and save
        self.npcs[npc_id] = new_npc
        self.save_dynamic_npcs()
        
        print(f"[NPC] Created new dynamic NPC: {new_npc['name']} ({npc_id})", file=sys.stderr)
        return new_npc

    def generate_npc_details(self, template: Dict, location: Dict, name: str = None) -> Optional[Dict]:
        """Generate specific NPC details using LLM"""
        
        prompt = f"""Generate a unique Minecraft NPC character based on this template:
Template: {template['base_personality']}
Backstory Base: {template['base_backstory']}
Location: {location.get('biome', 'unknown')} biome at ({location.get('x')}, {location.get('y')}, {location.get('z')})

"""
        if name:
            prompt += f"Name: {name}\n"
        else:
            prompt += "Generate a fitting fantasy name.\n"
            
        prompt += """
Return ONLY valid JSON in this format:
{
  "name": "Name",
  "personality": "Detailed personality description extending the base",
  "backstory": "Specific backstory connecting them to this location and their role"
}
"""

        try:
            # Use a fast model for generation
            model = template['models'][0]
            
            response = requests.post(
                f"{self.ollama_url}/api/generate",
                json={
                    "model": model,
                    "prompt": prompt,
                    "stream": False,
                    "format": "json"
                },
                timeout=30
            )
            response.raise_for_status()
            
            return json.loads(response.json()['response'])
            
        except Exception as e:
            print(f"[NPC] Error generating NPC details: {e}", file=sys.stderr)
            # Fallback if LLM fails
            return {
                "name": name or f"Unknown {template.get('type', 'NPC')}",
                "personality": template['base_personality'],
                "backstory": template['base_backstory']
            }

    def get_player_context(self, player_name: str, nearby_entities: Optional[List[Dict]] = None) -> Dict:
        """
        Get comprehensive player context from Minecraft events

        Returns:
            Dict with recent activity, location, inventory, etc.
        """
        try:
            with open(self.events_path, 'r') as f:
                events = json.load(f)
        except (FileNotFoundError, json.JSONDecodeError):
            return {"error": "No events found"}

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

        # Extract context
        context = {
            "player": player_name,
            "recent_activity": {},
            "location": None,
            "inventory": None,
            "nearby_entities": nearby_entities or [],
            "stats": {
                "builds_completed": 0,
                "blocks_placed": 0,
                "mobs_killed": 0,
                "chats": [],
                "biomes_visited": set()
            }
        }

        for event in player_events:
            event_type = event.get('eventType')
            data = event.get('data', {})

            if event_type == 'player_state':
                context['location'] = {
                    "x": data.get('x'),
                    "y": data.get('y'),
                    "z": data.get('z'),
                    "biome": data.get('biome'),
                    "dimension": data.get('dimension'),
                    "weather": data.get('weather'),
                    "timeOfDay": data.get('timeOfDay'),
                    "health": data.get('health'),
                    "hunger": data.get('hunger')
                }
                context['stats']['biomes_visited'].add(data.get('biome', 'unknown'))

            elif event_type == 'build_complete':
                context['stats']['builds_completed'] += 1
                block_counts = data.get('blockCounts', {})
                context['stats']['blocks_placed'] += sum(block_counts.values())
                if not context['recent_activity'].get('building'):
                    context['recent_activity']['building'] = []
                context['recent_activity']['building'].append({
                    "blocks": list(block_counts.keys()),
                    "count": sum(block_counts.values()),
                    "timestamp": event['timestamp']
                })

            elif event_type == 'mob_killed':
                context['stats']['mobs_killed'] += 1
                if not context['recent_activity'].get('combat'):
                    context['recent_activity']['combat'] = []
                context['recent_activity']['combat'].append({
                    "mob": data.get('mobType'),
                    "timestamp": event['timestamp']
                })

            elif event_type == 'player_chat':
                context['stats']['chats'].append(data.get('message'))

            elif event_type == 'inventory_snapshot':
                context['inventory'] = data.get('inventory', [])

        # Convert sets to lists for JSON serialization
        context['stats']['biomes_visited'] = list(context['stats']['biomes_visited'])

        return context

    def get_npc_memory(self, npc_id: str, player_name: str) -> List[Dict]:
        """Get conversation history between NPC and player"""
        key = f"{npc_id}:{player_name}"
        return self.memory.get(key, [])

    def add_to_memory(self, npc_id: str, player_name: str, role: str, content: str):
        """Add a message to NPC's memory of conversation with player"""
        key = f"{npc_id}:{player_name}"
        if key not in self.memory:
            self.memory[key] = []

        self.memory[key].append({
            "role": role,
            "content": content,
            "timestamp": datetime.now().isoformat()
        })

        # Keep only last 20 messages per NPC-player pair
        if len(self.memory[key]) > 20:
            self.memory[key] = self.memory[key][-20:]

        self.save_memory()

    def generate_npc_response(
        self,
        npc_id: str,
        player_name: str,
        player_message: str,
        context: Dict = None
    ) -> str:
        """
        Generate NPC response using local Ollama LLM

        Args:
            npc_id: NPC identifier
            player_name: Player's name
            player_message: What the player said
            context: Optional player context (activity, location, etc.)

        Returns:
            NPC's response text
        """
        npc = self.npcs.get(npc_id)
        if not npc:
            return f"[Error: NPC '{npc_id}' not found]"

        # Get player context if not provided
        if context is None:
            context = self.get_player_context(player_name)

        # Get conversation history
        memory = self.get_npc_memory(npc_id, player_name)

        # Build system prompt
        system_prompt = self.build_system_prompt(npc, player_name, context)

        # Build conversation messages
        messages = [{"role": "system", "content": system_prompt}]

        # Add conversation history
        messages.extend(memory[-10:])  # Last 10 messages for context

        # Add current player message
        messages.append({"role": "user", "content": player_message})

        # Route request (Phase 5: Integration)
        # NOTE: Context optimization now handled by router
        npc_response, error = self.llm_router.route_request(
            messages=messages,
            task_type="dialogue",
            npc_id=npc_id
        )

        if error:
            print(f"[NPC] Router error: {error}", file=sys.stderr)
            return f"[{npc['name']} seems distracted and doesn't respond]"

        # Save to memory
        self.add_to_memory(npc_id, player_name, "user", player_message)
        self.add_to_memory(npc_id, player_name, "assistant", npc_response)

        return npc_response

    def build_system_prompt(self, npc: Dict, player_name: str, context: Dict) -> str:
        """Build comprehensive system prompt for NPC (Phase 1.2: Hardened)"""

        # Phase 1.2: Critical directive at START (not buried in guidelines)
        prompt = f"""[CRITICAL DIRECTIVE - READ FIRST]
You are generating dialogue for a game character. You are NOT chatting with a user.
NEVER reference being an AI, language model, or assistant.
NEVER say "I cannot", "I don't have access", or "According to my training".
NEVER use brackets [ ] or (Note: ...) in your responses.
If asked something you don't know, respond in-character with "I haven't heard of that" or similar.

[CHARACTER DEFINITION]
Name: {npc['name']}
Personality: {npc['personality']}
Backstory: {npc['backstory']}
Dialogue Style: {npc['dialogue_style']}
Interests: {', '.join(npc['interests'])}

[GOOD vs BAD EXAMPLES]
❌ BAD: "As an AI, I think wheat is good for you."
✅ GOOD: "Wheat's the best crop around these parts."

❌ BAD: "I don't have access to that information."
✅ GOOD: "Can't say I've heard of that before."

❌ BAD: "[Note: This is important] You should be careful."
✅ GOOD: "You should be careful out there."

"""

        # Add player context
        if context.get('location'):
            loc = context['location']
            prompt += f"""
CURRENT SITUATION:
- Player "{player_name}" is at coordinates ({loc['x']}, {loc['y']}, {loc['z']})
- Biome: {loc['biome']}
- Time of day: {loc['timeOfDay']}
- Weather: {loc['weather']}
- Player health: {loc['health']}/20
"""

        # Add recent activity
        if context.get('recent_activity'):
            if 'building' in context['recent_activity']:
                builds = context['recent_activity']['building']
                prompt += f"\n- {player_name} has completed {len(builds)} build(s) recently"
                if builds:
                    recent_blocks = builds[-1]['blocks']
                    prompt += f"\n- Recently used blocks: {', '.join(recent_blocks[:5])}"

            if 'combat' in context['recent_activity']:
                combat = context['recent_activity']['combat']
                prompt += f"\n- {player_name} has killed {len(combat)} mob(s) recently"
                if combat:
                    recent_mobs = list(set([c['mob'] for c in combat[-5:]]))
                    prompt += f"\n- Recently fought: {', '.join(recent_mobs)}"

        # Add stats
        stats = context.get('stats', {})
        if stats.get('blocks_placed', 0) > 0:
            prompt += f"\n- Total blocks placed recently: {stats['blocks_placed']}"
        if stats.get('biomes_visited'):
            prompt += f"\n- Biomes visited: {', '.join(stats['biomes_visited'])}"

        # Add proximity awareness (Phase 2.2)
        prompt += self._format_nearby_entities(context.get('nearby_entities', []))

        # Phase 1.2: Stronger in-character framing
        prompt += f"""

[YOUR RESPONSE]
Speak ONLY as {npc['name']}. Stay in character at ALL times.

Guidelines:
- Keep responses conversational (2-4 sentences usually)
- Reference your backstory and interests naturally
- React to the player's recent activity if relevant
- You can offer quests or share lore when appropriate
- Use the dialogue style specified for your character
- Comment on player's builds or combat if relevant

        Remember: You are {npc['name']}, a living character in this world with your own goals and personality.
        You are NOT an AI assistant. Never break character.
        """

        return prompt

    def _format_nearby_entities(self, entities: List[Dict]) -> str:
        """Format nearby entities for prompt injection"""
        if not entities:
            return ""

        lines = []
        for entity in entities[:10]:
            etype = entity.get('type')
            distance = entity.get('distance')

            if etype == 'npc':
                lines.append(f"- {entity.get('name', 'NPC')} ({distance}m)")
            elif etype == 'player':
                lines.append(f"- Player: {entity.get('name', 'Unknown')} ({distance}m)")
            elif etype == 'mob':
                hostile = "HOSTILE" if entity.get('hostile') else "passive"
                lines.append(f"- {entity.get('mob_type', 'mob')} ({hostile}, {distance}m)")

        if not lines:
            return ""

        return "\n[NEARBY ENTITIES]\n" + "\n".join(lines) + "\nIMPORTANT: Adjust your tone if guards, hostile mobs, or other witnesses are present.\n"

    def generate_quest(
        self,
        npc_id: str,
        player_name: str,
        quest_type: str = None
    ) -> Optional[Dict]:
        """
        Generate a quest based on player activity and NPC personality

        Args:
            npc_id: NPC offering the quest
            player_name: Player receiving the quest
            quest_type: Optional type override

        Returns:
            Quest dict or None
        """
        npc = self.npcs.get(npc_id)
        if not npc:
            return None

        context = self.get_player_context(player_name)

        # Determine quest type from NPC's specialties and player activity
        if not quest_type:
            quest_type = self.suggest_quest_type(npc, context)

        # Generate quest using LLM
        quest_prompt = self.build_quest_generation_prompt(npc, player_name, context, quest_type)

        try:
            response = requests.post(
                f"{self.ollama_url}/api/generate",
                json={
                    "model": npc['model'],
                    "prompt": quest_prompt,
                    "stream": False,
                    "format": "json"
                },
                timeout=30
            )
            response.raise_for_status()

            quest_data = json.loads(response.json()['response'])

            # Add metadata
            quest = {
                "id": f"{npc_id}_{player_name}_{datetime.now().timestamp()}",
                "npc_id": npc_id,
                "npc_name": npc['name'],
                "player": player_name,
                "type": quest_type,
                "status": "active",
                "created": datetime.now().isoformat(),
                **quest_data
            }

            # Add to active quests
            self.quests['active'].append(quest)
            self.save_quests()

            return quest

        except Exception as e:
            print(f"[NPC] Error generating quest: {e}", file=sys.stderr)
            return None

    def suggest_quest_type(self, npc: Dict, context: Dict) -> str:
        """Suggest quest type based on NPC and player activity"""
        # Check what player has been doing
        activity = context.get('recent_activity', {})

        if 'combat' in activity and 'combat' in npc.get('questTypes', []):
            return 'combat'
        elif 'building' in activity and 'building' in npc.get('questTypes', []):
            return 'building'
        elif npc.get('questTypes'):
            return npc['questTypes'][0]
        else:
            return 'exploration'

    def build_quest_generation_prompt(
        self,
        npc: Dict,
        player_name: str,
        context: Dict,
        quest_type: str
    ) -> str:
        """Build prompt for quest generation"""

        prompt = f"""You are {npc['name']}, and you want to give {player_name} a quest.

Based on what you've observed:
- Player has been {self.summarize_activity(context)}
- Current location: {context.get('location', {}).get('biome', 'unknown')} biome
- Quest type: {quest_type}

Generate a quest that fits your personality and the player's recent activity.

Return ONLY valid JSON in this exact format:
{{
  "title": "Quest Title",
  "description": "A narrative description of the quest (2-3 sentences, in character)",
  "objectives": [
    {{"type": "kill_mobs", "target": "zombie", "count": 10}},
    {{"type": "return_to_npc", "npc": "{npc['id']}"}}
  ],
  "reward": {{
    "type": "lore",
    "content": "A piece of lore or knowledge you'll share (1-2 sentences)"
  }}
}}

Make the quest interesting and tied to your character's interests: {', '.join(npc['interests'])}
"""

        return prompt

    def summarize_activity(self, context: Dict) -> str:
        """Summarize player's recent activity"""
        activity = context.get('recent_activity', {})
        stats = context.get('stats', {})

        parts = []
        if stats.get('builds_completed', 0) > 0:
            parts.append(f"building ({stats['blocks_placed']} blocks)")
        if stats.get('mobs_killed', 0) > 0:
            parts.append(f"fighting ({stats['mobs_killed']} mobs killed)")
        if stats.get('biomes_visited'):
            parts.append(f"exploring ({len(stats['biomes_visited'])} biomes)")

        return ", ".join(parts) if parts else "exploring the world"

    def check_quest_progress(self, player_name: str) -> Dict:
        """
        Check and update quest progress based on player events

        Returns:
            Dict with updated quests and any completions
        """
        # Get active quests for this player
        active_quests = [q for q in self.quests['active'] if q['player'] == player_name]

        if not active_quests:
            return {"player": player_name, "active_quests": 0, "updates": []}

        # Load events
        try:
            with open(self.events_path, 'r') as f:
                events = json.load(f)
        except (FileNotFoundError, json.JSONDecodeError):
            return {"player": player_name, "error": "No events found"}

        updates = []
        completed_quests = []

        for quest in active_quests:
            quest_updates = []
            all_complete = True

            # Get events since quest was created
            quest_created = datetime.fromisoformat(quest['created'].replace('Z', '+00:00'))
            relevant_events = [
                e for e in events
                if e.get('data', {}).get('playerName') == player_name
                and datetime.fromisoformat(e['timestamp'].replace('Z', '+00:00')) > quest_created
            ]

            # Check each objective
            for i, objective in enumerate(quest.get('objectives', [])):
                obj_type = objective.get('type')

                # Initialize progress tracking if not exists
                if 'progress' not in objective:
                    objective['progress'] = 0
                if 'completed' not in objective:
                    objective['completed'] = False

                if objective['completed']:
                    continue

                # Check based on objective type
                if obj_type == 'kill_mobs':
                    target = objective.get('target', '').lower()
                    count = objective.get('count', 1)

                    kills = [
                        e for e in relevant_events
                        if e.get('eventType') == 'mob_killed'
                        and target in e.get('data', {}).get('mobType', '').lower()
                    ]

                    objective['progress'] = len(kills)
                    if len(kills) >= count:
                        objective['completed'] = True
                        quest_updates.append(f"Killed {count} {target}(s)")
                    else:
                        all_complete = False

                elif obj_type == 'collect_items':
                    # Check latest inventory snapshot
                    item = objective.get('target', '').lower()
                    count = objective.get('count', 1)

                    inventory_events = [
                        e for e in relevant_events
                        if e.get('eventType') == 'inventory_snapshot'
                    ]

                    if inventory_events:
                        latest = inventory_events[-1]
                        items = latest.get('data', {}).get('inventory', [])
                        item_count = sum(
                            i.get('count', 0) for i in items
                            if item in i.get('item', '').lower()
                        )
                        objective['progress'] = item_count
                        if item_count >= count:
                            objective['completed'] = True
                            quest_updates.append(f"Collected {count} {item}(s)")
                        else:
                            all_complete = False
                    else:
                        all_complete = False

                elif obj_type == 'visit_biome':
                    biome = objective.get('target', '').lower()

                    biome_visits = [
                        e for e in relevant_events
                        if e.get('eventType') == 'player_state'
                        and biome in e.get('data', {}).get('biome', '').lower()
                    ]

                    if biome_visits:
                        objective['completed'] = True
                        objective['progress'] = 1
                        quest_updates.append(f"Visited {biome} biome")
                    else:
                        all_complete = False

                elif obj_type == 'build_blocks':
                    count = objective.get('count', 1)
                    block_type = objective.get('target', None)

                    build_events = [
                        e for e in relevant_events
                        if e.get('eventType') == 'build_complete'
                    ]

                    total_blocks = 0
                    for e in build_events:
                        block_counts = e.get('data', {}).get('blockCounts', {})
                        if block_type:
                            total_blocks += block_counts.get(block_type, 0)
                        else:
                            total_blocks += sum(block_counts.values())

                    objective['progress'] = total_blocks
                    if total_blocks >= count:
                        objective['completed'] = True
                        quest_updates.append(f"Placed {count} blocks")
                    else:
                        all_complete = False

                elif obj_type == 'return_to_npc':
                    # This requires player to be near NPC - check player state
                    npc_id = objective.get('npc')
                    npc = self.npcs.get(npc_id)

                    if npc:
                        npc_loc = npc.get('location', {})
                        state_events = [
                            e for e in relevant_events
                            if e.get('eventType') == 'player_state'
                        ]

                        for e in state_events:
                            data = e.get('data', {})
                            dx = abs(data.get('x', 0) - npc_loc.get('x', 0))
                            dy = abs(data.get('y', 0) - npc_loc.get('y', 0))
                            dz = abs(data.get('z', 0) - npc_loc.get('z', 0))

                            if dx <= 10 and dy <= 10 and dz <= 10:
                                objective['completed'] = True
                                objective['progress'] = 1
                                quest_updates.append(f"Returned to {npc['name']}")
                                break
                        else:
                            all_complete = False
                    else:
                        all_complete = False

                else:
                    # Unknown objective type
                    all_complete = False

            # Check if quest is complete
            if all_complete and quest.get('objectives'):
                quest['status'] = 'completed'
                quest['completed_at'] = datetime.now().isoformat()
                completed_quests.append(quest)

                # Deliver the reward
                reward_result = self.deliver_reward(player_name, quest)

                updates.append({
                    "quest_id": quest['id'],
                    "title": quest.get('title'),
                    "status": "completed",
                    "updates": quest_updates,
                    "reward": quest.get('reward'),
                    "reward_delivered": reward_result
                })
            elif quest_updates:
                updates.append({
                    "quest_id": quest['id'],
                    "title": quest.get('title'),
                    "status": "in_progress",
                    "updates": quest_updates
                })

        # Move completed quests
        for quest in completed_quests:
            self.quests['active'].remove(quest)
            self.quests['completed'].append(quest)

        # Save updated quests
        self.save_quests()

        return {
            "player": player_name,
            "active_quests": len(active_quests) - len(completed_quests),
            "completed": len(completed_quests),
            "updates": updates
        }

    def load_build_challenges(self) -> List[Dict]:
        """Load build challenge templates from config"""
        try:
            with open(self.npc_config_path, 'r') as f:
                config = json.load(f)
                return config.get('build_challenges', [])
        except (FileNotFoundError, KeyError):
            print("[NPC] No build challenges found in config", file=sys.stderr)
            return []

    def get_suitable_build_challenges(self, npc_id: str) -> List[Dict]:
        """Get build challenges suitable for this NPC"""
        challenges = self.load_build_challenges()
        npc = self.npcs.get(npc_id)

        if not npc:
            return []

        # Filter by giver_affinity
        suitable = [
            c for c in challenges
            if npc_id in c.get('giver_affinity', [])
        ]

        return suitable

    def generate_build_challenge_quest(
        self,
        npc_id: str,
        player_name: str,
        challenge_id: str = None
    ) -> Optional[Dict]:
        """
        Generate a themed build challenge quest

        Args:
            npc_id: NPC offering the challenge
            player_name: Player receiving the challenge
            challenge_id: Optional specific challenge ID, otherwise random suitable challenge

        Returns:
            Quest dict with build challenge requirements
        """
        npc = self.npcs.get(npc_id)
        if not npc:
            return None

        # Get suitable challenges for this NPC
        suitable_challenges = self.get_suitable_build_challenges(npc_id)

        if not suitable_challenges:
            print(f"[NPC] No suitable build challenges for {npc_id}", file=sys.stderr)
            return None

        # Select specific challenge or pick one
        if challenge_id:
            challenge = next((c for c in suitable_challenges if c['id'] == challenge_id), None)
            if not challenge:
                print(f"[NPC] Challenge {challenge_id} not found or not suitable for {npc_id}", file=sys.stderr)
                return None
        else:
            # Pick random suitable challenge
            import random
            challenge = random.choice(suitable_challenges)

        # Convert challenge template to quest format
        quest = {
            "id": f"{npc_id}_{player_name}_challenge_{datetime.now().timestamp()}",
            "npc_id": npc_id,
            "npc_name": npc['name'],
            "player": player_name,
            "type": "build_challenge",
            "challenge_id": challenge['id'],
            "status": "active",
            "created": datetime.now().isoformat(),
            "title": challenge['title'],
            "description": challenge['description'],
            "difficulty": challenge.get('difficulty', 'medium'),
            "requirements": challenge['requirements'],
            "reward": challenge['reward'],
            "validation": challenge.get('validation', {}),
            "objectives": [
                {
                    "type": "build_blocks",
                    "requirements": challenge['requirements'],
                    "progress": 0,
                    "complete": False
                },
                {
                    "type": "return_to_npc",
                    "npc": npc_id,
                    "progress": 0,
                    "complete": False
                }
            ]
        }

        # Add to active quests
        self.quests['active'].append(quest)
        self.save_quests()

        return quest

    def validate_build_challenge(
        self,
        player_name: str,
        quest_id: str,
        build_data: Dict
    ) -> Dict:
        """
        Validate a build against challenge requirements

        Args:
            player_name: Player who completed the build
            quest_id: Quest ID to validate
            build_data: Dict with block counts from recent build session

        Returns:
            Validation result with pass/fail and details
        """
        # Find the quest
        quest = next(
            (q for q in self.quests['active']
             if q.get('id') == quest_id and q.get('player') == player_name),
            None
        )

        if not quest or quest.get('type') != 'build_challenge':
            return {"valid": False, "reason": "Quest not found or not a build challenge"}

        requirements = quest.get('requirements', {})
        validation_rules = quest.get('validation', {})

        # Extract build statistics from build_data
        blocks_placed = build_data.get('blocks', {})
        total_blocks = sum(blocks_placed.values())
        unique_blocks = len(blocks_placed.keys())
        height = build_data.get('height', 0)

        validation_result = {
            "quest_id": quest_id,
            "challenge_id": quest.get('challenge_id'),
            "valid": True,
            "checks": {},
            "statistics": {
                "total_blocks": total_blocks,
                "unique_blocks": unique_blocks,
                "height": height
            }
        }

        # Check minimum blocks
        min_blocks = requirements.get('minBlocks', 0)
        if total_blocks < min_blocks:
            validation_result["checks"]["min_blocks"] = {
                "pass": False,
                "required": min_blocks,
                "actual": total_blocks
            }
            validation_result["valid"] = False
        else:
            validation_result["checks"]["min_blocks"] = {
                "pass": True,
                "required": min_blocks,
                "actual": total_blocks
            }

        # Check minimum height
        min_height = requirements.get('minHeight', 0)
        if height < min_height:
            validation_result["checks"]["min_height"] = {
                "pass": False,
                "required": min_height,
                "actual": height
            }
            validation_result["valid"] = False
        else:
            validation_result["checks"]["min_height"] = {
                "pass": True,
                "required": min_height,
                "actual": height
            }

        # Check required block types
        required_blocks = requirements.get('requiredBlockTypes', {})
        for block_type, block_req in required_blocks.items():
            if isinstance(block_req, dict):
                min_count = block_req.get('min', 0)
                # Handle special cases like "flowers" with anyOf
                if 'anyOf' in block_req:
                    # Count any of the acceptable block types
                    total_count = sum(
                        blocks_placed.get(b, 0)
                        for b in block_req['anyOf']
                    )
                    if total_count < min_count:
                        validation_result["checks"][block_type] = {
                            "pass": False,
                            "required": min_count,
                            "actual": total_count,
                            "options": block_req['anyOf']
                        }
                        validation_result["valid"] = False
                    else:
                        validation_result["checks"][block_type] = {
                            "pass": True,
                            "required": min_count,
                            "actual": total_count
                        }
                else:
                    actual_count = blocks_placed.get(block_type, 0)
                    if actual_count < min_count:
                        validation_result["checks"][block_type] = {
                            "pass": False,
                            "required": min_count,
                            "actual": actual_count
                        }
                        validation_result["valid"] = False
                    else:
                        validation_result["checks"][block_type] = {
                            "pass": True,
                            "required": min_count,
                            "actual": actual_count
                        }

        # Check minimum unique blocks (if required)
        if validation_rules.get('minUniqueBlocks'):
            min_unique = validation_rules['minUniqueBlocks']
            if unique_blocks < min_unique:
                validation_result["checks"]["unique_blocks"] = {
                    "pass": False,
                    "required": min_unique,
                    "actual": unique_blocks
                }
                validation_result["valid"] = False
            else:
                validation_result["checks"]["unique_blocks"] = {
                    "pass": True,
                    "required": min_unique,
                    "actual": unique_blocks
                }

        return validation_result

    def get_player_quests(self, player_name: str) -> Dict:
        """Get all quests for a player"""
        active = [q for q in self.quests['active'] if q['player'] == player_name]
        completed = [q for q in self.quests['completed'] if q['player'] == player_name]

        return {
            "active": active,
            "completed": completed
        }

    def deliver_reward(self, player_name: str, quest: Dict) -> Dict:
        """
        Deliver quest reward to player

        Args:
            player_name: Player receiving the reward
            quest: Completed quest with reward

        Returns:
            Dict with delivery status
        """
        reward = quest.get('reward', {})
        if not reward:
            return {"delivered": False, "reason": "No reward defined"}

        reward_type = reward.get('type', 'lore')
        delivery_result = {
            "quest_id": quest.get('id'),
            "quest_title": quest.get('title'),
            "reward_type": reward_type,
            "delivered": False
        }

        try:
            if reward_type == 'lore':
                # Deliver lore by adding to discovered lore
                lore_content = reward.get('content', '')
                if lore_content:
                    self._deliver_lore_reward(player_name, quest, lore_content)
                    delivery_result["delivered"] = True
                    delivery_result["content"] = lore_content

            elif reward_type == 'items':
                # Send item reward via HTTP bridge to Minecraft
                items = reward.get('items', [])
                if items:
                    self._deliver_item_reward(player_name, items)
                    delivery_result["delivered"] = True
                    delivery_result["items"] = items

            elif reward_type == 'xp':
                # Send XP reward via HTTP bridge
                xp_amount = reward.get('amount', 0)
                if xp_amount > 0:
                    self._deliver_xp_reward(player_name, xp_amount)
                    delivery_result["delivered"] = True
                    delivery_result["xp"] = xp_amount

            else:
                delivery_result["reason"] = f"Unknown reward type: {reward_type}"

        except Exception as e:
            delivery_result["error"] = str(e)
            print(f"[NPC] Error delivering reward: {e}", file=sys.stderr)

        return delivery_result

    def _deliver_lore_reward(self, player_name: str, quest: Dict, lore_content: str):
        """Add lore to player's discovered lore"""
        lore_path = self.root / 'discovered_lore.json'

        try:
            with open(lore_path, 'r') as f:
                discovered = json.load(f)
        except (FileNotFoundError, json.JSONDecodeError):
            discovered = {}

        if player_name not in discovered:
            discovered[player_name] = []

        # Add the lore entry
        lore_entry = {
            "source": f"quest:{quest.get('id')}",
            "npc": quest.get('npc_name', 'Unknown'),
            "content": lore_content,
            "discovered_at": datetime.now().isoformat()
        }
        discovered[player_name].append(lore_entry)

        # Save
        with open(lore_path, 'w') as f:
            json.dump(discovered, f, indent=2)

        # Also send chat message to player
        self._send_chat_to_player(
            player_name,
            f"[Quest Complete] {quest.get('npc_name', 'NPC')} shares: {lore_content}"
        )

    def _deliver_item_reward(self, player_name: str, items: list):
        """Send items to player via Minecraft HTTP bridge"""
        for item in items:
            self._send_command_to_minecraft('give_item', {
                'player': player_name,
                'item': item.get('id', 'minecraft:diamond'),
                'count': item.get('count', 1)
            })

    def _deliver_xp_reward(self, player_name: str, xp_amount: int):
        """Send XP to player via Minecraft HTTP bridge"""
        self._send_command_to_minecraft('give_xp', {
            'player': player_name,
            'amount': xp_amount
        })

    def _send_chat_to_player(self, player_name: str, message: str):
        """Send chat message to player"""
        self._send_command_to_minecraft('send_chat', {
            'player': player_name,
            'message': message
        })

    def _send_command_to_minecraft(self, command_type: str, data: dict):
        """Send command to Minecraft via HTTP bridge"""
        try:
            response = requests.post(
                'http://localhost:5558/command',
                json={
                    'type': command_type,
                    'data': data
                },
                timeout=5
            )
            if response.status_code != 200:
                print(f"[NPC] Command failed: {response.status_code}", file=sys.stderr)
        except Exception as e:
            print(f"[NPC] Failed to send command to Minecraft: {e}", file=sys.stderr)


def main():
    """Test the NPC service"""
    service = NPCService()

    print("\n=== NPC Service Test ===\n")

    # List NPCs
    print("Available NPCs:")
    for npc_id, npc in service.npcs.items():
        print(f"  - {npc['name']} ({npc_id})")
        print(f"    Personality: {npc['personality']}")
        print()

    # Test dialogue
    if service.npcs:
        test_npc = list(service.npcs.keys())[0]
        print(f"\nTesting dialogue with {service.npcs[test_npc]['name']}:")
        print("-" * 60)

        response = service.generate_npc_response(
            npc_id=test_npc,
            player_name="TestPlayer",
            player_message="Hello! I've been building a castle."
        )

        print(f"Player: Hello! I've been building a castle.")
        print(f"{service.npcs[test_npc]['name']}: {response}")
        print()


if __name__ == '__main__':
    main()
