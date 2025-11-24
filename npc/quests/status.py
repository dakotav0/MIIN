#!/usr/bin/env python3
"""Simple script to get player quest status"""
import json
from pathlib import Path

import sys, os
from pathlib import Path

# Ensure MIIN root is on path so npc/dialogue imports resolve
ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

# Get arguments
if len(sys.argv) < 2:
    print(json.dumps({"error": "Usage: npc_quest_status.py <player_name>"}))
    sys.exit(1)

player_name = sys.argv[1]

from npc.scripts.service import NPCService

if player_name in ("undefined", "", None):
    print(json.dumps({"error": "Invalid player name", "player": player_name}))
    sys.exit(1)

service = NPCService()
quests = service.get_player_quests(player_name)
print(json.dumps(quests))
