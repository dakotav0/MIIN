#!/usr/bin/env python3
"""
Minecraft MCP Bridge - Connects Minecraft MCP Server to MIIN Intelligence

Similar to music_mcp_bridge.py, but for Minecraft events and analysis.
"""

import json
import subprocess
import threading
import time
from pathlib import Path
from typing import Dict, List, Optional
from flask import Flask, request, jsonify


class MinecraftMCPBridge:
    """
    Bridge for Minecraft MCP Server
    - Singleton pattern to prevent multiple server instances
    - Proper MCP initialization handshake
    - Thread-safe tool calls
    - HTTP endpoint for Kotlin mod integration
    """

    _instance = None
    _lock = threading.Lock()
    _initialized = False

    def __new__(cls, *args, **kwargs):
        """Singleton pattern - only create one instance"""
        if cls._instance is None:
            with cls._lock:
                if cls._instance is None:
                    cls._instance = super().__new__(cls)
        return cls._instance

    def __init__(self, mcp_path: str = None):
        """
        Initialize the bridge with path to Minecraft MCP server
        (Only runs once due to singleton pattern)

        Args:
            mcp_path: Path to MIIN/dist/index.js
        """
        # Only initialize once
        if self._initialized:
            return

        minecraft_mcp_dir = Path(__file__).parent.parent

        self.mcp_path = mcp_path or str(minecraft_mcp_dir / 'dist' / 'index.js')
        self.mcp_available = Path(self.mcp_path).exists()

        self.process = None
        self._call_lock = threading.Lock()  # Lock for thread-safe MCP calls
        self._mcp_initialized = False  # Track MCP initialization state

        print(f"[MINECRAFT] Minecraft MCP Bridge initialized:")
        print(f"   MCP Server: {'✓ Available' if self.mcp_available else '✗ Not found'}")
        print(f"   Path: {self.mcp_path}")

        self._initialized = True

    def is_server_alive(self) -> bool:
        """Check if the MCP server process is still running"""
        if not self.process:
            return False
        return self.process.poll() is None

    def start_server(self, force_restart: bool = False) -> bool:
        """
        Start the Minecraft MCP server with proper initialization

        Args:
            force_restart: If True, stop and restart even if already running
        """
        if not self.mcp_available:
            print("[WARN] Minecraft MCP server not available")
            return False

        # Check if already running and healthy
        if not force_restart and self.is_server_alive() and self._mcp_initialized:
            return True  # Already running and initialized

        # Stop existing process if any
        if self.process:
            self.stop_server()

        try:
            self.process = subprocess.Popen(
                ['node', self.mcp_path],
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                bufsize=1  # Line buffered
            )

            # Start stderr reader thread to capture debug logs
            def read_stderr():
                if self.process and self.process.stderr:
                    for line in self.process.stderr:
                        if line.strip():
                            print(f"[MCP stderr] {line.rstrip()}", flush=True)

            stderr_thread = threading.Thread(target=read_stderr, daemon=True)
            stderr_thread.start()

            # Give server time to initialize
            time.sleep(0.5)

            if not self.is_server_alive():
                print(f"[ERROR] Minecraft MCP server died immediately after start")
                return False

            # Send MCP initialization request
            init_request = {
                'jsonrpc': '2.0',
                'id': 0,
                'method': 'initialize',
                'params': {
                    'protocolVersion': '2024-11-05',
                    'capabilities': {},
                    'clientInfo': {
                        'name': 'MIIN-Minecraft-Intelligence',
                        'version': '1.0.0'
                    }
                }
            }

            try:
                self.process.stdin.write(json.dumps(init_request) + '\n')
                self.process.stdin.flush()

                # Wait for initialization response
                init_timeout = 5
                start_time = time.time()
                init_response_line = None

                while time.time() - start_time < init_timeout:
                    if self.process.stdout.readable():
                        init_response_line = self.process.stdout.readline()
                        if init_response_line:
                            break
                    time.sleep(0.1)

                if init_response_line:
                    response_data = json.loads(init_response_line)
                    if 'error' in response_data:
                        print(f"[ERROR] MCP initialization failed: {response_data['error']}")
                        self.stop_server()
                        return False
                    elif 'result' in response_data:
                        self._mcp_initialized = True
                        server_name = response_data['result'].get('serverInfo', {}).get('name', 'Unknown')
                        print(f"✓ MCP initialized: {server_name}")
                else:
                    print(f"[WARN] No initialization response (timeout after {init_timeout}s)")
                    self._mcp_initialized = True  # Assume success

            except Exception as e:
                print(f"[WARN] MCP initialization warning: {e}")
                self._mcp_initialized = True

            print(f"✓ Started Minecraft MCP server (PID: {self.process.pid})")
            return True

        except Exception as e:
            print(f"[ERROR] Failed to start Minecraft MCP server: {e}")
            return False

    def call_tool(self, tool_name: str, params: Dict, timeout: int = 120) -> Dict:
        """
        Call a tool on the Minecraft MCP server (thread-safe)

        Args:
            tool_name: Name of the MCP tool to call
            params: Parameters for the tool
            timeout: Timeout in seconds

        Returns:
            Dict with tool result or error
        """
        with self._call_lock:  # Ensure only one call at a time
            # Ensure server is running and initialized
            if not self.is_server_alive() or not self._mcp_initialized:
                if not self.start_server():
                    return {'error': 'Failed to start MCP server'}

            # MCP protocol call
            request = {
                'jsonrpc': '2.0',
                'id': int(time.time() * 1000),  # Unique ID per request
                'method': 'tools/call',
                'params': {
                    'name': tool_name,
                    'arguments': params
                }
            }

            try:
                # Write request
                self.process.stdin.write(json.dumps(request) + '\n')
                self.process.stdin.flush()

                # Read response with timeout
                result = {'error': f'Timeout waiting for MCP response (>{timeout}s)'}

                def read_response():
                    try:
                        line = self.process.stdout.readline()
                        if line:
                            result['data'] = json.loads(line)
                            result.pop('error', None)
                    except Exception as e:
                        result['error'] = f'Read error: {e}'

                thread = threading.Thread(target=read_response, daemon=True)
                thread.start()
                thread.join(timeout=timeout)

                if 'data' in result:
                    return result['data']
                else:
                    print(f"[WARN] MCP call to '{tool_name}' timed out after {timeout}s")
                    print(f"   Params: {params}")

                    if not self.is_server_alive():
                        print(f"   Server died - will restart on next call")
                        self._mcp_initialized = False

                    return {'error': result.get('error', 'Timeout')}

            except BrokenPipeError:
                print(f"[ERROR] MCP server connection broken - server may have crashed")
                self.process = None
                self._mcp_initialized = False
                return {'error': 'Server connection lost'}

            except Exception as e:
                print(f"[ERROR] MCP call failed: {e}")
                return {'error': str(e)}

    # === High-level methods ===

    def analyze_build(self, build_data: Dict) -> Dict:
        """Analyze a build using creative intelligence"""
        return self.call_tool('minecraft_analyze_build', build_data)

    def suggest_palette(self, theme: str, existing_blocks: List[str] = None, palette_size: int = 10) -> Dict:
        """Get block palette suggestions"""
        return self.call_tool('minecraft_suggest_palette', {
            'theme': theme,
            'existingBlocks': existing_blocks or [],
            'paletteSize': palette_size
        })

    def detect_patterns(self, days: int = 30, pattern_type: str = 'all') -> Dict:
        """Detect patterns in player behavior"""
        return self.call_tool('minecraft_detect_patterns', {
            'days': days,
            'patternType': pattern_type
        })

    def get_insights(self, context: Dict = None) -> Dict:
        """Get proactive insights and suggestions"""
        return self.call_tool('minecraft_get_insights', {
            'context': context or {}
        })

    def track_event(self, event_type: str, data: Dict) -> Dict:
        """Track a Minecraft event"""
        return self.call_tool('minecraft_track_event', {
            'eventType': event_type,
            'data': data
        })

    def stop_server(self):
        """Stop the MCP server gracefully"""
        if self.process and self.is_server_alive():
            try:
                self.process.terminate()
                self.process.wait(timeout=2)
            except subprocess.TimeoutExpired:
                print("[WARN] MCP server didn't terminate gracefully, killing...")
                self.process.kill()
            finally:
                self.process = None
                self._mcp_initialized = False
                print(f"[STOP] Stopped Minecraft MCP server")
        else:
            self.process = None
            self._mcp_initialized = False

    def __del__(self):
        """Cleanup when bridge is destroyed"""
        self.stop_server()


# === HTTP Bridge for Kotlin mod ===

def create_http_bridge(bridge: MinecraftMCPBridge, port: int = 5557):
    """
    Create HTTP bridge for Kotlin mod to communicate with MCP server

    This allows the Fabric mod to send events via HTTP POST
    instead of dealing with stdio directly.
    """
    app = Flask(__name__)

    @app.route('/mcp/call', methods=['POST'])
    def mcp_call():
        """Handle MCP tool calls from Kotlin mod"""
        try:
            data = request.json
            tool = data.get('tool')
            arguments = data.get('arguments', {})

            result = bridge.call_tool(tool, arguments)
            return jsonify(result)

        except Exception as e:
            return jsonify({'error': str(e)}), 500

    @app.route('/mcp/health', methods=['GET'])
    def health():
        """Health check endpoint"""
        return jsonify({
            'status': 'ok' if bridge.is_server_alive() else 'down',
            'initialized': bridge._mcp_initialized
        })

    return app


def main():
    """Test the bridge"""
    bridge = MinecraftMCPBridge()

    if bridge.mcp_available:
        print(f"\n✓ Minecraft MCP available")

        # Start server
        if bridge.start_server():
            print("\n✓ Server started")

            # Test track event
            print("\n📊 Testing event tracking...")
            result = bridge.track_event('block_place', {
                'block': 'stone',
                'timestamp': time.time()
            })
            print(f"Result: {result}")

            bridge.stop_server()
    else:
        print("\n✗ Minecraft MCP not available")
        print("Run: cd MIIN && npm install && npm run build")


if __name__ == '__main__':
    main()
