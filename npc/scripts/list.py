#!/usr/bin/env python3
"""Simple script to list NPCs as JSON"""
import sys
import json
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent))
from npc.scripts.service import NPCService

service = NPCService()
npcs = [
    {
        'id': npc_id,
        'name': npc['name'],
        'personality': npc['personality'],
        'location': npc['location'],
        'interests': npc['interests']
    }
    for npc_id, npc in service.npcs.items()
]
print(json.dumps(npcs))
