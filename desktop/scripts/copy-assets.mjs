import { copyFileSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const desktopDir = dirname(dirname(fileURLToPath(import.meta.url)));
mkdirSync(join(desktopDir, "out"), { recursive: true });
copyFileSync(join(desktopDir, "src", "splash.html"), join(desktopDir, "out", "splash.html"));
