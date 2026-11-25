import { spawn, type ChildProcess, type SpawnOptions } from "node:child_process";
import { once } from "node:events";
import * as fs from "node:fs";
import * as net from "node:net";
import * as path from "node:path";
import {
  backendOrigin,
  backendPort,
  binaryName,
  dataDir,
  dbName,
  dbUser,
  frontendOrigin,
  frontendPort,
  isDev,
  logDir,
  postgresPort,
  repoDir,
  resourcePath,
  runtimeDir,
  tmpDir,
  type Settings,
} from "./config";
import { Logger } from "./logger";

export type BootstrapStep = "runtime" | "database" | "backend" | "frontend" | "ready";
export type StepState = "pending" | "active" | "done" | "error";

export interface BootstrapProgress {
  step: BootstrapStep;
  state: StepState;
  message: string;
}

export interface BootstrapCallbacks {
  onProgress: (progress: BootstrapProgress) => void;
}

const STEP_LABELS: Record<Exclude<BootstrapStep, "ready" | "runtime">, { active: string; done: string }> = {
  database: { active: "Datenbank wird vorbereitet …", done: "Datenbank ist bereit." },
  backend: { active: "Backend wird gestartet …", done: "Backend ist bereit." },
  frontend: { active: "Oberfläche wird gestartet …", done: "Oberfläche ist bereit." },
};

const delay = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

function postfix(name: string): string {
  return binaryName(name);
}

function resolvePostgresBin(): { dir: string; exe: (name: string) => string } {
  const bundled = resourcePath(path.join("postgres", "bin"));
  const dir = process.env.FW_POSTGRES_BIN ?? (fs.existsSync(bundled) ? bundled : undefined);
  if (!dir || !fs.existsSync(path.join(dir, postfix("initdb")))) {
    throw new Error(
      "PostgreSQL-Binaries fehlen. Bitte zuerst 'npm run prepare:resources' mit PG_HOME ausführen " +
      "oder FW_POSTGRES_BIN auf ein Verzeichnis mit initdb/pg_ctl setzen.",
    );
  }
  return { dir, exe: (name) => path.join(dir, postfix(name)) };
}

function resolveJava(): string {
  const bundled = resourcePath(path.join("jre", "bin", postfix("java")));
  if (fs.existsSync(bundled)) return bundled;
  if (process.env.FW_JAVA) return process.env.FW_JAVA;
  if (process.env.JAVA_HOME) return path.join(process.env.JAVA_HOME, "bin", postfix("java"));
  return "java";
}

function resolveBackendJar(): string {
  if (process.env.FW_BACKEND_JAR) return process.env.FW_BACKEND_JAR;
  const target = resourcePath(path.join("backend", "backend.jar"));
  if (fs.existsSync(target)) return target;
  const dir = path.dirname(target);
  const candidates = fs.existsSync(dir)
    ? fs.readdirSync(dir).filter((name) => /^backend-.*\.jar$/.test(name))
    : [];
  if (candidates.length > 0) return path.join(dir, candidates[0]);
  throw new Error(
    "Backend-JAR fehlt. Bitte zuerst das Backend bauen (backend/mvnw -Dmaven.test.skip=true package) " +
    "oder FW_BACKEND_JAR setzen.",
  );
}

function resolveFrontend(): { serverJs: string; cwd: string } {
  const source = isDev
    ? path.join(repoDir, "frontend", ".next", "standalone")
    : resourcePath("frontend");

  if (!fs.existsSync(path.join(source, "server.js"))) {
    throw new Error(
      "Next.js-Standalone-Build fehlt. Bitte zuerst 'cd frontend && npm ci && npm run build' ausführen.",
    );
  }

  // Der reine Standalone-Output enthält .next/static und public nicht. Ohne diese
  // Dateien liefert Next.js die Seiten zwar aus, aber ohne CSS und JavaScript.
  // Deshalb immer in eine vollständige, beschreibbare Laufzeit-Kopie legen.
  const target = path.join(runtimeDir, "frontend");
  const sourceId = readBuildId(source);
  const targetId = readBuildId(target);
  const complete = fs.existsSync(path.join(target, ".next", "static"))
    && fs.existsSync(path.join(target, "public"));
  if (sourceId !== targetId || !complete) {
    fs.rmSync(target, { recursive: true, force: true });
    fs.mkdirSync(path.dirname(target), { recursive: true });
    fs.cpSync(source, target, { recursive: true });

    if (isDev) {
      const frontendDir = path.join(repoDir, "frontend");
      fs.cpSync(path.join(frontendDir, ".next", "static"), path.join(target, ".next", "static"), { recursive: true });
      fs.cpSync(path.join(frontendDir, "public"), path.join(target, "public"), { recursive: true });
    }
  }
  return { serverJs: path.join(target, "server.js"), cwd: target };
}

function readBuildId(dir: string): string | null {
  try {
    return fs.readFileSync(path.join(dir, ".next", "BUILD_ID"), "utf8").trim();
  } catch {
    return null;
  }
}

async function runOnce(
  command: string,
  args: string[],
  log: Logger,
  options: Omit<SpawnOptions, "stdio"> = {},
): Promise<{ code: number; output: string }> {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, {
      ...options,
      stdio: ["ignore", "pipe", "pipe"],
      windowsHide: true,
    });
    let output = "";
    const collect = (chunk: Buffer) => {
      const text = chunk.toString();
      output += text;
      log.stream(command, text);
    };
    child.stdout?.on("data", collect);
    child.stderr?.on("data", collect);
    child.once("error", reject);
    child.once("close", (code) => resolve({ code: code ?? -1, output }));
  });
}

async function waitForTcp(port: number, host: string, timeoutMs: number): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const open = await new Promise<boolean>((resolve) => {
      const socket = new net.Socket();
      const finish = (value: boolean) => {
        socket.destroy();
        resolve(value);
      };
      socket.setTimeout(1000);
      socket.once("connect", () => finish(true));
      socket.once("timeout", () => finish(false));
      socket.once("error", () => finish(false));
      socket.connect(port, host);
    });
    if (open) return;
    await delay(400);
  }
  throw new Error(`Port ${port} auf ${host} war nach ${Math.round(timeoutMs / 1000)} s nicht erreichbar.`);
}

async function waitForHttp(
  url: string,
  timeoutMs: number,
  label: string,
  isAlive?: () => boolean,
): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (isAlive && !isAlive()) {
      throw new Error(`${label} wurde unerwartet beendet. Details in den Protokollen im Datenverzeichnis.`);
    }
    try {
      const response = await fetch(url, { signal: AbortSignal.timeout(2000) });
      if (response.ok) return;
    } catch {
      // Dienst noch nicht bereit.
    }
    await delay(500);
  }
  throw new Error(`${label} war nach ${Math.round(timeoutMs / 1000)} s nicht bereit (${url}).`);
}

export class Services {
  private readonly children: ChildProcess[] = [];
  private readonly log = new Logger("fasswerk");
  private postgresBin?: ReturnType<typeof resolvePostgresBin>;

  constructor(
    private readonly settings: Settings,
    private readonly callbacks: BootstrapCallbacks,
  ) {}

  async start(): Promise<void> {
    await this.step("database", async () => this.startPostgres());
    await this.step("backend", async () => this.startBackend());
    await this.step("frontend", async () => this.startFrontend());
    this.callbacks.onProgress({ step: "ready", state: "done", message: "FassWerk ist bereit." });
  }

  private async step(
    step: Exclude<BootstrapStep, "ready" | "runtime">,
    action: () => Promise<void>,
  ): Promise<void> {
    this.callbacks.onProgress({ step, state: "active", message: STEP_LABELS[step].active });
    try {
      await action();
      this.callbacks.onProgress({ step, state: "done", message: STEP_LABELS[step].done });
    } catch (error) {
      const message = errorMessage(error);
      this.log.error(`${step}: ${message}`);
      this.callbacks.onProgress({ step, state: "error", message });
      throw error;
    }
  }

  private postgresEnv(): NodeJS.ProcessEnv {
    const env: NodeJS.ProcessEnv = { ...process.env };
    if (!this.postgresBin) return env;
    const lib = path.join(this.postgresBin.dir, "..", "lib");
    if (process.platform === "win32") {
      env.PATH = `${lib}${path.delimiter}${env.PATH ?? ""}`;
    } else if (process.platform === "darwin") {
      env.DYLD_LIBRARY_PATH = `${lib}${path.delimiter}${env.DYLD_LIBRARY_PATH ?? ""}`;
    } else {
      env.LD_LIBRARY_PATH = `${lib}${path.delimiter}${env.LD_LIBRARY_PATH ?? ""}`;
    }
    return env;
  }

  private async startPostgres(): Promise<void> {
    this.postgresBin = resolvePostgresBin();
    const { exe } = this.postgresBin;
    const pgData = path.join(dataDir, "postgres");

    fs.mkdirSync(pgData, { recursive: true });
    fs.mkdirSync(tmpDir, { recursive: true });

    if (process.platform !== "win32") {
      for (const name of ["initdb", "pg_ctl", "postgres"]) {
        const binary = exe(name);
        if (fs.existsSync(binary)) fs.chmodSync(binary, 0o755);
      }
    }

    if (!fs.existsSync(path.join(pgData, "PG_VERSION"))) {
      const passwordFile = path.join(tmpDir, "postgres-password.txt");
      fs.writeFileSync(passwordFile, this.settings.dbPassword, { mode: 0o600 });
      try {
        const init = await runOnce(exe("initdb"), [
          "-D", pgData,
          "-U", dbUser,
          "--auth-local=trust",
          "--auth-host=scram-sha-256",
          "--encoding=UTF8",
          "--no-locale",
          "--pwfile", passwordFile,
        ], this.log, { env: this.postgresEnv() });
        if (init.code !== 0) throw new Error("PostgreSQL-Datenverzeichnis konnte nicht initialisiert werden.");
      } finally {
        fs.rmSync(passwordFile, { force: true });
      }
    }

    const socketDir = path.join(tmpDir, "pgsocket");
    fs.mkdirSync(socketDir, { recursive: true });
    const socketOption = process.platform === "win32" ? "" : ` -k "${socketDir}"`;
    const start = await runOnce(exe("pg_ctl"), [
      "-D", pgData,
      "-l", path.join(logDir, "postgres.log"),
      "-o", `-p ${postgresPort} -h 127.0.0.1${socketOption} -c listen_addresses=127.0.0.1`,
      "-w",
      "start",
    ], this.log, { env: this.postgresEnv() });
    if (start.code !== 0) throw new Error("PostgreSQL konnte nicht gestartet werden.");

    await waitForTcp(postgresPort, "127.0.0.1", 30_000);
  }

  private async startBackend(): Promise<void> {
    const jar = resolveBackendJar();
    const java = resolveJava();
    const env: NodeJS.ProcessEnv = {
      ...process.env,
      SPRING_PROFILES_ACTIVE: "prod",
      DB_URL: `jdbc:postgresql://127.0.0.1:${postgresPort}/${dbName}`,
      DB_USER: dbUser,
      DB_PASSWORD: this.settings.dbPassword,
      JWT_SECRET: this.settings.jwtSecret,
      JWT_ISSUER: "fasswerk-backend",
      SERVER_PORT: String(backendPort),
      QR_SCAN_BASE_URL: frontendOrigin,
      RESERVATION_TIMEZONE: "Europe/Berlin",
      SALES_BUSINESS_TIMEZONE: "Europe/Berlin",
      RESERVATION_MAIL_ENABLED: "false",
      FLYWAY_CONNECT_RETRIES: "30",
      FLYWAY_CONNECT_RETRIES_INTERVAL: "2s",
      // Lokaler Desktop-Login: fester Staff-Zugang statt zufälligem Admin-Passwort.
      DESKTOP_STAFF_LOGIN: "true",
      DESKTOP_STAFF_EMAIL: "staff@fasswerk.local",
      DESKTOP_STAFF_PASSWORD: "fasswerk",
      BOOTSTRAP_ADMIN_EMAIL: "",
      BOOTSTRAP_ADMIN_PASSWORD: "",
    };

    const child = this.spawnChild("backend", java, [
      "-XX:MaxRAMPercentage=60.0",
      "-XX:+ExitOnOutOfMemoryError",
      "-jar", jar,
    ], { env });

    await waitForHttp(`${backendOrigin}/actuator/health`, 180_000, "Das Backend", () => child.exitCode === null);
  }

  private async startFrontend(): Promise<void> {
    const { serverJs, cwd } = resolveFrontend();
    const env: NodeJS.ProcessEnv = {
      ...process.env,
      ELECTRON_RUN_AS_NODE: "1",
      NODE_ENV: "production",
      NEXT_TELEMETRY_DISABLED: "1",
      PORT: String(frontendPort),
      HOSTNAME: "127.0.0.1",
      BACKEND_BASE_URL: backendOrigin,
      APP_ORIGIN: frontendOrigin,
    };

    const child = this.spawnChild("frontend", process.execPath, [serverJs], { env, cwd });
    await waitForHttp(`${frontendOrigin}/login`, 90_000, "Die Oberfläche", () => child.exitCode === null);
  }

  private spawnChild(name: string, command: string, args: string[], options: SpawnOptions): ChildProcess {
    const out = fs.openSync(path.join(logDir, `${name}.log`), "a");
    const child = spawn(command, args, {
      ...options,
      stdio: ["ignore", out, out],
      windowsHide: true,
    });
    fs.closeSync(out);
    this.children.push(child);
    child.once("error", (error) => this.log.error(`${name}: ${error.message}`));
    child.once("exit", (code, signal) => {
      this.log.info(`${name} beendet (code=${code ?? "null"}, signal=${signal ?? "null"}).`);
    });
    return child;
  }

  async stop(): Promise<void> {
    for (const child of [...this.children].reverse()) {
      if (child.exitCode === null && !child.killed) {
        child.kill("SIGTERM");
        await Promise.race([once(child, "exit"), delay(5000)]);
        if (child.exitCode === null) child.kill("SIGKILL");
      }
    }
    this.children.length = 0;

    if (this.postgresBin) {
      const { exe } = this.postgresBin;
      const pgData = path.join(dataDir, "postgres");
      if (fs.existsSync(pgData)) {
        await runOnce(exe("pg_ctl"), ["-D", pgData, "-m", "fast", "-w", "stop"], this.log, { env: this.postgresEnv() });
      }
    }
  }
}
