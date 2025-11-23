#!/usr/bin/env python3
"""
Direct test of MCP server via stdio (bypassing HTTP bridge)
"""
import subprocess
import json
import time

def test_mcp_stdio():
    print("Starting MCP server process...")

    proc = subprocess.Popen(
        ['node', r'~\MIIN\dist\index.js'], # Adjust path as necessary
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        bufsize=1
    )

    time.sleep(2)  # Let server initialize

    # Send initialize request (required by MCP protocol)
    init_request = {
        "jsonrpc": "2.0",
        "id": 1,
        "method": "initialize",
        "params": {
            "protocolVersion": "2024-11-05",
            "capabilities": {},
            "clientInfo": {
                "name": "test-client",
                "version": "1.0.0"
            }
        }
    }

    print(f"\nSending initialize request:\n{json.dumps(init_request, indent=2)}")
    proc.stdin.write(json.dumps(init_request) + '\n')
    proc.stdin.flush()

    print("\nWaiting for response (10s timeout)...")
    start = time.time()
    while time.time() - start < 10:
        line = proc.stdout.readline()
        if line:
            print(f"Response: {line.strip()}")
            break
        time.sleep(0.1)
    else:
        print("No response after 10 seconds")

    # Check stderr
    proc.stderr_lines = []
    while True:
        try:
            line = proc.stderr.readline()
            if not line:
                break
            print(f"STDERR: {line.strip()}")
        except:
            break

    proc.terminate()
    print("\nTest complete")

if __name__ == '__main__':
    test_mcp_stdio()
