/**
 * NPC and Dialogue Tool Handlers
 *
 * Handles all NPC interaction, dialogue wheels, and conversation management
 */

import { executePythonScript, createErrorResult, createSuccessResult } from '../utils/python-executor.js';
import { ToolHandler } from '../types.js';
import { promisify } from 'util';
import { exec as execCallback } from 'child_process';
import { assertArgs } from '../utils/assert-args.js';
import { dialogueDaemon } from '../services/dialogue-daemon.js';

const exec = promisify(execCallback);

/**
 * minecraft_npc_talk - Basic NPC conversation
 */
export const npcTalkHandler: ToolHandler = async (args) => {
  const { npc, player, message } = assertArgs<{
    npc: string;
    player: string;
    message: string;
  }>(args, ['npc', 'player', 'message']);

  try {
    const scriptPath = `${process.cwd()}/npc/scripts/talk.py`;
    // Escape message properly for shell (double quotes to preserve full message)
    const escapedMessage = message.replace(/"/g, '\\"');
    const { stdout, stderr } = await exec(`python "${scriptPath}" "${npc}" "${player}" "${escapedMessage}"`);

    if (stderr) {
      console.error('[NPC] stderr:', stderr);
    }

    const result = JSON.parse(stdout.trim());
    return createSuccessResult(result);
  } catch (error: any) {
    return createErrorResult(error, 'Failed to get NPC response');
  }
};

/**
 * minecraft_npc_talk_with_suggestions - NPC talk with follow-up suggestions
 */
export const npcTalkWithSuggestionsHandler: ToolHandler = async (args) => {
  const { npc, player, message, request_suggestions } = assertArgs<{
    npc: string;
    player: string;
    message: string;
    request_suggestions?: boolean;
  }>(args, ['npc', 'player', 'message']);

  try {
    const scriptPath = `${process.cwd()}/npc/scripts/talk.py`;
    // Escape message properly for shell (double quotes to preserve full message)
    const escapedMessage = message.replace(/"/g, '\\"');
    const suggestionsFlag = request_suggestions ? '--suggestions' : '';
    const { stdout, stderr } = await exec(
      `python "${scriptPath}" "${npc}" "${player}" "${escapedMessage}" ${suggestionsFlag}`,
      { timeout: 150000 }
    );

    if (stderr) {
      console.error('[NPC] stderr:', stderr);
    }

    const result = JSON.parse(stdout.trim());
    return createSuccessResult(result);
  } catch (error: any) {
    return createErrorResult(error, 'Failed to get NPC response with suggestions');
  }
};

/**
 * minecraft_npc_list - List all available NPCs
 */
export const npcListHandler: ToolHandler = async (args) => {
  try {
    const scriptPath = `${process.cwd()}/npc/scripts/list.py`;
    const { stdout, stderr } = await exec(`python "${scriptPath}"`);

    if (stderr) {
      console.error('[NPC] stderr:', stderr);
    }

    const result = JSON.parse(stdout.trim());
    return createSuccessResult(result);
  } catch (error: any) {
    return createErrorResult(error, 'Failed to list NPCs');
  }
};

/**
 * minecraft_dialogue_start_llm - Start LLM-driven dialogue
 */
export const dialogueStartLLMHandler: ToolHandler = async (args) => {
  const { npc, player, nearby_entities } = assertArgs<{
    npc: string;
    player: string;
    nearby_entities?: any[];
  }>(args, ['npc', 'player']);

  try {
    const result = await dialogueDaemon.call('start_llm', {
      npc,
      player,
      nearby_entities: nearby_entities ?? [],
    });
    return createSuccessResult(result);
  } catch (error: any) {
    return createErrorResult(error, 'Failed to start LLM dialogue');
  }
};

/**
 * minecraft_dialogue_respond - Respond in LLM-driven dialogue
 */
export const dialogueRespondHandler: ToolHandler = async (args) => {
  const { conversation_id, npc, player, option_text, nearby_entities } = assertArgs<{
    conversation_id: string;
    npc: string;
    player: string;
    option_text: string;
    nearby_entities?: any[];
  }>(args, ['conversation_id', 'npc', 'player', 'option_text']);

  try {
    const result = await dialogueDaemon.call('respond', {
      conversation_id,
      npc,
      player,
      option_text,
      nearby_entities: nearby_entities ?? [],
    });
    return createSuccessResult(result);
  } catch (error: any) {
    return createErrorResult(error, 'Failed to respond in dialogue');
  }
};

/**
 * minecraft_dialogue_options - Get BG3-style dialogue options
 */
export const dialogueOptionsHandler: ToolHandler = async (args) => {
  const { npc, player, context } = assertArgs<{
    npc: string;
    player: string;
    context?: string;
  }>(args, ['npc', 'player']);

  try {
    const options = await dialogueDaemon.call('options', {
      npc,
      player,
      context: context || 'greeting',
    });
    return createSuccessResult(options);
  } catch (error: any) {
    return createErrorResult(error, 'Failed to get dialogue options');
  }
};

/**
 * minecraft_dialogue_select - Select a dialogue option
 */
export const dialogueSelectHandler: ToolHandler = async (args) => {
  const { npc, player, option_id, option_text, relationship_delta } = assertArgs<{
    npc: string;
    player: string;
    option_id: number;
    option_text: string;
    relationship_delta?: number;
  }>(args, ['npc', 'player', 'option_id', 'option_text']);

  try {
    const result = await dialogueDaemon.call('select', {
      npc,
      player,
      option_id,
      option_text,
      relationship_delta: relationship_delta ?? 0,
    });
    return createSuccessResult(result);
  } catch (error: any) {
    return createErrorResult(error, 'Failed to select dialogue option');
  }
};

export const npcCreateHandler: ToolHandler = async (args) => {
  const { template_id, x, y, z, dimension, biome, name } = assertArgs<{
    template_id: string;
    x: number;
    y: number;
    z: number;
    dimension: string;
    biome: string;
    name?: string;
  }>(args, ['template_id', 'x', 'y', 'z', 'dimension', 'biome']);

  try {
    const scriptPath = `${process.cwd()}/npc/scripts/create.py`;
    const nameArg = name ? `--name "${name.replace(/"/g, '\\"')}"` : '';

    const { stdout, stderr } = await exec(
      `python "${scriptPath}" "${template_id}" ${x} ${y} ${z} "${dimension}" "${biome}" ${nameArg}`
    );

    if (stderr) {
      console.error(`[NPC Create] Stderr: ${stderr}`);
    }

    try {
      const result = JSON.parse(stdout);
      return createSuccessResult(result);
    } catch (e) {
      return createErrorResult(e, `Error parsing output: ${stdout}`);
    }
  } catch (error: any) {
    return createErrorResult(error, 'Failed to create NPC');
  }
};

/**
 * NPC Tool Handler Registry
 */
export const NPC_HANDLERS = {
  'minecraft_npc_talk': npcTalkHandler,
  'minecraft_npc_talk_with_suggestions': npcTalkWithSuggestionsHandler,
  'minecraft_npc_list': npcListHandler,
  'minecraft_dialogue_start_llm': dialogueStartLLMHandler,
  'minecraft_dialogue_respond': dialogueRespondHandler,
  'minecraft_dialogue_options': dialogueOptionsHandler,
  'minecraft_dialogue_select': dialogueSelectHandler,
  'minecraft_npc_create': npcCreateHandler,
  'minecraft_generate_dynamic_npc': npcCreateHandler,
};
