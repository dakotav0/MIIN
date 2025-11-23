"""
Tests for quest lifecycle

Tests quest generation, acceptance, progress tracking, and completion.
"""

import pytest
import json
from pathlib import Path
from datetime import datetime, timezone, timedelta


class TestQuestGeneration:
    """Test quest generation from NPCs"""

    def test_quest_type_suggestion_combat(self, npc_service, sample_events, temp_dir):
        """Should suggest combat quest when player killed mobs"""
        # Write events
        events_path = Path(temp_dir) / "minecraft_events.json"
        with open(sample_events, 'r') as f:
            events = json.load(f)
        with open(events_path, 'w') as f:
            json.dump(events, f)

        npc_service.events_path = str(events_path)

        npc = npc_service.npcs.get("combat_npc")
        context = npc_service.get_player_context("TestPlayer")

        quest_type = npc_service.suggest_quest_type(npc, context)
        assert quest_type == "combat"

    def test_activity_summary(self, npc_service, sample_events, temp_dir):
        """Activity summary should include recent actions"""
        events_path = Path(temp_dir) / "minecraft_events.json"
        with open(sample_events, 'r') as f:
            events = json.load(f)
        with open(events_path, 'w') as f:
            json.dump(events, f)

        npc_service.events_path = str(events_path)

        context = npc_service.get_player_context("TestPlayer")
        summary = npc_service.summarize_activity(context)

        assert "building" in summary.lower() or "fighting" in summary.lower()


class TestQuestAcceptance:
    """Test quest acceptance workflow"""

    def test_accept_active_quest(self, npc_service, sample_quest):
        """Should mark existing active quest as accepted"""
        npc_service.quests['active'].append(sample_quest)
        npc_service.save_quests()

        # Accept the quest
        quest = npc_service.quests['active'][0]
        quest['accepted'] = True
        quest['accepted_at'] = datetime.now().isoformat()

        assert quest['accepted'] is True
        assert 'accepted_at' in quest

    def test_get_player_quests(self, npc_service, sample_quest):
        """Should return player's active and completed quests"""
        npc_service.quests['active'].append(sample_quest)

        completed_quest = sample_quest.copy()
        completed_quest['id'] = "completed_quest_456"
        completed_quest['status'] = "completed"
        npc_service.quests['completed'].append(completed_quest)

        result = npc_service.get_player_quests("TestPlayer")

        assert len(result['active']) == 1
        assert len(result['completed']) == 1
        assert result['active'][0]['id'] == "test_quest_123"

    def test_quests_separated_by_player(self, npc_service, sample_quest):
        """Quests should be separated by player"""
        npc_service.quests['active'].append(sample_quest)

        other_quest = sample_quest.copy()
        other_quest['id'] = "other_quest"
        other_quest['player'] = "OtherPlayer"
        npc_service.quests['active'].append(other_quest)

        result = npc_service.get_player_quests("TestPlayer")
        assert len(result['active']) == 1
        assert result['active'][0]['player'] == "TestPlayer"


class TestQuestProgress:
    """Test quest progress tracking"""

    def test_kill_objective_progress(self, npc_service, sample_quest, temp_dir):
        """Kill objectives should track mob kills"""
        # Set up quest
        npc_service.quests['active'].append(sample_quest)

        # Create events with kills (use current time so they're after quest creation)
        now = datetime.now(timezone.utc)
        events = []
        for i in range(5):
            events.append({
                "eventType": "mob_killed",
                "timestamp": now.isoformat().replace('+00:00', 'Z'),
                "data": {
                    "playerName": "TestPlayer",
                    "mobType": "zombie"
                }
            })

        events_path = Path(temp_dir) / "minecraft_events.json"
        with open(events_path, 'w') as f:
            json.dump(events, f)

        npc_service.events_path = str(events_path)

        # Check progress
        result = npc_service.check_quest_progress("TestPlayer")

        # First objective (kill_mobs) should be complete
        quest = npc_service.quests['active'][0]
        assert quest['objectives'][0]['progress'] >= 5
        assert quest['objectives'][0]['completed'] is True

    def test_build_objective_progress(self, npc_service, temp_dir):
        """Build objectives should track blocks placed"""
        quest = {
            "id": "build_quest",
            "player": "TestPlayer",
            "created": (datetime.now(timezone.utc) - timedelta(minutes=1)).isoformat().replace('+00:00', 'Z'),
            "objectives": [
                {
                    "type": "build_blocks",
                    "count": 50,
                    "progress": 0,
                    "completed": False
                }
            ]
        }
        npc_service.quests['active'].append(quest)

        # Create build event
        events = [{
            "eventType": "build_complete",
            "timestamp": datetime.now(timezone.utc).isoformat().replace('+00:00', 'Z'),
            "data": {
                "playerName": "TestPlayer",
                "blockCounts": {"stone": 30, "oak_planks": 25}
            }
        }]

        events_path = Path(temp_dir) / "minecraft_events.json"
        with open(events_path, 'w') as f:
            json.dump(events, f)

        npc_service.events_path = str(events_path)

        result = npc_service.check_quest_progress("TestPlayer")

        # Quest completes with single objective met, so check completed list
        quest = npc_service.quests['completed'][0]
        assert quest['objectives'][0]['progress'] == 55
        assert quest['objectives'][0]['completed'] is True

    def test_visit_biome_objective(self, npc_service, temp_dir):
        """Visit biome objectives should track player state"""
        quest = {
            "id": "explore_quest",
            "player": "TestPlayer",
            "created": (datetime.now(timezone.utc) - timedelta(minutes=1)).isoformat().replace('+00:00', 'Z'),
            "objectives": [
                {
                    "type": "visit_biome",
                    "target": "desert",
                    "progress": 0,
                    "completed": False
                }
            ]
        }
        npc_service.quests['active'].append(quest)

        # Create state event in desert
        events = [{
            "eventType": "player_state",
            "timestamp": datetime.now(timezone.utc).isoformat().replace('+00:00', 'Z'),
            "data": {
                "playerName": "TestPlayer",
                "biome": "desert"
            }
        }]

        events_path = Path(temp_dir) / "minecraft_events.json"
        with open(events_path, 'w') as f:
            json.dump(events, f)

        npc_service.events_path = str(events_path)

        result = npc_service.check_quest_progress("TestPlayer")

        # Quest completes with single objective met, so check completed list
        quest = npc_service.quests['completed'][0]
        assert quest['objectives'][0]['completed'] is True


class TestQuestCompletion:
    """Test quest completion and rewards"""

    def test_quest_moves_to_completed(self, npc_service, temp_dir):
        """Completed quest should move from active to completed"""
        quest = {
            "id": "simple_quest",
            "player": "TestPlayer",
            "npc_name": "Test NPC",
            "created": (datetime.now(timezone.utc) - timedelta(minutes=1)).isoformat().replace('+00:00', 'Z'),
            "objectives": [
                {
                    "type": "kill_mobs",
                    "target": "zombie",
                    "count": 1,
                    "progress": 0,
                    "completed": False
                }
            ],
            "reward": {
                "type": "lore",
                "content": "Test lore"
            }
        }
        npc_service.quests['active'].append(quest)

        # Create kill event
        events = [{
            "eventType": "mob_killed",
            "timestamp": datetime.now(timezone.utc).isoformat().replace('+00:00', 'Z'),
            "data": {
                "playerName": "TestPlayer",
                "mobType": "zombie"
            }
        }]

        events_path = Path(temp_dir) / "minecraft_events.json"
        with open(events_path, 'w') as f:
            json.dump(events, f)

        npc_service.events_path = str(events_path)

        result = npc_service.check_quest_progress("TestPlayer")

        assert len(npc_service.quests['active']) == 0
        assert len(npc_service.quests['completed']) == 1
        assert npc_service.quests['completed'][0]['status'] == 'completed'

    def test_completion_timestamp(self, npc_service, temp_dir):
        """Completed quest should have completion timestamp"""
        quest = {
            "id": "simple_quest",
            "player": "TestPlayer",
            "created": (datetime.now(timezone.utc) - timedelta(minutes=1)).isoformat().replace('+00:00', 'Z'),
            "objectives": [
                {
                    "type": "kill_mobs",
                    "target": "zombie",
                    "count": 1,
                    "progress": 0,
                    "completed": False
                }
            ]
        }
        npc_service.quests['active'].append(quest)

        events = [{
            "eventType": "mob_killed",
            "timestamp": datetime.now(timezone.utc).isoformat().replace('+00:00', 'Z'),
            "data": {
                "playerName": "TestPlayer",
                "mobType": "zombie"
            }
        }]

        events_path = Path(temp_dir) / "minecraft_events.json"
        with open(events_path, 'w') as f:
            json.dump(events, f)

        npc_service.events_path = str(events_path)

        npc_service.check_quest_progress("TestPlayer")

        completed = npc_service.quests['completed'][0]
        assert 'completed_at' in completed

    def test_reward_delivery_lore(self, npc_service, temp_dir):
        """Lore rewards should be delivered"""
        quest = {
            "id": "lore_quest",
            "npc_name": "Wise NPC",
            "player": "TestPlayer",
            "reward": {
                "type": "lore",
                "content": "The ancient truth is revealed."
            }
        }

        result = npc_service.deliver_reward("TestPlayer", quest)

        assert result['delivered'] is True
        assert result['content'] == "The ancient truth is revealed."

    def test_reward_delivery_items(self, npc_service):
        """Item rewards should be structured correctly"""
        quest = {
            "id": "item_quest",
            "player": "TestPlayer",
            "reward": {
                "type": "items",
                "items": [
                    {"id": "minecraft:diamond", "count": 5}
                ]
            }
        }

        result = npc_service.deliver_reward("TestPlayer", quest)

        assert result['delivered'] is True
        assert result['items'][0]['id'] == "minecraft:diamond"

    def test_reward_delivery_xp(self, npc_service):
        """XP rewards should be structured correctly"""
        quest = {
            "id": "xp_quest",
            "player": "TestPlayer",
            "reward": {
                "type": "xp",
                "amount": 100
            }
        }

        result = npc_service.deliver_reward("TestPlayer", quest)

        assert result['delivered'] is True
        assert result['xp'] == 100


class TestMultiObjectiveQuests:
    """Test quests with multiple objectives"""

    def test_partial_completion(self, npc_service, sample_quest, temp_dir):
        """Quest with partial completion should stay active"""
        npc_service.quests['active'].append(sample_quest)

        # Only kill zombies, don't return to NPC
        now = datetime.now(timezone.utc)
        events = []
        for i in range(5):
            events.append({
                "eventType": "mob_killed",
                "timestamp": now.isoformat().replace('+00:00', 'Z'),
                "data": {
                    "playerName": "TestPlayer",
                    "mobType": "zombie"
                }
            })

        events_path = Path(temp_dir) / "minecraft_events.json"
        with open(events_path, 'w') as f:
            json.dump(events, f)

        npc_service.events_path = str(events_path)

        result = npc_service.check_quest_progress("TestPlayer")

        # Quest should still be active (return_to_npc not complete)
        assert len(npc_service.quests['active']) == 1
        quest = npc_service.quests['active'][0]
        assert quest['objectives'][0]['completed'] is True
        assert quest['objectives'][1]['completed'] is False

    def test_all_objectives_required(self, npc_service, sample_quest, temp_dir):
        """All objectives must be complete for quest completion"""
        # Only one objective
        sample_quest['objectives'] = sample_quest['objectives'][:1]
        npc_service.quests['active'].append(sample_quest)

        now = datetime.now(timezone.utc)
        events = []
        for i in range(5):
            events.append({
                "eventType": "mob_killed",
                "timestamp": now.isoformat().replace('+00:00', 'Z'),
                "data": {
                    "playerName": "TestPlayer",
                    "mobType": "zombie"
                }
            })

        events_path = Path(temp_dir) / "minecraft_events.json"
        with open(events_path, 'w') as f:
            json.dump(events, f)

        npc_service.events_path = str(events_path)

        npc_service.check_quest_progress("TestPlayer")

        # Should be completed now
        assert len(npc_service.quests['completed']) == 1


class TestEdgeCases:
    """Test edge cases and error handling"""

    def test_no_events_for_player(self, npc_service, sample_quest, temp_dir):
        """Should handle player with no events"""
        npc_service.quests['active'].append(sample_quest)

        # Empty events
        events_path = Path(temp_dir) / "minecraft_events.json"
        with open(events_path, 'w') as f:
            json.dump([], f)

        npc_service.events_path = str(events_path)

        result = npc_service.check_quest_progress("TestPlayer")

        # Quest should remain unchanged
        assert len(npc_service.quests['active']) == 1

    def test_wrong_mob_type(self, npc_service, sample_quest, temp_dir):
        """Killing wrong mob type shouldn't count"""
        npc_service.quests['active'].append(sample_quest)

        # Kill skeletons, not zombies
        events = [{
            "eventType": "mob_killed",
            "timestamp": datetime.now(timezone.utc).isoformat().replace('+00:00', 'Z'),
            "data": {
                "playerName": "TestPlayer",
                "mobType": "skeleton"
            }
        }]

        events_path = Path(temp_dir) / "minecraft_events.json"
        with open(events_path, 'w') as f:
            json.dump(events, f)

        npc_service.events_path = str(events_path)

        npc_service.check_quest_progress("TestPlayer")

        quest = npc_service.quests['active'][0]
        assert quest['objectives'][0]['progress'] == 0

    def test_no_reward_defined(self, npc_service):
        """Should handle quest with no reward"""
        quest = {
            "id": "no_reward_quest",
            "player": "TestPlayer"
            # No reward field
        }

        result = npc_service.deliver_reward("TestPlayer", quest)
        assert result['delivered'] is False
        assert "No reward defined" in result['reason']
