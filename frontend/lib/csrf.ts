export function isAllowedApiRequest(
  method: string,
  origin: string | null,
  fetchSite: string | null,
  expectedOrigin: string,
): boolean {
  if (["GET", "HEAD", "OPTIONS"].includes(method.toUpperCase())) return true;
  if (!origin || fetchSite === "cross-site") return false;
  try {
    const source = new URL(origin);
    const target = new URL(expectedOrigin);
    return ["http:", "https:"].includes(source.protocol)
      && source.origin === target.origin
      && !source.username && !source.password
      && source.pathname === "/" && !source.search && !source.hash;
  } catch {
    return false;
  }
}
