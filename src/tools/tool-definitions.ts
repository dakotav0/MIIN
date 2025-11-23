/**
 * Tool definitions for Minecraft Intelligence MCP Server
 *
 * All 33 MCP tools organized by category
 */

import { Tool } from '@modelcontextprotocol/sdk/types.js';

// Build & Intelligence Tools
export const BUILD_TOOLS: Tool[] = [
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
];

// Player State & Game Tools
export const GAME_TOOLS: Tool[] = [
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
];

// NPC & Dialogue Tools
export const NPC_TOOLS: Tool[] = [
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
    name: 'minecraft_npc_create',
    description: 'Create a new dynamic NPC from a template at a specific location.',
    inputSchema: {
      type: 'object',
      properties: {
        template_id: {
          type: 'string',
          description: 'ID of the template to use (e.g., "villager", "guard")',
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
        dimension: {
          type: 'string',
          description: 'Dimension (e.g., "minecraft:overworld")',
        },
        biome: {
          type: 'string',
          description: 'Biome name',
        },
        name: {
          type: 'string',
          description: 'Optional specific name for the NPC',
        },
      },
      required: ['template_id', 'x', 'y', 'z', 'dimension', 'biome'],
    },
  },
];

// Quest Tools
export const QUEST_TOOLS: Tool[] = [
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
];

// Milestone Tools
export const MILESTONE_TOOLS: Tool[] = [
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
];

// Lore Tools
export const LORE_TOOLS: Tool[] = [
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
];

// Party Tools
export const PARTY_TOOLS: Tool[] = [
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
];

// Export all tools as a single array
export const ALL_TOOLS: Tool[] = [
  ...BUILD_TOOLS,
  ...GAME_TOOLS,
  ...NPC_TOOLS,
  ...QUEST_TOOLS,
  ...MILESTONE_TOOLS,
  ...LORE_TOOLS,
  ...PARTY_TOOLS,
];
