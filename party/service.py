#!/usr/bin/env python3
"""
Party Service - Multi-agent coordination for NPC parties

Handles:
- Party formation and management
- Multi-agent dialogue routing
- Shared quests and objectives
- NPC collaboration based on expertise
"""

import json
from pathlib import Path
from typing import Dict, List, Optional, Any
from datetime import datetime
import requests

import sys, os
BASE = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
sys.path.insert(0, BASE)

from npc.scripts.service import NPCService


class PartyService:
    """
    Party Service - Coordinate multiple NPCs working together
    """

    def __init__(
        self,
        parties_path: str = None,
        ollama_url: str = "http://localhost:11434"
    ):
        """Initialize party service"""
        self.root = Path(__file__).parent
        self.parties_path = parties_path or str(self.root / 'player_parties.json')
        self.ollama_url = ollama_url

        # Load NPC service
        self.npc_service = NPCService()

        # Load parties
        self.parties = self.load_parties()

        print(f"[Party] Service initialized", file=sys.stderr)

    def load_parties(self) -> Dict:
        """Load party data"""
        try:
            with open(self.parties_path, 'r') as f:
                return json.load(f)
        except FileNotFoundError:
            return {}

    def save_parties(self):
        """Save party data with error logging"""
        try:
            with open(self.parties_path, 'w') as f:
                json.dump(self.parties, f, indent=2)
        except Exception as e:
            print(f"[Party] Error saving parties: {e}", file=sys.stderr)

    def get_player_party(self, player_name: str) -> Optional[Dict]:
        """Get player's current party"""
        return self.parties.get(player_name)

    def create_party(self, player_name: str, party_name: str = None) -> Dict:
        """Create a new party for a player"""
        if player_name in self.parties:
            return {
                "error": "Player already has a party",
                "party": self.parties[player_name]
            }

        party = {
            "name": party_name or f"{player_name}'s Party",
            "leader": player_name,
            "members": [],  # NPC IDs
            "created": datetime.now().isoformat(),
            "shared_quests": [],
            "chat_history": [],
            "active": True
        }

        self.parties[player_name] = party
        self.save_parties()

        return {
            "success": True,
            "message": f"Created party: {party['name']}",
            "party": party
        }

    def invite_npc(self, player_name: str, npc_id: str) -> Dict:
        """Invite an NPC to join the party"""
        if player_name not in self.parties:
            return {"error": "No active party. Create one first with /party create"}

        party = self.parties[player_name]

        # Check if NPC exists
        npc = self.npc_service.npcs.get(npc_id)
        if not npc:
            return {"error": f"NPC '{npc_id}' not found"}

        # Check if already in party
        if npc_id in party['members']:
            return {"error": f"{npc['name']} is already in your party"}

        # Check party size limit
        if len(party['members']) >= 4:
            return {"error": "Party is full (max 4 members)"}

        # Add NPC to party
        party['members'].append(npc_id)
        self.save_parties()

        # Generate NPC response to joining
        response = self._generate_join_response(npc, player_name, party)

        return {
            "success": True,
            "npc_id": npc_id,
            "npc_name": npc['name'],
            "message": f"{npc['name']} has joined the party!",
            "npc_response": response,
            "party_size": len(party['members'])
        }

    def _generate_join_response(self, npc: Dict, player_name: str, party: Dict) -> str:
        """Generate NPC's response to joining a party"""
        # Get other party members for context
        other_members = [
            self.npc_service.npcs.get(npc_id, {}).get('name', npc_id)
            for npc_id in party['members']
            if npc_id != npc['id']
        ]

        prompt = f"""You are {npc['name']}, a {npc['personality']} character.
{player_name} has invited you to join their party{f" with {', '.join(other_members)}" if other_members else ""}.

Generate a brief (1-2 sentence) in-character response accepting the invitation.
Your dialogue style: {npc['dialogue_style']}

Just provide the dialogue response, no extra formatting."""

        try:
            response = requests.post(
                f"{self.ollama_url}/api/generate",
                json={
                    "model": "llama3.2:latest",
                    "prompt": prompt,
                    "stream": False
                },
                timeout=15
            )
            response.raise_for_status()
            return response.json()['response'].strip()
        except Exception as e:
            print(f"[Party] Error generating join response: {e}", file=sys.stderr)
            return f"*{npc['name']} nods and joins the party*"

    def leave_party(self, player_name: str, npc_id: str = None) -> Dict:
        """Remove an NPC from party, or disband if no NPC specified"""
        if player_name not in self.parties:
            return {"error": "No active party"}

        party = self.parties[player_name]

        if npc_id:
            # Remove specific NPC
            if npc_id not in party['members']:
                return {"error": f"NPC not in party"}

            party['members'].remove(npc_id)
            npc = self.npc_service.npcs.get(npc_id, {})
            self.save_parties()

            return {
                "success": True,
                "message": f"{npc.get('name', npc_id)} has left the party",
                "remaining": len(party['members'])
            }
        else:
            # Disband entire party
            del self.parties[player_name]
            self.save_parties()

            return {
                "success": True,
                "message": "Party has been disbanded"
            }

    def party_chat(self, player_name: str, message: str) -> Dict:
        """
        Send a message to the party - routes to most appropriate NPC
        based on message content and NPC expertise
        """
        if player_name not in self.parties:
            return {"error": "No active party"}

        party = self.parties[player_name]

        if not party['members']:
            return {"error": "No members in party"}

        # Route to appropriate NPC based on message content
        responding_npc = self._route_message(message, party['members'])

        if not responding_npc:
            responding_npc = party['members'][0]  # Fallback to first member

        npc = self.npc_service.npcs.get(responding_npc)
        if not npc:
            return {"error": f"NPC '{responding_npc}' not found"}

        # Get other party members for context
        other_members = [
            self.npc_service.npcs.get(npc_id, {}).get('name', npc_id)
            for npc_id in party['members']
            if npc_id != responding_npc
        ]

        # Generate response with party context
        response = self._generate_party_response(
            npc, player_name, message, other_members, party
        )

        # Add to chat history
        party['chat_history'].append({
            "player": player_name,
            "message": message,
            "responder": responding_npc,
            "response": response,
            "timestamp": datetime.now().isoformat()
        })
        # Keep last 50 messages
        party['chat_history'] = party['chat_history'][-50:]
        self.save_parties()

        return {
            "success": True,
            "responder": {
                "id": responding_npc,
                "name": npc['name']
            },
            "response": response,
            "party_members": [
                {"id": npc_id, "name": self.npc_service.npcs.get(npc_id, {}).get('name', npc_id)}
                for npc_id in party['members']
            ]
        }

    def _route_message(self, message: str, party_members: List[str]) -> Optional[str]:
        """
        Route message to most appropriate party member based on content
        Uses NPC interests/expertise for matching
        """
        message_lower = message.lower()
        scores = {}

        for npc_id in party_members:
            npc = self.npc_service.npcs.get(npc_id)
            if not npc:
                continue

            score = 0
            interests = npc.get('interests', [])
            quest_types = npc.get('questTypes', [])

            # Check interests
            for interest in interests:
                if interest.lower() in message_lower:
                    score += 3

            # Check quest types
            for qt in quest_types:
                if qt.lower() in message_lower:
                    score += 2

            # Check personality keywords
            personality = npc.get('personality', '').lower()
            personality_words = personality.split(', ')
            for word in personality_words:
                if word in message_lower:
                    score += 1

            # Combat keywords -> Kira
            combat_keywords = [
                'fight', 'combat', 'monster', 'kill', 'attack', 'defend', 'weapon', 'sword',
                'armor', 'battle', 'war', 'enemy', 'mob', 'zombie', 'skeleton', 'creeper',
                'enderman', 'hostile', 'danger', 'protect', 'guard', 'raid', 'pillager',
                'damage', 'health', 'shield', 'bow', 'arrow', 'axe', 'trident', 'hunt'
            ]
            if any(w in message_lower for w in combat_keywords):
                if 'combat' in interests or 'protection' in quest_types:
                    score += 5

            # Building/Architecture keywords -> Eldrin
            building_keywords = [
                'build', 'structure', 'block', 'construct', 'house', 'castle', 'tower',
                'wall', 'roof', 'floor', 'foundation', 'design', 'architecture', 'blueprint',
                'medieval', 'modern', 'rustic', 'mansion', 'fort', 'fortress', 'bridge',
                'temple', 'monument', 'statue', 'garden', 'landscape', 'terraforming',
                'symmetry', 'layout', 'interior', 'exterior', 'decoration', 'renovation'
            ]
            if any(w in message_lower for w in building_keywords):
                if 'ancient architecture' in interests or 'building' in quest_types:
                    score += 5

            # Art/Creative keywords -> Lyra
            art_keywords = [
                'art', 'beauty', 'star', 'color', 'aesthetic', 'palette', 'theme', 'style',
                'creative', 'inspiration', 'vision', 'mood', 'atmosphere', 'vibe', 'feeling',
                'beautiful', 'pretty', 'gorgeous', 'stunning', 'elegant', 'cozy', 'warm',
                'dramatic', 'mystical', 'enchanting', 'magical', 'lighting', 'ambiance',
                'texture', 'pattern', 'gradient', 'contrast', 'harmony', 'composition'
            ]
            if any(w in message_lower for w in art_keywords):
                if 'aesthetics' in interests or 'artistic' in quest_types:
                    score += 5

            # Crafting/Technical keywords -> Thane
            technical_keywords = [
                'craft', 'resource', 'redstone', 'efficiency', 'farm', 'automate', 'machine',
                'mechanism', 'contraption', 'circuit', 'piston', 'hopper', 'dispenser',
                'observer', 'comparator', 'repeater', 'storage', 'sorting', 'item',
                'xp', 'grind', 'optimize', 'efficient', 'productivity', 'yield', 'output',
                'input', 'system', 'design', 'technical', 'engineering', 'calculation'
            ]
            if any(w in message_lower for w in technical_keywords):
                if 'crafting' in interests or 'optimization' in quest_types:
                    score += 5

            # Exploration/Lore keywords -> Sage
            exploration_keywords = [
                'explore', 'discover', 'find', 'search', 'adventure', 'journey', 'travel',
                'biome', 'cave', 'dungeon', 'stronghold', 'end', 'nether', 'portal',
                'treasure', 'loot', 'chest', 'secret', 'hidden', 'mystery', 'lore',
                'history', 'ancient', 'ruins', 'artifact', 'relic', 'legend', 'story',
                'map', 'compass', 'coordinate', 'location', 'spawn', 'village', 'temple'
            ]
            if any(w in message_lower for w in exploration_keywords):
                if 'exploration' in interests or 'lore' in quest_types or 'nature' in interests:
                    score += 5

            scores[npc_id] = score

        if scores:
            return max(scores.items(), key=lambda x: x[1])[0]
        return None

    def _generate_party_response(
        self,
        npc: Dict,
        player_name: str,
        message: str,
        other_members: List[str],
        party: Dict
    ) -> str:
        """Generate NPC response in party context"""

        # Get recent chat history
        recent_chat = ""
        if party['chat_history']:
            recent = party['chat_history'][-5:]
            recent_chat = "\n".join([
                f"{c['player']}: {c['message']}\n{c['responder']}: {c['response']}"
                for c in recent
            ])

        prompt = f"""You are {npc['name']}, a {npc['personality']} character in a party.

Your dialogue style: {npc['dialogue_style']}

Party members: {', '.join(other_members) if other_members else 'Just you'}

Recent party chat:
{recent_chat if recent_chat else "First message"}

{player_name} says: "{message}"

Generate a helpful in-character response. You may:
- Reference other party members if relevant
- Suggest they ask another member if it's more their expertise
- Stay true to your personality and knowledge

Keep response concise (2-3 sentences max).
Just provide the dialogue, no extra formatting."""

        try:
            response = requests.post(
                f"{self.ollama_url}/api/generate",
                json={
                    "model": "llama3.2:latest",
                    "prompt": prompt,
                    "stream": False
                },
                timeout=30
            )
            response.raise_for_status()
            return response.json()['response'].strip()
        except Exception as e:
            print(f"[Party] Error generating response: {e}", file=sys.stderr)
            return f"*{npc['name']} considers your words thoughtfully*"

    def get_party_status(self, player_name: str) -> Dict:
        """Get current party status"""
        if player_name not in self.parties:
            return {
                "has_party": False,
                "message": "No active party"
            }

        party = self.parties[player_name]

        members = []
        for npc_id in party['members']:
            npc = self.npc_service.npcs.get(npc_id, {})
            members.append({
                "id": npc_id,
                "name": npc.get('name', npc_id),
                "personality": npc.get('personality', ''),
                "interests": npc.get('interests', [])
            })

        return {
            "has_party": True,
            "name": party['name'],
            "leader": party['leader'],
            "members": members,
            "member_count": len(members),
            "shared_quests": party['shared_quests'],
            "created": party['created'],
            "recent_chat": len(party['chat_history'])
        }

    def party_discuss(self, player_name: str, topic: str) -> Dict:
        """
        Have all party members discuss a topic - multi-agent response
        Each NPC gives their perspective based on their expertise
        """
        if player_name not in self.parties:
            return {"error": "No active party"}

        party = self.parties[player_name]

        if not party['members']:
            return {"error": "No members in party"}

        responses = []

        for npc_id in party['members']:
            npc = self.npc_service.npcs.get(npc_id)
            if not npc:
                continue

            # Get other members for context
            others = [
                self.npc_service.npcs.get(other_id, {}).get('name', other_id)
                for other_id in party['members']
                if other_id != npc_id
            ]

            prompt = f"""You are {npc['name']}, a {npc['personality']} character.
Your interests: {', '.join(npc.get('interests', []))}
Your dialogue style: {npc['dialogue_style']}

The party leader {player_name} wants to discuss: "{topic}"

Other party members who will also give their perspective: {', '.join(others)}

Give your unique perspective on this topic based on your expertise and personality.
Keep it brief (1-2 sentences) and distinct from what others might say.
Just provide your dialogue response."""

            try:
                response = requests.post(
                    f"{self.ollama_url}/api/generate",
                    json={
                        "model": "llama3.2:latest",
                        "prompt": prompt,
                        "stream": False
                    },
                    timeout=20
                )
                response.raise_for_status()
                npc_response = response.json()['response'].strip()
            except Exception as e:
                print(f"[Party] Error getting {npc['name']}'s response: {e}", file=sys.stderr)
                npc_response = f"*{npc['name']} thinks quietly*"

            responses.append({
                "npc_id": npc_id,
                "npc_name": npc['name'],
                "response": npc_response
            })

        return {
            "success": True,
            "topic": topic,
            "responses": responses,
            "participant_count": len(responses)
        }


def main():
    """CLI for party service"""
    if len(sys.argv) < 2:
        print(json.dumps({
            "error": "Usage: party_service.py <command> [args...]",
            "commands": ["create", "invite", "leave", "chat", "status", "discuss"]
        }))
        sys.exit(1)

    command = sys.argv[1]
    service = PartyService()

    if command == "create":
        if len(sys.argv) < 3:
            result = {"error": "Usage: create <player> [party_name]"}
        else:
            player = sys.argv[2]
            party_name = sys.argv[3] if len(sys.argv) > 3 else None
            result = service.create_party(player, party_name)

    elif command == "invite":
        if len(sys.argv) < 4:
            result = {"error": "Usage: invite <player> <npc_id>"}
        else:
            player = sys.argv[2]
            npc_id = sys.argv[3]
            result = service.invite_npc(player, npc_id)

    elif command == "leave":
        if len(sys.argv) < 3:
            result = {"error": "Usage: leave <player> [npc_id]"}
        else:
            player = sys.argv[2]
            npc_id = sys.argv[3] if len(sys.argv) > 3 else None
            result = service.leave_party(player, npc_id)

    elif command == "chat":
        if len(sys.argv) < 4:
            result = {"error": "Usage: chat <player> <message>"}
        else:
            player = sys.argv[2]
            message = " ".join(sys.argv[3:])
            result = service.party_chat(player, message)

    elif command == "status":
        if len(sys.argv) < 3:
            result = {"error": "Usage: status <player>"}
        else:
            player = sys.argv[2]
            result = service.get_party_status(player)

    elif command == "discuss":
        if len(sys.argv) < 4:
            result = {"error": "Usage: discuss <player> <topic>"}
        else:
            player = sys.argv[2]
            topic = " ".join(sys.argv[3:])
            result = service.party_discuss(player, topic)

    else:
        result = {"error": f"Unknown command: {command}"}

    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
