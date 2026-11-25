import { contextBridge, ipcRenderer } from "electron";
import type { BootstrapProgress } from "./bootstrap";

export interface FasswerkBridge {
  onProgress: (callback: (progress: BootstrapProgress) => void) => void;
  onCredentials: (callback: (credentials: { email: string; password: string }) => void) => void;
  retry: () => void;
  quit: () => void;
  version: () => Promise<string>;
}

const bridge: FasswerkBridge = {
  onProgress: (callback) => {
    ipcRenderer.on("bootstrap:progress", (_event, progress: BootstrapProgress) => callback(progress));
  },
  onCredentials: (callback) => {
    ipcRenderer.on("bootstrap:credentials", (_event, credentials) => callback(credentials));
  },
  retry: () => ipcRenderer.send("bootstrap:retry"),
  quit: () => ipcRenderer.send("app:quit"),
  version: () => ipcRenderer.invoke("app:version"),
};

contextBridge.exposeInMainWorld("fasswerk", bridge);
