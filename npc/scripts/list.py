#!/usr/bin/env python3
"""Simple script to list NPCs as JSON"""
import json
import sys
from pathlib import Path

# Ensure project root is on path (so npc/dialogue modules resolve regardless of cwd)
ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from npc.scripts.service import NPCService

service = NPCService()
npcs = [
    {
        'id': npc_id,
        'name': npc['name'],
        'personality': npc.get('personality', ''),
        'location': npc.get('location', {}),
        'interests': npc.get('interests', [])
    }
    for npc_id, npc in service.npcs.items()
]
print(json.dumps(npcs))
