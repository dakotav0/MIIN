#!/usr/bin/env python3
"""Simple script to generate NPC quest"""
import sys
import json
from pathlib import Path

# Add project root to path (go up from npc/quests/ to root)
project_root = Path(__file__).parent.parent.parent
sys.path.insert(0, str(project_root))

# Get arguments
if len(sys.argv) < 3:
    print(json.dumps({"error": "Usage: npc_quest.py <npc_id> <player_name>"}))
    sys.exit(1)

npc_id = sys.argv[1]
player_name = sys.argv[2]

from npc.scripts.service import NPCService

service = NPCService()
quest = service.generate_quest(
    npc_id=npc_id,
    player_name=player_name
)

if quest:
    print(json.dumps(quest))
else:
    print(json.dumps({"error": "No quest available"}))
