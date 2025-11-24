#!/usr/bin/env python3
"""Simple script to generate NPC quest"""
import json
from pathlib import Path

import sys, os
from pathlib import Path

# Ensure MIIN root is on path so npc/dialogue imports resolve
ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

# Get arguments
if len(sys.argv) < 3:
    print(json.dumps({"error": "Usage: npc_quest.py <npc_id> <player_name>"}))
    sys.exit(1)

npc_id = sys.argv[1]
player_name = sys.argv[2]

from npc.scripts.service import NPCService

if npc_id in ("undefined", "", None) or player_name in ("undefined", "", None):
    print(json.dumps({"error": "Invalid arguments", "npc_id": npc_id, "player": player_name}))
    sys.exit(1)

service = NPCService()
quest = service.generate_quest(
    npc_id=npc_id,
    player_name=player_name
)

if quest:
    print(json.dumps(quest))
else:
    print(json.dumps({"error": "No quest available"}))
