#!/usr/bin/env python3
"""
Validate a build against a build challenge quest
"""

import sys
import json
from npc.scripts.service import NPCService


def main():
    if len(sys.argv) < 4:
        print(json.dumps({"error": "Usage: npc_build_challenge_validate.py <player_name> <quest_id> <build_data_json>"}))
        sys.exit(1)

    player_name = sys.argv[1]
    quest_id = sys.argv[2]
    build_data_str = sys.argv[3]

    try:
        build_data = json.loads(build_data_str)
    except json.JSONDecodeError as e:
        print(json.dumps({"error": f"Invalid build_data JSON: {e}"}))
        sys.exit(1)

    service = NPCService()

    # Validate build challenge
    result = service.validate_build_challenge(
        player_name=player_name,
        quest_id=quest_id,
        build_data=build_data
    )

    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
