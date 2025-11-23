# Minecraft Intelligence - Quick Start

Get up and running in 5 minutes!

## Prerequisites

- Minecraft 1.20.1 with Fabric mod loader
- Node.js 18+
- Python 3.10+
- MIIN installed and dashboard running

## Step 1: Build the MCP Server (2 minutes)

```bash
cd MIIN
npm install
npm run build
```

## Step 2: Start the HTTP Bridge (1 minute)

```bash
# Option A: From dashboard
# Go to http://localhost:5000/services
# Click "Start" on minecraft-http-bridge

# Option B: From terminal
python MIIN/services/minecraft_http_bridge.py
```

You should see:
```
✅ Minecraft HTTP Bridge ready!
   HTTP Endpoint: http://localhost:5557/mcp/call
```

## Step 3: Install the Fabric Mod (2 minutes)

1. Copy `kotlin-examples/MIINListener.kt` to your mod's source:
   ```bash
   cp kotlin-examples/MIINListener.kt /path/to/your/mod/src/main/kotlin/com/MIIN/listener/
   ```

2. Update `build.gradle.kts` with dependencies from `kotlin-examples/build.gradle.kts`

3. Build your mod:
   ```bash
   cd /path/to/your/mod
   ./gradlew build
   ```

4. Copy the JAR to Minecraft:
   ```bash
   cp build/libs/MIIN-listener-1.0.0.jar ~/.minecraft/mods/
   ```

## Step 4: Test It! (30 seconds)

1. Start Minecraft
2. Join a world
3. Place 10+ blocks to create a small build
4. Wait 30 seconds (build session finalizes)
5. Check `minecraft_events.json` in MIIN root:
   ```bash
   tail minecraft_events.json
   ```

You should see your build event!

## Step 5: Get Creative Insights (30 seconds)

### Via Claude Desktop (if you have MCP configured):

```
Hey Claude, analyze my recent Minecraft builds!
```

### Via Dashboard API:

```bash
curl -X POST http://localhost:5557/mcp/call \
  -H "Content-Type: application/json" \
  -d '{
    "tool": "minecraft_get_insights",
    "arguments": {
      "context": {}
    }
  }'
```

### Via Python:

```python
from MIIN.protocols.minecraft_mcp_bridge import MinecraftMCPBridge

bridge = MinecraftMCPBridge()
bridge.start_server()

# Get insights
insights = bridge.get_insights()
print(insights)

# Suggest palette
palette = bridge.suggest_palette("cozy cabin", ["oak_planks"])
print(palette)
```

## What's Next?

### Analyze Your Building Style

```python
# Detect patterns in your builds
patterns = bridge.detect_patterns(days=7, pattern_type='all')
print(f"Peak building time: {patterns['patterns']['temporal']['peakHour']}:00")
print(f"Favorite blocks: {patterns['patterns']['preferences']['topBlocks']}")
```

### Get Build Suggestions

```python
# Analyze a completed build
analysis = bridge.analyze_build({
    'buildName': 'My Castle',
    'blocks': ['stone_bricks', 'oak_planks', 'iron_bars'],
    'blockCounts': {
        'stone_bricks': 500,
        'oak_planks': 100,
        'iron_bars': 50
    },
    'buildTime': 1800,
    'tags': ['medieval', 'castle']
})

print(f"Themes detected: {analysis['themes']}")
print(f"Complexity score: {analysis['metrics']['complexity']}")
print(f"Suggestions: {analysis['suggestions']}")
```

### Create Custom Palettes

```python
# Get complementary blocks for your theme
palette = bridge.suggest_palette(
    theme="futuristic city",
    existing_blocks=["quartz_block", "glass"],
    palette_size=15
)

for block_info in palette['palette']:
    print(f"  {block_info['block']} - {block_info['reason']}")
```

## Troubleshooting

**HTTP bridge won't start:**
```bash
# Check if port 5557 is in use
lsof -i :5557  # macOS/Linux
netstat -ano | findstr :5557  # Windows
```

**Mod not tracking events:**
```bash
# Check Minecraft logs for:
# "MIIN Listener initialized successfully!"

# Enable debug logging in MIINListener.kt:
# Change LoggerFactory.getLogger level
```

**No intelligence services:**
```bash
# Start from dashboard:
http://localhost:5000/services

# Or manually:
python music_intelligence/music_intelligence_service.py  # Port 5555
python unified_intelligence_api.py  # Port 5556
```

## Configuration

### Adjust Build Session Timeout

In `MIINListener.kt`:
```kotlin
fun shouldFinalize(): Boolean {
    val inactiveTime = System.currentTimeMillis() - lastActivity
    return inactiveTime > 60_000 && blockCount > 0  // 60 seconds instead of 30
}
```

### Change HTTP Endpoint

In `MIINListener.kt`:
```kotlin
private const val MCP_ENDPOINT = "http://your-server:5557/mcp/call"
```

### Adjust Event Storage Limit

In `src/event-tracker.ts`:
```typescript
private maxEvents: number = 50000;  // Store more events
```

## Next Steps

- Read `README.md` for full documentation
- Explore the intelligence bridge in `src/intelligence-bridge.ts`
- Customize theme mappings for your favorite building styles
- Check out the dashboard Intelligence page for visualizations

Enjoy building with AI-powered insights! 🎮🧠
