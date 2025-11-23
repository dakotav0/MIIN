#!/usr/bin/env python3
"""
Request a themed build challenge from an NPC
"""

import sys
import json
from npc.scripts.service import NPCService


def main():
    if len(sys.argv) < 3:
        print(json.dumps({"error": "Usage: npc_build_challenge_request.py <npc_id> <player_name> [challenge_id]"}))
        sys.exit(1)

    npc_id = sys.argv[1]
    player_name = sys.argv[2]
    challenge_id = sys.argv[3] if len(sys.argv) > 3 else None

    service = NPCService()

    # Generate build challenge quest
    quest = service.generate_build_challenge_quest(
        npc_id=npc_id,
        player_name=player_name,
        challenge_id=challenge_id
    )

    if quest:
        print(json.dumps(quest, indent=2))
    else:
        print(json.dumps({"error": "Failed to generate build challenge"}))


if __name__ == '__main__':
    main()
