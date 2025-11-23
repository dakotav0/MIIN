#!/usr/bin/env python3
"""Simple script to generate NPC dialogue"""
import sys
import json
from pathlib import Path

# Add project root to path (go up from npc/scripts/ to root)
project_root = Path(__file__).parent.parent.parent
sys.path.insert(0, str(project_root))

# Get arguments
if len(sys.argv) < 4:
    print(json.dumps({"error": "Usage: npc_talk.py <npc_id> <player_name> <message> [--suggestions]"}))
    sys.exit(1)

npc_id = sys.argv[1]
player_name = sys.argv[2]
message = sys.argv[3]
request_suggestions = "--suggestions" in sys.argv

print(f"[npc_talk] Received npc_id: {npc_id}, player: {player_name}, message: {message}", file=sys.stderr)

from npc.scripts.service import NPCService
from dialogue.service import DialogueService

# 1. Get NPC response
service = NPCService()
response = service.generate_npc_response(
    npc_id=npc_id,
    player_name=player_name,
    player_message=message
)

result = {
    "npc": npc_id,
    "player": player_name,
    "response": response
}

# 2. Get suggestions if requested
if request_suggestions:
    try:
        dialogue_service = DialogueService()
        # Generate options based on the conversation context
        # Use "greeting" as valid context type (conversation_turn not recognized)
        options_result = dialogue_service.generate_dialogue_options(npc_id, player_name, "greeting")

        # Check if we got valid options
        if "options" in options_result and len(options_result["options"]) > 0:
            # Extract just the text of the options for the suggestions list
            suggestions = [opt["text"] for opt in options_result["options"]]
            result["suggestions"] = suggestions

            # Also include the full options objects if needed for the frontend to build DialogueState
            result["options"] = options_result["options"]
        else:
            # No options returned - provide fallback
            print(f"[npc_talk] Warning: No options generated, using fallback", file=sys.stderr)
            result["suggestions"] = [
                "Tell me more about that.",
                "What should I do next?",
                "I should go."
            ]
            result["options"] = [
                {"id": 1, "text": "Tell me more about that.", "tone": "curious", "relationship_delta": 0, "leads_to": "response"},
                {"id": 2, "text": "What should I do next?", "tone": "neutral", "relationship_delta": 0, "leads_to": "response"},
                {"id": 3, "text": "I should go.", "tone": "neutral", "relationship_delta": 0, "leads_to": "farewell"}
            ]
    except Exception as e:
        # Error generating suggestions - provide fallback and continue
        print(f"[npc_talk] Error generating suggestions: {e}", file=sys.stderr)
        result["suggestions"] = ["Continue...", "Goodbye."]
        result["options"] = [
            {"id": 1, "text": "Continue...", "tone": "neutral", "relationship_delta": 0, "leads_to": "response"},
            {"id": 2, "text": "Goodbye.", "tone": "neutral", "relationship_delta": 0, "leads_to": "farewell"}
        ]

print(json.dumps(result))
