#!/usr/bin/env python3
"""Simple script to get player quest status"""
import sys
import json
from pathlib import Path

# Add project root to path (go up from npc/quests/ to root)
project_root = Path(__file__).parent.parent.parent
sys.path.insert(0, str(project_root))

# Get arguments
if len(sys.argv) < 2:
    print(json.dumps({"error": "Usage: npc_quest_status.py <player_name>"}))
    sys.exit(1)

player_name = sys.argv[1]

from npc.scripts.service import NPCService

service = NPCService()
quests = service.get_player_quests(player_name)
print(json.dumps(quests))
