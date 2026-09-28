export class ApiClientError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = "ApiClientError";
    this.status = status;
  }
}

export type JsonParser<T> = (value: unknown) => T | null;

export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

export function parseArray<T>(value: unknown, parseItem: JsonParser<T>): T[] | null {
  if (!Array.isArray(value)) return null;
  const result: T[] = [];
  for (const item of value) {
    const parsed = parseItem(item);
    if (parsed === null) return null;
    result.push(parsed);
  }
  return result;
}

export async function readApiError(response: Response, fallback: string): Promise<string> {
  const payload: unknown = await response.clone().json().catch(() => null);
  if (isRecord(payload)) {
    if (typeof payload.message === "string" && payload.message.trim()) return payload.message;
    if (typeof payload.error === "string" && payload.error.trim()) return payload.error;
  }

  const text = await response.clone().text().catch(() => "");
  if (text.trim()) return text;
  if (response.status === 401) return "Bitte neu einloggen.";
  if (response.status === 403) return "Keine Berechtigung für diese Aktion.";
  if (response.status === 409) return "Der Eintrag kollidiert mit bestehenden Daten.";
  if (response.status === 400 || response.status === 422) return "Bitte Eingaben prüfen.";
  return fallback;
}

export async function parseJsonResponse<T>(response: Response, parser: JsonParser<T>, label: string): Promise<T> {
  if (!response.ok) {
    throw new ApiClientError(await readApiError(response, `${label} fehlgeschlagen.`), response.status);
  }
  const payload: unknown = await response.json();
  const parsed = parser(payload);
  if (parsed === null) {
    throw new ApiClientError(`${label}: Die Serverantwort hat ein unerwartetes Format.`, response.status);
  }
  return parsed;
}
