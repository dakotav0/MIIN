#!/usr/bin/env python3
"""
Minecraft HTTP Bridge Service

Runs the HTTP bridge for Kotlin mod to communicate with Minecraft MCP server.
Listens on port 5557 by default.
"""

import sys
from pathlib import Path

# Add parent directory to path
sys.path.insert(0, str(Path(__file__).parent.parent.parent))

from mcp_bridge import MinecraftMCPBridge, create_http_bridge


def main():
    """Run the HTTP bridge server"""
    print("=" * 60)
    print("🎮 Minecraft HTTP Bridge Starting...")
    print("=" * 60)

    # Initialize bridge
    bridge = MinecraftMCPBridge()

    if not bridge.mcp_available:
        print("❌ Minecraft MCP server not found!")
        print(f"   Expected at: {bridge.mcp_path}")
        print("\nSetup instructions:")
        print("   cd MIIN")
        print("   npm install")
        print("   npm run build")
        sys.exit(1)

    # Start MCP server
    print("\n🚀 Starting Minecraft MCP server...")
    if not bridge.start_server():
        print("❌ Failed to start MCP server")
        sys.exit(1)

    # Create and run HTTP bridge
    print("\n🌐 Starting HTTP bridge on port 5557...")
    app = create_http_bridge(bridge, port=5557)

    print("\n✅ Minecraft HTTP Bridge ready!")
    print("   HTTP Endpoint: http://localhost:5557/mcp/call")
    print("   Health Check:  http://localhost:5557/mcp/health")
    print("\nPress Ctrl+C to stop")
    print()

    try:
        app.run(host='0.0.0.0', port=5557, debug=False)
    except KeyboardInterrupt:
        print("\n\n🛑 Shutting down...")
        bridge.stop_server()
        print("✅ Minecraft HTTP Bridge stopped")


if __name__ == '__main__':
    main()
