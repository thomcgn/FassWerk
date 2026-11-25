import * as fs from "node:fs";
import * as path from "node:path";
import { logDir } from "./config";

export class Logger {
  private readonly file: string;

  constructor(name: string) {
    fs.mkdirSync(logDir, { recursive: true });
    this.file = path.join(logDir, `${name}.log`);
  }

  info(message: string): void {
    this.write("INFO", message);
  }

  error(message: string): void {
    this.write("ERROR", message);
  }

  /** Schreibt eine komplette Prozessausgabe zeilenweise mit dem gegebenen Prefix. */
  stream(prefix: string, chunk: Buffer | string): void {
    const text = chunk.toString();
    for (const line of text.split(/\r?\n/)) {
      if (line.trim()) this.write(prefix, line);
    }
  }

  private write(level: string, message: string): void {
    const line = `[${new Date().toISOString()}] [${level}] ${message}\n`;
    try {
      fs.appendFileSync(this.file, line);
    } catch {
      // Logging darf den Start nicht verhindern, falls userData gerade nicht schreibbar ist.
    }
    if (level === "ERROR") console.error(line.trimEnd());
  }
}
