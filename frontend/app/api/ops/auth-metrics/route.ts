import { NextResponse } from "next/server";
import { backendFetchWithAuth } from "@/lib/server-auth";

type MetricEntry = {
  name: string;
  value: number;
};

const METRIC_NAMES = [
  "auth.login.success",
  "auth.login.failure",
  "auth.refresh.success",
  "auth.refresh.failure",
  "auth.refresh.replay_detected",
  "auth.logout.single",
  "auth.logout.all",
  "auth.session.revoke",
  "auth.cleanup.revoked_access_tokens.removed",
  "auth.cleanup.refresh_tokens.removed",
] as const;

function aggregateMeasurements(payload: unknown): number {
  if (!payload || typeof payload !== "object") {
    return 0;
  }

  const measurements = (payload as { measurements?: Array<{ value?: number }> }).measurements;
  if (!Array.isArray(measurements)) {
    return 0;
  }

  return measurements.reduce((sum, measurement) => {
    const value = typeof measurement.value === "number" ? measurement.value : 0;
    return sum + value;
  }, 0);
}

export async function GET() {
  const metrics: MetricEntry[] = [];

  for (const name of METRIC_NAMES) {
    const response = await backendFetchWithAuth(`/actuator/metrics/${encodeURIComponent(name)}`);

    if (response.status === 401 || response.status === 403) {
      return NextResponse.json({ message: "unauthorized" }, { status: response.status });
    }

    if (!response.ok) {
      metrics.push({ name, value: 0 });
      continue;
    }

    const payload = await response.json().catch(() => ({}));
    metrics.push({ name, value: aggregateMeasurements(payload) });
  }

  return NextResponse.json({ metrics });
}

