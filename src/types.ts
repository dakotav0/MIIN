/**
 * Shared TypeScript types for Minecraft MCP Server
 */

import { CallToolResult } from '@modelcontextprotocol/sdk/types.js';

/**
 * Tool handler function type
 */
export type ToolHandler = (args: any) => Promise<CallToolResult>;

/**
 * Tool handler registry type
 */
export type ToolHandlerRegistry = Record<string, ToolHandler>;

/**
 * Python script execution options
 */
export interface PythonExecutionOptions {
  timeout?: number;
  cwd?: string;
}

/**
 * Intelligence bridge configuration
 */
export interface IntelligenceBridgeConfig {
  musicIntelligenceUrl: string;
  unifiedIntelligenceUrl: string;
}

/**
 * Player state data
 */
export interface PlayerState {
  x: number;
  y: number;
  z: number;
  dimension: string;
  health: number;
  food: number;
  xp: number;
  gamemode: string;
}

/**
 * Event data structure
 */
export interface MinecraftEvent {
  timestamp: number;
  type: string;
  data: any;
}

/**
 * Build analysis result
 */
export interface BuildAnalysis {
  theme?: string;
  style?: string;
  complexity?: string;
  patterns?: string[];
  suggestions?: string[];
}
