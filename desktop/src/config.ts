import { app } from "electron";
import { randomBytes } from "node:crypto";
import * as fs from "node:fs";
import * as path from "node:path";

/** Im Entwicklungs-Modus liegt das Programm unter desktop/; sonst unter process.resourcesPath. */
export const isDev = !app.isPackaged;
export const desktopDir = path.resolve(__dirname, "..");
export const repoDir = path.resolve(desktopDir, "..");

export const userDataDir = app.getPath("userData");
export const dataDir = path.join(userDataDir, "data");
export const postgresDataDir = path.join(dataDir, "postgres");
export const runtimeDir = path.join(dataDir, "runtime");
export const logDir = path.join(userDataDir, "logs");
export const tmpDir = path.join(userDataDir, "tmp");
export const settingsFile = path.join(userDataDir, "settings.json");

export const frontendPort = Number(process.env.FW_FRONTEND_PORT ?? 3000);
export const backendPort = Number(process.env.FW_BACKEND_PORT ?? 8080);
export const postgresPort = Number(process.env.FW_POSTGRES_PORT ?? 55432);

export const frontendOrigin = `http://127.0.0.1:${frontendPort}`;
export const backendOrigin = `http://127.0.0.1:${backendPort}`;

// Die lokale Cluster-Standarddatenbank wird genutzt; dadurch werden keine psql/createdb-Binaries benötigt.
export const dbName = "postgres";
export const dbUser = "fasswerk";

const resourceOverrides = new Map<string, string>();

export function setResourceOverride(name: string, directory: string): void {
  resourceOverrides.set(name, path.resolve(directory));
}

export interface WindowBounds {
  x?: number;
  y?: number;
  width: number;
  height: number;
}

export interface Settings {
  dbPassword: string;
  jwtSecret: string;
  bootstrapEmail?: string;
  bootstrapPassword?: string;
  firstRunDone?: boolean;
  windowBounds?: WindowBounds;
}

/** Ressourcen liegen im Paket direkt unter resourcesPath, im Dev-Modus unter desktop/resources. */
export function resourcePath(name: string): string {
  const [head, ...rest] = name.split(/[\\/]/);
  const override = resourceOverrides.get(head);
  if (override) return path.join(override, ...rest);
  return isDev
    ? path.join(desktopDir, "resources", name)
    : path.join(process.resourcesPath, name);
}

export function binaryName(name: string): string {
  return process.platform === "win32" ? `${name}.exe` : name;
}

export function loadSettings(): Settings {
  let settings: Settings | undefined;
  try {
    settings = JSON.parse(fs.readFileSync(settingsFile, "utf8")) as Settings;
  } catch {
    settings = undefined;
  }

  if (!settings?.jwtSecret || !settings?.dbPassword) {
    settings = {
      jwtSecret: randomBytes(48).toString("hex"),
      dbPassword: randomBytes(24).toString("hex"),
      ...(settings ?? {}),
    };
    saveSettings(settings);
  }

  return settings;
}

export function saveSettings(settings: Settings): void {
  fs.mkdirSync(path.dirname(settingsFile), { recursive: true, mode: 0o700 });
  fs.writeFileSync(settingsFile, JSON.stringify(settings, null, 2), { mode: 0o600 });
  try {
    fs.chmodSync(settingsFile, 0o600);
  } catch {
    // Windows kennt POSIX-Rechte nicht; die Datei liegt trotzdem im privaten userData-Verzeichnis.
  }
}

export function ensureDirectories(): void {
  for (const dir of [dataDir, runtimeDir, logDir, tmpDir]) {
    fs.mkdirSync(dir, { recursive: true });
  }
}
