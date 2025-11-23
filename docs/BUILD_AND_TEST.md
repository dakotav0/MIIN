# Build, Test, and Development Guide

**Version**: 1.0
**Last Updated**: 2025-01-21

---

## Table of Contents

- [Prerequisites](#prerequisites)
- [Initial Setup](#initial-setup)
- [Building](#building)
- [Testing](#testing)
- [Adding Features](#adding-features)
- [Debugging](#debugging)
- [Common Issues](#common-issues)

---

## Prerequisites

### Required Software

#### 1. Minecraft 1.20.1 with Fabric

**Install Fabric Loader:**
```bash
# Download Fabric installer
https://fabricmc.net/use/installer/

# Run installer, select:
# - Minecraft version: 1.20.1
# - Loader version: 0.15.11 (or latest stable)
# - Install location: Default (.minecraft)
```

**Verify Installation:**
```bash
# Launch Minecraft
# Profile: "fabric-loader-1.20.1" should appear
```

#### 2. Node.js 18+

**Install:**
```bash
# macOS (Homebrew)
brew install node@18

# Ubuntu/Debian
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt-get install -y nodejs

# Windows (Installer)
https://nodejs.org/en/download/
```

**Verify:**
```bash
node --version  # Should be v18.x.x or higher
npm --version   # Should be 9.x.x or higher
```

#### 3. Python 3.10+

**Install:**
```bash
# macOS (Homebrew)
brew install python@3.10

# Ubuntu/Debian
sudo apt-get install python3.10 python3.10-venv

# Windows (Installer)
https://www.python.org/downloads/
```

**Verify:**
```bash
python --version  # or python3 --version
# Should be Python 3.10.x or higher
```

#### 4. Java 17 (for Kotlin mod)

**Install:**
```bash
# macOS (Homebrew)
brew install openjdk@17

# Ubuntu/Debian
sudo apt-get install openjdk-17-jdk

# Windows (Installer)
https://adoptium.net/
```

**Verify:**
```bash
java --version
# Should be openjdk 17.x.x
```

#### 5. Git

**Install:**
```bash
# macOS (comes with Xcode Command Line Tools)
xcode-select --install

# Ubuntu/Debian
sudo apt-get install git

# Windows (Git Bash)
https://git-scm.com/download/win
```

### Optional Software

#### Ollama (for LLM integration)

**Install:**
```bash
# macOS/Linux
curl https://ollama.ai/install.sh | sh

# Windows
https://ollama.ai/download
```

**Pull Models:**
```bash
ollama pull llama3.2:latest      # Fast, conversational (3B)
ollama pull llama3.1:8b          # Creative, reasoning (8B)
ollama pull deepseek-r1:latest   # Analytical, code (8B)
```

**Start Ollama:**
```bash
ollama serve  # Runs on port 11434
```

#### VSCode (Recommended IDE)

**Extensions:**
- Kotlin Language
- TypeScript and JavaScript Language Features
- Python
- Fabric Dev Tools

---

## Initial Setup

### 1. Clone Repository

```bash
git clone https://github.com/dakotav0/MIIN.git
cd MIIN
```

### 2. Install Dependencies

#### MCP Server (TypeScript)

```bash
# Install npm dependencies
npm install

# Verify packages installed
ls node_modules/  # Should see @modelcontextprotocol, zod, etc.
```

#### Python Backend

```bash
# Create virtual environment (recommended)
python -m venv venv

# Activate virtual environment
source venv/bin/activate      # macOS/Linux
venv\Scripts\activate         # Windows

# Install dependencies
pip install requests          # Core HTTP library

# Verify
python -c "import requests; print('Requests OK')"
```

#### Fabric Mod (Kotlin)

```bash
cd MIIN

# Gradle wrapper should be included
./gradlew --version  # Should show Gradle 8.x

# Download dependencies (first run takes time)
./gradlew build --dry-run

cd ..
```

---

## Building

### Build MCP Server (TypeScript)

```bash
# From project root
npm run build

# Output: dist/ directory
# Files: dist/index.js, dist/tools/*.js
```

**Verify Build:**
```bash
# Check dist/ exists
ls dist/

# Should see:
# index.js
# index.d.ts
# tools/
```

**Watch Mode** (auto-rebuild on changes):
```bash
npm run dev
```

### Build Fabric Mod (Kotlin)

```bash
cd MIIN

# Clean build
./gradlew clean build

# Output: build/libs/MIIN-1.0.0.jar

# Time: ~30 seconds (first build)
# Time: ~10 seconds (subsequent builds)
```

**Verify Build:**
```bash
ls build/libs/

# Should see:
# MIIN-1.0.0.jar
# MIIN-1.0.0-sources.jar
# MIIN-1.0.0-dev.jar
```

**Common Build Errors:**

**Error: "Task :compileKotlin FAILED"**
```bash
# Solution: Check Kotlin version in build.gradle.kts
# Should match: kotlin("jvm") version "1.9.23"
```

**Error: "Could not resolve net.fabricmc:fabric-loader"**
```bash
# Solution: Check internet connection, retry
./gradlew clean build --refresh-dependencies
```

### Install Mod to Minecraft

```bash
# Copy JAR to mods folder
cp MIIN/build/libs/MIIN-1.0.0.jar ~/.minecraft/mods/

# Windows
copy MIIN\build\libs\MIIN-1.0.0.jar %APPDATA%\.minecraft\mods\

# Restart Minecraft
```

---

## Testing

### Unit Tests (Future)

Currently, testing is primarily manual. Unit tests are a welcome contribution!

**Planned Structure:**
```
tests/
├── typescript/
│   └── test-tools.ts
├── python/
│   ├── test_npc_service.py
│   ├── test_dialogue_service.py
│   └── test_merchant_service.py
└── kotlin/
    └── src/test/kotlin/
        └── MIINListenerTest.kt
```

### Manual Testing

#### Test 1: MCP Server

**Start MCP Server:**
```bash
npm run start
```

**Expected Output:**
```
MIIN Minecraft MCP Server starting...
Loaded 30 tools
Server ready on stdio
```

**Test Tool (using MCP Inspector):**
```bash
npm run inspect
```

Opens browser at http://localhost:6274

**Test `minecraft_npc_list`:**
```json
{
  "tool": "minecraft_npc_list",
  "arguments": {}
}
```

**Expected Result:**
```json
{
  "npcs": [
    {"id": "rowan", "name": "Rowan", "location": {...}},
    {"id": "marina", "name": "Marina", "location": {...}}
  ]
}
```

#### Test 2: Python Services

**Test NPC Service:**
```bash
cd npc/scripts
python service.py

# Should print:
# [NPC] Service initialized with X NPCs
```

**Test Dialogue Service:**
```bash
cd dialogue
python service.py

# Should not crash
# Check for syntax errors
```

**Test Import Paths:**
```bash
python -c "from npc.scripts.service import NPCService; print('OK')"
python -c "from dialogue.service import DialogueService; print('OK')"
python -c "from npc.scripts.llm_router import SimpleLLMRouter; print('OK')"
```

#### Test 3: Kotlin Mod

**Prerequisites:**
- Ollama running (`ollama serve`)
- MCP server running (`npm run start`)
- Mod installed in Minecraft

**In-Game Tests:**

**Test 3.1: NPC Spawning**
1. Start Minecraft with Fabric + mod
2. Create/join world
3. Check logs for: `[MIIN] Service initialized`
4. NPCs should spawn at configured locations
5. NPCs should be visible and clickable

**Test 3.2: Dialogue System**
1. Right-click NPC
2. Should see greeting in chat
3. Type message in chat
4. NPC should respond within 2-5 seconds
5. Check `npc/config/memory.json` - conversation saved

**Test 3.3: Merchant System** (if implemented)
1. Talk to merchant NPC (e.g., Rowan)
2. Say "I want to buy wheat"
3. Should see inventory with prices
4. Complete purchase
5. Check `npc/merchant/trades.db` - transaction logged

**Test 3.4: Memory Persistence**
1. Talk to NPC, have short conversation
2. Stop server
3. Restart server
4. Talk to same NPC
5. NPC should reference previous conversation

**Test 3.5: Relationship System**
1. Help NPC with task (accept quest, give gift)
2. Check `npc/config/relationships.json` - relationship increased
3. Talk to NPC again
4. Dialogue should reflect improved relationship

#### Test 4: LLM Router

**Test Keep-Alive:**
```bash
# Start Ollama
ollama serve

# Check models loaded
ollama ps
# Should be empty initially

# Trigger dialogue (talk to NPC)
# Wait 2 seconds

# Check again
ollama ps
# Should show: llama3.1:8b (or configured model)
# UNTIL: 10 minutes from now

# Talk to NPC again (within 10 min)
# Response should be instant (~2s, no 20s load time)
```

**Test Context Optimization:**
```bash
# Have 15+ exchange conversation with NPC
# Check memory.json - all messages saved (40+ messages)

# Add logging to llm_router.py:
# print(f"Messages sent to Ollama: {len(messages)}")

# Should see: ~20 messages (optimized from 40+)
```

**Test Fallback:**
```bash
# Stop Ollama
killall ollama

# Try to talk to NPC
# Should see error message (graceful failure)

# Restart Ollama
ollama serve

# Talk to NPC
# Should work again
```

### Integration Testing Checklist

- [ ] MCP server starts without errors
- [ ] All 30 tools load successfully
- [ ] Python services import without errors
- [ ] Kotlin mod compiles and installs
- [ ] NPCs spawn at server start
- [ ] Dialogue system responds to player messages
- [ ] Memory persists across server restarts
- [ ] Relationship tracking updates correctly
- [ ] Merchant trades work (if implemented)
- [ ] Faction reputation cascades (if implemented)
- [ ] LLM keep-alive prevents reload delays
- [ ] Context optimization reduces token usage

---

## Adding Features

### Workflow

1. **Plan** - Document feature in GitHub issue
2. **Branch** - Create feature branch (`git checkout -b feature/name`)
3. **Implement** - Write code (follow [CONTRIBUTING.md](../CONTRIBUTING.md))
4. **Test** - Manual testing + unit tests (if applicable)
5. **Document** - Update README, inline comments
6. **Commit** - Follow commit message guidelines
7. **Pull Request** - Submit for review

### Example: Add New Dialogue Option Tone

**Goal**: Add "sarcastic" tone to dialogue options

**Files to Modify:**
1. `dialogue/service.py` - Add tone to options generation
2. `npc/scripts/service.py` - Update system prompt to recognize tone
3. `src/tools/dialogue-tools.ts` - Add to schema validation

**Step-by-Step:**

#### 1. Update Dialogue Service

```python
# dialogue/service.py

DIALOGUE_TONES = [
    'friendly',
    'aggressive',
    'curious',
    'flirty',
    'intimidating',
    'sarcastic'  # NEW
]

def _build_options_prompt(self, ...):
    """Add sarcastic tone example"""
    prompt += """
    Available tones:
    - friendly: Kind, supportive
    - aggressive: Hostile, confrontational
    - curious: Inquisitive, seeking knowledge
    - flirty: Charming, romantic
    - intimidating: Threatening, forceful
    - sarcastic: Ironic, mocking (NEW)

    Generate 3-5 options covering different tones.
    """
```

#### 2. Update System Prompt

```python
# npc/scripts/service.py

def build_system_prompt(self, npc, player, context):
    prompt += """
    DIALOGUE GUIDELINES:
    - Respond to sarcastic player choices with wit or irritation
    - Sarcasm may decrease relationship with formal NPCs
    - Sarcasm may increase relationship with roguish NPCs
    """
```

#### 3. Update TypeScript Schema

```typescript
// src/tools/dialogue-tools.ts

const dialogueToneSchema = z.enum([
  'friendly',
  'aggressive',
  'curious',
  'flirty',
  'intimidating',
  'sarcastic'  // NEW
]);
```

#### 4. Test

```bash
# Build
npm run build
cd MIIN && ./gradlew build

# Test in-game
# Talk to NPC, select sarcastic option
# Verify NPC reacts appropriately
```

#### 5. Document

```markdown
# Add to README.md:

### Dialogue Tones

Players can choose dialogue options with different tones:
- **Friendly**: Kind, supportive responses
- **Sarcastic**: Ironic, mocking responses (may affect relationship)
...
```

#### 6. Commit

```bash
git add dialogue/service.py npc/scripts/service.py src/tools/dialogue-tools.ts
git commit -m "feat(dialogue): add sarcastic tone option

Allows players to respond with sarcasm. NPC reactions vary based on personality.

- Added 'sarcastic' to DIALOGUE_TONES
- Updated system prompt with sarcasm guidelines
- Updated TypeScript schema validation"
```

### Example: Add New MCP Tool

**Goal**: Add tool to get NPC relationship level

**Files to Create/Modify:**
1. Create `npc/scripts/get_relationship.py`
2. Update `src/index.ts` - register new tool

**Step-by-Step:**

#### 1. Create Python Script

```python
# npc/scripts/get_relationship.py
import sys
import json
from pathlib import Path

# Add project root to path
project_root = Path(__file__).parent.parent.parent
sys.path.insert(0, str(project_root))

from dialogue.service import DialogueService

def get_relationship(npc_id: str, player_name: str):
    """Get player's relationship with NPC"""
    dialogue_service = DialogueService()
    relationship = dialogue_service.get_relationship(npc_id, player_name)

    return {
        'npc_id': npc_id,
        'player_name': player_name,
        'level': relationship.get('level', 0),
        'title': relationship.get('title', 'Stranger'),
        'interactions': relationship.get('interactions', 0)
    }

if __name__ == "__main__":
    npc_id = sys.argv[1]
    player_name = sys.argv[2]

    result = get_relationship(npc_id, player_name)
    print(json.dumps(result, indent=2))
```

#### 2. Register MCP Tool

```typescript
// src/index.ts

server.tool(
  "minecraft_npc_get_relationship",
  "Get player's relationship level with an NPC",
  {
    npc: z.string().describe("NPC identifier (e.g., 'rowan')"),
    player: z.string().describe("Player name")
  },
  async ({ npc, player }) => {
    const scriptPath = `${process.cwd()}/npc/scripts/get_relationship.py`;
    const result = await exec(`python "${scriptPath}" "${npc}" "${player}"`);

    return {
      content: [
        {
          type: "text",
          text: result.stdout
        }
      ]
    };
  }
);
```

#### 3. Test

```bash
# Build
npm run build

# Test with MCP Inspector
npm run inspect

# Call tool:
{
  "tool": "minecraft_npc_get_relationship",
  "arguments": {
    "npc": "rowan",
    "player": "vDakota"
  }
}

# Expected result:
{
  "npc_id": "rowan",
  "player_name": "vDakota",
  "level": 15,
  "title": "Acquaintance",
  "interactions": 5
}
```

#### 4. Document

Update tool count in `package.json` and README.

### Example: Add New NPC Archetype

**Goal**: Add "blacksmith" archetype

**Files to Modify:**
1. `npc/config/archetypes.json` - Define archetype
2. `npc/skins/database.json` - Add blacksmith skins

**Step-by-Step:**

#### 1. Define Archetype

```json
// npc/config/archetypes.json

{
  "archetypes": {
    "blacksmith": {
      "personality": "Gruff, hardworking craftsman who values quality and skill",
      "interests": ["metalworking", "craftsmanship", "armor", "weapons"],
      "questTypes": ["crafting", "gathering", "combat"],
      "dialogue_style": "Direct, practical, occasionally grumpy but fair",
      "default_skin_tags": ["strong", "professional", "craftsman"],
      "merchant_role": "weaponsmith",
      "default_inventory": [
        {"item": "minecraft:iron_sword", "quantity": 5, "price_buy": 50},
        {"item": "minecraft:iron_chestplate", "quantity": 3, "price_buy": 100},
        {"item": "minecraft:anvil", "quantity": 2, "price_buy": 150}
      ]
    }
  }
}
```

#### 2. Add Skins

```json
// npc/skins/database.json

{
  "skins": [
    {
      "filename": "blacksmith_01.png",
      "tags": {
        "role": ["blacksmith", "craftsman", "merchant"],
        "vibe": ["strong", "professional", "gruff"],
        "palette": ["gray", "brown", "black"],
        "biome": ["village", "city"]
      }
    },
    {
      "filename": "blacksmith_02.png",
      "tags": {
        "role": ["blacksmith", "craftsman"],
        "vibe": ["strong", "skilled", "hardworking"],
        "palette": ["dark", "leather", "metal"],
        "biome": ["village", "forge"]
      }
    }
  ]
}
```

#### 3. Create Blacksmith NPC

```bash
# Use minecraft_npc_generate tool or add to npcs.json

{
  "npcs": [
    {
      "id": "alaric",
      "name": "Alaric",
      "archetype": "blacksmith",
      "personality": "Gruff master blacksmith, perfectionist",
      "backstory": "Former army weaponsmith, now runs village forge",
      "location": {
        "x": 100,
        "y": 64,
        "z": 50,
        "dimension": "minecraft:overworld"
      }
    }
  ]
}
```

#### 4. Test

Spawn blacksmith NPC, verify:
- Personality matches archetype
- Correct skin applied
- Merchant inventory correct
- Quests align with archetype

---

## Debugging

### Enable Debug Logging

#### Kotlin Mod

```kotlin
// MIIN/src/main/kotlin/MIIN/listener/MIINListener.kt

private val logger = LoggerFactory.getLogger("MIIN")

// Change log level
logger.debug("NPC spawned: ${npc.npcId}")  // Debug
logger.info("Server started")              // Info
logger.warn("Missing config")              // Warning
logger.error("Failed to spawn NPC", e)     // Error
```

**View Logs:**
```bash
# In-game: Check logs/latest.log
tail -f ~/.minecraft/logs/latest.log | grep MIIN

# Or runtime bug log
tail -f MIIN/runtimebuglog.txt
```

#### Python Services

```python
import sys

# Print to stderr (visible in MCP server output)
print("[DEBUG] NPC response generated", file=sys.stderr)
print(f"[DEBUG] Memory key: {key}", file=sys.stderr)
```

**View Logs:**
```bash
# When running MCP server
npm run start

# Debug output appears in terminal
```

#### MCP Server (TypeScript)

```typescript
// src/index.ts

console.error('[DEBUG] Tool called:', toolName);
console.error('[DEBUG] Arguments:', args);
console.error('[DEBUG] Python output:', result.stdout);
```

**View Logs:**
```bash
# Run in dev mode
npm run dev

# All console.error() output visible
```

### Common Debugging Scenarios

#### NPC Not Responding

**Symptoms:**
- Player talks to NPC
- No response in chat
- No error messages

**Debug Steps:**

1. **Check Ollama Running:**
   ```bash
   ollama ps
   # If empty, start: ollama serve
   ```

2. **Check MCP Server:**
   ```bash
   # Should be running
   ps aux | grep "node dist/index.js"
   ```

3. **Check Kotlin Logs:**
   ```bash
   tail -f ~/.minecraft/logs/latest.log | grep MIIN
   # Look for HTTP errors
   ```

4. **Test Python Script Directly:**
   ```bash
   python npc/scripts/talk.py rowan vDakota "Hello"
   # Should print NPC response
   ```

5. **Check Memory File:**
   ```bash
   cat npc/config/memory.json | grep rowan
   # Should see conversation history
   ```

#### Memory Not Persisting

**Symptoms:**
- NPC doesn't remember previous conversation
- `memory.json` empty or not updated

**Debug Steps:**

1. **Check File Permissions:**
   ```bash
   ls -la npc/config/memory.json
   # Should be writable
   ```

2. **Check Atomic Writes:**
   ```python
   # Add debug logging to service.py:save_memory()
   print(f"[DEBUG] Saving memory to {self.memory_path}", file=sys.stderr)
   ```

3. **Verify UUID Usage:**
   ```bash
   # Check if keys use UUID (not npc_id)
   cat npc/config/memory.json | grep -o '"[^"]*":' | head
   # Should see UUID-like strings
   ```

4. **Check NBT Persistence:**
   ```bash
   # View entity NBT (in-game)
   /data get entity @e[type=MIIN:npc,limit=1]
   # Should show MIIN_npc_id, MIIN_archetype, etc.
   ```

#### Slow LLM Responses

**Symptoms:**
- 20+ second delays for NPC responses
- Models reloading frequently

**Debug Steps:**

1. **Check Keep-Alive:**
   ```bash
   ollama ps
   # UNTIL column should show future time (not "0 seconds")
   ```

2. **Verify Router Config:**
   ```bash
   cat npc/config/llm_router_config.json | grep keep_alive
   # Should show: "duration": "10m"
   ```

3. **Check Context Size:**
   ```python
   # Add logging to llm_router.py:route_request()
   print(f"[DEBUG] Messages: {len(messages)}", file=sys.stderr)
   # Should be <30 for dialogue
   ```

4. **Monitor Ollama Logs:**
   ```bash
   # If running Ollama manually
   ollama serve
   # Watch for model load messages
   ```

---

## Common Issues

### Issue: "gradlew: Permission denied"

**Solution:**
```bash
chmod +x gradlew
./gradlew build
```

### Issue: "Python command not found"

**Solution:**
```bash
# Try python3
python3 --version

# Create alias (add to ~/.bashrc or ~/.zshrc)
alias python=python3
```

### Issue: "Module not found: npc.scripts.service"

**Solution:**
```python
# Ensure correct path resolution in script
from pathlib import Path
import sys

project_root = Path(__file__).parent.parent.parent
sys.path.insert(0, str(project_root))

# Now import works
from npc.scripts.service import NPCService
```

### Issue: "Port 11434 already in use"

**Solution:**
```bash
# Find process using port
lsof -i :11434          # macOS/Linux
netstat -ano | findstr :11434  # Windows

# Kill process
kill -9 <PID>

# Or use different port in config
```

### Issue: "Mod not loading in Minecraft"

**Solution:**

1. **Check Fabric installed:**
   ```bash
   # Launch Minecraft, check profiles
   # Should see: fabric-loader-1.20.1
   ```

2. **Check mod in mods folder:**
   ```bash
   ls ~/.minecraft/mods/ | grep MIIN
   # Should see: MIIN-1.0.0.jar
   ```

3. **Check Minecraft version:**
   ```bash
   # Mod requires 1.20.1
   # Check build.gradle.kts: minecraft_version=1.20.1
   ```

4. **Check logs for errors:**
   ```bash
   tail -100 ~/.minecraft/logs/latest.log | grep -i error
   ```

### Issue: "JSON corrupted after crash"

**Solution:**

1. **Restore from backup:**
   ```bash
   cp npc/config/memory_backup.json npc/config/memory.json
   ```

2. **Implement atomic writes** (see CONTRIBUTING.md):
   ```python
   import tempfile, os
   temp_path = f"{file_path}.tmp"
   with open(temp_path, 'w') as f:
       json.dump(data, f)
   os.replace(temp_path, file_path)
   ```

---

## Performance Profiling

### Measure LLM Response Time

```python
# Add to npc/scripts/service.py

import time

def generate_npc_response(...):
    start = time.time()

    # ... existing code ...

    response, error = self.llm_router.route_request(...)

    elapsed = time.time() - start
    print(f"[PERF] Response generated in {elapsed:.2f}s", file=sys.stderr)

    return response
```

### Measure Token Usage

```python
# Add to llm_router.py

def route_request(...):
    # Count tokens (rough estimate)
    total_chars = sum(len(m['content']) for m in messages)
    estimated_tokens = total_chars / 4  # ~4 chars per token

    print(f"[PERF] Estimated tokens: {estimated_tokens}", file=sys.stderr)
```

### Profile Python Scripts

```bash
# Use cProfile
python -m cProfile -s cumtime npc/scripts/talk.py rowan vDakota "Hello"

# Output shows time spent in each function
```

---

## Continuous Integration (Future)

**Planned GitHub Actions:**

```yaml
# .github/workflows/build.yml

name: Build and Test

on: [push, pull_request]

jobs:
  build-typescript:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-node@v3
        with:
          node-version: '18'
      - run: npm install
      - run: npm run build

  build-kotlin:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
      - run: cd MIIN && ./gradlew build

  test-python:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-python@v4
        with:
          python-version: '3.10'
      - run: pip install requests
      - run: python -m unittest discover tests/
```

---

## Additional Resources

- [ARCHITECTURE.md](ARCHITECTURE.md) - System architecture
- [CONTRIBUTING.md](../CONTRIBUTING.md) - Contribution guidelines
- [SYSTEMS_ROADMAP.md](SYSTEMS_ROADMAP.md) - Development roadmap
- [QUICKSTART.md](../QUICKSTART.md) - Quick setup guide

---

**Last Updated**: 2025-01-21
**Contributors**: Dakota V, Claude (AI Assistant)
**License**: MIT
