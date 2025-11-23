# Prerequisites and System Requirements

**Version**: 1.0
**Last Updated**: 2025-01-21

---

## Table of Contents

- [System Requirements](#system-requirements)
- [Required Software](#required-software)
- [Optional Software](#optional-software)
- [Installation Instructions](#installation-instructions)
- [Verification](#verification)
- [Troubleshooting](#troubleshooting)

---

## System Requirements

### Minimum Requirements

**Hardware:**
- **CPU**: Intel i5 or AMD Ryzen 5 (4 cores)
- **RAM**: 8 GB
  - 4 GB for Minecraft
  - 2 GB for Ollama (if using local LLMs)
  - 2 GB for system + development
- **Storage**: 10 GB free space
  - 5 GB for Minecraft + mods
  - 3 GB for Ollama models (if using)
  - 2 GB for project + dependencies
- **OS**: Windows 10/11, macOS 11+, or Linux (Ubuntu 20.04+)

### Recommended Requirements

**Hardware:**
- **CPU**: Intel i7 or AMD Ryzen 7 (8 cores)
- **RAM**: 16 GB
  - 6 GB for Minecraft
  - 6 GB for Ollama (multiple models loaded)
  - 4 GB for system + development
- **Storage**: 20 GB free space
- **GPU**: Not required, but helps with Ollama inference (NVIDIA/AMD)

**OS-Specific Notes:**

**Windows:**
- Windows 10 (build 19041+) or Windows 11
- PowerShell 5.1+ or PowerShell Core 7+

**macOS:**
- macOS 11 (Big Sur) or newer
- Apple Silicon (M1/M2) or Intel

**Linux:**
- Ubuntu 20.04+, Debian 11+, Fedora 35+, or equivalent
- X11 or Wayland display server

---

## Required Software

### 1. Minecraft 1.20.1

**Why**: Core game that the mod runs on

**Installation:**

**Windows/macOS/Linux:**
1. Purchase Minecraft Java Edition from https://www.minecraft.net/
2. Install Minecraft Launcher
3. Launch once to download 1.20.1

**Verify:**
```bash
# Launch Minecraft Launcher
# Click "Installations" tab
# Should see version 1.20.1 available
```

### 2. Fabric Mod Loader 0.15.11+

**Why**: Mod loader required for Kotlin mod

**Installation:**

1. Download Fabric Installer:
   - https://fabricmc.net/use/installer/

2. Run installer:
   ```bash
   java -jar fabric-installer-1.0.0.jar
   ```

3. Select:
   - **Minecraft Version**: 1.20.1
   - **Loader Version**: 0.15.11 (or latest)
   - **Install Location**: Default (.minecraft)

4. Click "Install"

**Verify:**
```bash
# Launch Minecraft
# Profile dropdown should show: "fabric-loader-1.20.1"
```

### 3. Java 17

**Why**: Required for Minecraft 1.20.1 and Kotlin mod compilation

**Installation:**

**macOS (Homebrew):**
```bash
brew install openjdk@17
```

**Ubuntu/Debian:**
```bash
sudo apt-get update
sudo apt-get install openjdk-17-jdk
```

**Windows:**
1. Download from https://adoptium.net/
2. Select: OpenJDK 17 (LTS)
3. Run installer

**Verify:**
```bash
java --version
# Output: openjdk 17.0.x
```

**Note**: If multiple Java versions installed:
```bash
# Set JAVA_HOME
export JAVA_HOME=/path/to/java17  # macOS/Linux
set JAVA_HOME=C:\path\to\java17   # Windows

# Or use jEnv (macOS/Linux)
jenv global 17
```

### 4. Node.js 18+

**Why**: Required for MCP server (TypeScript)

**Installation:**

**macOS (Homebrew):**
```bash
brew install node@18
```

**Ubuntu/Debian:**
```bash
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt-get install -y nodejs
```

**Windows:**
1. Download from https://nodejs.org/
2. Select: 18.x LTS
3. Run installer

**Verify:**
```bash
node --version  # v18.x.x or v20.x.x
npm --version   # 9.x.x or 10.x.x
```

### 5. Python 3.10+

**Why**: Backend services, NPC/dialogue/merchant systems

**Installation:**

**macOS (Homebrew):**
```bash
brew install python@3.10
```

**Ubuntu/Debian:**
```bash
sudo apt-get install python3.10 python3.10-venv python3-pip
```

**Windows:**
1. Download from https://www.python.org/downloads/
2. Select: Python 3.10+ (or 3.11, 3.12)
3. Run installer
4. **Check**: "Add Python to PATH"

**Verify:**
```bash
python --version   # or python3 --version
# Python 3.10.x or higher

pip --version      # or pip3 --version
# pip 22.x.x or higher
```

### 6. Git

**Why**: Version control, required for cloning repository

**Installation:**

**macOS:**
```bash
xcode-select --install  # Installs Git + dev tools
```

**Ubuntu/Debian:**
```bash
sudo apt-get install git
```

**Windows:**
1. Download from https://git-scm.com/download/win
2. Run installer (use defaults)

**Verify:**
```bash
git --version
# git version 2.x.x
```

---

## Optional Software

### 1. Ollama (Local LLM Inference)

**Why**: Runs LLMs locally for NPC dialogue (llama3.2, llama3.1, deepseek-r1)

**Pros:**
- 100% local, no cloud API costs
- Full privacy, no data sent to third parties
- Fast responses with keep-alive

**Cons:**
- Requires 4-16 GB RAM depending on models
- CPU/GPU intensive during inference

**Installation:**

**macOS/Linux:**
```bash
curl https://ollama.ai/install.sh | sh
```

**Windows:**
1. Download from https://ollama.ai/download
2. Run installer

**Verify:**
```bash
ollama --version
# ollama version 0.x.x
```

**Pull Models:**
```bash
# Fast conversational model (3B, ~2GB RAM)
ollama pull llama3.2:latest

# Creative reasoning model (8B, ~6GB RAM)
ollama pull llama3.1:8b

# Analytical code model (8B, ~6GB RAM)
ollama pull deepseek-r1:latest
```

**Start Ollama:**
```bash
ollama serve
# Runs on http://localhost:11434
```

**Note**: If not using Ollama, you'll need to configure cloud LLM providers (Gemini, Claude) - see Phase 6 in [SYSTEMS_ROADMAP.md](docs/SYSTEMS_ROADMAP.md).

### 2. VSCode (Recommended IDE)

**Why**: Best IDE for TypeScript, Python, and Kotlin development

**Installation:**
1. Download from https://code.visualstudio.com/
2. Install recommended extensions:
   - **Kotlin Language** by fwcd
   - **Python** by Microsoft
   - **TypeScript and JavaScript Language Features** (built-in)
   - **Fabric Dev Tools** (optional, for Minecraft modding)

**Alternative IDEs:**
- **IntelliJ IDEA** (great for Kotlin/Java)
- **PyCharm** (great for Python)
- **Vim/Neovim** (if you prefer)

### 3. Claude Desktop (MCP Client)

**Why**: Provides UI for interacting with MCP server tools

**Installation:**
1. Download from https://claude.ai/
2. Follow setup instructions for MCP server configuration

**Note**: Not required for development, but useful for testing MCP tools.

### 4. Docker (For Isolated Testing)

**Why**: Test in clean environment, isolate dependencies

**Installation:**
1. Download from https://www.docker.com/products/docker-desktop/
2. Follow installation instructions for your OS

**Note**: Advanced use case, not required for basic development.

---

## Installation Instructions

### Quick Start (All Platforms)

```bash
# 1. Clone repository
git clone https://github.com/dakotav0/MIIN.git
cd MIIN

# 2. Install Node.js dependencies
npm install

# 3. Install Python dependencies
python -m venv venv
source venv/bin/activate      # macOS/Linux
venv\Scripts\activate         # Windows
pip install requests

# 4. Build TypeScript MCP server
npm run build

# 5. Build Kotlin Fabric mod
cd MIIN
./gradlew build               # macOS/Linux
gradlew.bat build             # Windows

# 6. Install mod to Minecraft
cp build/libs/MIIN-1.0.0.jar ~/.minecraft/mods/
# Windows: copy build\libs\MIIN-1.0.0.jar %APPDATA%\.minecraft\mods\

# 7. Start Ollama (optional)
ollama serve

# 8. Done! See QUICKSTART.md for testing
```

### Platform-Specific Notes

#### macOS

**Homebrew** highly recommended for package management:
```bash
# Install Homebrew
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# Install all dependencies at once
brew install openjdk@17 node@18 python@3.10 git ollama
```

**Apple Silicon (M1/M2) Notes:**
- Ollama runs natively on ARM64
- Java 17 works via Rosetta 2
- Minecraft runs via Rosetta 2 (slight performance impact)

#### Windows

**Package Manager** options:
- **Chocolatey**: `choco install openjdk17 nodejs python git`
- **winget**: `winget install AdoptOpenJDK.OpenJDK.17 OpenJS.NodeJS Python.Python.3.10 Git.Git`

**Path Environment Variable:**
After installing Java, Node.js, Python:
1. Search "Environment Variables" in Start menu
2. Edit "Path" variable
3. Add installation directories if not already present

**PowerShell Execution Policy:**
```powershell
# If scripts blocked
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

#### Linux

**Ubuntu/Debian:**
```bash
sudo apt-get update
sudo apt-get install -y openjdk-17-jdk nodejs python3.10 python3.10-venv git

# Ollama
curl https://ollama.ai/install.sh | sh
```

**Fedora:**
```bash
sudo dnf install -y java-17-openjdk nodejs python3 git

# Ollama
curl https://ollama.ai/install.sh | sh
```

**Arch Linux:**
```bash
sudo pacman -S jdk17-openjdk nodejs python git

# Ollama (AUR)
yay -S ollama
```

---

## Verification

### Check All Prerequisites

Run this verification script:

```bash
#!/bin/bash
# verify-prerequisites.sh

echo "=== Prerequisite Verification ==="

# Java
echo -n "Java 17: "
if java --version 2>&1 | grep -q "openjdk 17"; then
    echo "✅ $(java --version | head -1)"
else
    echo "❌ Not found or wrong version"
fi

# Node.js
echo -n "Node.js 18+: "
if command -v node &> /dev/null; then
    NODE_VERSION=$(node --version)
    if [[ "$NODE_VERSION" == v1[89]* ]] || [[ "$NODE_VERSION" == v2* ]]; then
        echo "✅ $NODE_VERSION"
    else
        echo "❌ Found $NODE_VERSION, need v18+"
    fi
else
    echo "❌ Not found"
fi

# Python
echo -n "Python 3.10+: "
if command -v python3 &> /dev/null; then
    PYTHON_VERSION=$(python3 --version | awk '{print $2}')
    if [[ "$PYTHON_VERSION" > "3.10" ]] || [[ "$PYTHON_VERSION" == "3.10"* ]]; then
        echo "✅ Python $PYTHON_VERSION"
    else
        echo "❌ Found Python $PYTHON_VERSION, need 3.10+"
    fi
else
    echo "❌ Not found"
fi

# Git
echo -n "Git: "
if command -v git &> /dev/null; then
    echo "✅ $(git --version)"
else
    echo "❌ Not found"
fi

# Ollama (optional)
echo -n "Ollama (optional): "
if command -v ollama &> /dev/null; then
    echo "✅ $(ollama --version)"
else
    echo "⚠️  Not found (optional)"
fi

# Minecraft
echo -n "Minecraft 1.20.1: "
if [ -d "$HOME/.minecraft/versions/1.20.1" ]; then
    echo "✅ Installed"
elif [ -d "$APPDATA/.minecraft/versions/1.20.1" ]; then
    echo "✅ Installed"
else
    echo "❌ Not found"
fi

# Fabric
echo -n "Fabric Loader: "
if [ -d "$HOME/.minecraft/versions/fabric-loader-0.15"* ]; then
    echo "✅ Installed"
elif [ -d "$APPDATA/.minecraft/versions/fabric-loader-0.15"* ]; then
    echo "✅ Installed"
else
    echo "❌ Not found"
fi

echo "=== Verification Complete ==="
```

**Run:**
```bash
chmod +x verify-prerequisites.sh
./verify-prerequisites.sh
```

### Manual Verification

```bash
# Java
java --version
# Expected: openjdk 17.0.x

# Node.js
node --version
npm --version
# Expected: v18.x.x or v20.x.x, npm 9.x.x+

# Python
python --version  # or python3 --version
pip --version     # or pip3 --version
# Expected: Python 3.10.x+, pip 22.x.x+

# Git
git --version
# Expected: git version 2.x.x

# Ollama (optional)
ollama --version
ollama list  # Shows installed models
# Expected: ollama version 0.x.x

# Minecraft
ls ~/.minecraft/versions/1.20.1/        # macOS/Linux
dir %APPDATA%\.minecraft\versions\1.20.1\  # Windows
# Expected: 1.20.1.jar, 1.20.1.json

# Fabric
ls ~/.minecraft/versions/fabric-loader*    # macOS/Linux
dir %APPDATA%\.minecraft\versions\fabric-loader*  # Windows
# Expected: fabric-loader-0.15.11-1.20.1 (or similar)
```

---

## Troubleshooting

### Java Issues

**Problem**: `java: command not found`

**Solution:**
```bash
# macOS
brew install openjdk@17
echo 'export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc

# Ubuntu
sudo apt-get install openjdk-17-jdk
```

**Problem**: Wrong Java version

**Solution:**
```bash
# Check installed versions
/usr/libexec/java_home -V  # macOS
update-alternatives --config java  # Linux

# Set JAVA_HOME
export JAVA_HOME=/path/to/java17
```

### Node.js Issues

**Problem**: `node: command not found`

**Solution:**
```bash
# Use nvm (Node Version Manager)
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.0/install.sh | bash
nvm install 18
nvm use 18
```

**Problem**: Permission errors with `npm install`

**Solution:**
```bash
# Don't use sudo! Fix npm permissions
mkdir ~/.npm-global
npm config set prefix '~/.npm-global'
echo 'export PATH=~/.npm-global/bin:$PATH' >> ~/.bashrc
source ~/.bashrc
```

### Python Issues

**Problem**: `python: command not found`

**Solution:**
```bash
# Use python3 instead
python3 --version

# Or create alias
echo 'alias python=python3' >> ~/.bashrc
echo 'alias pip=pip3' >> ~/.bashrc
source ~/.bashrc
```

**Problem**: `pip: command not found`

**Solution:**
```bash
# Ubuntu/Debian
sudo apt-get install python3-pip

# macOS
python3 -m ensurepip --upgrade
```

### Minecraft Issues

**Problem**: Minecraft won't launch with Fabric

**Solution:**
1. Verify Java 17 installed
2. Reinstall Fabric (use installer)
3. Check logs: `~/.minecraft/logs/latest.log`

**Problem**: Mod not loading

**Solution:**
1. Check mod in `~/.minecraft/mods/` folder
2. Verify Fabric Loader version matches mod requirement
3. Check Minecraft version is 1.20.1
4. Look for errors in logs

### Ollama Issues

**Problem**: `ollama serve` fails to start

**Solution:**
```bash
# Check port 11434 not in use
lsof -i :11434          # macOS/Linux
netstat -ano | findstr :11434  # Windows

# Kill existing process if found
kill -9 <PID>

# Start Ollama
ollama serve
```

**Problem**: Out of memory when loading models

**Solution:**
```bash
# Use smaller models
ollama pull llama3.2:latest  # 3B, ~2GB RAM

# Or close other applications
# Or increase system swap/virtual memory
```

---

## Platform-Specific Prerequisites

### macOS-Specific

**Xcode Command Line Tools:**
```bash
xcode-select --install
```

**Homebrew** (package manager):
```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

### Windows-Specific

**Visual C++ Redistributable:**
- Required for some Node.js packages
- Download from: https://aka.ms/vs/17/release/vc_redist.x64.exe

**Windows Terminal** (recommended):
- Download from Microsoft Store
- Better than cmd.exe for development

### Linux-Specific

**Build essentials:**
```bash
# Ubuntu/Debian
sudo apt-get install build-essential

# Fedora
sudo dnf groupinstall "Development Tools"

# Arch
sudo pacman -S base-devel
```

---

## Next Steps

After verifying all prerequisites:

1. **Read [QUICKSTART.md](QUICKSTART.md)** - 5-minute setup guide
2. **Read [BUILD_AND_TEST.md](docs/BUILD_AND_TEST.md)** - Detailed build instructions
3. **Read [CONTRIBUTING.md](CONTRIBUTING.md)** - Development guidelines
4. **Read [ARCHITECTURE.md](docs/ARCHITECTURE.md)** - System overview

---

## Support

**Issues**: https://github.com/dakotav0/MIIN/issues

**Common Problems**: See [BUILD_AND_TEST.md](docs/BUILD_AND_TEST.md) → Common Issues

---

**Last Updated**: 2025-01-21
**Contributors**: Dakota V, Claude (AI Assistant)
**License**: MIT
