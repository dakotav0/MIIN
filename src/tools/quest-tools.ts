/**
 * Quest and Build Challenge Tool Handlers
 *
 * Handles quest requests, status tracking, progression, and build challenges
 */

import { executePythonScript, createErrorResult, createSuccessResult } from '../utils/python-executor.js';
import { ToolHandler } from '../types.js';
import { promisify } from 'util';
import { exec as execCallback } from 'child_process';
import { assertArgs } from '../utils/assert-args.js';

const exec = promisify(execCallback);

/**
 * minecraft_quest_request - Request a quest from an NPC
 */
export const questRequestHandler: ToolHandler = async (args) => {
  const { npc, player } = assertArgs<{
    npc: string;
    player: string;
  }>(args, ['npc', 'player']);

  try {
    const scriptPath = `${process.cwd()}/npc/quests/request.py`;
    const { stdout, stderr } = await exec(`python "${scriptPath}" "${npc}" "${player}"`);

    if (stderr) {
      console.error('[NPC] stderr:', stderr);
    }

    const quest = JSON.parse(stdout.trim());
    return createSuccessResult(quest || { error: 'Failed to generate quest' });
  } catch (error: any) {
    return createErrorResult(error, 'Failed to generate quest');
  }
};

/**
 * minecraft_quest_status - Get player's quest status
 */
export const questStatusHandler: ToolHandler = async (args) => {
  const { player } = assertArgs<{ player: string }>(args, ['player']);

  try {
    const scriptPath = `${process.cwd()}/npc/quests/status.py`;
    const { stdout, stderr } = await exec(`python "${scriptPath}" "${player}"`);

    if (stderr) {
      console.error('[NPC] stderr:', stderr);
    }

    const quests = JSON.parse(stdout.trim());
    return createSuccessResult(quests);
  } catch (error: any) {
    return createErrorResult(error, 'Failed to get quest status');
  }
};

/**
 * minecraft_quest_check_progress - Check player's quest progress
 */
export const questCheckProgressHandler: ToolHandler = async (args) => {
  const { player } = assertArgs<{ player: string }>(args, ['player']);

  try {
    const scriptPath = `${process.cwd()}/npc/quests/check_progress.py`;
    const { stdout, stderr } = await exec(`python "${scriptPath}" "${player}"`);

    if (stderr) {
      console.error('[NPC] stderr:', stderr);
    }

    const progress = JSON.parse(stdout.trim());
    return createSuccessResult(progress);
  } catch (error: any) {
    return createErrorResult(error, 'Failed to check quest progress');
  }
};

/**
 * minecraft_quest_accept - Accept a quest
 */
export const questAcceptHandler: ToolHandler = async (args) => {
  const { npc, player, quest_id } = assertArgs<{
    npc: string;
    player: string;
    quest_id: string;
  }>(args, ['npc', 'player', 'quest_id']);

  try {
    const scriptPath = `${process.cwd()}/npc/quests/accept.py`;
    const { stdout, stderr } = await exec(`python "${scriptPath}" "${npc}" "${player}" "${quest_id}"`);

    if (stderr) {
      console.error('[NPC] stderr:', stderr);
    }

    const result = JSON.parse(stdout.trim());
    return createSuccessResult(result);
  } catch (error: any) {
    return createErrorResult(error, 'Failed to accept quest');
  }
};

/**
 * minecraft_build_challenge_request - Request a build challenge
 */
export const buildChallengeRequestHandler: ToolHandler = async (args) => {
  const { npc, player, challenge_id } = assertArgs<{
    npc: string;
    player: string;
    challenge_id?: string;
  }>(args, ['npc', 'player']);

  try {
    const scriptPath = `${process.cwd()}/npc/challenges/request.py`;
    const cmd = challenge_id
      ? `python "${scriptPath}" "${npc}" "${player}" "${challenge_id}"`
      : `python "${scriptPath}" "${npc}" "${player}"`;
    const { stdout, stderr } = await exec(cmd);

    if (stderr) {
      console.error('[Build Challenge] stderr:', stderr);
    }

    const quest = JSON.parse(stdout.trim());
    return createSuccessResult(quest);
  } catch (error: any) {
    return createErrorResult(error, 'Failed to generate build challenge');
  }
};

/**
 * minecraft_build_challenge_validate - Validate completed build challenge
 */
export const buildChallengeValidateHandler: ToolHandler = async (args) => {
  const { player, quest_id, build_data } = assertArgs<{
    player: string;
    quest_id: string;
    build_data: { blocks: Record<string, number>; height: number };
  }>(args, ['player', 'quest_id', 'build_data']);

  try {
    const scriptPath = `${process.cwd()}/npc/challenges/validate.py`;
    const buildDataJson = JSON.stringify(build_data).replace(/"/g, '\\"');
    const { stdout, stderr } = await exec(`python "${scriptPath}" "${player}" "${quest_id}" "${buildDataJson}"`);

    if (stderr) {
      console.error('[Build Challenge] stderr:', stderr);
    }

    const result = JSON.parse(stdout.trim());
    return createSuccessResult(result);
  } catch (error: any) {
    return createErrorResult(error, 'Failed to validate build challenge');
  }
};

/**
 * minecraft_build_challenge_list - List available build challenges
 */
export const buildChallengeListHandler: ToolHandler = async (args) => {
  const { npc } = assertArgs<{ npc?: string }>(args, []);

  try {
    const scriptPath = `${process.cwd()}/npc/challenges/list.py`;
    const cmd = npc
      ? `python "${scriptPath}" "${npc}"`
      : `python "${scriptPath}"`;
    const { stdout, stderr } = await exec(cmd);

    if (stderr) {
      console.error('[Build Challenge] stderr:', stderr);
    }

    const challenges = JSON.parse(stdout.trim());
    return createSuccessResult(challenges);
  } catch (error: any) {
    return createErrorResult(error, 'Failed to list build challenges');
  }
};

/**
 * Quest Tool Handler Registry
 */
export const QUEST_HANDLERS = {
  'minecraft_quest_request': questRequestHandler,
  'minecraft_quest_status': questStatusHandler,
  'minecraft_quest_check_progress': questCheckProgressHandler,
  'minecraft_quest_accept': questAcceptHandler,
  'minecraft_build_challenge_request': buildChallengeRequestHandler,
  'minecraft_build_challenge_validate': buildChallengeValidateHandler,
  'minecraft_build_challenge_list': buildChallengeListHandler,
};
