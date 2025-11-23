#!/usr/bin/env python3
"""
Quest Accept Script - Accept a quest from an NPC

This script handles quest acceptance, moving quests from offered to active state
and confirming acceptance for the player.
"""

import json
import sys
from pathlib import Path
from datetime import datetime

def main():
    if len(sys.argv) < 4:
        print(json.dumps({
            "error": "Usage: npc_quest_accept.py <npc_id> <player_name> <quest_id>"
        }))
        sys.exit(1)

    npc_id = sys.argv[1]
    player_name = sys.argv[2]
    quest_id = sys.argv[3]

    # Load quests
    quest_path = Path(__file__).parent / 'npc_quests.json'
    try:
        with open(quest_path, 'r') as f:
            quests = json.load(f)
    except FileNotFoundError:
        quests = {"active": [], "completed": [], "offered": []}

    # Ensure all lists exist
    if "offered" not in quests:
        quests["offered"] = []
    if "active" not in quests:
        quests["active"] = []

    result = None

    # First check if quest is in offered state
    offered_quest = None
    for i, quest in enumerate(quests.get("offered", [])):
        if quest.get("id") == quest_id and quest.get("player") == player_name:
            offered_quest = quests["offered"].pop(i)
            break

    if offered_quest:
        # Move from offered to active
        offered_quest["status"] = "active"
        offered_quest["accepted_at"] = datetime.now().isoformat()
        offered_quest["accepted"] = True
        quests["active"].append(offered_quest)

        result = {
            "success": True,
            "action": "accepted",
            "message": f"Quest '{offered_quest.get('title', quest_id)}' accepted!",
            "quest": offered_quest,
            "npc": npc_id,
            "player": player_name
        }
    else:
        # Check if quest is already active
        active_quest = None
        for quest in quests.get("active", []):
            if quest.get("id") == quest_id and quest.get("player") == player_name:
                active_quest = quest
                break

        if active_quest:
            # Mark as explicitly accepted
            active_quest["accepted"] = True
            active_quest["accepted_at"] = active_quest.get("accepted_at", datetime.now().isoformat())

            result = {
                "success": True,
                "action": "confirmed",
                "message": f"Quest '{active_quest.get('title', quest_id)}' is already active.",
                "quest": active_quest,
                "npc": npc_id,
                "player": player_name
            }
        else:
            # Quest not found
            result = {
                "success": False,
                "error": f"Quest '{quest_id}' not found for player '{player_name}'",
                "hint": "Use minecraft_quest_request to get a quest first",
                "available_quests": [q.get("id") for q in quests.get("active", []) if q.get("player") == player_name]
            }

    # Save quests
    try:
        with open(quest_path, 'w') as f:
            json.dump(quests, f, indent=2)
    except Exception as e:
        result["warning"] = f"Failed to save quest state: {e}"

    print(json.dumps(result))

if __name__ == '__main__':
    main()
