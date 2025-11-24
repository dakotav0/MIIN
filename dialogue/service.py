#!/usr/bin/env python3
"""
Dialogue Service - BG3-style dialogue wheel with memory and relationships

Generates context-aware dialogue options based on:
- Player's recent actions
- Conversation history
- NPC personality
- Relationship level
"""

import json
import re
from pathlib import Path
from typing import Dict, List, Optional, Any
from datetime import datetime
import requests

import sys, os
BASE = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
sys.path.insert(0, BASE)

from npc.scripts.service import NPCService
from lore.service import LoreService


class DialogueService:
    """
    Dialogue Service - Generates BG3-style dialogue options
    """

    def __init__(
        self,
        relationships_path: str = None,
        ollama_url: str = "http://localhost:11434"
    ):
        """Initialize dialogue service"""
        self.root = Path(BASE)
        self.relationships_path = relationships_path or str(self.root / 'npc' / 'config' / 'relationships.json')
        self.inventory_path = str(self.root / 'npc' / 'merchant' / 'inventory.json')
        self.ollama_url = ollama_url

        # Load NPC service for access to NPCs and memory
        self.npc_service = NPCService()

        # Load lore service for RAG integration
        self.lore_service = LoreService()

        # Load relationships
        self.relationships = self.load_relationships()

        # Load merchant inventory (Phase 1.3)
        self.merchant_inventory = self.load_merchant_inventory()

        # Capture proximity snapshot from MCP request (Phase 2.1)
        self.nearby_entities = self._load_nearby_entities()

        print(f"[Dialogue] Service initialized with lore integration", file=sys.stderr)

    def load_relationships(self) -> Dict:
        """Load player-NPC relationships"""
        try:
            with open(self.relationships_path, 'r') as f:
                return json.load(f)
        except FileNotFoundError:
            return {}

    def save_relationships(self):
        """Save relationships with error logging"""
        try:
            with open(self.relationships_path, 'w') as f:
                json.dump(self.relationships, f, indent=2)
        except Exception as e:
            print(f"[Dialogue] Error saving relationships: {e}", file=sys.stderr)

    def load_merchant_inventory(self) -> Dict:
        """Load merchant inventory data (Phase 1.3)"""
        try:
            with open(self.inventory_path, 'r') as f:
                return json.load(f)
        except FileNotFoundError:
            print(f"[Dialogue] Merchant inventory not found at {self.inventory_path}", file=sys.stderr)
            return {}
        except Exception as e:
            print(f"[Dialogue] Error loading merchant inventory: {e}", file=sys.stderr)
            return {}

    def _load_nearby_entities(self) -> List[Dict]:
        """Parse nearby entity snapshot from env (provided by MCP caller)"""
        raw = os.environ.get("NEARBY_ENTITIES", "[]")
        try:
            data = json.loads(raw)
            return data if isinstance(data, list) else []
        except Exception:
            return []

    def _get_npc_inventory(self, npc_id: str) -> Optional[Dict]:
        """Get inventory for a specific NPC merchant (Phase 1.3)"""
        return self.merchant_inventory.get(npc_id)

    def _format_inventory_for_prompt(self, inventory: Dict) -> str:
        """Format inventory for LLM context (Phase 1.3)"""
        items = []
        for item in inventory.get('stock', []):
            items.append(
                f"- {item['item']}: {item['quantity']} available @ {item['price_buy']} emeralds each"
            )
        return "\n".join(items)

    def get_relationship(self, npc_id: str, player_name: str) -> Dict:
        """Get relationship data between NPC and player"""
        key = f"{npc_id}:{player_name}"
        if key not in self.relationships:
            self.relationships[key] = {
                "level": 0,  # -100 to 100
                "title": "Stranger",
                "interactions": 0,
                "quests_completed": 0,
                "last_interaction": None,
                "memorable_actions": [],  # Important things player did
                "dialogue_choices": []  # Past dialogue choices
            }
        return self.relationships[key]

    def update_relationship(self, npc_id: str, player_name: str, delta: int, reason: str = None):
        """Update relationship level"""
        rel = self.get_relationship(npc_id, player_name)
        rel['level'] = max(-100, min(100, rel['level'] + delta))
        rel['interactions'] += 1
        rel['last_interaction'] = datetime.now().isoformat()

        # Update title based on level
        level = rel['level']
        if level >= 80:
            rel['title'] = "Trusted Ally"
        elif level >= 50:
            rel['title'] = "Friend"
        elif level >= 20:
            rel['title'] = "Acquaintance"
        elif level >= -20:
            rel['title'] = "Stranger"
        elif level >= -50:
            rel['title'] = "Distrusted"
        else:
            rel['title'] = "Enemy"

        if reason:
            rel['memorable_actions'].append({
                "action": reason,
                "delta": delta,
                "timestamp": datetime.now().isoformat()
            })
            # Keep only last 20 memorable actions
            rel['memorable_actions'] = rel['memorable_actions'][-20:]

        self.save_relationships()

    def generate_dialogue_options(
        self,
        npc_id: str,
        player_name: str,
        context_type: str = "greeting"
    ) -> Dict:
        """
        Generate BG3-style dialogue options for an NPC interaction

        Args:
            npc_id: NPC identifier
            player_name: Player's name
            context_type: greeting, quest, trade, farewell, specific_topic

        Returns:
            Dict with dialogue options and metadata
        """
        npc = self.npc_service.npcs.get(npc_id)
        if not npc:
            return {"error": f"NPC '{npc_id}' not found"}

        # Get relationship
        relationship = self.get_relationship(npc_id, player_name)

        # OPTIMIZATION: Use template for first-time greeting (no memory, no quests)
        # This avoids LLM call for simple "Hello" interactions
        memory = self.npc_service.get_npc_memory(npc_id, player_name)
        player_quests = self.npc_service.get_player_quests(player_name)
        npc_quests = [q for q in player_quests['active'] if q.get('npc_id') == npc_id]

        if context_type == "greeting" and len(memory) == 0 and len(npc_quests) == 0:
            print(f"[Dialogue] Using greeting template for {npc_id} (no history)", file=sys.stderr)
            return self._get_greeting_template(npc, player_name, relationship)

        # Get player context
        player_context = self.npc_service.get_player_context(player_name)

        # Build the prompt for generating options
        prompt = self._build_options_prompt(
            npc, player_name, relationship, player_context,
            memory, npc_quests, context_type
        )

        try:
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
            response.raise_for_status()

            options_data = json.loads(response.json()['response'])

            return {
                "npc_id": npc_id,
                "npc_name": npc['name'],
                "player": player_name,
                "relationship": {
                    "level": relationship['level'],
                    "title": relationship['title']
                },
                "greeting": options_data.get('greeting', f"{npc['name']} looks at you."),
                "options": options_data.get('options', []),
                "context": context_type
            }

        except Exception as e:
            print(f"[Dialogue] Error generating options: {e}", file=sys.stderr)
            # Return fallback options
            return self._fallback_options(npc, player_name, relationship, context_type)

    def _build_options_prompt(
        self,
        npc: Dict,
        player_name: str,
        relationship: Dict,
        player_context: Dict,
        memory: List,
        active_quests: List,
        context_type: str
    ) -> str:
        """Build prompt for generating dialogue options"""

        # Summarize recent memory
        recent_memory = ""
        if memory:
            recent = memory[-5:]
            recent_memory = "\n".join([
                f"- {m['role']}: {m['content'][:100]}..."
                for m in recent
            ])

        # Summarize memorable actions
        memorable = ""
        if relationship['memorable_actions']:
            actions = relationship['memorable_actions'][-5:]
            memorable = "\n".join([
                f"- {a['action']} (relationship {'+'if a['delta']>0 else ''}{a['delta']})"
                for a in actions
            ])

        # Get player's discovered lore for shared knowledge
        discovered_lore = self.lore_service.get_all_lore_for_npc(player_name)
        lore_summary = ""
        if discovered_lore:
            lore_items = [f"- {l['title']} ({l['category']})" for l in discovered_lore[:10]]
            lore_summary = "\n".join(lore_items)

        # Phase 1.3: Load merchant inventory if NPC is a merchant
        inventory = self._get_npc_inventory(npc['id'])
        inventory_context = ""
        if inventory:
            inventory_context = self._format_inventory_for_prompt(inventory)

        prompt = f"""You are generating dialogue options for a Baldur's Gate 3 style RPG interaction.

NPC: {npc['name']}
Personality: {npc['personality']}
Dialogue Style: {npc['dialogue_style']}

PLAYER: {player_name}
Relationship: {relationship['title']} (level {relationship['level']}/100)
Interactions: {relationship['interactions']}

RECENT CONVERSATION:
{recent_memory if recent_memory else "First meeting"}

MEMORABLE PLAYER ACTIONS:
{memorable if memorable else "None yet"}

PLAYER'S RECENT ACTIVITY:
{self._summarize_context(player_context)}

PLAYER'S DISCOVERED LORE (shared knowledge - NPC can reference these topics):
{lore_summary if lore_summary else "None discovered yet"}
"""

        # Phase 1.3: Inject merchant inventory if available
        if inventory_context:
            prompt += f"""
MERCHANT INVENTORY (Phase 1.3: Only offer items you have in stock):
{inventory_context}

IMPORTANT: Only offer items you have in stock. Check quantities before mentioning trades.
"""

        # Continue building prompt
        prompt += f"""
ACTIVE QUESTS FROM THIS NPC:
{json.dumps(active_quests, indent=2) if active_quests else "None"}

CONTEXT: {context_type}

Generate a dialogue interaction with 3-5 options. Each option should:
1. Fit the player's relationship level with the NPC
2. Reference recent activity or past conversations when relevant
3. Include different tones (friendly, neutral, aggressive, curious)
4. Some options may affect relationship

Return JSON in this exact format:
{{
  "greeting": "What the NPC says when the player approaches (1-2 sentences, in character)",
  "options": [
    {{
      "id": 1,
      "text": "What the player can say",
      "tone": "friendly/neutral/aggressive/curious/flirty/intimidating",
      "relationship_delta": -5 to +5 (0 for neutral),
      "leads_to": "response/quest/trade/farewell/combat"
    }}
  ]
}}

Make options feel natural and reactive to the context. High relationship = more friendly options available. Low relationship = more hostile options."""

        return prompt

    def _summarize_context(self, context: Dict) -> str:
        """Summarize player context for the prompt"""
        if not context or 'error' in context:
            return "Unknown"

        parts = []
        stats = context.get('stats', {})

        if stats.get('blocks_placed', 0) > 0:
            parts.append(f"Built {stats['blocks_placed']} blocks recently")
        if stats.get('mobs_killed', 0) > 0:
            parts.append(f"Killed {stats['mobs_killed']} mobs")
        if stats.get('biomes_visited'):
            parts.append(f"Visited {len(stats['biomes_visited'])} biomes")

        location = context.get('location')
        if location:
            parts.append(f"Currently in {location.get('biome', 'unknown')} biome")
            if location.get('health', 20) < 10:
                parts.append("Low health")

        return "\n".join(parts) if parts else "Just exploring"

    def _get_greeting_template(
        self,
        npc: Dict,
        player_name: str,
        relationship: Dict
    ) -> Dict:
        """
        Generate instant greeting template based on NPC personality.

        This avoids LLM call for first-time interactions (no memory, no quests).
        Response time: <1ms vs 3-8s with LLM.
        """
        npc_id = npc['id']
        level = relationship['level']
        personality = npc.get('personality', '')

        # Personality-based greetings
        greeting_templates = {
            'marina': {
                'neutral': f"{npc['name']} looks up from mending nets. The sea's calm today.",
                'friendly': f"Ahoy, {player_name}! The tides brought you here at the right time.",
                'close': f"{player_name}, my friend! The ocean whispers your name today."
            },
            'vex': {
                'neutral': f"{npc['name']} stares through you, seeing... something else.",
                'friendly': f"You again. The dimensions align when you're near, {player_name}.",
                'close': f"{player_name}... I've seen you in seventeen realities. This one feels... real."
            },
            'rowan': {
                'neutral': f"{npc['name']} sizes you up with a merchant's eye.",
                'friendly': f"Well met, {player_name}. I was hoping you'd show up—business opportunity.",
                'close': f"{player_name}, my favorite customer! I've been saving something special for you."
            },
            'kira': {
                'neutral': f"{npc['name']} nods curtly. Dusk falls—dangerous creatures stir.",
                'friendly': f"{player_name}. Good timing. I could use someone who knows how to fight.",
                'close': f"{player_name}! Perfect timing. Got a hunt planned that needs two swords."
            },
            'sage': {
                'neutral': f"{npc['name']} acknowledges you with a gentle smile. The forest hums softly.",
                'friendly': f"Welcome, {player_name}. The plants have been whispering about you.",
                'close': f"{player_name}, dear friend. The forest spirit says you bring harmony."
            },
            'thane': {
                'neutral': f"{npc['name']} glances up from blueprints, hammer in hand.",
                'friendly': f"{player_name}. Your timing's good—I need someone with steady hands.",
                'close': f"{player_name}! Finally, someone who appreciates proper craftsmanship."
            },
            'lyra': {
                'neutral': f"{npc['name']} looks up from star charts, aura shimmering.",
                'friendly': f"{player_name}... your aura shifts like aurora tonight. Intriguing.",
                'close': f"{player_name}! The cosmos aligns—I was just thinking of you."
            }
        }

        # Select greeting based on relationship level
        if level >= 50:
            tone = 'close'
        elif level >= 20:
            tone = 'friendly'
        else:
            tone = 'neutral'

        # Get template or use generic
        templates = greeting_templates.get(npc_id, {})
        greeting = templates.get(tone, f"{npc['name']} looks at you.")

        # Standard dialogue options based on relationship
        if level >= 50:
            options = [
                {"id": 1, "text": "Good to see you! What's happening?", "tone": "friendly", "relationship_delta": 1, "leads_to": "response"},
                {"id": 2, "text": "I need your expertise on something.", "tone": "neutral", "relationship_delta": 0, "leads_to": "quest"},
                {"id": 3, "text": "Just wanted to say hi.", "tone": "friendly", "relationship_delta": 1, "leads_to": "farewell"}
            ]
        elif level >= 0:
            options = [
                {"id": 1, "text": "Hello. Can we talk?", "tone": "neutral", "relationship_delta": 1, "leads_to": "response"},
                {"id": 2, "text": "Do you have any work?", "tone": "neutral", "relationship_delta": 0, "leads_to": "quest"},
                {"id": 3, "text": "[Leave]", "tone": "neutral", "relationship_delta": 0, "leads_to": "farewell"}
            ]
        else:
            options = [
                {"id": 1, "text": "I come in peace.", "tone": "friendly", "relationship_delta": 2, "leads_to": "response"},
                {"id": 2, "text": "Let's start over.", "tone": "neutral", "relationship_delta": 1, "leads_to": "response"},
                {"id": 3, "text": "[Leave quietly]", "tone": "neutral", "relationship_delta": 0, "leads_to": "farewell"}
            ]

        return {
            "npc_id": npc_id,
            "npc_name": npc['name'],
            "player": player_name,
            "relationship": {
                "level": relationship['level'],
                "title": relationship['title']
            },
            "greeting": greeting,
            "options": options,
            "context": "greeting",
            "note": "Template greeting (instant, no LLM)"
        }

    def _fallback_options(
        self,
        npc: Dict,
        player_name: str,
        relationship: Dict,
        context_type: str
    ) -> Dict:
        """Return fallback dialogue options when LLM fails"""
        level = relationship['level']

        if level >= 50:
            greeting = f"Ah, {player_name}! Good to see you again, friend."
            options = [
                {"id": 1, "text": "Good to see you too! What's new?", "tone": "friendly", "relationship_delta": 1, "leads_to": "response"},
                {"id": 2, "text": "I need your help with something.", "tone": "neutral", "relationship_delta": 0, "leads_to": "quest"},
                {"id": 3, "text": "Just passing through.", "tone": "neutral", "relationship_delta": 0, "leads_to": "farewell"},
            ]
        elif level >= 0:
            greeting = f"{npc['name']} regards you with cautious interest."
            options = [
                {"id": 1, "text": "Hello. I'd like to talk.", "tone": "neutral", "relationship_delta": 1, "leads_to": "response"},
                {"id": 2, "text": "Do you have any work for me?", "tone": "neutral", "relationship_delta": 0, "leads_to": "quest"},
                {"id": 3, "text": "[Leave]", "tone": "neutral", "relationship_delta": 0, "leads_to": "farewell"},
            ]
        else:
            greeting = f"{npc['name']} eyes you with suspicion."
            options = [
                {"id": 1, "text": "I mean no harm.", "tone": "friendly", "relationship_delta": 2, "leads_to": "response"},
                {"id": 2, "text": "We don't have to be enemies.", "tone": "neutral", "relationship_delta": 1, "leads_to": "response"},
                {"id": 3, "text": "[Attack]", "tone": "aggressive", "relationship_delta": -20, "leads_to": "combat"},
                {"id": 4, "text": "[Leave]", "tone": "neutral", "relationship_delta": 0, "leads_to": "farewell"},
            ]

        return {
            "npc_id": npc['id'],
            "npc_name": npc['name'],
            "player": player_name,
            "relationship": {
                "level": relationship['level'],
                "title": relationship['title']
            },
            "greeting": greeting,
            "options": options,
            "context": context_type,
            "note": "Fallback options (LLM unavailable)"
        }

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

    def select_option(
        self,
        npc_id: str,
        player_name: str,
        option_id: int,
        option_text: str,
        relationship_delta: int = 0
    ) -> Dict:
        """
        Process a selected dialogue option and generate NPC response

        Args:
            npc_id: NPC identifier
            player_name: Player's name
            option_id: Selected option ID
            option_text: Text of the selected option
            relationship_delta: Relationship change from this option

        Returns:
            Dict with NPC response and updated state
        """
        # Update relationship
        if relationship_delta != 0:
            self.update_relationship(
                npc_id, player_name, relationship_delta,
                f"Dialogue choice: {option_text[:50]}"
            )

        # Record the choice
        rel = self.get_relationship(npc_id, player_name)
        rel['dialogue_choices'].append({
            "option_id": option_id,
            "text": option_text,
            "timestamp": datetime.now().isoformat()
        })
        # Keep only last 50 choices
        rel['dialogue_choices'] = rel['dialogue_choices'][-50:]
        self.save_relationships()

        # Generate NPC response using the existing NPC service
        player_context = self.npc_service.get_player_context(
            player_name, nearby_entities=self.nearby_entities
        )
        response = self.npc_service.generate_npc_response(
            npc_id, player_name, option_text, context=player_context
        )

        # Sanitize response to remove meta-awareness (Phase 1.1)
        response = self._sanitize_npc_response(response)

        return {
            "npc_id": npc_id,
            "player": player_name,
            "player_choice": option_text,
            "npc_response": response,
            "relationship_change": relationship_delta,
            "new_relationship": {
                "level": rel['level'],
                "title": rel['title']
            }
        }


    def start_llm_dialogue(
        self,
        npc_id: str,
        player_name: str
    ) -> Dict:
        """
        Start a new LLM-driven dialogue session
        """
        # Generate a conversation ID
        import uuid
        conversation_id = str(uuid.uuid4())
        
        # Get initial options (greeting)
        result = self.generate_dialogue_options(npc_id, player_name, "greeting")
        
        # Add conversation ID to result
        result["conversation_id"] = conversation_id
        
        return result

    def respond_to_dialogue(
        self,
        conversation_id: str,
        npc_id: str,
        player_name: str,
        option_text: str
    ) -> Dict:
        """
        Handle player response and generate next turn of dialogue
        """
        # 1. Process selection (update relationship, memory)
        # We don't have option_id here, so we pass 0 or find it if possible.
        # For LLM dialogue, strict ID tracking is less critical than text.
        selection_result = self.select_option(npc_id, player_name, 0, option_text)

        # Response is already sanitized in select_option
        npc_response = selection_result["npc_response"]
        
        # 2. Check if conversation should end
        # Simple heuristic: if response is short and final, or contains farewell keywords
        conversation_ended = False
        lower_response = npc_response.lower()
        if "goodbye" in lower_response or "farewell" in lower_response or "safe travels" in lower_response:
             conversation_ended = True
             
        # 3. Generate new options if not ended
        new_options = []
        if not conversation_ended:
            # Generate options based on the new context (response we just gave)
            # We pass "continue" as context type, or maybe the last topic
            next_turn = self.generate_dialogue_options(npc_id, player_name, "conversation_turn")
            new_options = next_turn.get("options", [])
            
        return {
            "npc_id": npc_id,
            "conversation_id": conversation_id,
            "npc_response": npc_response,
            "conversation_ended": conversation_ended,
            "new_options": new_options
        }

def main():
    """CLI + daemon entrypoint for dialogue service"""
    # Daemon mode: long-lived process serving JSONL requests on stdin/stdout
    if len(sys.argv) >= 2 and sys.argv[1] == "serve":
        service = DialogueService()

        for line in sys.stdin:
            line = line.strip()
            if not line:
                continue

            try:
                msg = json.loads(line)
                req_id = msg.get("id")
                command = msg.get("command")
                args = msg.get("args", {}) or {}

                # Update proximity snapshot per request
                service.nearby_entities = args.get("nearby_entities", [])

                if command == "options":
                    npc_id = args["npc"]
                    player_name = args["player"]
                    context_type = args.get("context", "greeting")
                    result = service.generate_dialogue_options(npc_id, player_name, context_type)
                elif command == "select":
                    result = service.select_option(
                        args["npc"],
                        args["player"],
                        int(args.get("option_id", 0)),
                        args.get("option_text", ""),
                        int(args.get("relationship_delta", 0))
                    )
                elif command == "start_llm":
                    result = service.start_llm_dialogue(args["npc"], args["player"])
                elif command == "respond":
                    result = service.respond_to_dialogue(
                        args["conversation_id"],
                        args["npc"],
                        args["player"],
                        args.get("option_text", "")
                    )
                else:
                    raise ValueError(f"Unknown command: {command}")

                print(json.dumps({"id": req_id, "result": result}))
                sys.stdout.flush()
            except Exception as e:
                req_id = None
                try:
                    req_id = msg.get("id") if 'msg' in locals() and isinstance(msg, dict) else None
                except Exception:
                    req_id = None
                print(json.dumps({"id": req_id, "error": str(e)}))
                sys.stdout.flush()
        return

    # Legacy CLI mode
    if len(sys.argv) < 4:
        print(json.dumps({
            "error": "Usage: dialogue_service.py <command> <npc_id> <player_name> [args...]",
            "commands": ["options", "select", "start_llm", "respond", "serve"]
        }))
        sys.exit(1)

    command = sys.argv[1]
    npc_id = sys.argv[2]
    player_name = sys.argv[3]

    service = DialogueService()

    if command == "options":
        context_type = sys.argv[4] if len(sys.argv) > 4 else "greeting"
        result = service.generate_dialogue_options(npc_id, player_name, context_type)
    elif command == "select":
        if len(sys.argv) < 7:
            result = {"error": "Usage: select <npc_id> <player> <option_id> <option_text> <delta>"}
        else:
            option_id = int(sys.argv[4])
            option_text = sys.argv[5]
            delta = int(sys.argv[6])
            result = service.select_option(npc_id, player_name, option_id, option_text, delta)
    elif command == "start_llm":
        result = service.start_llm_dialogue(npc_id, player_name)
    elif command == "respond":
        if len(sys.argv) < 6:
             result = {"error": "Usage: respond <npc_id> <player> <conversation_id> <option_text>"}
        else:
            conversation_id = sys.argv[4]
            option_text = sys.argv[5]
            result = service.respond_to_dialogue(conversation_id, npc_id, player_name, option_text)
    else:
        result = {"error": f"Unknown command: {command}"}

    print(json.dumps(result))


if __name__ == '__main__':
    main()
