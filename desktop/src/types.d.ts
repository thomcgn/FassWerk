import type { FasswerkBridge } from "./preload";
import type { BootstrapProgress } from "./bootstrap";

declare global {
  interface Window {
    fasswerk: FasswerkBridge;
  }
}

export type { BootstrapProgress };
