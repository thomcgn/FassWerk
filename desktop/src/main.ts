import * as fs from "node:fs";
import * as path from "node:path";
import { app, BrowserWindow, dialog, ipcMain } from "electron";
import { Services, type BootstrapProgress } from "./bootstrap";
import {
  binaryName,
  desktopDir,
  ensureDirectories,
  frontendOrigin,
  isDev,
  loadSettings,
  resourcePath,
  saveSettings,
  setResourceOverride,
  userDataDir,
  type Settings,
} from "./config";
import { Logger } from "./logger";
import { ensureNativeResources } from "./resources";

const log = new Logger("main");

let splash: BrowserWindow | null = null;
let mainWindow: BrowserWindow | null = null;
let services: Services | null = null;
let settings: Settings;
let firstRun = false;
let shuttingDown = false;

if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  void run();
}

async function run(): Promise<void> {
  ensureDirectories();
  settings = loadSettings();
  prepareFirstRun();
  registerIpc();

  app.on("second-instance", () => {
    if (!mainWindow) return;
    if (mainWindow.isMinimized()) mainWindow.restore();
    mainWindow.focus();
  });

  app.on("window-all-closed", () => app.quit());

  app.on("before-quit", (event) => {
    if (shuttingDown) return;
    event.preventDefault();
    void shutdown();
  });

  await app.whenReady();
  createSplash();
  await startServices();
}

function prepareFirstRun(): void {
  firstRun = settings.firstRunDone !== true;
  if (!firstRun) return;

  // Fester lokaler Zugang für die Desktop-App.
  settings.bootstrapEmail = "staff";
  settings.bootstrapPassword = "fasswerk";
  saveSettings(settings);

  const target = path.join(userDataDir, "Zugangsdaten.txt");
  const content = [
    "FassWerk – Zugangsdaten für den ersten Start",
    "",
    `Benutzer: ${settings.bootstrapEmail}`,
    `Passwort: ${settings.bootstrapPassword}`,
    "",
    "Lokaler Desktop-Zugang. Die Anmeldung erfolgt mit dem Benutzernamen 'staff'.",
    "",
  ].join("\r\n");
  try {
    fs.writeFileSync(target, content, { mode: 0o600 });
  } catch (error) {
    log.error(`Zugangsdaten konnten nicht gespeichert werden: ${String(error)}`);
  }
}

function registerIpc(): void {
  ipcMain.handle("app:version", () => app.getVersion());
  ipcMain.on("app:quit", () => app.quit());
  ipcMain.on("bootstrap:retry", () => {
    void startServices();
  });
}

function createSplash(): void {
  splash = new BrowserWindow({
    width: 560,
    height: 540,
    resizable: false,
    minimizable: false,
    maximizable: false,
    center: true,
    frame: false,
    backgroundColor: "#050c10",
    show: true,
    webPreferences: {
      preload: path.join(__dirname, "preload.js"),
      contextIsolation: true,
      nodeIntegration: false,
    },
  });
  void splash.loadFile(path.join(__dirname, "splash.html"));
}

function publishProgress(progress: BootstrapProgress): void {
  log.info(`${progress.step}: ${progress.state} – ${progress.message}`);
  splash?.webContents.send("bootstrap:progress", progress);
}

async function startServices(): Promise<void> {
  if (services) await services.stop();

  splash?.show();

  try {
    const nativeDir = isDev
      ? path.join(desktopDir, "resources")
      : path.join(userDataDir, "native");
    const bundled = fs.existsSync(resourcePath(path.join("jre", "bin", binaryName("java"))))
      && fs.existsSync(resourcePath(path.join("postgres", "bin", binaryName("initdb"))));

    publishProgress({ step: "runtime", state: "active", message: "Laufzeit wird geprüft …" });
    if (!bundled) {
      // Paket ohne native Laufzeit: beim Start nachladen und dauerhaft im userData-Verzeichnis halten.
      await ensureNativeResources(nativeDir, (message) => {
        publishProgress({ step: "runtime", state: "active", message });
      });
      setResourceOverride("jre", path.join(nativeDir, "jre"));
      setResourceOverride("postgres", path.join(nativeDir, "postgres"));
    }
    publishProgress({ step: "runtime", state: "done", message: "Laufzeit ist bereit." });

    services = new Services(settings, { onProgress: publishProgress });
    await services.start();
    if (firstRun && settings.bootstrapEmail && settings.bootstrapPassword) {
      splash?.webContents.send("bootstrap:credentials", {
        email: settings.bootstrapEmail,
        password: settings.bootstrapPassword,
      });
    }
    createMainWindow();
  } catch (error) {
    log.error(`Start fehlgeschlagen: ${String(error)}`);
  }
}

function createMainWindow(): void {
  const bounds = settings.windowBounds ?? { width: 1280, height: 820 };
  const win = new BrowserWindow({
    x: bounds.x,
    y: bounds.y,
    width: bounds.width,
    height: bounds.height,
    minWidth: 760,
    minHeight: 560,
    show: false,
    title: "FassWerk",
    backgroundColor: "#07141a",
    webPreferences: {
      preload: path.join(__dirname, "preload.js"),
      contextIsolation: true,
      nodeIntegration: false,
      spellcheck: false,
    },
  });

  // Die Anwendung bleibt bewusst vollständig lokal. Externe Navigationen werden blockiert.
  win.webContents.on("will-navigate", (event, url) => {
    if (!url.startsWith(frontendOrigin)) event.preventDefault();
  });
  win.webContents.setWindowOpenHandler(() => ({ action: "deny" }));

  win.on("close", () => {
    settings.windowBounds = win.getBounds();
    saveSettings(settings);
  });
  win.on("closed", () => {
    mainWindow = null;
  });

  mainWindow = win;
  void win.loadURL(frontendOrigin)
    .then(() => {
      splash?.close();
      splash = null;
      win.show();
      win.focus();
      if (firstRun) void showFirstRunDialog();
    })
    .catch((error: unknown) => {
      log.error(`Oberfläche konnte nicht geladen werden: ${String(error)}`);
      publishProgress({
        step: "frontend",
        state: "error",
        message: `Oberfläche konnte nicht geladen werden: ${String(error)}`,
      });
      splash?.show();
      win.close();
    });
}

async function showFirstRunDialog(): Promise<void> {
  const result = await dialog.showMessageBox(mainWindow!, {
    type: "info",
    title: "FassWerk – erster Start",
    message: "Ihr Admin-Zugang wurde erstellt.",
    detail:
      `Benutzer: ${settings.bootstrapEmail}\n` +
      `Passwort: ${settings.bootstrapPassword}\n\n` +
      "Melden Sie sich mit dem Benutzernamen 'staff' an. " +
      "Die Zugangsdaten liegen zusätzlich in Zugangsdaten.txt im Datenverzeichnis.",
    buttons: ["OK"],
    defaultId: 0,
  });
  if (result.response === 0) {
    settings.firstRunDone = true;
    saveSettings(settings);
    firstRun = false;
  }
}

async function shutdown(): Promise<void> {
  shuttingDown = true;
  try {
    await services?.stop();
  } catch (error) {
    log.error(`Herunterfahren fehlgeschlagen: ${String(error)}`);
  }
  app.exit(0);
}
