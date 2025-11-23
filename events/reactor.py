#!/usr/bin/env python3
"""
Event Reactor - Watches Minecraft events and triggers NPC responses

This service monitors minecraft_events.json and triggers appropriate NPC
reactions based on player behavior patterns. This is the "reactive layer"
that makes the world feel alive.

Features:
- Pattern detection (combat streaks, build sessions, exploration)
- NPC ambient dialogue based on events
- Dynamic quest offers
- World commentary (weather, time, biome)
"""

import json
import sys
import time
import requests
from pathlib import Path
from datetime import datetime, timedelta, timezone
from typing import Dict, List, Optional
from collections import defaultdict
import threading

# Add parent directory to path
sys.path.insert(0, str(Path(__file__).parent.parent))


class EventReactor:
    """
    Watches events and triggers NPC responses

    The reactor runs in a loop, checking for new events and triggering
    appropriate NPC reactions based on patterns detected.
    """

    def __init__(
        self,
        events_path: str = None,
        npc_config_path: str = None,
        check_interval: float = 5.0,
        bridge_url: str = "http://localhost:5558"
    ):
        """Initialize the event reactor"""
        self.root = Path(__file__).parent.parent  # Go up to MIIN root
        self.events_path = events_path or str(self.root / 'events' / 'minecraft_events.json')
        self.npc_config_path = npc_config_path or str(self.root / 'npc' / 'config' / 'npcs.json')
        self.check_interval = check_interval
        self.bridge_url = bridge_url

        # Track processed events
        self.last_event_count = 0
        self.last_check_time = datetime.now(timezone.utc)

        # Pattern tracking per player
        self.player_patterns = defaultdict(lambda: {
            'combat_streak': 0,
            'build_blocks': 0,
            'biomes_visited': set(),
            'last_combat': None,
            'last_build': None,
            'last_ambient': None
        })

        # Reaction cooldowns (prevent spam)
        self.cooldowns = defaultdict(lambda: datetime.min.replace(tzinfo=timezone.utc))

        # Load NPC config
        self.npcs = self._load_npcs()

        # Running flag
        self.running = False

        print(f"[EventReactor] Initialized with {len(self.npcs)} NPCs", file=sys.stderr)

    def _load_npcs(self) -> Dict:
        """Load NPC configurations"""
        try:
            with open(self.npc_config_path, 'r') as f:
                config = json.load(f)
                npcs = {}
                for npc in config['npcs']:
                    npc_id = npc.get('id')
                    if not npc_id:
                        npc_id = npc.get('name', 'unknown').lower().replace(' ', '_')
                    npcs[npc_id] = npc
                return npcs
        except FileNotFoundError:
            print(f"[EventReactor] Warning: NPC config not found", file=sys.stderr)
            return {}

    def start(self):
        """Start the reactor loop"""
        self.running = True
        print(f"[EventReactor] Starting event monitoring (interval: {self.check_interval}s)", file=sys.stderr)

        while self.running:
            try:
                self._check_events()
            except Exception as e:
                print(f"[EventReactor] Error in check loop: {e}", file=sys.stderr)

            time.sleep(self.check_interval)

    def stop(self):
        """Stop the reactor loop"""
        self.running = False
        print("[EventReactor] Stopping", file=sys.stderr)

    def _check_events(self):
        """Check for new events and react"""
        try:
            with open(self.events_path, 'r') as f:
                events = json.load(f)
        except (FileNotFoundError, json.JSONDecodeError):
            return

        # Only process new events
        if len(events) <= self.last_event_count:
            return

        new_events = events[self.last_event_count:]
        self.last_event_count = len(events)

        # Group events by player
        player_events = defaultdict(list)
        for event in new_events:
            player_name = event.get('data', {}).get('playerName')
            if player_name:
                player_events[player_name].append(event)

        # Process each player's events
        for player_name, events in player_events.items():
            self._process_player_events(player_name, events)

    def _process_player_events(self, player_name: str, events: List[Dict]):
        """Process events for a single player and trigger reactions"""
        patterns = self.player_patterns[player_name]

        for event in events:
            event_type = event.get('eventType')
            data = event.get('data', {})

            if event_type == 'mob_killed':
                self._handle_mob_kill(player_name, data, patterns)

            elif event_type == 'build_complete':
                self._handle_build_complete(player_name, data, patterns)

            elif event_type == 'player_state':
                self._handle_player_state(player_name, data, patterns)

            elif event_type == 'session_start':
                self._handle_session_start(player_name, data)

    def _handle_mob_kill(self, player_name: str, data: Dict, patterns: Dict):
        """React to mob kills - combat streaks trigger NPC comments"""
        patterns['combat_streak'] += 1
        patterns['last_combat'] = datetime.now(timezone.utc)

        mob_type = data.get('mobType', 'creature')

        # Combat streak reactions
        if patterns['combat_streak'] == 5:
            self._trigger_combat_reaction(player_name, 'streak_5', mob_type)
        elif patterns['combat_streak'] == 10:
            self._trigger_combat_reaction(player_name, 'streak_10', mob_type)
        elif patterns['combat_streak'] == 25:
            self._trigger_combat_reaction(player_name, 'streak_25', mob_type)

        # Reset streak after 2 minutes of no combat
        if patterns['last_combat']:
            if datetime.now(timezone.utc) - patterns['last_combat'] > timedelta(minutes=2):
                patterns['combat_streak'] = 1

    def _handle_build_complete(self, player_name: str, data: Dict, patterns: Dict):
        """React to builds - large builds trigger NPC comments"""
        block_counts = data.get('blockCounts', {})
        total_blocks = sum(block_counts.values())
        patterns['build_blocks'] += total_blocks
        patterns['last_build'] = datetime.now(timezone.utc)

        # React to significant builds
        if total_blocks >= 50:
            self._trigger_build_reaction(player_name, block_counts, total_blocks)

    def _handle_player_state(self, player_name: str, data: Dict, patterns: Dict):
        """React to player state changes - biome discoveries, weather, time"""
        biome = data.get('biome', 'unknown')
        weather = data.get('weather', 'clear')
        time_of_day = data.get('timeOfDay', 'day')
        health = data.get('health', 20)

        # New biome discovery
        if biome not in patterns['biomes_visited']:
            patterns['biomes_visited'].add(biome)
            self._trigger_biome_discovery(player_name, biome)

        # Weather commentary (occasional)
        if weather == 'thundering':
            self._trigger_weather_reaction(player_name, weather)

        # Low health warning
        if health < 6:
            self._trigger_health_warning(player_name, health)

        # Night time ambient (occasional)
        if time_of_day == 'night':
            self._trigger_time_reaction(player_name, time_of_day)

    def _handle_session_start(self, player_name: str, data: Dict):
        """Greet player when they join"""
        # Reset patterns for new session
        self.player_patterns[player_name] = {
            'combat_streak': 0,
            'build_blocks': 0,
            'biomes_visited': set(),
            'last_combat': None,
            'last_build': None,
            'last_ambient': None
        }

        # Welcome message from random NPC (with cooldown to prevent spam)
        if not self._check_cooldown(player_name, 'session_welcome', seconds=1800):
            return  # Only welcome once per 30 minutes

        if self.npcs:
            npc_id = list(self.npcs.keys())[0]
            npc = self.npcs[npc_id]
            self._send_ambient(
                player_name,
                npc['name'],
                f"Welcome back, {player_name}. The world awaits your adventures."
            )

    def _trigger_combat_reaction(self, player_name: str, streak_type: str, mob_type: str):
        """Trigger NPC reaction to combat streak"""
        if not self._check_cooldown(player_name, f'combat_{streak_type}', seconds=60):
            return

        # Find combat-focused NPC (Kira)
        npc = self._find_npc_by_interest('combat')
        if not npc:
            return

        messages = {
            'streak_5': f"I see you've been busy with those {mob_type}s. Keep your guard up.",
            'streak_10': f"Ten {mob_type}s down! Your combat skills are improving.",
            'streak_25': f"Twenty-five kills! You fight like a seasoned warrior, {player_name}."
        }

        message = messages.get(streak_type, "Impressive combat.")
        self._send_ambient(player_name, npc['name'], message)

    def _trigger_build_reaction(self, player_name: str, block_counts: Dict, total: int):
        """Trigger NPC reaction to building"""
        if not self._check_cooldown(player_name, 'build', seconds=120):
            return

        # Find building-focused NPC
        npc = self._find_npc_by_interest('architecture') or self._find_npc_by_interest('building')
        if not npc:
            return

        # Determine build type from blocks
        primary_block = max(block_counts.items(), key=lambda x: x[1])[0] if block_counts else 'blocks'

        message = f"I noticed your construction using {primary_block}. {total} blocks placed - quite the project!"
        self._send_ambient(player_name, npc['name'], message)

    def _trigger_biome_discovery(self, player_name: str, biome: str):
        """Trigger NPC reaction to new biome discovery"""
        if not self._check_cooldown(player_name, f'biome_{biome}', seconds=300):
            return

        # Find exploration-focused NPC
        npc = self._find_npc_by_interest('exploration') or self._find_npc_by_interest('nature')
        if not npc:
            return

        biome_comments = {
            'forest': "The forest holds many secrets. Watch for rare mushrooms.",
            'desert': "The desert is unforgiving. Stay hydrated and watch for temples.",
            'ocean': "The ocean depths contain treasures and dangers alike.",
            'mountains': "High altitudes offer rare ores. Mind your step.",
            'swamp': "Swamps are treacherous but rich in slimes and witches.",
            'jungle': "The jungle is dense with life. Parrots and ocelots roam here.",
            'taiga': "Cold lands breed hardy creatures. Wolves make loyal companions.",
            'plains': "Open plains are good for farming and horse taming.",
        }

        # Find matching biome comment
        comment = None
        for key, msg in biome_comments.items():
            if key in biome.lower():
                comment = msg
                break

        if not comment:
            comment = f"You've discovered the {biome}. Explore carefully."

        self._send_ambient(player_name, npc['name'], comment)

    def _trigger_weather_reaction(self, player_name: str, weather: str):
        """Trigger NPC reaction to weather"""
        if not self._check_cooldown(player_name, 'weather', seconds=300):
            return

        npc = self._find_npc_by_interest('nature') or (list(self.npcs.values())[0] if self.npcs else None)
        if not npc:
            return

        if weather == 'thundering':
            self._send_ambient(
                player_name,
                npc['name'],
                "A storm approaches. Seek shelter, or use the lightning to your advantage."
            )

    def _trigger_health_warning(self, player_name: str, health: float):
        """Warn player about low health"""
        if not self._check_cooldown(player_name, 'health_warning', seconds=60):
            return

        npc = list(self.npcs.values())[0] if self.npcs else None
        if not npc:
            return

        self._send_ambient(
            player_name,
            npc['name'],
            f"Your health is dangerously low ({health:.0f}/20). Find food or shelter!"
        )

    def _trigger_time_reaction(self, player_name: str, time_of_day: str):
        """Occasional time-based ambient"""
        if not self._check_cooldown(player_name, 'time_ambient', seconds=600):
            return

        npc = self._find_npc_by_interest('mysterious') or (list(self.npcs.values())[0] if self.npcs else None)
        if not npc:
            return

        if time_of_day == 'night':
            self._send_ambient(
                player_name,
                npc['name'],
                "The night brings dangers... and opportunities. Stay vigilant."
            )

    def _find_npc_by_interest(self, interest: str) -> Optional[Dict]:
        """Find an NPC with a specific interest"""
        for npc in self.npcs.values():
            if interest.lower() in [i.lower() for i in npc.get('interests', [])]:
                return npc
        return None

    def _check_cooldown(self, player_name: str, cooldown_type: str, seconds: int) -> bool:
        """Check if cooldown has expired"""
        key = f"{player_name}:{cooldown_type}"
        now = datetime.now(timezone.utc)

        if now - self.cooldowns[key] < timedelta(seconds=seconds):
            return False

        self.cooldowns[key] = now
        return True

    def _send_ambient(self, player_name: str, npc_name: str, message: str):
        """Send ambient dialogue to player"""
        full_message = f"[{npc_name}] {message}"

        try:
            response = requests.post(
                f'{self.bridge_url}/command',
                json={
                    'type': 'send_chat',
                    'data': {
                        'player': player_name,
                        'message': full_message
                    }
                },
                timeout=5
            )

            if response.status_code == 200:
                print(f"[EventReactor] Sent: {full_message[:50]}...", file=sys.stderr)
            else:
                print(f"[EventReactor] Failed to send: {response.status_code}", file=sys.stderr)

        except Exception as e:
            print(f"[EventReactor] Error sending ambient: {e}", file=sys.stderr)


def main():
    """Run the event reactor"""
    import argparse

    parser = argparse.ArgumentParser(description='Event Reactor for Minecraft MCP')
    parser.add_argument('--interval', type=float, default=5.0, help='Check interval in seconds')
    parser.add_argument('--once', action='store_true', help='Run once and exit')

    args = parser.parse_args()

    reactor = EventReactor(check_interval=args.interval)

    if args.once:
        reactor._check_events()
    else:
        try:
            reactor.start()
        except KeyboardInterrupt:
            reactor.stop()


if __name__ == '__main__':
    main()
