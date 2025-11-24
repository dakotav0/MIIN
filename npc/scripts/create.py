#!/usr/bin/env python3
"""
NPC Creation Script
Usage: python npc_create.py <template_id> <x> <y> <z> <dimension> <biome> [name]
"""

import sys
import json
import argparse
from pathlib import Path

# Ensure project root is on path
ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from npc.scripts.service import NPCService

def main():
    parser = argparse.ArgumentParser(description='Create a dynamic NPC')
    parser.add_argument('template_id', help='NPC template ID')
    parser.add_argument('x', type=float, help='X coordinate')
    parser.add_argument('y', type=float, help='Y coordinate')
    parser.add_argument('z', type=float, help='Z coordinate')
    parser.add_argument('dimension', help='Dimension')
    parser.add_argument('biome', help='Biome')
    parser.add_argument('--name', help='Optional specific name', default=None)
    
    args = parser.parse_args()
    
    try:
        service = NPCService()
        
        location = {
            "x": args.x,
            "y": args.y,
            "z": args.z,
            "dimension": args.dimension,
            "biome": args.biome
        }
        
        npc = service.create_npc(
            template_id=args.template_id,
            location=location,
            name=args.name
        )
        
        if npc:
            print(json.dumps(npc))
        else:
            print(json.dumps({"error": "Failed to create NPC"}))
            sys.exit(1)
            
    except Exception as e:
        print(json.dumps({"error": str(e)}))
        sys.exit(1)

if __name__ == "__main__":
    main()
