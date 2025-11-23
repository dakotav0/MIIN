import requests
import json
import sys

def check_port(url, name):
    try:
        response = requests.get(url, timeout=5)
        print(f"[PASS] {name} is reachable at {url}. Status: {response.status_code}")
        return True
    except Exception as e:
        print(f"[FAIL] {name} is NOT reachable at {url}. Error: {e}")
        return False

def test_mcp_tool_call():
    url = "http://localhost:5557/mcp/call"

    # Test 1: Valid NPC with greeting template (should be fast)
    print("\n=== Test 1: Valid NPC (marina) - Template Greeting ===")
    payload = {
        "tool": "minecraft_dialogue_start_llm",
        "arguments": {
            "npc": "marina",
            "player": "vDakota"
        }
    }
    try:
        print(f"Sending tool call to {url}...")
        response = requests.post(url, json=payload, timeout=130)  # Match bridge timeout
        print(f"Response status: {response.status_code}")
        print(f"Response body: {response.text[:500]}...")  # First 500 chars
    except Exception as e:
        print(f"[FAIL] Test 1 failed. Error: {e}")

    # Test 2: Invalid NPC (should return error quickly)
    print("\n=== Test 2: Invalid NPC (test_npc) - Should Error Fast ===")
    payload = {
        "tool": "minecraft_dialogue_start_llm",
        "arguments": {
            "npc": "test_npc",
            "player": "test_player"
        }
    }
    try:
        print(f"Sending tool call to {url}...")
        response = requests.post(url, json=payload, timeout=130)
        print(f"Response status: {response.status_code}")
        print(f"Response body: {response.text}")
    except Exception as e:
        print(f"[FAIL] Test 2 failed. Error: {e}")

def main():
    print("Checking services...")
    
    # Check Python HTTP Bridge (5557)
    check_port("http://localhost:5557/mcp/health", "Python HTTP Bridge")
    
    # Check Kotlin HTTP Bridge (5558)
    check_port("http://localhost:5558/health", "Kotlin HTTP Bridge")
    
    # Check Ollama (11434)
    check_port("http://localhost:11434", "Ollama")
    
    print("\nTesting Tool Call...")
    test_mcp_tool_call()

if __name__ == "__main__":
    main()
