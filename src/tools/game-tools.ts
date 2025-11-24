/**
 * Game Interaction Tool Handlers
 *
 * Handles player state, inventory, chat, teleport, and event tracking
 */

import { ToolHandler } from '../types.js';
import { MinecraftEventTracker } from '../event-tracker.js';
import { assertArgs } from '../utils/assert-args.js';

// Import event tracker instance (will be injected via factory)
let eventTracker: MinecraftEventTracker;

/**
 * Initialize game tools with event tracker
 */
export function initializeGameTools(tracker: MinecraftEventTracker) {
  eventTracker = tracker;
}

/**
 * Helper function to calculate total distance traveled
 */
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

/**
 * minecraft_track_event - Track a Minecraft event
 */
export const trackEventHandler: ToolHandler = async (args) => {
  const { eventType, data } = assertArgs<{
    eventType: string;
    data: any;
  }>(args, ['eventType', 'data']);

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
};

/**
 * minecraft_send_chat - Send chat message to player(s)
 */
export const sendChatHandler: ToolHandler = async (args) => {
  const { message, player } = assertArgs<{
    message: string;
    player?: string;
  }>(args, ['message']);

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
};

/**
 * minecraft_get_inventory - Get player inventory snapshot
 */
export const getInventoryHandler: ToolHandler = async (args) => {
  const { player } = assertArgs<{ player: string }>(args, ['player']);

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
};

/**
 * minecraft_get_player_state - Get current player state
 */
export const getPlayerStateHandler: ToolHandler = async (args) => {
  const { player } = assertArgs<{ player: string }>(args, ['player']);

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
};

/**
 * minecraft_get_recent_activity - Get recent player activity summary
 */
export const getRecentActivityHandler: ToolHandler = async (args) => {
  const { player, minutes } = assertArgs<{
    player: string;
    minutes?: number;
  }>(args, ['player']);

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
};

/**
 * minecraft_teleport - Teleport player to coordinates
 */
export const teleportHandler: ToolHandler = async (args) => {
  const { player, x, y, z } = assertArgs<{
    player: string;
    x: number;
    y: number;
    z: number;
  }>(args, ['player', 'x', 'y', 'z']);

  try {
    const response = await fetch('http://localhost:5558/command', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        type: 'teleport',
        data: { player, x, y, z },
      }),
    });

    const result = await response.json();

    // Track teleport event
    eventTracker.trackEvent('ai_teleport', {
      player,
      destination: { x, y, z },
      timestamp: new Date().toISOString(),
    });

    return {
      content: [
        {
          type: 'text',
          text: JSON.stringify({
            success: true,
            player,
            destination: { x, y, z },
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
            error: 'Failed to teleport player',
            details: error.message,
            note: 'Minecraft bridge may not be available',
          }),
        },
      ],
    };
  }
};

/**
 * Game Tool Handler Registry
 */
export const GAME_HANDLERS = {
  'minecraft_track_event': trackEventHandler,
  'minecraft_send_chat': sendChatHandler,
  'minecraft_get_inventory': getInventoryHandler,
  'minecraft_get_player_state': getPlayerStateHandler,
  'minecraft_get_recent_activity': getRecentActivityHandler,
  'minecraft_teleport': teleportHandler,
};
