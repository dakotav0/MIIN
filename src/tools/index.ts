/**
 * Tool Router - Central registry for all tool handlers
 *
 * Aggregates handlers from all tool modules and routes tool calls
 */

import { ToolHandler, ToolHandlerRegistry } from '../types.js';
import { BUILD_HANDLERS, initializeBuildTools } from './build-tools.js';
import { GAME_HANDLERS, initializeGameTools } from './game-tools.js';
import { NPC_HANDLERS } from './npc-tools.js';
import { QUEST_HANDLERS } from './quest-tools.js';
import { MILESTONE_HANDLERS } from './milestone-tools.js';
import { LORE_HANDLERS } from './lore-tools.js';
import { PARTY_HANDLERS } from './party-tools.js';
import type { IntelligenceBridge } from '../intelligence-bridge.js';
import type { MinecraftEventTracker } from '../event-tracker.js';

/**
 * Complete registry of all tool handlers
 */
export const TOOL_HANDLERS: ToolHandlerRegistry = {
  // Build analysis tools (5 tools)
  ...BUILD_HANDLERS,

  // Game interaction tools (6 tools)
  ...GAME_HANDLERS,

  // NPC and dialogue tools (7 tools)
  ...NPC_HANDLERS,

  // Quest and challenge tools (7 tools)
  ...QUEST_HANDLERS,

  // Milestone tools (2 tools)
  ...MILESTONE_HANDLERS,

  // Lore discovery tools (3 tools)
  ...LORE_HANDLERS,

  // Party management tools (6 tools)
  ...PARTY_HANDLERS,
};

/**
 * Initialize tool handlers with dependencies
 */
export function initializeToolHandlers(
  intelligenceBridge: IntelligenceBridge,
  eventTracker: MinecraftEventTracker
) {
  initializeBuildTools(intelligenceBridge, eventTracker);
  initializeGameTools(eventTracker);
}

/**
 * Route a tool call to the appropriate handler
 */
export async function routeToolCall(toolName: string, args: any): Promise<any> {
  const handler = TOOL_HANDLERS[toolName];

  if (!handler) {
    throw new Error(`Unknown tool: ${toolName}`);
  }

  try {
    return await handler(args);
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
}

/**
 * Get list of all registered tool names
 */
export function getRegisteredTools(): string[] {
  return Object.keys(TOOL_HANDLERS);
}

/**
 * Check if a tool is registered
 */
export function isToolRegistered(toolName: string): boolean {
  return toolName in TOOL_HANDLERS;
}
