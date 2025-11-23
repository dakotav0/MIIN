#!/usr/bin/env python3
"""
Simple LLM Router - Ollama only (Phase 4)

Routes requests based on task type with fallback support.
Cloud providers can be added in Phase 6.
"""

import json
import sys
from pathlib import Path
from typing import Dict, List, Tuple, Optional
import requests


class SimpleLLMRouter:
    """Basic router for Ollama models"""

    def __init__(self, config_path: str = None):
        """Initialize router"""
        self.root = Path(__file__).parent.parent.parent
        self.config_path = config_path or str(self.root / 'npc' / 'config' / 'llm_router_config.json')
        self.config = self.load_config()

    def load_config(self) -> Dict:
        """Load router configuration"""
        try:
            with open(self.config_path, 'r') as f:
                return json.load(f)
        except FileNotFoundError:
            print(f"[Router] Config not found, using defaults", file=sys.stderr)
            return self._default_config()

    def route_request(
        self,
        messages: List[Dict],
        task_type: str = "dialogue",
        npc_id: Optional[str] = None
    ) -> Tuple[str, Optional[str]]:
        """
        Route request to appropriate model

        Returns:
            (response_text, error_message or None)
        """
        # Select model based on task type
        model_name = self._select_model(task_type)

        # Optimize context
        if self.config['context_optimization']['enabled']:
            messages = self._optimize_context(messages, task_type)

        # Call Ollama
        try:
            response = self._call_ollama(model_name, messages)
            return response, None

        except Exception as e:
            # Try fallback
            fallback = self.config['task_types'][task_type].get('fallback')
            if fallback and fallback != model_name:
                try:
                    print(f"[Router] Primary {model_name} failed, trying {fallback}", file=sys.stderr)
                    response = self._call_ollama(fallback, messages)
                    return response, None
                except Exception as e2:
                    return None, f"Both primary and fallback failed: {e}, {e2}"

            return None, str(e)

    def _select_model(self, task_type: str) -> str:
        """Select model based on task type"""
        task_config = self.config['task_types'].get(task_type, {})
        return task_config.get('preferred_model', 'llama3.1:8b')

    def _call_ollama(self, model: str, messages: List[Dict]) -> str:
        """Call Ollama API"""
        endpoint = self.config['providers']['ollama']['endpoint']
        keep_alive = self.config['keep_alive']['duration']

        response = requests.post(
            f"{endpoint}/api/chat",
            json={
                "model": model,
                "messages": messages,
                "stream": False,
                "keep_alive": keep_alive
            },
            timeout=30
        )
        response.raise_for_status()
        return response.json()['message']['content']

    def _optimize_context(self, messages: List[Dict], task_type: str) -> List[Dict]:
        """Optimize context based on task type"""
        memory_window = self.config['context_optimization']['memory_window'][task_type]

        system_msgs = [m for m in messages if m['role'] == 'system']
        conversation = [m for m in messages if m['role'] != 'system']

        if len(conversation) > memory_window * 2:
            conversation = conversation[-(memory_window * 2):]

        return system_msgs + conversation

    def _default_config(self) -> Dict:
        """Minimal default configuration"""
        return {
            "providers": {
                "ollama": {
                    "endpoint": "http://localhost:11434",
                    "enabled": True
                }
            },
            "task_types": {
                "dialogue": {
                    "preferred_model": "llama3.1:8b",
                    "fallback": "llama3.2:latest"
                }
            },
            "keep_alive": {
                "duration": "10m"
            },
            "context_optimization": {
                "enabled": True,
                "memory_window": {
                    "quick_response": 3,
                    "dialogue": 10,
                    "quest_generation": 20
                }
            }
        }
