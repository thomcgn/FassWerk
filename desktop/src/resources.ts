import { createHash } from "node:crypto";
import * as fs from "node:fs";
import * as os from "node:os";
import * as path from "node:path";
import { Readable } from "node:stream";
import extractZip from "extract-zip";
import * as tar from "tar";

export type Report = (message: string) => void;

const PG_VERSION = "16.16.0-beta.19";
const JAVA_VERSION = 21;

function exe(name: string): string {
  return process.platform === "win32" ? `${name}.exe` : name;
}

function platformKey(): string | undefined {
  const table: Record<string, Record<string, string>> = {
    linux: { x64: "linux-x64", arm64: "linux-arm64", ppc64: "linux-ppc64", ia32: "linux-ia32", arm: "linux-arm" },
    darwin: { x64: "darwin-x64", arm64: "darwin-arm64" },
    win32: { x64: "windows-x64" },
  };
  return table[process.platform]?.[process.arch];
}

function hasJava(dir: string): boolean {
  return fs.existsSync(path.join(dir, "bin", exe("java")));
}

function hasPostgres(dir: string): boolean {
  return fs.existsSync(path.join(dir, "bin", exe("initdb")))
    && fs.existsSync(path.join(dir, "bin", exe("pg_ctl")))
    && fs.existsSync(path.join(dir, "bin", exe("postgres")));
}

async function download(url: string, target: string, report: Report): Promise<void> {
  report(`Lade ${url} …`);
  const response = await fetch(url, { redirect: "follow" });
  if (!response.ok) {
    throw new Error(`Download fehlgeschlagen (HTTP ${response.status}): ${url}`);
  }

  const total = Number(response.headers.get("content-length") ?? 0);
  let received = 0;
  let lastPercent = -1;
  const output = fs.createWriteStream(target);

  if (!response.body) throw new Error("Leere Antwort beim Download.");
  for await (const chunk of Readable.fromWeb(response.body as never)) {
    const buffer = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
    output.write(buffer);
    received += buffer.length;
    if (total > 0) {
      const percent = Math.min(100, Math.floor((received / total) * 100));
      if (percent >= lastPercent + 10) {
        lastPercent = percent;
        report(`Lade … ${percent} %`);
      }
    } else if (received % (8 * 1024 * 1024) < buffer.length) {
      report(`Lade … ${Math.round(received / 1024 / 1024)} MB`);
    }
  }

  await new Promise<void>((resolve, reject) => {
    output.once("finish", resolve);
    output.once("error", reject);
    output.end();
  });
}

async function verifyIntegrity(file: string, integrity: string | undefined): Promise<void> {
  if (!integrity) return;
  const [algorithm, encoded] = integrity.split("-", 2);
  if (!encoded || (algorithm !== "sha512" && algorithm !== "sha256")) return;
  const actual = createHash(algorithm).update(fs.readFileSync(file)).digest("base64");
  if (actual !== encoded) throw new Error(`Integritätsprüfung des Downloads fehlgeschlagen (${algorithm}).`);
}

function copyDirectory(source: string, target: string): void {
  fs.mkdirSync(target, { recursive: true });
  fs.cpSync(source, target, { recursive: true });
}

/** npm-Tarballs enthalten die Shared-Library-Symlinks nicht; die Manifest-Datei stellt sie wieder her. */
function hydrateSymlinks(root: string, manifest: string): void {
  if (!fs.existsSync(manifest)) return;
  const links = JSON.parse(fs.readFileSync(manifest, "utf8")) as Array<{
    source: string;
    target: string;
  }>;
  const local = (value: string) => value.replace(/^native[\\/]/, "");
  for (const link of links) {
    const source = path.join(root, local(link.source));
    const target = path.join(root, local(link.target));
    if (!fs.existsSync(source) || fs.existsSync(target)) continue;
    fs.mkdirSync(path.dirname(target), { recursive: true });
    try {
      if (process.platform === "win32") {
        fs.copyFileSync(source, target);
      } else {
        const relative = path.relative(path.dirname(target), source);
        // Alle Manifest-Einträge liegen im selben Verzeichnis; nie absolute Ziele in das Paket schreiben.
        fs.symlinkSync(path.isAbsolute(relative) ? path.basename(source) : relative, target);
      }
    } catch {
      fs.copyFileSync(source, target);
    }
  }
}

function moveDirectory(source: string, target: string): void {
  try {
    fs.renameSync(source, target);
    return;
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code !== "EXDEV") throw error;
  }

  // Über verschiedene Dateisysteme hinweg kopieren und Symlinks relativ wiederherstellen.
  const links: Array<{ path: string; target: string }> = [];
  const collect = (directory: string) => {
    for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
      const current = path.join(directory, entry.name);
      if (entry.isSymbolicLink()) {
        links.push({ path: current, target: fs.readlinkSync(current) });
      } else if (entry.isDirectory()) {
        collect(current);
      }
    }
  };
  collect(source);
  copyDirectory(source, target);
  for (const link of links) {
    const destination = path.join(target, path.relative(source, link.path));
    fs.rmSync(destination, { force: true });
    fs.mkdirSync(path.dirname(destination), { recursive: true });
    fs.symlinkSync(link.target, destination);
  }
  fs.rmSync(source, { recursive: true, force: true });
}

function flattenSingleDirectory(root: string): string {
  const entries = fs.readdirSync(root, { withFileTypes: true });
  const directories = entries.filter((entry) => entry.isDirectory());
  if (directories.length === 1 && entries.length === 1) {
    return path.join(root, directories[0].name);
  }
  return root;
}

async function installJre(target: string, report: Report): Promise<void> {
  const stage = fs.mkdtempSync(path.join(os.tmpdir(), "fasswerk-jre-"));
  try {
    if (process.env.FW_JRE_DIR && fs.existsSync(process.env.FW_JRE_DIR)) {
      report("Kopiere bereitgestellte Java-Laufzeit …");
      copyDirectory(process.env.FW_JRE_DIR, path.join(stage, "root"));
    } else {
      const osName = process.platform === "win32" ? "windows" : process.platform === "darwin" ? "mac" : "linux";
      const arch = process.arch === "arm64" ? "aarch64" : process.arch === "ia32" ? "x86" : "x64";
      const url = `https://api.adoptium.net/v3/binary/latest/${JAVA_VERSION}/ga/${osName}/${arch}/jre/hotspot/normal/eclipse`;
      const archive = path.join(stage, `jre.${osName === "windows" ? "zip" : "tar.gz"}`);
      await download(url, archive, report);
      const extractTo = path.join(stage, "extract");
      fs.mkdirSync(extractTo, { recursive: true });
      if (archive.endsWith(".zip")) {
        await extractZip(archive, { dir: extractTo });
      } else {
        await tar.x({ file: archive, cwd: extractTo, strip: 1 });
      }
      const root = flattenSingleDirectory(extractTo);
      copyDirectory(root, path.join(stage, "root"));
    }

    if (!hasJava(path.join(stage, "root"))) {
      throw new Error("Die heruntergeladene Java-Laufzeit enthält keine bin/java.");
    }
    fs.rmSync(target, { recursive: true, force: true });
    moveDirectory(path.join(stage, "root"), target);
    report("Java-Laufzeit installiert.");
  } finally {
    fs.rmSync(stage, { recursive: true, force: true });
  }
}

async function installPostgres(target: string, report: Report): Promise<void> {
  const stage = fs.mkdtempSync(path.join(os.tmpdir(), "fasswerk-postgres-"));
  try {
    const root = path.join(stage, "root");
    if (process.env.PG_HOME && fs.existsSync(path.join(process.env.PG_HOME, "bin"))) {
      report("Kopiere bereitgestellte PostgreSQL-Installation …");
      for (const dir of ["bin", "lib", "share"]) {
        const source = path.join(process.env.PG_HOME, dir);
        if (fs.existsSync(source)) copyDirectory(source, path.join(root, dir));
      }
    } else {
      const key = platformKey();
      if (!key) {
        throw new Error(`Keine PostgreSQL-Binaries für ${process.platform}/${process.arch} verfügbar. Bitte PG_HOME setzen.`);
      }
      const metadataUrl = `https://registry.npmjs.org/@embedded-postgres/${key}`;
      let integrity: string | undefined;
      try {
        const metadata = (await (await fetch(metadataUrl)).json()) as {
          versions?: Record<string, { dist?: { integrity?: string } }>;
        };
        integrity = metadata.versions?.[PG_VERSION]?.dist?.integrity;
      } catch {
        // Integritätsprüfung ist optional; der Download läuft weiter.
      }

      const url = `https://registry.npmjs.org/@embedded-postgres/${key}/-/${key}-${PG_VERSION}.tgz`;
      const archive = path.join(stage, "postgres.tgz");
      await download(url, archive, report);
      await verifyIntegrity(archive, integrity);

      const extractTo = path.join(stage, "extract");
      fs.mkdirSync(extractTo, { recursive: true });
      // package/native/... -> extract/native/...
      await tar.x({ file: archive, cwd: extractTo, strip: 1 });
      const native = path.join(extractTo, "native");
      for (const dir of ["bin", "lib", "share"]) {
        copyDirectory(path.join(native, dir), path.join(root, dir));
      }
      // Nach dem Kopieren anlegen: fs.cpSync macht aus relativen Symlinks absolute Pfade.
      hydrateSymlinks(root, path.join(native, "pg-symlinks.json"));
    }

    if (!hasPostgres(root)) {
      throw new Error("Die PostgreSQL-Binaries sind unvollständig (initdb/pg_ctl/postgres fehlen).");
    }
    fs.rmSync(target, { recursive: true, force: true });
    moveDirectory(root, target);
    report("PostgreSQL installiert.");
  } finally {
    fs.rmSync(stage, { recursive: true, force: true });
  }
}

/**
 * Prüft die nativen Laufzeiten und installiert fehlende Bestandteile.
 * Läuft sowohl im Build-Skript als auch beim Start der Anwendung.
 */
export async function ensureNativeResources(
  targetDir: string,
  report: Report = () => undefined,
): Promise<void> {
  fs.mkdirSync(targetDir, { recursive: true });

  const jreDir = path.join(targetDir, "jre");
  const systemJava = process.env.FW_JAVA && fs.existsSync(process.env.FW_JAVA);
  if (hasJava(jreDir) || systemJava) {
    report("Java-Laufzeit ist vorhanden.");
  } else {
    await installJre(jreDir, report);
  }

  const postgresDir = path.join(targetDir, "postgres");
  if (hasPostgres(postgresDir)) {
    report("PostgreSQL ist vorhanden.");
  } else {
    await installPostgres(postgresDir, report);
  }
}
