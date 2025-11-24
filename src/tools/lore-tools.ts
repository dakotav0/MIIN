/**
 * Lore and Discovery Tool Handlers
 *
 * Handles lore book discovery, tracking, and satchel viewing
 */

import { executePythonScript, createErrorResult, createSuccessResult } from '../utils/python-executor.js';
import { ToolHandler } from '../types.js';
import { promisify } from 'util';
import { exec as execCallback } from 'child_process';

const exec = promisify(execCallback);

/**
 * minecraft_lore_get - Get a lore book (by ID or random)
 */
export const loreGetHandler: ToolHandler = async (args) => {
  const { lore_id, category } = args as {
    lore_id?: string;
    category?: string;
  };

  try {
    const scriptPath = `${process.cwd()}/lore/service.py`;
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
    return createSuccessResult(result);
  } catch (error: any) {
    return createErrorResult(error, 'Failed to get lore book');
  }
};

/**
 * minecraft_lore_progress - Get player's lore discovery progress
 */
export const loreProgressHandler: ToolHandler = async (args) => {
  const { player } = args as {
    player: string;
  };

  try {
    const scriptPath = `${process.cwd()}/lore/service.py`;
    const { stdout, stderr } = await exec(`python "${scriptPath}" progress "${player}"`);

    if (stderr) {
      console.error('[Lore] stderr:', stderr);
    }

    const result = JSON.parse(stdout.trim());
    return createSuccessResult(result);
  } catch (error: any) {
    return createErrorResult(error, 'Failed to get lore progress');
  }
};

export const loreGenerateHandler: ToolHandler = async (args) => {
  return createErrorResult(
    new Error('minecraft_lore_generate is not implemented in this build'),
    'Lore generation not available'
  );
};

/**
 * minecraft_lore_query - placeholder (not yet implemented)
 */
export const loreQueryHandler: ToolHandler = async (args) => {
  return createErrorResult(
    new Error('minecraft_lore_query is not implemented in this build'),
    'Lore query not available'
  );
};

/**
 * minecraft_satchel_view - View player's lore satchel with details
 */
export const satchelViewHandler: ToolHandler = async (args) => {
  const { player } = args as {
    player: string;
  };

  try {
    // Get full lore progress with details
    const scriptPath = `${process.cwd()}/lore/service.py`;
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

    return createSuccessResult({
      player,
      satchel: {
        discovered: progress.discovered,
        total: progress.total,
        completion: progress.completion,
        categories: progress.categories,
        books: discoveredBooks,
      },
    });
  } catch (error: any) {
    return createErrorResult(error, 'Failed to view satchel');
  }
};

/**
 * Lore Tool Handler Registry
 */
export const LORE_HANDLERS = {
  'minecraft_lore_get': loreGetHandler,
  'minecraft_lore_progress': loreProgressHandler,
  'minecraft_satchel_view': satchelViewHandler,
  'minecraft_lore_generate': loreGenerateHandler,
  'minecraft_lore_query': loreQueryHandler,
};