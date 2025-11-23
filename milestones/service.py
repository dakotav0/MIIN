#!/usr/bin/env python3
"""
Milestone Service - Tracks player progress and announces achievements

Monitors player events and detects when milestones are reached,
providing narrative celebrations for building achievements.
"""

import json
import sys
from pathlib import Path
from typing import Dict, List, Any
from datetime import datetime, timezone

# Milestone definitions
MILESTONES = {
    "blocks_placed": {
        "name": "Builder",
        "thresholds": [
            (100, "Novice Builder", "You've placed your first 100 blocks!"),
            (500, "Apprentice Builder", "500 blocks placed - your builds are taking shape!"),
            (1000, "Journeyman Builder", "1,000 blocks! You're becoming a skilled builder."),
            (5000, "Expert Builder", "5,000 blocks placed - your creations are impressive!"),
            (10000, "Master Builder", "10,000 blocks! A true master of construction."),
            (50000, "Legendary Builder", "50,000 blocks - your legacy will stand for ages!"),
        ]
    },
    "builds_completed": {
        "name": "Architect",
        "thresholds": [
            (1, "First Build", "You've completed your first build!"),
            (5, "Budding Architect", "5 builds completed - you're on a roll!"),
            (10, "Established Architect", "10 builds - your portfolio grows!"),
            (25, "Renowned Architect", "25 builds! Your name is known."),
            (50, "Legendary Architect", "50 builds - a true visionary!"),
        ]
    },
    "mobs_killed": {
        "name": "Hunter",
        "thresholds": [
            (10, "Novice Hunter", "10 mobs defeated - the hunt begins!"),
            (50, "Skilled Hunter", "50 mobs down - you're getting dangerous!"),
            (100, "Expert Hunter", "100 mobs! The creatures fear you."),
            (500, "Master Hunter", "500 mobs defeated - a true champion!"),
            (1000, "Legendary Hunter", "1,000 mobs! You are death incarnate."),
        ]
    },
    "biomes_visited": {
        "name": "Explorer",
        "thresholds": [
            (3, "Curious Wanderer", "You've explored 3 different biomes!"),
            (5, "Adventurer", "5 biomes discovered - the world opens up!"),
            (10, "Seasoned Explorer", "10 biomes! You know these lands well."),
            (15, "World Traveler", "15 biomes explored - few places remain unknown!"),
            (20, "Legendary Explorer", "20 biomes! You've seen it all."),
        ]
    },
    "time_played": {
        "name": "Veteran",
        "thresholds": [
            (3600, "First Hour", "You've spent an hour in this world!"),
            (18000, "Dedicated Player", "5 hours played - you're hooked!"),
            (36000, "Committed Builder", "10 hours! This world is your home."),
            (180000, "Veteran", "50 hours played - a true veteran!"),
            (360000, "Legend", "100 hours! Your dedication is legendary."),
        ]
    },
    "unique_blocks_used": {
        "name": "Collector",
        "thresholds": [
            (10, "Block Curious", "You've used 10 different block types!"),
            (25, "Material Explorer", "25 block types - variety is key!"),
            (50, "Block Connoisseur", "50 types! You know your materials."),
            (100, "Master Collector", "100 block types used - impressive variety!"),
        ]
    }
}


class MilestoneService:
    """
    Milestone Service - Track achievements and progress
    """

    def __init__(
        self,
        milestones_path: str = None,
        events_path: str = None
    ):
        """Initialize milestone service"""
        self.root = Path(__file__).parent
        self.milestones_path = milestones_path or str(self.root / 'player_milestones.json')
        self.events_path = events_path or str(self.root / 'events' / 'minecraft_events.json')

        # Load existing milestones
        self.player_milestones = self.load_milestones()

        print(f"[Milestones] Service initialized", file=sys.stderr)

    def load_milestones(self) -> Dict:
        """Load player milestone progress"""
        try:
            with open(self.milestones_path, 'r') as f:
                return json.load(f)
        except FileNotFoundError:
            return {}

    def save_milestones(self):
        """Save player milestones"""
        with open(self.milestones_path, 'w') as f:
            json.dump(self.player_milestones, f, indent=2)

    def get_player_stats(self, player_name: str) -> Dict:
        """
        Calculate current stats for a player from events
        """
        try:
            with open(self.events_path, 'r') as f:
                events = json.load(f)
        except (FileNotFoundError, json.JSONDecodeError):
            return {}

        # Filter events for this player
        player_events = [
            e for e in events
            if e.get('data', {}).get('playerName') == player_name
        ]

        # Calculate stats
        stats = {
            "blocks_placed": 0,
            "builds_completed": 0,
            "mobs_killed": 0,
            "biomes_visited": set(),
            "time_played": 0,
            "unique_blocks_used": set(),
        }

        session_starts = {}

        for event in player_events:
            event_type = event.get('eventType')
            data = event.get('data', {})

            if event_type == 'build_complete':
                stats['builds_completed'] += 1
                block_counts = data.get('blockCounts', {})
                stats['blocks_placed'] += sum(block_counts.values())
                stats['unique_blocks_used'].update(block_counts.keys())

            elif event_type == 'mob_killed':
                stats['mobs_killed'] += 1

            elif event_type == 'player_state':
                biome = data.get('biome')
                if biome:
                    stats['biomes_visited'].add(biome)

            elif event_type == 'session_start':
                session_starts[data.get('playerId')] = datetime.fromisoformat(
                    event['timestamp'].replace('Z', '+00:00')
                )

            elif event_type == 'session_end':
                player_id = data.get('playerId')
                if player_id in session_starts:
                    end_time = datetime.fromisoformat(
                        event['timestamp'].replace('Z', '+00:00')
                    )
                    duration = (end_time - session_starts[player_id]).total_seconds()
                    stats['time_played'] += duration
                    del session_starts[player_id]

        # Convert sets to counts
        stats['biomes_visited'] = len(stats['biomes_visited'])
        stats['unique_blocks_used'] = len(stats['unique_blocks_used'])

        return stats

    def check_milestones(self, player_name: str) -> Dict:
        """
        Check for new milestones reached by a player

        Returns:
            Dict with new milestones and current progress
        """
        stats = self.get_player_stats(player_name)

        if not stats:
            return {"player": player_name, "error": "No stats found"}

        # Initialize player milestone tracking if needed
        if player_name not in self.player_milestones:
            self.player_milestones[player_name] = {
                "achieved": {},
                "last_checked": None
            }

        player_data = self.player_milestones[player_name]
        new_milestones = []
        progress = {}

        # Check each milestone category
        for category, milestone_def in MILESTONES.items():
            current_value = stats.get(category, 0)

            # Track progress
            thresholds = milestone_def['thresholds']
            next_threshold = None
            current_level = None

            for threshold, title, message in thresholds:
                milestone_id = f"{category}_{threshold}"

                if current_value >= threshold:
                    current_level = title

                    # Check if this is a new milestone
                    if milestone_id not in player_data['achieved']:
                        player_data['achieved'][milestone_id] = {
                            "achieved_at": datetime.now().isoformat(),
                            "value": current_value
                        }
                        new_milestones.append({
                            "category": category,
                            "title": title,
                            "message": message,
                            "threshold": threshold,
                            "current_value": current_value
                        })
                else:
                    if next_threshold is None:
                        next_threshold = {
                            "threshold": threshold,
                            "title": title,
                            "remaining": threshold - current_value
                        }
                    break

            progress[category] = {
                "current_value": current_value,
                "current_level": current_level,
                "next_milestone": next_threshold,
                "category_name": milestone_def['name']
            }

        # Update last checked time
        player_data['last_checked'] = datetime.now().isoformat()
        self.save_milestones()

        return {
            "player": player_name,
            "new_milestones": new_milestones,
            "progress": progress,
            "total_achievements": len(player_data['achieved'])
        }

    def get_all_milestones(self, player_name: str) -> Dict:
        """Get all achieved milestones for a player"""
        if player_name not in self.player_milestones:
            return {"player": player_name, "achievements": [], "count": 0}

        achieved = self.player_milestones[player_name]['achieved']

        achievements = []
        for milestone_id, data in achieved.items():
            # Parse milestone ID
            parts = milestone_id.rsplit('_', 1)
            category = parts[0]
            threshold = int(parts[1])

            # Find the milestone details
            if category in MILESTONES:
                for t, title, message in MILESTONES[category]['thresholds']:
                    if t == threshold:
                        achievements.append({
                            "category": category,
                            "title": title,
                            "message": message,
                            "achieved_at": data['achieved_at'],
                            "value_at_achievement": data['value']
                        })
                        break

        # Sort by achievement date
        achievements.sort(key=lambda x: x['achieved_at'], reverse=True)

        return {
            "player": player_name,
            "achievements": achievements,
            "count": len(achievements)
        }


def main():
    """CLI for milestone service"""
    if len(sys.argv) < 3:
        print(json.dumps({
            "error": "Usage: milestone_service.py <command> <player_name>",
            "commands": ["check", "list"]
        }))
        sys.exit(1)

    command = sys.argv[1]
    player_name = sys.argv[2]

    service = MilestoneService()

    if command == "check":
        result = service.check_milestones(player_name)
    elif command == "list":
        result = service.get_all_milestones(player_name)
    else:
        result = {"error": f"Unknown command: {command}"}

    print(json.dumps(result))


if __name__ == '__main__':
    main()
