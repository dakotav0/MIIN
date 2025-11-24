#!/usr/bin/env python3
"""
Check quest progress for a player based on their Minecraft events.
Called by the MCP server.
"""

import json
from pathlib import Path

import sys, os
BASE = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
sys.path.insert(0, BASE)

from npc.scripts.service import NPCService

def main():
    if len(sys.argv) < 2:
        print(json.dumps({"error": "Usage: npc_check_progress.py <player_name>"}))
        sys.exit(1)

    player_name = sys.argv[1]

    service = NPCService()
    result = service.check_quest_progress(player_name)

    print(json.dumps(result))


if __name__ == '__main__':
    main()
