#!/usr/bin/env node

/**
 * Minecraft Intelligence MCP Server
 *
 * Bridges Minecraft events to MIIN intelligence modules for:
 * - Creative build analysis
 * - Pattern detection
 * - Proactive suggestions
 * - Cross-domain insights
 */

import { Server } from '@modelcontextprotocol/sdk/server/index.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
  Tool,
} from '@modelcontextprotocol/sdk/types.js';
import { z } from 'zod';
import { IntelligenceBridge } from './intelligence-bridge.js';
import { MinecraftEventTracker } from './event-tracker.js';
import { spawn, ChildProcess } from 'child_process';
import path from 'path';
import { initializeToolHandlers, routeToolCall } from './tools/index.js';
import { ALL_TOOLS } from './tools/tool-definitions.js';

console.error('[MCP] Initializing intelligence bridge...');
// Initialize intelligence bridge
const intelligenceBridge = new IntelligenceBridge({
  musicIntelligenceUrl: 'http://localhost:5555',
  unifiedIntelligenceUrl: 'http://localhost:5556',
});
console.error('[MCP] Intelligence bridge initialized');

console.error('[MCP] Initializing event tracker...');
// Initialize event tracker
const eventTracker = new MinecraftEventTracker();
console.error('[MCP] Event tracker initialized');

console.error('[MCP] Initializing tool handlers...');
// Initialize tool handlers with dependencies
initializeToolHandlers(intelligenceBridge, eventTracker);
console.error('[MCP] Tool handlers initialized');

// DISABLED: Don't spawn event reactor on module load - causes initialization issues
// It will be spawned in main() after server connects
/*
// Start Event Reactor (Python service)
const eventReactorPath = path.join(process.cwd(), 'events/reactor.py');
console.error(`[MCP] Starting Event Reactor from: ${eventReactorPath}`);

const eventReactor = spawn('python', [eventReactorPath], {
  stdio: ['ignore', 'pipe', 'pipe'], // Ignore stdin, pipe stdout/stderr
  cwd: process.cwd(),
});

eventReactor.stdout.on('data', (data) => {
  console.error(`[EventReactor] ${data.toString().trim()}`);
});

eventReactor.stderr.on('data', (data) => {
  console.error(`[EventReactor Error] ${data.toString().trim()}`);
});

eventReactor.on('close', (code) => {
  console.error(`[EventReactor] Process exited with code ${code}`);
});

// Ensure cleanup on exit
process.on('exit', () => {
  eventReactor.kill();
});
process.on('SIGINT', () => {
  eventReactor.kill();
  process.exit();
});
process.on('SIGTERM', () => {
  eventReactor.kill();
  process.exit();
});
*/

// Helper function to calculate total distance traveled
function calculateTotalDistance(states: any[]): number {
  if (states.length < 2) return 0;

  let totalDistance = 0;
  for (let i = 1; i < states.length; i++) {
    const prev = states[i - 1].data;
    const curr = states[i].data;

    if (prev.dimension === curr.dimension) {
      const dx = curr.x - prev.x;
      const dy = curr.y - prev.y;
      const dz = curr.z - prev.z;
      totalDistance += Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
  }

  return Math.round(totalDistance);
}

// Create MCP server
console.error('[MCP] Creating server instance...');
const server = new Server(
  {
    name: 'minecraft-intelligence-mcp',
    version: '1.0.0',
  },
  {
    capabilities: {
      tools: {},
    },
  }
);
console.error('[MCP] Server instance created');

// Register ListTools handler
console.error('[MCP] Registering ListTools handler...');
server.setRequestHandler(ListToolsRequestSchema, async () => {
  console.error('[MCP] ListTools handler called');
  return { tools: ALL_TOOLS };
});

// OLD MANUAL TOOL DEFINITIONS (REPLACED BY tool-definitions.ts)
// Keeping for reference during migration, will be removed after testing
/*
server.setRequestHandler(ListToolsRequestSchema, async () => {
  console.error('[MCP] ListTools handler called');
  const tools: Tool[] = [
    {
      name: 'minecraft_analyze_build',
      description: 'Analyze a Minecraft build for creative patterns, themes, and style. Uses MIIN creative intelligence.',
      inputSchema: {
        type: 'object',
        properties: {
          buildName: {
            type: 'string',
            description: 'Name or description of the build',
          },
          blocks: {
            type: 'array',
            description: 'Array of block types used',
            items: { type: 'string' },
          },
          blockCounts: {
            type: 'object',
            description: 'Block type counts (e.g., {"stone": 100, "oak_planks": 50})',
          },
          buildTime: {
            type: 'number',
            description: 'Time spent building (in seconds)',
          },
          tags: {
            type: 'array',
            description: 'Optional tags (e.g., ["medieval", "castle"])',
            items: { type: 'string' },
          },
        },
        required: ['buildName', 'blocks'],
      },
    },
    {
      name: 'minecraft_suggest_palette',
      description: 'Get block palette suggestions based on current build theme. Like music curation, but for blocks!',
      inputSchema: {
        type: 'object',
        properties: {
          theme: {
            type: 'string',
            description: 'Build theme or mood (e.g., "cozy cabin", "futuristic city", "natural garden")',
          },
          existingBlocks: {
            type: 'array',
            description: 'Blocks already used in the build',
            items: { type: 'string' },
          },
          paletteSize: {
            type: 'number',
            description: 'Number of blocks to suggest',
            default: 10,
          },
        },
        required: ['theme'],
      },
    },
    {
      name: 'minecraft_detect_patterns',
      description: 'Detect patterns in player behavior (building style, time preferences, block usage correlations)',
      inputSchema: {
        type: 'object',
        properties: {
          days: {
            type: 'number',
            description: 'Number of days to analyze',
            default: 30,
          },
          patternType: {
            type: 'string',
            description: 'Type of pattern to detect',
            enum: ['temporal', 'behavioral', 'preference', 'all'],
            default: 'all',
          },
        },
      },
    },
    {
      name: 'minecraft_get_insights',
      description: 'Get proactive insights and suggestions based on building history and patterns',
      inputSchema: {
        type: 'object',
        properties: {
          context: {
            type: 'object',
            description: 'Current context (time, recent activity, mood)',
          },
        },
      },
    },
    {
      name: 'minecraft_track_event',
      description: 'Track a Minecraft event for pattern analysis',
      inputSchema: {
        type: 'object',
        properties: {
          eventType: {
            type: 'string',
            description: 'Type of event',
            enum: ['block_place', 'block_break', 'build_complete', 'session_start', 'session_end', 'player_chat', 'player_state', 'mob_killed', 'inventory_snapshot'],
          },
          data: {
            type: 'object',
            description: 'Event data',
          },
        },
        required: ['eventType', 'data'],
      },
    },
    {
      name: 'minecraft_send_chat',
      description: 'Send a chat message from the AI to player(s) in Minecraft. Use this to communicate insights, suggestions, or respond to player queries.',
      inputSchema: {
        type: 'object',
        properties: {
          message: {
            type: 'string',
            description: 'The message to send',
          },
          player: {
            type: 'string',
            description: 'Player name (optional - if not specified, broadcasts to all players)',
          },
        },
        required: ['message'],
      },
    },
    {
      name: 'minecraft_get_inventory',
      description: 'Get a snapshot of a player\'s inventory. Useful for understanding resources available for building.',
      inputSchema: {
        type: 'object',
        properties: {
          player: {
            type: 'string',
            description: 'Player name',
          },
        },
        required: ['player'],
      },
    },
    {
      name: 'minecraft_get_player_state',
      description: 'Get current player state (position, dimension, biome, health, weather, time of day)',
      inputSchema: {
        type: 'object',
        properties: {
          player: {
            type: 'string',
            description: 'Player name',
          },
        },
        required: ['player'],
      },
    },
    {
      name: 'minecraft_get_recent_activity',
      description: 'Get recent player activity summary (builds, chat, mobs killed, movement)',
      inputSchema: {
        type: 'object',
        properties: {
          player: {
            type: 'string',
            description: 'Player name',
          },
          minutes: {
            type: 'number',
            description: 'Minutes of history to retrieve',
            default: 30,
          },
        },
        required: ['player'],
      },
    },
    {
      name: 'minecraft_npc_talk',
      description: 'Talk to an NPC. The NPC will respond based on their personality, your recent activity, and conversation history. NPCs remember previous conversations.',
      inputSchema: {
        type: 'object',
        properties: {
          npc: {
            type: 'string',
            description: 'NPC identifier (e.g., "eldrin", "kira", "lyra", "thane")',
          },
          player: {
            type: 'string',
            description: 'Player name',
          },
          message: {
            type: 'string',
            description: 'What the player wants to say to the NPC',
          },
        },
        required: ['npc', 'player', 'message'],
      },
    },
    {
      name: 'minecraft_npc_list',
      description: 'List all available NPCs with their personalities and locations',
      inputSchema: {
        type: 'object',
        properties: {},
      },
    },
    {
      name: 'minecraft_quest_request',
      description: 'Request a quest from an NPC. The NPC will generate a quest based on your recent activity and their personality.',
      inputSchema: {
        type: 'object',
        properties: {
          npc: {
            type: 'string',
            description: 'NPC identifier',
          },
          player: {
            type: 'string',
            description: 'Player name',
          },
        },
        required: ['npc', 'player'],
      },
    },
    {
      name: 'minecraft_quest_status',
      description: 'Get status of active and completed quests for a player',
      inputSchema: {
        type: 'object',
        properties: {
          player: {
            type: 'string',
            description: 'Player name',
          },
        },
        required: ['player'],
      },
    },
    {
      name: 'minecraft_quest_check_progress',
      description: 'Check and update quest progress for a player based on their recent activity. Automatically marks objectives as complete when met.',
      inputSchema: {
        type: 'object',
        properties: {
          player: {
            type: 'string',
            description: 'Player name',
          },
        },
        required: ['player'],
      },
    },
    {
      name: 'minecraft_quest_accept',
      description: 'Accept a quest from an NPC. The quest will be added to the player\'s active quests and can be tracked.',
      inputSchema: {
        type: 'object',
        properties: {
          npc: {
            type: 'string',
            description: 'NPC identifier who offered the quest',
          },
          player: {
            type: 'string',
            description: 'Player name accepting the quest',
          },
          quest_id: {
            type: 'string',
            description: 'Quest identifier to accept',
          },
        },
        required: ['npc', 'player', 'quest_id'],
      },
    },
    {
      name: 'minecraft_build_challenge_request',
      description: 'Request a themed build challenge from an NPC. The NPC will offer a specific building challenge with clear requirements and rewards. Build challenges have specific block requirements, dimensions, and validation criteria.',
      inputSchema: {
        type: 'object',
        properties: {
          npc: {
            type: 'string',
            description: 'NPC identifier (e.g., "thane", "lyra", "sage") - different NPCs offer different challenge types',
          },
          player: {
            type: 'string',
            description: 'Player name requesting the challenge',
          },
          challenge_id: {
            type: 'string',
            description: 'Optional specific challenge ID (e.g., "medieval_tower", "garden_sanctuary"). If not provided, NPC will choose a suitable challenge.',
          },
        },
        required: ['npc', 'player'],
      },
    },
    {
      name: 'minecraft_build_challenge_validate',
      description: 'Validate a completed build against an active build challenge. Checks if the build meets all requirements (block counts, height, specific blocks used). Returns detailed validation results.',
      inputSchema: {
        type: 'object',
        properties: {
          player: {
            type: 'string',
            description: 'Player name who completed the build',
          },
          quest_id: {
            type: 'string',
            description: 'Quest ID of the build challenge to validate',
          },
          build_data: {
            type: 'object',
            description: 'Build statistics: {blocks: {block_type: count}, height: number}',
            properties: {
              blocks: {
                type: 'object',
                description: 'Block type counts (e.g., {"stone_bricks": 45, "oak_planks": 25})',
              },
              height: {
                type: 'number',
                description: 'Total height of the build in blocks',
              },
            },
            required: ['blocks', 'height'],
          },
        },
        required: ['player', 'quest_id', 'build_data'],
      },
    },
    {
      name: 'minecraft_build_challenge_list',
      description: 'List all available build challenges and which NPCs offer them. Shows difficulty, requirements summary, and rewards.',
      inputSchema: {
        type: 'object',
        properties: {
          npc: {
            type: 'string',
            description: 'Optional NPC filter - only show challenges this NPC offers',
          },
        },
      },
    },
    {
      name: 'minecraft_teleport',
      description: 'Teleport a player to specific coordinates. Use with caution - only for authorized admin actions.',
      inputSchema: {
        type: 'object',
        properties: {
          player: {
            type: 'string',
            description: 'Player name to teleport',
          },
          x: {
            type: 'number',
            description: 'X coordinate',
          },
          y: {
            type: 'number',
            description: 'Y coordinate',
          },
          z: {
            type: 'number',
            description: 'Z coordinate',
          },
        },
        required: ['player', 'x', 'y', 'z'],
      },
    },
    {
      name: 'minecraft_classify_archetype',
      description: 'Classify a Minecraft build into an archetype (castle, house, tower, temple, etc.) using LLM analysis. Returns style, scale, and improvement suggestions.',
      inputSchema: {
        type: 'object',
        properties: {
          blocks: {
            type: 'array',
            description: 'Array of block types used in the build',
            items: { type: 'string' },
          },
          blockCounts: {
            type: 'object',
            description: 'Block type counts (e.g., {"stone": 100, "oak_planks": 50})',
          },
          buildTime: {
            type: 'number',
            description: 'Time spent building (in seconds)',
          },
        },
        required: ['blocks', 'blockCounts'],
      },
    },
    {
      name: 'minecraft_check_milestones',
      description: 'Check for new milestones reached by a player. Returns newly achieved milestones and progress toward next ones.',
      inputSchema: {
        type: 'object',
        properties: {
          player: {
            type: 'string',
            description: 'Player name',
          },
        },
        required: ['player'],
      },
    },
    {
      name: 'minecraft_list_milestones',
      description: 'List all milestones achieved by a player, sorted by date.',
      inputSchema: {
        type: 'object',
        properties: {
          player: {
            type: 'string',
            description: 'Player name',
          },
        },
        required: ['player'],
      },
    },
    {
      name: 'minecraft_dialogue_options',
      description: 'Get BG3-style dialogue options for an NPC interaction. Returns greeting and context-aware dialogue choices.',
      inputSchema: {
        type: 'object',
        properties: {
          npc: {
            type: 'string',
            description: 'NPC identifier',
          },
          player: {
            type: 'string',
            description: 'Player name',
          },
          context: {
            type: 'string',
            description: 'Context type (greeting, quest, trade, farewell)',
          },
        },
        required: ['npc', 'player'],
      },
    },
    {
      name: 'minecraft_dialogue_select',
      description: 'Select a dialogue option and get NPC response. Updates relationship based on choice.',
      inputSchema: {
        type: 'object',
        properties: {
          npc: {
            type: 'string',
            description: 'NPC identifier',
          },
          player: {
            type: 'string',
            description: 'Player name',
          },
          option_id: {
            type: 'number',
            description: 'ID of selected option',
          },
          option_text: {
            type: 'string',
            description: 'Text of selected option',
          },
          relationship_delta: {
            type: 'number',
            description: 'Relationship change from this choice',
          },
        },
        required: ['npc', 'player', 'option_id', 'option_text'],
      },
    },
    {
      name: 'minecraft_lore_get',
      description: 'Get a specific lore book by ID or a random book. Returns F-EIGHT canon content.',
      inputSchema: {
        type: 'object',
        properties: {
          lore_id: {
            type: 'string',
            description: 'Specific lore book ID (or "random" for random book)',
          },
          category: {
            type: 'string',
            description: 'Category filter for random book (ancient_builders, dimensional_secrets, etc.)',
          },
        },
      },
    },
    {
      name: 'minecraft_lore_progress',
      description: 'Get player lore discovery progress - what they have found and what remains.',
      inputSchema: {
        type: 'object',
        properties: {
          player: {
            type: 'string',
            description: 'Player name',
          },
        },
        required: ['player'],
      },
    },
    {
      name: 'minecraft_satchel_view',
      description: 'View contents of player lore satchel - shows all discovered lore with summaries for NPC context.',
      inputSchema: {
        type: 'object',
        properties: {
          player: {
            type: 'string',
            description: 'Player name',
          },
        },
        required: ['player'],
      },
    },
    {
      name: 'minecraft_party_create',
      description: 'Create a new party for coordinating multiple NPCs.',
      inputSchema: {
        type: 'object',
        properties: {
          player: {
            type: 'string',
            description: 'Player name (party leader)',
          },
          party_name: {
            type: 'string',
            description: 'Optional custom party name',
          },
        },
        required: ['player'],
      },
    },
    {
      name: 'minecraft_party_invite',
      description: 'Invite an NPC to join your party (max 4 members).',
      inputSchema: {
        type: 'object',
        properties: {
          player: {
            type: 'string',
            description: 'Player name',
          },
          npc_id: {
            type: 'string',
            description: 'NPC to invite (e.g., eldrin, kira, lyra, thane)',
          },
        },
        required: ['player', 'npc_id'],
      },
    },
    {
      name: 'minecraft_party_leave',
      description: 'Remove an NPC from party, or disband party if no NPC specified.',
      inputSchema: {
        type: 'object',
        properties: {
          player: {
            type: 'string',
            description: 'Player name',
          },
          npc_id: {
            type: 'string',
            description: 'NPC to remove (omit to disband entire party)',
          },
        },
        required: ['player'],
      },
    },
    {
      name: 'minecraft_party_chat',
      description: 'Send message to party - routes to most appropriate NPC based on expertise.',
      inputSchema: {
        type: 'object',
        properties: {
          player: {
            type: 'string',
            description: 'Player name',
          },
          message: {
            type: 'string',
            description: 'Message to send to party',
          },
        },
        required: ['player', 'message'],
      },
    },
    {
      name: 'minecraft_party_status',
      description: 'Get current party status including members and their expertise.',
      inputSchema: {
        type: 'object',
        properties: {
          player: {
            type: 'string',
            description: 'Player name',
          },
        },
        required: ['player'],
      },
    },
    {
      name: 'minecraft_party_discuss',
      description: 'Have all party members discuss a topic - each gives their unique perspective.',
      inputSchema: {
        type: 'object',
        properties: {
          player: {
            type: 'string',
            description: 'Player name',
          },
          topic: {
            type: 'string',
            description: 'Topic for party discussion',
          },
        },
        required: ['player', 'topic'],
      },
    },
    {
      name: 'minecraft_dialogue_start_llm',
      description: 'Start a dynamic LLM-driven dialogue with an NPC.',
      inputSchema: {
        type: 'object',
        properties: {
          npc: {
            type: 'string',
            description: 'NPC identifier',
          },
          player: {
            type: 'string',
            description: 'Player name',
          },
        },
        required: ['npc', 'player'],
      },
    },
    {
      name: 'minecraft_dialogue_respond',
      description: 'Respond to an NPC in an LLM-driven dialogue.',
      inputSchema: {
        type: 'object',
        properties: {
          conversation_id: {
            type: 'string',
            description: 'Conversation ID',
          },
          npc: {
            type: 'string',
            description: 'NPC identifier',
          },
          player: {
            type: 'string',
            description: 'Player name',
          },
          option_text: {
            type: 'string',
            description: 'Selected option text',
          },
        },
        required: ['conversation_id', 'npc', 'player', 'option_text'],
      },
    },
    {
      name: 'minecraft_npc_talk_with_suggestions',
      description: 'Talk to an NPC and get follow-up suggestions.',
      inputSchema: {
        type: 'object',
        properties: {
          npc: {
            type: 'string',
            description: 'NPC identifier',
          },
          player: {
            type: 'string',
            description: 'Player name',
          },
          message: {
            type: 'string',
            description: 'Message to send',
          },
          request_suggestions: {
            type: 'boolean',
            description: 'Whether to request follow-up suggestions',
          },
        },
        required: ['npc', 'player', 'message'],
      },
    },
  ];

  return { tools };
});
*/

// Register CallTool handler - NOW USING MODULAR ROUTER!
console.error('[MCP] Registering CallTool handler...');
server.setRequestHandler(CallToolRequestSchema, async (request) => {
  console.error(`[MCP] CallTool handler called: ${request.params.name}`);
  const { name, arguments: args } = request.params;

  // Route to appropriate handler via modular tool router
  return await routeToolCall(name, args);
});

// OLD MONOLITHIC SWITCH STATEMENT (REPLACED BY MODULAR HANDLERS)
// Keeping for reference during migration, will be removed after testing
/*
server.setRequestHandler(CallToolRequestSchema, async (request) => {
  const { name, arguments: args } = request.params;

  try {
    switch (name) {
      case 'minecraft_analyze_build': {
        const { buildName, blocks, blockCounts, buildTime, tags } = args as {
          buildName: string;
          blocks: string[];
          blockCounts?: Record<string, number>;
          buildTime?: number;
          tags?: string[];
        };

        // Track the build event
        eventTracker.trackEvent('build_complete', {
          buildName,
          blocks,
          blockCounts,
          buildTime,
          tags,
          timestamp: new Date().toISOString(),
        });

        // Analyze with creative intelligence
        const analysis = await intelligenceBridge.analyzeCreativeBuild({
          title: buildName,
          blocks,
          blockCounts: blockCounts || {},
          duration: buildTime,
          tags: tags || [],
        });

        return {
          content: [
            {
              type: 'text',
              text: JSON.stringify(analysis, null, 2),
            },
          ],
        };
      }

      case 'minecraft_suggest_palette': {
        const { theme, existingBlocks, paletteSize } = args as {
          theme: string;
          existingBlocks?: string[];
          paletteSize?: number;
        };

        // Use music intelligence pattern for block curation
        const suggestions = await intelligenceBridge.suggestBlockPalette({
          theme,
          existingBlocks: existingBlocks || [],
          count: paletteSize || 10,
        });

        return {
          content: [
            {
              type: 'text',
              text: JSON.stringify(suggestions, null, 2),
            },
          ],
        };
      }

      case 'minecraft_detect_patterns': {
        const { days, patternType } = args as {
          days?: number;
          patternType?: string;
        };

        // Get patterns from event history
        const patterns = await intelligenceBridge.detectMinecraftPatterns({
          days: days || 30,
          patternType: patternType || 'all',
          events: eventTracker.getEvents(days || 30),
        });

        return {
          content: [
            {
              type: 'text',
              text: JSON.stringify(patterns, null, 2),
            },
          ],
        };
      }

      case 'minecraft_get_insights': {
        const { context } = args as {
          context?: any;
        };

        // Get insights from intelligence bridge
        const insights = await intelligenceBridge.getProactiveInsights({
          context: context || {},
          recentEvents: eventTracker.getEvents(10),
        });

        return {
          content: [
            {
              type: 'text',
              text: JSON.stringify(insights, null, 2),
            },
          ],
        };
      }

      case 'minecraft_track_event': {
        const { eventType, data } = args as {
          eventType: string;
          data: any;
        };

        eventTracker.trackEvent(eventType, {
          ...data,
          timestamp: new Date().toISOString(),
        });

        return {
          content: [
            {
              type: 'text',
              text: JSON.stringify({ success: true, eventType, data }),
            },
          ],
        };
      }

      case 'minecraft_send_chat': {
        const { message, player } = args as {
          message: string;
          player?: string;
        };

        // Send command to Minecraft mod via HTTP bridge
        try {
          const response = await fetch('http://localhost:5558/command', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              type: 'send_chat',
              data: {
                message,
                player: player || null,
              },
            }),
          });

          const result = await response.json();

          // Also track as event
          eventTracker.trackEvent('ai_chat_sent', {
            message,
            targetPlayer: player || 'all',
            timestamp: new Date().toISOString(),
          });

          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify({
                  success: true,
                  message,
                  sentTo: player || 'all players',
                  bridgeResponse: result,
                }),
              },
            ],
          };
        } catch (error: any) {
          // Fallback to just tracking if bridge is not available
          eventTracker.trackEvent('ai_chat_sent', {
            message,
            targetPlayer: player || 'all',
            timestamp: new Date().toISOString(),
            bridgeError: error.message,
          });

          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify({
                  success: false,
                  message,
                  sentTo: player || 'all players',
                  note: 'Chat message tracked but bridge unavailable',
                  error: error.message,
                }),
              },
            ],
          };
        }
      }

      case 'minecraft_get_inventory': {
        const { player } = args as {
          player: string;
        };

        // Get most recent inventory snapshot for this player
        const inventoryEvents = eventTracker.getEventsByType('inventory_snapshot');
        const playerInventory = inventoryEvents
          .filter((e: any) => e.data.playerName === player)
          .sort((a: any, b: any) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime())[0];

        if (!playerInventory) {
          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify({
                  error: `No inventory data found for player: ${player}`,
                  suggestion: 'Player may not have joined recently or inventory tracking is disabled',
                }),
              },
            ],
          };
        }

        return {
          content: [
            {
              type: 'text',
              text: JSON.stringify({
                player,
                inventory: playerInventory.data.inventory,
                timestamp: playerInventory.timestamp,
              }, null, 2),
            },
          ],
        };
      }

      case 'minecraft_get_player_state': {
        const { player } = args as {
          player: string;
        };

        // Get most recent player state
        const stateEvents = eventTracker.getEventsByType('player_state');
        const playerState = stateEvents
          .filter((e: any) => e.data.playerName === player)
          .sort((a: any, b: any) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime())[0];

        if (!playerState) {
          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify({
                  error: `No state data found for player: ${player}`,
                  suggestion: 'Player may not be online or state tracking is disabled',
                }),
              },
            ],
          };
        }

        return {
          content: [
            {
              type: 'text',
              text: JSON.stringify({
                player,
                position: {
                  x: playerState.data.x,
                  y: playerState.data.y,
                  z: playerState.data.z,
                },
                dimension: playerState.data.dimension,
                biome: playerState.data.biome,
                health: playerState.data.health,
                hunger: playerState.data.hunger,
                gameMode: playerState.data.gameMode,
                weather: playerState.data.weather,
                timeOfDay: playerState.data.timeOfDay,
                timestamp: playerState.timestamp,
              }, null, 2),
            },
          ],
        };
      }

      case 'minecraft_get_recent_activity': {
        const { player, minutes } = args as {
          player: string;
          minutes?: number;
        };

        const lookbackMinutes = minutes || 30;
        const cutoff = new Date();
        cutoff.setMinutes(cutoff.getMinutes() - lookbackMinutes);

        // Get all events for this player
        const playerEvents = eventTracker.getEvents(1) // Get last day
          .filter((e: any) => {
            const eventTime = new Date(e.timestamp);
            return eventTime >= cutoff &&
              (e.data.playerName === player || e.data.playerId);
          });

        // Categorize events
        const builds = playerEvents.filter((e: any) => e.eventType === 'build_complete');
        const chats = playerEvents.filter((e: any) => e.eventType === 'player_chat');
        const mobKills = playerEvents.filter((e: any) => e.eventType === 'mob_killed');
        const states = playerEvents.filter((e: any) => e.eventType === 'player_state');

        // Calculate stats
        const totalBlocks = builds.reduce((sum: number, e: any) =>
          sum + Object.values(e.data.blockCounts || {}).reduce((a: number, b: any) => a + b, 0), 0);

        const uniqueBiomes = [...new Set(states.map((e: any) => e.data.biome))];
        const totalDistance = calculateTotalDistance(states);

        return {
          content: [
            {
              type: 'text',
              text: JSON.stringify({
                player,
                timeframe: `Last ${lookbackMinutes} minutes`,
                summary: {
                  totalEvents: playerEvents.length,
                  buildsCompleted: builds.length,
                  totalBlocksPlaced: totalBlocks,
                  chatMessages: chats.length,
                  mobsKilled: mobKills.length,
                  biomesVisited: uniqueBiomes.length,
                  approximateDistanceTraveled: totalDistance,
                },
                recentBuilds: builds.slice(-3).map((e: any) => ({
                  blocks: e.data.blockCounts,
                  duration: e.data.buildTime,
                  timestamp: e.timestamp,
                })),
                recentChats: chats.slice(-5).map((e: any) => ({
                  message: e.data.message,
                  timestamp: e.timestamp,
                })),
                recentKills: mobKills.slice(-5).map((e: any) => ({
                  mobType: e.data.mobType,
                  timestamp: e.timestamp,
                })),
              }, null, 2),
            },
          ],
        };
      }

      case 'minecraft_npc_talk': {
        const { npc, player, message } = args as {
          npc: string;
          player: string;
          message: string;
        };

        const { promisify } = await import('util');
        const exec = promisify((await import('child_process')).exec);

        try {
          const scriptPath = `${process.cwd()}/npc_talk.py`;
          // Escape message properly for shell
          const escapedMessage = message.replace(/'/g, "'\\''");
          const { stdout, stderr } = await exec(`python "${scriptPath}" "${npc}" "${player}" '${escapedMessage}'`);

          if (stderr) {
            console.error('[NPC] stderr:', stderr);
          }

          const result = JSON.parse(stdout.trim());

          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify(result, null, 2),
              },
            ],
          };
        } catch (error: any) {
          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify({
                  error: 'Failed to get NPC response',
                  details: error.message,
                }),
              },
            ],
          };
        }
      }

      case 'minecraft_npc_talk_with_suggestions': {
        const { npc, player, message, request_suggestions } = args as {
          npc: string;
          player: string;
          message: string;
          request_suggestions?: boolean;
        };

        const { promisify } = await import('util');
        const exec = promisify((await import('child_process')).exec);

        try {
          const scriptPath = `${process.cwd()}/npc_talk.py`;
          // Escape message properly for shell (single quotes)
          const escapedMessage = message.replace(/'/g, "'\\''");
          const suggestionsFlag = request_suggestions ? '--suggestions' : '';
          const { stdout, stderr } = await exec(`python "${scriptPath}" "${npc}" "${player}" '${escapedMessage}' ${suggestionsFlag}`, { timeout: 150000 });

          if (stderr) {
            console.error('[NPC] stderr:', stderr);
          }

          const result = JSON.parse(stdout.trim());

          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify(result, null, 2),
              },
            ],
          };
        } catch (error: any) {
          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify({
                  error: 'Failed to get NPC response with suggestions',
                  details: error.message,
                }),
              },
            ],
          };
        }
      }

      case 'minecraft_dialogue_start_llm': {
        const { npc, player } = args as {
          npc: string;
          player: string;
        };

        const { promisify } = await import('util');
        const exec = promisify((await import('child_process')).exec);

        try {
          const scriptPath = `${process.cwd()}/dialogue_service.py`;
          const { stdout, stderr } = await exec(`python "${scriptPath}" start_llm "${npc}" "${player}"`, { timeout: 120000 });

          if (stderr) {
            console.error('[Dialogue] stderr:', stderr);
          }

          const result = JSON.parse(stdout.trim());

          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify(result, null, 2),
              },
            ],
          };
        } catch (error: any) {
          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify({
                  error: 'Failed to start LLM dialogue',
                  details: error.message,
                }),
              },
            ],
          };
        }
      }

      case 'minecraft_dialogue_respond': {
        const { conversation_id, npc, player, option_text } = args as {
          conversation_id: string;
          npc: string;
          player: string;
          option_text: string;
        };

        const { promisify } = await import('util');
        const exec = promisify((await import('child_process')).exec);

        try {
          const scriptPath = `${process.cwd()}/dialogue_service.py`;
          // Escape option_text properly for shell (single quotes)
          const escapedText = option_text.replace(/'/g, "'\\''");
          const { stdout, stderr } = await exec(`python "${scriptPath}" respond "${conversation_id}" "${npc}" "${player}" '${escapedText}'`, { timeout: 120000 });

          if (stderr) {
            console.error('[Dialogue] stderr:', stderr);
          }

          const result = JSON.parse(stdout.trim());

          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify(result, null, 2),
              },
            ],
          };
        } catch (error: any) {
          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify({
                  error: 'Failed to respond to dialogue',
                  details: error.message,
                }),
              },
            ],
          };
        }
      }

      case 'minecraft_npc_list': {
        const { promisify } = await import('util');
        const exec = promisify((await import('child_process')).exec);

        try {
          const scriptPath = `${process.cwd()}/npc_list.py`;
          const { stdout, stderr } = await exec(`python "${scriptPath}"`);

          if (stderr) {
            console.error('[NPC] stderr:', stderr);
          }

          const npcs = JSON.parse(stdout.trim());

          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify({ npcs }, null, 2),
              },
            ],
          };
        } catch (error: any) {
          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify({
                  error: 'Failed to list NPCs',
                  details: error.message,
                }),
              },
            ],
          };
        }
      }

      case 'minecraft_quest_request': {
        const { npc, player } = args as {
          npc: string;
          player: string;
        };

        const { promisify } = await import('util');
        const exec = promisify((await import('child_process')).exec);

        try {
          const scriptPath = `${process.cwd()}/npc_quest.py`;
          const { stdout, stderr } = await exec(`python "${scriptPath}" "${npc}" "${player}"`);

          if (stderr) {
            console.error('[NPC] stderr:', stderr);
          }

          const quest = JSON.parse(stdout.trim());

          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify(quest || { error: 'Failed to generate quest' }, null, 2),
              },
            ],
          };
        } catch (error: any) {
          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify({
                  error: 'Failed to generate quest',
                  details: error.message,
                }),
              },
            ],
          };
        }
      }

      case 'minecraft_quest_status': {
        const { player } = args as {
          player: string;
        };

        const { promisify } = await import('util');
        const exec = promisify((await import('child_process')).exec);

        try {
          const scriptPath = `${process.cwd()}/npc_quest_status.py`;
          const { stdout, stderr } = await exec(`python "${scriptPath}" "${player}"`);

          if (stderr) {
            console.error('[NPC] stderr:', stderr);
          }

          const quests = JSON.parse(stdout.trim());

          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify(quests, null, 2),
              },
            ],
          };
        } catch (error: any) {
          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify({
                  error: 'Failed to get quest status',
                  details: error.message,
                }),
              },
            ],
          };
        }
      }

      case 'minecraft_quest_check_progress': {
        const { player } = args as {
          player: string;
        };

        const { promisify } = await import('util');
        const exec = promisify((await import('child_process')).exec);

        try {
          const scriptPath = `${process.cwd()}/npc_check_progress.py`;
          const { stdout, stderr } = await exec(`python "${scriptPath}" "${player}"`);

          if (stderr) {
            console.error('[NPC] stderr:', stderr);
          }

          const progress = JSON.parse(stdout.trim());

          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify(progress, null, 2),
              },
            ],
          };
        } catch (error: any) {
          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify({
                  error: 'Failed to check quest progress',
                  details: error.message,
                }),
              },
            ],
          };
        }
      }

      case 'minecraft_quest_accept': {
        const { npc, player, quest_id } = args as {
          npc: string;
          player: string;
          quest_id: string;
        };

        const { promisify } = await import('util');
        const exec = promisify((await import('child_process')).exec);

        try {
          const scriptPath = `${process.cwd()}/npc_quest_accept.py`;
          const { stdout, stderr } = await exec(`python "${scriptPath}" "${npc}" "${player}" "${quest_id}"`);

          if (stderr) {
            console.error('[NPC] stderr:', stderr);
          }

          const result = JSON.parse(stdout.trim());

          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify(result, null, 2),
              },
            ],
          };
        } catch (error: any) {
          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify({
                  error: 'Failed to accept quest',
                  details: error.message,
                }),
              },
            ],
          };
        }
      }

      case 'minecraft_build_challenge_request': {
        const { npc, player, challenge_id } = args as {
          npc: string;
          player: string;
          challenge_id?: string;
        };

        const { promisify } = await import('util');
        const exec = promisify((await import('child_process')).exec);

        try {
          const scriptPath = `${process.cwd()}/npc_build_challenge_request.py`;
          const cmd = challenge_id
            ? `python "${scriptPath}" "${npc}" "${player}" "${challenge_id}"`
            : `python "${scriptPath}" "${npc}" "${player}"`;
          const { stdout, stderr } = await exec(cmd);

          if (stderr) {
            console.error('[Build Challenge] stderr:', stderr);
          }

          const quest = JSON.parse(stdout.trim());

          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify(quest, null, 2),
              },
            ],
          };
        } catch (error: any) {
          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify({
                  error: 'Failed to generate build challenge',
                  details: error.message,
                }),
              },
            ],
          };
        }
      }

      case 'minecraft_build_challenge_validate': {
        const { player, quest_id, build_data } = args as {
          player: string;
          quest_id: string;
          build_data: { blocks: Record<string, number>; height: number };
        };

        const { promisify } = await import('util');
        const exec = promisify((await import('child_process')).exec);

        try {
          const scriptPath = `${process.cwd()}/npc_build_challenge_validate.py`;
          const buildDataJson = JSON.stringify(build_data).replace(/"/g, '\\"');
          const { stdout, stderr } = await exec(`python "${scriptPath}" "${player}" "${quest_id}" "${buildDataJson}"`);

          if (stderr) {
            console.error('[Build Challenge] stderr:', stderr);
          }

          const result = JSON.parse(stdout.trim());

          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify(result, null, 2),
              },
            ],
          };
        } catch (error: any) {
          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify({
                  error: 'Failed to validate build challenge',
                  details: error.message,
                }),
              },
            ],
          };
        }
      }

      case 'minecraft_build_challenge_list': {
        const { npc } = args as { npc?: string };

        const { promisify } = await import('util');
        const exec = promisify((await import('child_process')).exec);

        try {
          const scriptPath = `${process.cwd()}/npc_build_challenge_list.py`;
          const cmd = npc
            ? `python "${scriptPath}" "${npc}"`
            : `python "${scriptPath}"`;
          const { stdout, stderr } = await exec(cmd);

          if (stderr) {
            console.error('[Build Challenge] stderr:', stderr);
          }

          const challenges = JSON.parse(stdout.trim());

          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify(challenges, null, 2),
              },
            ],
          };
        } catch (error: any) {
          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify({
                  error: 'Failed to list build challenges',
                  details: error.message,
                }),
              },
            ],
          };
        }
      }

      case 'minecraft_teleport': {
        const { player, x, y, z } = args as {
          player: string;
          x: number;
          y: number;
          z: number;
        };

        // Send teleport command to Minecraft mod via HTTP bridge
        try {
          const response = await fetch('http://localhost:5558/command', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              type: 'teleport',
              data: {
                player,
                x,
                y,
                z,
              },
            }),
          });

          const result = await response.json();

          // Track as event
          eventTracker.trackEvent('teleport', {
            player,
            x,
            y,
            z,
            timestamp: new Date().toISOString(),
          });

          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify({
                  success: true,
                  player,
                  coordinates: { x, y, z },
                  bridgeResponse: result,
                }),
              },
            ],
          };
        } catch (error: any) {
          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify({
                  success: false,
                  player,
                  coordinates: { x, y, z },
                  error: error.message,
                  note: 'Teleport failed - bridge may be unavailable',
                }),
              },
            ],
          };
        }
      }

      case 'minecraft_classify_archetype': {
        const { blocks, blockCounts, buildTime } = args as {
          blocks: string[];
          blockCounts: Record<string, number>;
          buildTime?: number;
        };

        try {
          const classification = await intelligenceBridge.classifyArchetype({
            blocks,
            blockCounts,
            buildTime,
          });

          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify(classification, null, 2),
              },
            ],
          };
        } catch (error: any) {
          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify({
                  error: 'Failed to classify archetype',
                  details: error.message,
                }),
              },
            ],
          };
        }
      }

      case 'minecraft_check_milestones': {
        const { player } = args as {
          player: string;
        };

        const { promisify } = await import('util');
        const exec = promisify((await import('child_process')).exec);

        try {
          const scriptPath = `${process.cwd()}/milestone_service.py`;
          const { stdout, stderr } = await exec(`python "${scriptPath}" check "${player}"`);

          if (stderr) {
            console.error('[Milestones] stderr:', stderr);
          }

          const milestones = JSON.parse(stdout.trim());

          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify(milestones, null, 2),
              },
            ],
          };
        } catch (error: any) {
          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify({
                  error: 'Failed to check milestones',
                  details: error.message,
                }),
              },
            ],
          };
        }
      }

      case 'minecraft_list_milestones': {
        const { player } = args as {
          player: string;
        };

        const { promisify } = await import('util');
        const exec = promisify((await import('child_process')).exec);

        try {
          const scriptPath = `${process.cwd()}/milestone_service.py`;
          const { stdout, stderr } = await exec(`python "${scriptPath}" list "${player}"`);

          if (stderr) {
            console.error('[Milestones] stderr:', stderr);
          }

          const milestones = JSON.parse(stdout.trim());

          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify(milestones, null, 2),
              },
            ],
          };
        } catch (error: any) {
          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify({
                  error: 'Failed to list milestones',
                  details: error.message,
                }),
              },
            ],
          };
        }
      }

      case 'minecraft_dialogue_options': {
        const { npc, player, context } = args as {
          npc: string;
          player: string;
          context?: string;
        };

        const { promisify } = await import('util');
        const exec = promisify((await import('child_process')).exec);

        try {
          const scriptPath = `${process.cwd()}/dialogue_service.py`;
          const contextArg = context || 'greeting';
          const { stdout, stderr } = await exec(`python "${scriptPath}" options "${npc}" "${player}" "${contextArg}"`, { timeout: 120000 });

          if (stderr) {
            console.error('[Dialogue] stderr:', stderr);
          }

          const options = JSON.parse(stdout.trim());

          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify(options, null, 2),
              },
            ],
          };
        } catch (error: any) {
          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify({
                  error: 'Failed to get dialogue options',
                  details: error.message,
                }),
              },
            ],
          };
        }
      }

      case 'minecraft_dialogue_select': {
        const { npc, player, option_id, option_text, relationship_delta } = args as {
          npc: string;
          player: string;
          option_id: number;
          option_text: string;
          relationship_delta?: number;
        };

        const { promisify } = await import('util');
        const exec = promisify((await import('child_process')).exec);

        try {
          const scriptPath = `${process.cwd()}/dialogue_service.py`;
          const delta = relationship_delta || 0;
          // Escape the option text properly
          const escapedText = option_text.replace(/'/g, "'\\''");
          const { stdout, stderr } = await exec(`python "${scriptPath}" select "${npc}" "${player}" ${option_id} '${escapedText}' ${delta}`);

          if (stderr) {
            console.error('[Dialogue] stderr:', stderr);
          }

          const result = JSON.parse(stdout.trim());

          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify(result, null, 2),
              },
            ],
          };
        } catch (error: any) {
          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify({
                  error: 'Failed to select dialogue option',
                  details: error.message,
                }),
              },
            ],
          };
        }
      }

      case 'minecraft_lore_get': {
        const { lore_id, category } = args as {
          lore_id?: string;
          category?: string;
        };

        const { promisify } = await import('util');
        const exec = promisify((await import('child_process')).exec);

        try {
          const scriptPath = `${process.cwd()}/lore_service.py`;
          let cmd: string;

          if (lore_id && lore_id !== 'random') {
            cmd = `python "${scriptPath}" get_book "${lore_id}"`;
          } else {
            cmd = category
              ? `python "${scriptPath}" random "${category}"`
              : `python "${scriptPath}" random`;
          }

          const { stdout, stderr } = await exec(cmd);

          if (stderr) {
            console.error('[Lore] stderr:', stderr);
          }

          const result = JSON.parse(stdout.trim());

          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify(result, null, 2),
              },
            ],
          };
        } catch (error: any) {
          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify({
                  error: 'Failed to get lore book',
                  details: error.message,
                }),
              },
            ],
          };
        }
      }

      case 'minecraft_lore_progress': {
        const { player } = args as {
          player: string;
        };

        const { promisify } = await import('util');
        const exec = promisify((await import('child_process')).exec);

        try {
          const scriptPath = `${process.cwd()}/lore_service.py`;
          const { stdout, stderr } = await exec(`python "${scriptPath}" progress "${player}"`);

          if (stderr) {
            console.error('[Lore] stderr:', stderr);
          }

          const result = JSON.parse(stdout.trim());

          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify(result, null, 2),
              },
            ],
          };
        } catch (error: any) {
          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify({
                  error: 'Failed to get lore progress',
                  details: error.message,
                }),
              },
            ],
          };
        }
      }

      case 'minecraft_satchel_view': {
        const { player } = args as {
          player: string;
        };

        const { promisify } = await import('util');
        const exec = promisify((await import('child_process')).exec);

        try {
          // Get full lore progress with details
          const scriptPath = `${process.cwd()}/lore_service.py`;
          const { stdout: progressOut } = await exec(`python "${scriptPath}" progress "${player}"`);
          const progress = JSON.parse(progressOut.trim());

          // Get list of all discovered lore with full details
          const discoveredBooks: any[] = [];

          if (progress.recent && progress.recent.length > 0) {
            for (const loreId of progress.recent) {
              const { stdout: bookOut } = await exec(`python "${scriptPath}" get_book "${loreId}"`);
              const book = JSON.parse(bookOut.trim());
              if (!book.error) {
                discoveredBooks.push({
                  id: book.id,
                  title: book.title,
                  author: book.author,
                  category: book.category_name,
                  summary: book.pages[0].substring(0, 150) + '...',
                });
              }
            }
          }

          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify({
                  player,
                  satchel: {
                    discovered: progress.discovered,
                    total: progress.total,
                    completion: progress.completion,
                    categories: progress.categories,
                    books: discoveredBooks,
                  },
                }, null, 2),
              },
            ],
          };
        } catch (error: any) {
          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify({
                  error: 'Failed to view satchel',
                  details: error.message,
                }),
              },
            ],
          };
        }
      }

      case 'minecraft_party_create': {
        const { player, party_name } = args as {
          player: string;
          party_name?: string;
        };

        const { promisify } = await import('util');
        const exec = promisify((await import('child_process')).exec);

        try {
          const scriptPath = `${process.cwd()}/party_service.py`;
          const nameArg = party_name ? `"${party_name}"` : '';
          const { stdout } = await exec(`python "${scriptPath}" create "${player}" ${nameArg}`);
          const result = JSON.parse(stdout.trim());

          return {
            content: [{ type: 'text', text: JSON.stringify(result, null, 2) }],
          };
        } catch (error: any) {
          return {
            content: [{ type: 'text', text: JSON.stringify({ error: 'Failed to create party', details: error.message }) }],
          };
        }
      }

      case 'minecraft_party_invite': {
        const { player, npc_id } = args as {
          player: string;
          npc_id: string;
        };

        const { promisify } = await import('util');
        const exec = promisify((await import('child_process')).exec);

        try {
          const scriptPath = `${process.cwd()}/party_service.py`;
          const { stdout } = await exec(`python "${scriptPath}" invite "${player}" "${npc_id}"`);
          const result = JSON.parse(stdout.trim());

          return {
            content: [{ type: 'text', text: JSON.stringify(result, null, 2) }],
          };
        } catch (error: any) {
          return {
            content: [{ type: 'text', text: JSON.stringify({ error: 'Failed to invite NPC', details: error.message }) }],
          };
        }
      }

      case 'minecraft_party_leave': {
        const { player, npc_id } = args as {
          player: string;
          npc_id?: string;
        };

        const { promisify } = await import('util');
        const exec = promisify((await import('child_process')).exec);

        try {
          const scriptPath = `${process.cwd()}/party_service.py`;
          const npcArg = npc_id ? `"${npc_id}"` : '';
          const { stdout } = await exec(`python "${scriptPath}" leave "${player}" ${npcArg}`);
          const result = JSON.parse(stdout.trim());

          return {
            content: [{ type: 'text', text: JSON.stringify(result, null, 2) }],
          };
        } catch (error: any) {
          return {
            content: [{ type: 'text', text: JSON.stringify({ error: 'Failed to leave party', details: error.message }) }],
          };
        }
      }

      case 'minecraft_party_chat': {
        const { player, message } = args as {
          player: string;
          message: string;
        };

        const { promisify } = await import('util');
        const exec = promisify((await import('child_process')).exec);

        try {
          const scriptPath = `${process.cwd()}/party_service.py`;
          const { stdout } = await exec(`python "${scriptPath}" chat "${player}" "${message.replace(/"/g, '\\"')}"`);
          const result = JSON.parse(stdout.trim());

          return {
            content: [{ type: 'text', text: JSON.stringify(result, null, 2) }],
          };
        } catch (error: any) {
          return {
            content: [{ type: 'text', text: JSON.stringify({ error: 'Failed to send party chat', details: error.message }) }],
          };
        }
      }

      case 'minecraft_party_discuss': {
        const { player, topic } = args as {
          player: string;
          topic: string;
        };

        const { promisify } = await import('util');
        const exec = promisify((await import('child_process')).exec);

        try {
          const scriptPath = `${process.cwd()}/party_service.py`;
          const { stdout } = await exec(
            `python "${scriptPath}" discuss "${player}" "${topic.replace(/"/g, '\\"')}"`,
            { timeout: 60000 }  // Longer timeout for multi-agent response
          );
          const result = JSON.parse(stdout.trim());

          return {
            content: [{ type: 'text', text: JSON.stringify(result, null, 2) }],
          };
        } catch (error: any) {
          return {
            content: [{ type: 'text', text: JSON.stringify({ error: 'Failed to discuss topic', details: error.message }) }],
          };
        }
      }

      default:
        throw new Error(`Unknown tool: ${name}`);
    }
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : String(error);
    return {
      content: [
        {
          type: 'text',
          text: JSON.stringify({ error: errorMessage }),
        },
      ],
      isError: true,
    };
  }
});
*/

// Start event reactor as background process
let eventReactorProcess: ChildProcess | null = null;

function startEventReactor() {
  const scriptPath = path.join(process.cwd(), 'events/reactor.py');

  eventReactorProcess = spawn('python', [scriptPath, '--interval', '5'], {
    stdio: ['ignore', 'pipe', 'pipe'],
    detached: false,
  });

  eventReactorProcess.stdout?.on('data', (data: Buffer) => {
    console.error('[EventReactor]', data.toString().trim());
  });

  eventReactorProcess.stderr?.on('data', (data: Buffer) => {
    console.error('[EventReactor]', data.toString().trim());
  });

  eventReactorProcess.on('error', (error: Error) => {
    console.error('[EventReactor] Failed to start:', error.message);
  });

  eventReactorProcess.on('exit', (code: number) => {
    console.error(`[EventReactor] Exited with code ${code}`);
    // Restart after 5 seconds if it crashes
    if (code !== 0) {
      setTimeout(startEventReactor, 5000);
    }
  });

  console.error('[EventReactor] Started background event monitoring');
}

// Start server
async function main() {
  console.error('[MCP] main() function started');
  console.error('[MCP] Creating StdioServerTransport...');
  const transport = new StdioServerTransport();
  console.error('[MCP] Connecting server to transport...');
  await server.connect(transport);
  console.error('✅ Minecraft Intelligence MCP Server running on stdio');

  // Start event reactor in background after a short delay
  // This ensures the MCP server is fully initialized first
  console.error('[MCP] Scheduling event reactor start in 2 seconds...');
  setTimeout(() => {
    console.error('[MCP] Starting event reactor now...');
    startEventReactor();
  }, 2000);
}

// Cleanup on exit
process.on('exit', () => {
  if (eventReactorProcess) {
    eventReactorProcess.kill();
  }
});

process.on('SIGINT', () => {
  if (eventReactorProcess) {
    eventReactorProcess.kill();
  }
  process.exit(0);
});

main().catch((error) => {
  console.error('Fatal error:', error);
  process.exit(1);
});
