#!/usr/bin/env python3
"""
Lore Service - Manages F-EIGHT canon lore for Minecraft integration

Handles:
- Lore book generation
- Discovered lore tracking per player
- RAG integration for NPC knowledge
"""

import json
import sys
from pathlib import Path
from typing import Dict, List, Any
from datetime import datetime

# F-EIGHT Canon Lore
LORE_LIBRARY = {
    "ancient_builders": {
        "category": "Ancient History",
        "books": [
            {
                "id": "builders_origin",
                "title": "The First Builders",
                "author": "Unknown Scholar",
                "pages": [
                    "In the time before time, when the world was still soft and malleable, the First Builders emerged from the void between stars.",
                    "They did not build with hands as we do, but with intention. Every block they placed carried meaning, every structure told a story that would outlast the ages.",
                    "The ruins we find scattered across the land are but shadows of what once was. The true monuments stand in dimensions we cannot yet perceive.",
                    "Remember: to build is to speak in the language of eternity. The ancients knew this. Do you?"
                ]
            },
            {
                "id": "builders_fall",
                "title": "The Fall of the Builders",
                "author": "Eldrin the Wanderer",
                "pages": [
                    "They grew too bold. They built too high. They pierced the veil between worlds and something... looked back.",
                    "The darkness that came was not evil - it was merely hungry. It consumed their greatest works and scattered their knowledge to the corners of existence.",
                    "Some say the End is where they made their final stand. Others say they became something else entirely. The truth, as always, lies somewhere between the blocks.",
                    "We who build in their footsteps must remember: ambition without wisdom invites the void."
                ]
            }
        ]
    },
    "dimensional_secrets": {
        "category": "Dimensional Theory",
        "books": [
            {
                "id": "nether_truth",
                "title": "The Nether Revelation",
                "author": "Lyra Starweaver",
                "pages": [
                    "The Nether is not hell. It is a mirror.",
                    "Every block of netherrack holds the memory of what once grew there. The soul sand remembers those who walked above it. The lava flows with the passion of forgotten builders.",
                    "When you enter the Nether, you enter the dreams of the world. Build carefully there, for what you create becomes part of the collective unconscious.",
                    "The piglins know this. They guard their bastions not from greed, but from sacred duty. Ask them sometime. They might surprise you."
                ]
            },
            {
                "id": "end_beginning",
                "title": "The End is the Beginning",
                "author": "Ancient Endermite",
                "pages": [
                    "What you call the End, we call the Canvas.",
                    "The dragon does not guard the End from you. It guards you from the End. The void between the islands is not empty - it is full of possibilities waiting to be claimed.",
                    "The Endermen were once builders like you. They chose to become something more. They chose to exist between moments, between places, between thoughts.",
                    "When you defeat the dragon, you do not win. You accept responsibility. The End becomes yours to shape. Build wisely."
                ]
            }
        ]
    },
    "constellation_lore": {
        "category": "Celestial Knowledge",
        "books": [
            {
                "id": "builder_constellation",
                "title": "The Constellation of the Builder",
                "author": "Lyra Starweaver",
                "pages": [
                    "When the Ninth Ember blazes in the northern sky, the Builder awakens.",
                    "Each star in the constellation represents a principle: Foundation. Structure. Purpose. Beauty. Legacy. The wise builder honors all five.",
                    "Build at night when the Builder is visible, and your structures will carry a spark of the eternal. The stars do not judge what you create - they celebrate it.",
                    "The ancients built observatories to track the Builder's movement. Some say these structures still stand, waiting for those who know where to look."
                ]
            },
            {
                "id": "nine_flames",
                "title": "The Nine Flames",
                "author": "F-EIGHT Archives",
                "pages": [
                    "In the beginning there were Nine Flames. Each represented a fundamental truth of existence.",
                    "The First Flame was Creation - the spark that ignites all things. The Ninth Flame was Mystery - the ember that never reveals itself fully.",
                    "Eight flames were extinguished during the Fall. Only the Ninth remained, hidden among the stars, waiting for builders worthy of rekindling the others.",
                    "Some say each flame can only be relit through an act of pure creation. A tower that touches the sky. A garden that heals the land. A bridge that connects enemies."
                ]
            }
        ]
    },
    "combat_wisdom": {
        "category": "Warrior's Path",
        "books": [
            {
                "id": "monster_truth",
                "title": "Understanding the Darkness",
                "author": "Kira Shadowhunter",
                "pages": [
                    "Every monster you face was something else once.",
                    "The zombies remember being alive. The skeletons remember having flesh. The creepers... the creepers remember joy, which is why they explode when they find it again.",
                    "Do not hate them. Understand them. A hunter who hates their prey becomes no better than what they hunt.",
                    "Light is your greatest weapon not because it destroys - but because it reminds. In the light, even the darkest creatures recall what they once were."
                ]
            },
            {
                "id": "defense_philosophy",
                "title": "The Art of the Wall",
                "author": "Thane Ironforge",
                "pages": [
                    "A wall is not a barrier. It is a statement.",
                    "When you build a wall, you declare: this far and no further. This is mine to protect. Every block you place says 'I am here and I will not be moved.'",
                    "The best walls are never tested. Their presence alone is enough. Build strong. Build visible. Build with purpose.",
                    "But remember: the greatest walls have gates. Isolation is not protection - it is slow defeat. Build walls that defend, not walls that imprison."
                ]
            }
        ]
    },
    "crafting_secrets": {
        "category": "Material Arts",
        "books": [
            {
                "id": "block_essence",
                "title": "The Essence of Materials",
                "author": "Thane Ironforge",
                "pages": [
                    "Every block has a story. Iron ore remembers the mountain that held it. Oak planks remember the forest where they grew.",
                    "When you craft, you are not just combining materials. You are weaving stories together. A tool made with respect serves better than one made with haste.",
                    "This is why some builds feel alive and others feel dead. The blocks know if you placed them with intention or with indifference.",
                    "Take time to know your materials. Where did they come from? What did they witness? Build with their stories, not against them."
                ]
            }
        ]
    }
}


class LoreService:
    """
    Lore Service - Manage F-EIGHT lore discovery and integration
    """

    def __init__(
        self,
        discovered_path: str = None,
        rag_corpus_path: str = None
    ):
        """Initialize lore service"""
        self.root = Path(__file__).parent.parent  # Go up to MIIN root
        self.discovered_path = discovered_path or str(self.root / 'lore' / 'discovered.json')
        self.rag_corpus_path = rag_corpus_path or str(self.root.parent / 'documents' / 'minecraft_lore')

        # Load discovered lore per player
        self.discovered = self.load_discovered()

        print(f"[Lore] Service initialized with {self._count_total_books()} books", file=sys.stderr)

    def _count_total_books(self) -> int:
        """Count total books in library"""
        return sum(len(cat['books']) for cat in LORE_LIBRARY.values())

    def load_discovered(self) -> Dict:
        """Load discovered lore tracking"""
        try:
            with open(self.discovered_path, 'r') as f:
                return json.load(f)
        except FileNotFoundError:
            return {}

    def save_discovered(self):
        """Save discovered lore"""
        with open(self.discovered_path, 'w') as f:
            json.dump(self.discovered, f, indent=2)

    def get_book(self, lore_id: str) -> Dict:
        """Get a specific lore book by ID"""
        for category, data in LORE_LIBRARY.items():
            for book in data['books']:
                if book['id'] == lore_id:
                    return {
                        **book,
                        'category_id': category,
                        'category_name': data['category']
                    }
        return None

    def get_random_book(self, category: str = None) -> Dict:
        """Get a random undiscovered book"""
        import random

        if category and category in LORE_LIBRARY:
            books = LORE_LIBRARY[category]['books']
            book = random.choice(books)
            return {
                **book,
                'category_id': category,
                'category_name': LORE_LIBRARY[category]['category']
            }
        else:
            # Random from any category
            all_books = []
            for cat_id, data in LORE_LIBRARY.items():
                for book in data['books']:
                    all_books.append({
                        **book,
                        'category_id': cat_id,
                        'category_name': data['category']
                    })
            return random.choice(all_books)

    def mark_discovered(self, player_name: str, lore_id: str, content: str = None):
        """Mark a lore book as discovered by a player"""
        if player_name not in self.discovered:
            self.discovered[player_name] = {
                'books': [],
                'categories': {}
            }

        player_data = self.discovered[player_name]

        if lore_id not in player_data['books']:
            player_data['books'].append(lore_id)

            # Get book info
            book = self.get_book(lore_id)
            if book:
                cat = book['category_id']
                if cat not in player_data['categories']:
                    player_data['categories'][cat] = []
                player_data['categories'][cat].append(lore_id)

            # Add to RAG corpus if content provided
            if content:
                self._add_to_rag(lore_id, content, book)

            self.save_discovered()

            return {
                "success": True,
                "player": player_name,
                "lore_id": lore_id,
                "total_discovered": len(player_data['books']),
                "total_available": self._count_total_books()
            }

        return {
            "success": False,
            "reason": "Already discovered",
            "player": player_name,
            "lore_id": lore_id
        }

    def _add_to_rag(self, lore_id: str, content: str, book: Dict):
        """Add lore to RAG corpus"""
        try:
            # Create lore documents directory if needed
            lore_dir = Path(self.rag_corpus_path)
            lore_dir.mkdir(parents=True, exist_ok=True)

            # Save as markdown file
            filename = f"{lore_id}.md"
            filepath = lore_dir / filename

            md_content = f"""# {book['title']}

**Author:** {book['author']}
**Category:** {book['category_name']}

---

{content}

---
*Lore ID: {lore_id}*
"""

            with open(filepath, 'w') as f:
                f.write(md_content)

            print(f"[Lore] Added '{book['title']}' to RAG corpus", file=sys.stderr)

        except Exception as e:
            print(f"[Lore] Error adding to RAG: {e}", file=sys.stderr)

    def get_player_progress(self, player_name: str) -> Dict:
        """Get player's lore discovery progress"""
        if player_name not in self.discovered:
            return {
                "player": player_name,
                "discovered": 0,
                "total": self._count_total_books(),
                "categories": {},
                "completion": 0.0
            }

        player_data = self.discovered[player_name]
        discovered = len(player_data['books'])
        total = self._count_total_books()

        # Calculate per-category progress
        categories = {}
        for cat_id, data in LORE_LIBRARY.items():
            total_in_cat = len(data['books'])
            discovered_in_cat = len(player_data['categories'].get(cat_id, []))
            categories[data['category']] = {
                'discovered': discovered_in_cat,
                'total': total_in_cat,
                'completion': discovered_in_cat / total_in_cat if total_in_cat > 0 else 0
            }

        return {
            "player": player_name,
            "discovered": discovered,
            "total": total,
            "completion": discovered / total if total > 0 else 0,
            "categories": categories,
            "recent": player_data['books'][-5:] if player_data['books'] else []
        }

    def get_all_lore_for_npc(self, player_name: str) -> List[Dict]:
        """Get all lore discovered by player for NPC context"""
        if player_name not in self.discovered:
            return []

        lore_list = []
        for lore_id in self.discovered[player_name]['books']:
            book = self.get_book(lore_id)
            if book:
                lore_list.append({
                    'id': lore_id,
                    'title': book['title'],
                    'category': book['category_name'],
                    'summary': book['pages'][0][:100] + '...' if book['pages'] else ''
                })

        return lore_list


def main():
    """CLI for lore service"""
    if len(sys.argv) < 2:
        print(json.dumps({
            "error": "Usage: lore_service.py <command> [args...]",
            "commands": ["get_book", "random", "discover", "progress", "list"]
        }))
        sys.exit(1)

    command = sys.argv[1]
    service = LoreService()

    if command == "get_book":
        if len(sys.argv) < 3:
            result = {"error": "Usage: get_book <lore_id>"}
        else:
            lore_id = sys.argv[2]
            book = service.get_book(lore_id)
            result = book if book else {"error": f"Book '{lore_id}' not found"}

    elif command == "random":
        category = sys.argv[2] if len(sys.argv) > 2 else None
        result = service.get_random_book(category)

    elif command == "discover":
        if len(sys.argv) < 4:
            result = {"error": "Usage: discover <player> <lore_id> [content]"}
        else:
            player = sys.argv[2]
            lore_id = sys.argv[3]
            content = sys.argv[4] if len(sys.argv) > 4 else None
            result = service.mark_discovered(player, lore_id, content)

    elif command == "progress":
        if len(sys.argv) < 3:
            result = {"error": "Usage: progress <player>"}
        else:
            player = sys.argv[2]
            result = service.get_player_progress(player)

    elif command == "list":
        # List all available lore
        result = {
            "categories": {}
        }
        for cat_id, data in LORE_LIBRARY.items():
            result["categories"][data['category']] = [
                {"id": book['id'], "title": book['title'], "author": book['author']}
                for book in data['books']
            ]

    else:
        result = {"error": f"Unknown command: {command}"}

    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
