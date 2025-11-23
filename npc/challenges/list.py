#!/usr/bin/env python3
"""
List all available build challenges
"""

import sys
import json
from npc.scripts.service import NPCService


def main():
    npc_filter = sys.argv[1] if len(sys.argv) > 1 else None

    service = NPCService()

    # Load all challenges
    all_challenges = service.load_build_challenges()

    if npc_filter:
        # Filter by NPC
        challenges = [
            c for c in all_challenges
            if npc_filter in c.get('giver_affinity', [])
        ]
    else:
        challenges = all_challenges

    # Format output with summary
    output = {
        "total": len(challenges),
        "challenges": []
    }

    for challenge in challenges:
        summary = {
            "id": challenge['id'],
            "title": challenge['title'],
            "description": challenge['description'],
            "difficulty": challenge.get('difficulty', 'medium'),
            "givers": challenge.get('giver_affinity', []),
            "requirements_summary": {
                "min_blocks": challenge['requirements'].get('minBlocks', 0),
                "min_height": challenge['requirements'].get('minHeight', 0),
                "required_block_types": len(challenge['requirements'].get('requiredBlockTypes', {}))
            },
            "reward": {
                "type": challenge['reward'].get('type', 'lore'),
                "xp": challenge['reward'].get('xp', 0),
                "items": challenge['reward'].get('items', [])
            }
        }
        output["challenges"].append(summary)

    print(json.dumps(output, indent=2))


if __name__ == '__main__':
    main()
