/**
 * Milestone Tool Handlers
 *
 * Handles milestone checking and tracking for player progression
 */

import { executePythonScript, createErrorResult, createSuccessResult } from '../utils/python-executor.js';
import { ToolHandler } from '../types.js';
import { promisify } from 'util';
import { exec as execCallback } from 'child_process';
import { assertArgs } from '../utils/assert-args.js';

const exec = promisify(execCallback);

/**
 * minecraft_check_milestones - Check milestones for a player
 */
export const checkMilestonesHandler: ToolHandler = async (args) => {
  const { player } = assertArgs<{ player: string }>(args, ['player']);

  try {
    const scriptPath = `${process.cwd()}/milestones/service.py`;
    const { stdout, stderr } = await exec(`python "${scriptPath}" check "${player}"`);

    if (stderr) {
      console.error('[Milestones] stderr:', stderr);
    }

    const milestones = JSON.parse(stdout.trim());
    return createSuccessResult(milestones);
  } catch (error: any) {
    return createErrorResult(error, 'Failed to check milestones');
  }
};

/**
 * minecraft_list_milestones - List all milestones for a player
 */
export const listMilestonesHandler: ToolHandler = async (args) => {
  const { player } = assertArgs<{ player: string }>(args, ['player']);

  try {
    const scriptPath = `${process.cwd()}/milestones/service.py`;
    const { stdout, stderr } = await exec(`python "${scriptPath}" list "${player}"`);

    if (stderr) {
      console.error('[Milestones] stderr:', stderr);
    }

    const milestones = JSON.parse(stdout.trim());
    return createSuccessResult(milestones);
  } catch (error: any) {
    return createErrorResult(error, 'Failed to list milestones');
  }
};

/**
 * Milestone Tool Handler Registry
 */
export const MILESTONE_HANDLERS = {
  'minecraft_check_milestones': checkMilestonesHandler,
  'minecraft_list_milestones': listMilestonesHandler,
};
