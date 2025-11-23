"""
Pytest fixtures for MIIN tests

Provides shared fixtures for testing NPC services, quests, and persistence.
"""

import pytest
import json
import tempfile
import shutil
from pathlib import Path
from datetime import datetime, timezone
import sys

# Add MIIN to path
sys.path.insert(0, str(Path(__file__).parent.parent))


@pytest.fixture
def temp_dir():
    """Create a temporary directory for test files"""
    temp = tempfile.mkdtemp()
    yield temp
    shutil.rmtree(temp)


@pytest.fixture
def sample_npc_config(temp_dir):
    """Create sample NPC configuration"""
    config = {
        "npcs": [
            {
                "id": "test_npc",
                "name": "Test NPC",
                "personality": "helpful and friendly",
                "backstory": "A test character",
                "dialogue_style": "casual",
                "interests": ["testing", "helping"],
                "model": "llama3.2:latest",
                "questTypes": ["exploration", "combat"],
                "location": {
                    "x": 0,
                    "y": 64,
                    "z": 0,
                    "dimension": "minecraft:overworld"
                }
            },
            {
                "id": "combat_npc",
                "name": "Warrior",
                "personality": "brave and strong",
                "backstory": "A battle-hardened warrior",
                "dialogue_style": "direct",
                "interests": ["combat", "training"],
                "model": "llama3.2:latest",
                "questTypes": ["combat"],
                "location": {
                    "x": 100,
                    "y": 64,
                    "z": 100,
                    "dimension": "minecraft:overworld"
                }
            }
        ]
    }

    config_path = Path(temp_dir) / "npc_config.json"
    with open(config_path, 'w') as f:
        json.dump(config, f)

    return str(config_path)


@pytest.fixture
def sample_events(temp_dir):
    """Create sample Minecraft events with current timestamps"""
    # Use current time so events are within the 1-hour window
    now = datetime.now(timezone.utc)

    events = [
        {
            "eventType": "mob_killed",
            "timestamp": now.isoformat().replace('+00:00', 'Z'),
            "data": {
                "playerName": "TestPlayer",
                "playerId": "test-uuid-1234",
                "mobType": "zombie",
                "mobCategory": "MONSTER",
                "x": 10,
                "y": 64,
                "z": 20
            }
        },
        {
            "eventType": "build_complete",
            "timestamp": now.isoformat().replace('+00:00', 'Z'),
            "data": {
                "playerName": "TestPlayer",
                "playerId": "test-uuid-1234",
                "blocks": ["stone", "oak_planks"],
                "blockCounts": {"stone": 30, "oak_planks": 20},
                "buildTime": 120
            }
        },
        {
            "eventType": "player_state",
            "timestamp": now.isoformat().replace('+00:00', 'Z'),
            "data": {
                "playerName": "TestPlayer",
                "playerId": "test-uuid-1234",
                "x": 50,
                "y": 64,
                "z": 100,
                "dimension": "minecraft:overworld",
                "biome": "forest",
                "health": 20,
                "hunger": 18,
                "weather": "clear",
                "timeOfDay": "day"
            }
        }
    ]

    events_path = Path(temp_dir) / "minecraft_events.json"
    with open(events_path, 'w') as f:
        json.dump(events, f)

    return str(events_path)


@pytest.fixture
def npc_service(temp_dir, sample_npc_config):
    """Create NPC service with test configuration"""
    from npc_service import NPCService

    memory_path = str(Path(temp_dir) / "npc_memory.json")
    quest_path = str(Path(temp_dir) / "npc_quests.json")

    service = NPCService(
        npc_config_path=sample_npc_config,
        memory_path=memory_path,
        quest_path=quest_path
    )

    # Point to temp events
    service.events_path = str(Path(temp_dir) / "minecraft_events.json")

    return service


@pytest.fixture
def dialogue_service(temp_dir):
    """Create dialogue service with test configuration"""
    from dialogue_service import DialogueService

    relationships_path = str(Path(temp_dir) / "npc_relationships.json")

    service = DialogueService(
        relationships_path=relationships_path
    )

    return service


@pytest.fixture
def party_service(temp_dir):
    """Create party service with test configuration"""
    from party_service import PartyService

    parties_path = str(Path(temp_dir) / "player_parties.json")

    service = PartyService(
        parties_path=parties_path
    )

    return service


@pytest.fixture
def sample_quest():
    """Create a sample quest for testing with timezone-aware timestamp"""
    # Use a timestamp from 1 minute ago to ensure events are "after" quest creation
    from datetime import timedelta
    quest_time = datetime.now(timezone.utc) - timedelta(minutes=1)

    return {
        "id": "test_quest_123",
        "npc_id": "test_npc",
        "npc_name": "Test NPC",
        "player": "TestPlayer",
        "type": "combat",
        "status": "active",
        "created": quest_time.isoformat().replace('+00:00', 'Z'),
        "title": "Zombie Slayer",
        "description": "Defeat 5 zombies in the forest",
        "objectives": [
            {
                "type": "kill_mobs",
                "target": "zombie",
                "count": 5,
                "progress": 0,
                "completed": False
            },
            {
                "type": "return_to_npc",
                "npc": "test_npc",
                "progress": 0,
                "completed": False
            }
        ],
        "reward": {
            "type": "lore",
            "content": "The zombies were once villagers from the Lost Village of Eldoria."
        }
    }
