/**
 * Python script execution utilities
 */

import { promisify } from 'util';
import { exec } from 'child_process';
import path from 'path';
import { PythonExecutionOptions } from '../types.js';

const execPromise = promisify(exec);

/**
 * Execute a Python script and return parsed JSON result
 *
 * @param scriptPath - Relative path to Python script from project root
 * @param args - Command-line arguments for the script
 * @param options - Execution options (timeout, cwd)
 * @returns Parsed JSON result from script stdout
 */
export async function executePythonScript(
  scriptPath: string,
  args: string[] = [],
  options: PythonExecutionOptions = {}
): Promise<any> {
  const fullPath = path.join(process.cwd(), scriptPath);
  const command = `python "${fullPath}" ${args.join(' ')}`;

  const { stdout, stderr } = await execPromise(command, {
    timeout: options.timeout || 120000,
    cwd: options.cwd || process.cwd(),
  });

  if (stderr) {
    console.error(`[Python] ${stderr}`);
  }

  return JSON.parse(stdout.trim());
}

/**
 * Create a tool result with error handling
 *
 * @param error - Error object
 * @param context - Additional context for debugging
 * @returns Tool result with error information
 */
export function createErrorResult(error: any, context?: string): any {
  const errorMessage = context
    ? `${context}: ${error.message}`
    : error.message;

  return {
    content: [
      {
        type: 'text',
        text: JSON.stringify({
          error: errorMessage,
          details: error.stack,
        }, null, 2),
      },
    ],
    isError: true,
  };
}

/**
 * Create a successful tool result
 *
 * @param data - Result data to return
 * @returns Tool result with data
 */
export function createSuccessResult(data: any): any {
  return {
    content: [
      {
        type: 'text',
        text: JSON.stringify(data, null, 2),
      },
    ],
  };
}
