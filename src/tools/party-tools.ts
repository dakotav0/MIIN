/**
 * Party Management Tool Handlers
 *
 * Handles party creation, invites, chat, and multi-NPC discussions
 */

import { executePythonScript, createErrorResult, createSuccessResult } from '../utils/python-executor.js';
import { ToolHandler } from '../types.js';
import { promisify } from 'util';
import { exec as execCallback } from 'child_process';

const exec = promisify(execCallback);

/**
 * minecraft_party_create - Create a new party
 */
export const partyCreateHandler: ToolHandler = async (args) => {
  const { player, party_name } = args as {
    player: string;
    party_name?: string;
  };

  try {
    const scriptPath = `${process.cwd()}/party/service.py`;
    const nameArg = party_name ? `"${party_name}"` : '';
    const { stdout } = await exec(`python "${scriptPath}" create "${player}" ${nameArg}`);
    const result = JSON.parse(stdout.trim());

    return createSuccessResult(result);
  } catch (error: any) {
    return createErrorResult(error, 'Failed to create party');
  }
};

/**
 * minecraft_party_invite - Invite an NPC to the party
 */
export const partyInviteHandler: ToolHandler = async (args) => {
  const { player, npc_id } = args as {
    player: string;
    npc_id: string;
  };

  try {
    const scriptPath = `${process.cwd()}/party/service.py`;
    const { stdout } = await exec(`python "${scriptPath}" invite "${player}" "${npc_id}"`);
    const result = JSON.parse(stdout.trim());

    return createSuccessResult(result);
  } catch (error: any) {
    return createErrorResult(error, 'Failed to invite NPC');
  }
};

/**
 * minecraft_party_leave - Remove an NPC from party or disband party
 */
export const partyLeaveHandler: ToolHandler = async (args) => {
  const { player, npc_id } = args as {
    player: string;
    npc_id?: string;
  };

  try {
    const scriptPath = `${process.cwd()}/party/service.py`;
    const npcArg = npc_id ? `"${npc_id}"` : '';
    const { stdout } = await exec(`python "${scriptPath}" leave "${player}" ${npcArg}`);
    const result = JSON.parse(stdout.trim());

    return createSuccessResult(result);
  } catch (error: any) {
    return createErrorResult(error, 'Failed to leave party');
  }
};

/**
 * minecraft_party_chat - Send a message to all party members
 */
export const partyChatHandler: ToolHandler = async (args) => {
  const { player, message } = args as {
    player: string;
    message: string;
  };

  try {
    const scriptPath = `${process.cwd()}/party/service.py`;
    const { stdout } = await exec(`python "${scriptPath}" chat "${player}" "${message.replace(/"/g, '\\"')}"`);
    const result = JSON.parse(stdout.trim());

    return createSuccessResult(result);
  } catch (error: any) {
    return createErrorResult(error, 'Failed to send party chat');
  }
};

/**
 * minecraft_party_status - Get current party status
 */
export const partyStatusHandler: ToolHandler = async (args) => {
  const { player } = args as {
    player: string;
  };

  try {
    const scriptPath = `${process.cwd()}/party/service.py`;
    const { stdout } = await exec(`python "${scriptPath}" status "${player}"`);
    const result = JSON.parse(stdout.trim());

    return createSuccessResult(result);
  } catch (error: any) {
    return createErrorResult(error, 'Failed to get party status');
  }
};

/**
 * minecraft_party_discuss - Discuss a topic with all party NPCs
 */
export const partyDiscussHandler: ToolHandler = async (args) => {
  const { player, topic } = args as {
    player: string;
    topic: string;
  };

  try {
    const scriptPath = `${process.cwd()}/party/service.py`;
    const { stdout } = await exec(
      `python "${scriptPath}" discuss "${player}" "${topic.replace(/"/g, '\\"')}"`,
      { timeout: 60000 }  // Longer timeout for multi-agent response
    );
    const result = JSON.parse(stdout.trim());

    return createSuccessResult(result);
  } catch (error: any) {
    return createErrorResult(error, 'Failed to discuss topic');
  }
};

/**
 * Party Tool Handler Registry
 */
export const PARTY_HANDLERS = {
  'minecraft_party_create': partyCreateHandler,
  'minecraft_party_invite': partyInviteHandler,
  'minecraft_party_leave': partyLeaveHandler,
  'minecraft_party_chat': partyChatHandler,
  'minecraft_party_status': partyStatusHandler,
  'minecraft_party_discuss': partyDiscussHandler,
};
