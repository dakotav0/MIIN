import { spawn, ChildProcessWithoutNullStreams } from 'child_process';
import path from 'path';
import { randomUUID } from 'crypto';

type DialogueCommand =
  | 'options'
  | 'select'
  | 'start_llm'
  | 'respond';

type PendingRequest = {
  resolve: (value: any) => void;
  reject: (reason?: any) => void;
};

/**
 * DialogueDaemon spawns a long-lived Python dialogue service and
 * multiplexes JSON-line requests to avoid reloading NPC/Lore/LLM
 * state on every tool call.
 */
class DialogueDaemon {
  private proc: ChildProcessWithoutNullStreams | null = null;
  private pending = new Map<string, PendingRequest>();
  private buffer = '';

  constructor() {
    this.start();
    this.handleExit();
  }

  /** Ensure the process is started */
  private start() {
    if (this.proc) return;

    const scriptPath = path.join(process.cwd(), 'dialogue', 'service.py');
    this.proc = spawn('python', [scriptPath, 'serve'], {
      cwd: process.cwd(),
      env: { ...process.env },
      stdio: ['pipe', 'pipe', 'pipe'],
    });

    this.proc.stdout.on('data', (data: Buffer) => {
      this.buffer += data.toString();

      let idx: number;
      while ((idx = this.buffer.indexOf('\n')) >= 0) {
        const line = this.buffer.slice(0, idx).trim();
        this.buffer = this.buffer.slice(idx + 1);
        if (!line) continue;
        try {
          const msg = JSON.parse(line);
          const { id, result, error } = msg;
          const pending = this.pending.get(id);
          if (pending) {
            this.pending.delete(id);
            if (error) pending.reject(new Error(error));
            else pending.resolve(result);
          }
        } catch (err) {
          // Drop malformed lines; should not happen in normal operation
        }
      }
    });

    this.proc.stderr.on('data', (data: Buffer) => {
      console.error('[DialogueDaemon stderr]', data.toString().trim());
    });

    this.proc.on('exit', (code) => {
      // Reject all pending requests if the process dies
      for (const [, pending] of this.pending) {
        pending.reject(new Error(`Dialogue daemon exited (${code})`));
      }
      this.pending.clear();
      this.proc = null;
    });
  }

  /** Graceful shutdown */
  private handleExit() {
    const cleanup = () => {
      if (this.proc) {
        this.proc.kill();
        this.proc = null;
      }
    };
    process.on('exit', cleanup);
    process.on('SIGINT', () => {
      cleanup();
      process.exit();
    });
    process.on('SIGTERM', () => {
      cleanup();
      process.exit();
    });
  }

  /**
   * Send a command to the daemon and return the parsed result.
   */
  async call(command: DialogueCommand, args: Record<string, any>): Promise<any> {
    if (!this.proc) this.start();
    if (!this.proc || !this.proc.stdin.writable) {
      throw new Error('Dialogue daemon is not available');
    }

    const id = randomUUID();
    const payload = JSON.stringify({ id, command, args });

    const promise = new Promise<any>((resolve, reject) => {
      this.pending.set(id, { resolve, reject });
      this.proc!.stdin.write(payload + '\n', (err) => {
        if (err) {
          this.pending.delete(id);
          reject(err);
        }
      });
    });

    return promise;
  }
}

export const dialogueDaemon = new DialogueDaemon();
