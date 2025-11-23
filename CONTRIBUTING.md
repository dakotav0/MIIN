# Contributing to MIIN Minecraft MCP

First off, thank you for considering contributing to MIIN Minecraft MCP! It's people like you that make this project possible. This document provides guidelines for contributing to the project.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [How Can I Contribute?](#how-can-i-contribute)
- [Development Setup](#development-setup)
- [Pull Request Process](#pull-request-process)
- [Coding Standards](#coding-standards)
- [Testing Guidelines](#testing-guidelines)
- [Commit Message Guidelines](#commit-message-guidelines)

---

## Code of Conduct

This project adheres to a code of conduct that all contributors are expected to follow:

### Our Pledge

- **Be respectful**: Treat all contributors with respect and kindness
- **Be inclusive**: Welcome contributors of all backgrounds and experience levels
- **Be collaborative**: Work together to improve the project
- **Be constructive**: Provide helpful feedback and accept criticism gracefully
- **Be patient**: Remember that everyone was a beginner once

### Our Standards

**Examples of behavior that contributes to a positive environment:**
- Using welcoming and inclusive language
- Being respectful of differing viewpoints
- Gracefully accepting constructive criticism
- Focusing on what's best for the community
- Showing empathy towards other community members

**Examples of unacceptable behavior:**
- Harassment, trolling, or insulting/derogatory comments
- Public or private harassment
- Publishing others' private information without permission
- Other conduct which could reasonably be considered inappropriate

---

## How Can I Contribute?

### Reporting Bugs

Before creating bug reports, please check existing issues to avoid duplicates. When creating a bug report, include:

**Bug Report Template:**
```markdown
**Description**
A clear description of what the bug is.

**To Reproduce**
Steps to reproduce the behavior:
1. Go to '...'
2. Click on '...'
3. See error

**Expected Behavior**
What you expected to happen.

**Actual Behavior**
What actually happened.

**Environment**
- Minecraft Version: [e.g., 1.20.1]
- Fabric Loader Version: [e.g., 0.15.11]
- Mod Version: [e.g., 1.0.0]
- Python Version: [e.g., 3.10.8]
- Node.js Version: [e.g., 18.16.0]
- OS: [e.g., Windows 11, macOS 13.4, Ubuntu 22.04]

**Screenshots/Logs**
If applicable, add screenshots or relevant log snippets.

**Additional Context**
Any other context about the problem.
```

### Suggesting Enhancements

Enhancement suggestions are tracked as GitHub issues. When creating an enhancement suggestion, include:

**Enhancement Template:**
```markdown
**Is your feature request related to a problem?**
A clear description of the problem. Ex. I'm frustrated when [...]

**Describe the solution you'd like**
A clear description of what you want to happen.

**Describe alternatives you've considered**
Alternative solutions or features you've considered.

**Additional context**
Any other context, mockups, or examples.
```

### Contributing Code

1. **Fork the repository**
2. **Create a feature branch** (`git checkout -b feature/amazing-feature`)
3. **Make your changes** (follow coding standards below)
4. **Test your changes** (see Testing Guidelines)
5. **Commit your changes** (follow commit message guidelines)
6. **Push to your fork** (`git push origin feature/amazing-feature`)
7. **Open a Pull Request**

### Areas Needing Help

Check issues labeled with:
- `good first issue` - Great for newcomers
- `help wanted` - We'd love community input
- `documentation` - Improve docs
- `enhancement` - New features
- `bug` - Something isn't working

---

## Development Setup

### Prerequisites

**Required:**
- **Minecraft 1.20.1** with Fabric mod loader
- **Node.js 18+** (for MCP server)
- **Python 3.10+** (for backend services)
- **Java 17** (for Kotlin mod compilation)
- **Git** for version control

**Optional:**
- **Ollama** (for local LLM integration)
- **VSCode** with Kotlin and TypeScript extensions

### Initial Setup

1. **Clone your fork:**
   ```bash
   git clone https://github.com/dakotav0/MIIN.git
   cd MIIN
   ```

2. **Install dependencies:**

   **MCP Server (TypeScript):**
   ```bash
   npm install
   npm run build
   ```

   **Python Backend:**
   ```bash
   # Create virtual environment (recommended)
   python -m venv venv
   source venv/bin/activate  # Windows: venv\Scripts\activate

   # Install dependencies
   pip install requests  # Core dependency
   ```

3. **Build the Fabric Mod:**
   ```bash
   cd MIIN
   ./gradlew build
   ```

4. **Verify installation:**
   ```bash
   # Test TypeScript compilation
   npm run build

   # Test Python imports
   python -c "import sys; print(sys.version)"

   # Test Kotlin build
   cd MIIN && ./gradlew tasks
   ```

### Project Structure

```
MIIN/
├── src/                      # MCP server (TypeScript)
│   ├── index.ts             # Main entry point
│   └── tools/               # MCP tool handlers
├── MIIN/        # Fabric mod (Kotlin)
│   └── src/main/kotlin/     # Mod source code
├── npc/                     # NPC system
│   ├── scripts/             # Python services
│   ├── config/              # NPC configurations
│   └── merchant/            # Trade system
├── dialogue/                # Dialogue system
├── party/                   # Party management
├── lore/                    # Lore discovery
├── events/                  # Event tracking
├── factions/                # Faction system
├── game_state/              # World state
└── docs/                    # Documentation
```

---

## Pull Request Process

### Before Submitting

1. **Update documentation** if you changed APIs or added features
2. **Add/update tests** for your changes
3. **Run tests** to ensure nothing broke
4. **Update CHANGELOG.md** with your changes (if exists)
5. **Follow commit message guidelines** (see below)

### PR Checklist

- [ ] Code follows the project's coding standards
- [ ] Self-review of code completed
- [ ] Comments added for complex/non-obvious code
- [ ] Documentation updated (README, inline comments)
- [ ] No new warnings or errors introduced
- [ ] Tests added/updated and passing
- [ ] Commit messages follow guidelines
- [ ] Branch is up-to-date with main

### PR Title Format

```
<type>(<scope>): <short description>

Examples:
feat(npc): add reputation-based merchant pricing
fix(dialogue): prevent meta-awareness in NPC responses
docs(readme): update installation instructions
refactor(router): simplify context optimization logic
```

### PR Description Template

```markdown
## Description
Brief description of changes and motivation.

## Type of Change
- [ ] Bug fix (non-breaking change fixing an issue)
- [ ] New feature (non-breaking change adding functionality)
- [ ] Breaking change (fix or feature causing existing functionality to break)
- [ ] Documentation update

## How Has This Been Tested?
Describe the tests you ran:
- [ ] Unit tests
- [ ] Integration tests
- [ ] Manual testing in Minecraft

**Test Configuration:**
- Minecraft version:
- Fabric version:
- Operating System:

## Screenshots (if applicable)
Add screenshots for UI changes.

## Checklist
- [ ] My code follows the project's style guidelines
- [ ] I have performed a self-review
- [ ] I have commented my code where needed
- [ ] I have updated documentation
- [ ] My changes generate no new warnings
- [ ] I have added tests that prove my fix/feature works
- [ ] New and existing tests pass locally
```

### Review Process

1. **Automated checks** must pass (linting, build, tests)
2. **At least one maintainer** must review
3. **All feedback addressed** before merging
4. **Squash and merge** for clean history (usually)

---

## Coding Standards

### TypeScript (MCP Server)

**Style:**
- **2 spaces** for indentation
- **Semicolons** required
- **Single quotes** for strings
- **Interfaces** for data structures
- **Async/await** for promises

**Example:**
```typescript
// Good
export async function handleNpcTalk(
  npc: string,
  player: string,
  message: string
): Promise<NpcResponse> {
  const escapedMessage = message.replace(/"/g, '\\"');
  const result = await exec(`python "${scriptPath}" "${npc}" "${player}" "${escapedMessage}"`);
  return JSON.parse(result.stdout);
}

// Bad
function handleNpcTalk(npc, player, message) {
  let result = exec('python script.py ' + npc)  // No types, no escaping
  return result
}
```

**Naming Conventions:**
- **camelCase** for variables and functions
- **PascalCase** for classes and interfaces
- **UPPER_SNAKE_CASE** for constants

### Python (Backend Services)

**Style:**
- **4 spaces** for indentation (PEP 8)
- **Type hints** for function signatures
- **Docstrings** for all public functions
- **pathlib.Path** for file paths

**Example:**
```python
# Good
def generate_npc_response(
    npc_id: str,
    player_name: str,
    message: str,
    context: Dict[str, Any] = None
) -> str:
    """
    Generate NPC response using LLM

    Args:
        npc_id: NPC identifier
        player_name: Player's name
        message: Player's message
        context: Optional context dict

    Returns:
        NPC response text
    """
    npc = self.npcs.get(npc_id)
    if not npc:
        return f"[Error: NPC '{npc_id}' not found]"

    # ... implementation

# Bad
def gen_resp(n, p, m):  # No types, unclear names
    npc = self.npcs[n]  # No error handling
    return npc.respond(m)
```

**Naming Conventions:**
- **snake_case** for variables and functions
- **PascalCase** for classes
- **UPPER_SNAKE_CASE** for constants

### Kotlin (Fabric Mod)

**Style:**
- **4 spaces** for indentation
- **Descriptive names** for clarity
- **Null safety** enforced
- **Async operations** for HTTP calls

**Example:**
```kotlin
// Good
fun sendPlayerStateEvent(player: ServerPlayerEntity) {
    CompletableFuture.runAsync {
        try {
            val payload = buildPlayerStatePayload(player)
            httpClient.post(MCP_ENDPOINT, payload)
        } catch (e: Exception) {
            logger.error("Failed to send player state: ${e.message}")
        }
    }
}

// Bad
fun send(p) {  // Unclear name, blocking call
    httpClient.post(URL, p)  // No error handling
}
```

### JSON Configuration Files

- **2 spaces** indentation
- **Sorted keys** (where order doesn't matter)
- **Comments** via separate `_comment` fields (JSON doesn't support comments)

**Example:**
```json
{
  "_comment": "NPC configuration for Marina the Fisher",
  "id": "marina",
  "name": "Marina",
  "personality": "Patient, superstitious fisher who values tradition",
  "faction": "fishers_lodge",
  "location": {
    "x": 50,
    "y": 64,
    "z": -30
  }
}
```

---

## Testing Guidelines

### TypeScript Tests

Currently manual testing is primary. Contributions adding automated tests welcome!

**Manual Testing:**
1. Build: `npm run build` (should succeed)
2. Run MCP inspector: `npm run inspect`
3. Test each tool manually

### Python Tests

**Unit Tests** (if adding):
```python
import unittest
from npc.scripts.service import NPCService

class TestNPCService(unittest.TestCase):
    def setUp(self):
        self.service = NPCService()

    def test_npc_memory(self):
        """Test memory storage and retrieval"""
        self.service.add_to_memory("test_uuid", "player", "user", "Hello")
        memory = self.service.get_npc_memory("test_uuid", "player")
        self.assertEqual(len(memory), 1)
        self.assertEqual(memory[0]['content'], "Hello")
```

**Run tests:**
```bash
python -m unittest discover tests/
```

### Integration Testing

Test in actual Minecraft environment:

1. **Build and install mod:**
   ```bash
   cd MIIN
   ./gradlew build
   cp build/libs/*.jar ~/.minecraft/mods/
   ```

2. **Start backend services:**
   ```bash
   python npc/scripts/service.py  # Or via dashboard
   ```

3. **Test in-game:**
   - Talk to NPCs
   - Check dialogue responses
   - Verify memory persistence
   - Test merchant trades

4. **Verify logs:**
   - Check `MIIN/runtimebuglog.txt`
   - Check Python service stderr output
   - Check `npc/config/memory.json` for saved conversations

---

## Commit Message Guidelines

We follow the **Conventional Commits** specification.

### Format

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Types

- **feat**: New feature
- **fix**: Bug fix
- **docs**: Documentation only
- **style**: Formatting, missing semicolons, etc (no code change)
- **refactor**: Code change that neither fixes a bug nor adds a feature
- **perf**: Performance improvement
- **test**: Adding or updating tests
- **chore**: Maintenance tasks, dependency updates

### Scope

Optional, specifies what part of codebase:
- `npc` - NPC system
- `dialogue` - Dialogue system
- `merchant` - Trade/merchant system
- `router` - LLM router
- `mod` - Kotlin Fabric mod
- `mcp` - MCP server
- `docs` - Documentation

### Examples

```
feat(npc): add UUID-based persistence system

Replaces string-based npc_id keys with entity UUID for proper instance tracking.
This prevents memory collisions when multiple NPCs share the same archetype.

- Add uuid_manager.py with SQLite registry
- Update memory.json to use UUID:player keys
- Implement NBT persistence in MIINNpcEntity.kt

Closes #123
```

```
fix(dialogue): prevent meta-awareness in NPC responses

Added sanitization filter to remove AI self-references and system prompt leakage.

- Filters phrases like "As an AI" and "According to my training"
- Removes bracketed meta-commentary
- Strengthened system prompts with explicit directives

Fixes #45
```

```
docs(readme): update installation instructions

Clarified Python dependency installation and added troubleshooting section.
```

---

## Common Pitfalls to Avoid

### 1. Shell Escaping Issues

**Problem:** Single-quote escaping causes message truncation

**Solution:** Use double-quote escaping
```typescript
// BAD
const escaped = message.replace(/'/g, "'\\''");
await exec(`python script.py '${escaped}'`);

// GOOD
const escaped = message.replace(/"/g, '\\"');
await exec(`python script.py "${escaped}"`);
```

### 2. Thread Blocking in Kotlin

**Problem:** HTTP calls block game thread

**Solution:** Use async operations
```kotlin
// BAD
val response = httpClient.post(url, payload)  // Blocks!

// GOOD
CompletableFuture.runAsync {
    val response = httpClient.post(url, payload)
}
```

### 3. JSON Corruption

**Problem:** Non-atomic writes can corrupt JSON files

**Solution:** Use atomic writes (temp file + rename)
```python
# BAD
with open(path, 'w') as f:
    json.dump(data, f)  # Can corrupt if interrupted

# GOOD
temp_path = f"{path}.tmp"
with open(temp_path, 'w') as f:
    json.dump(data, f)
os.replace(temp_path, path)  # Atomic operation
```

### 4. Hardcoded Paths

**Problem:** Paths break on different systems

**Solution:** Use Path resolution
```python
# BAD
path = "npc/config/npcs.json"  # Breaks on Windows

# GOOD
from pathlib import Path
root = Path(__file__).parent.parent
path = root / 'npc' / 'config' / 'npcs.json'
```

---

## Getting Help

- **Documentation:** Check `/docs` directory
- **GitHub Issues:** Search existing issues or create new one
- **Discussions:** Use GitHub Discussions for questions
- **Discord:** [Link if you have a server]

---

## Recognition

Contributors will be:
- Listed in `CONTRIBUTORS.md` (if we create one)
- Credited in release notes
- Mentioned in commit messages (Co-Authored-By)

---

## License

By contributing, you agree that your contributions will be licensed under the MIT License.

---

**Thank you for contributing to MIIN Minecraft MCP! Your efforts help create a better experience for the Minecraft and AI communities.** 🎮🤖
