import { isRecord, parseArray } from "../../lib/api-client.ts";
import type { Reservation, ReservationSettings, ReservationStatus } from "../../types/api.ts";

const statuses: ReservationStatus[] = ["PENDING", "CONFIRMED", "CHECKED_IN", "REJECTED", "CANCELLED", "NO_SHOW", "EXPIRED", "COMPLETED"];
const isStatus = (value: unknown): value is ReservationStatus => typeof value === "string" && statuses.some((status) => status === value);
const nullableString = (value: unknown): value is string | null => value === null || typeof value === "string";

export function parseReservation(value: unknown): Reservation | null {
  if (!isRecord(value) || typeof value.id !== "number" || typeof value.guestName !== "string"
    || !nullableString(value.contactEmail) || !nullableString(value.contactPhone) || typeof value.reservationDate !== "string"
    || typeof value.reservationTime !== "string" || typeof value.guestCount !== "number" || !isStatus(value.status)
    || !nullableString(value.expiresAt) || !nullableString(value.checkedInAt) || typeof value.qrCodeToken !== "string"
    || typeof value.qrScanUrl !== "string") return null;
  const assignedTableIds = parseArray(value.assignedTableIds, (id) => typeof id === "number" ? id : null);
  if (assignedTableIds === null || !(typeof value.durationMinutes === "number" || value.durationMinutes === null)
    || typeof value.startsAt !== "string" || !nullableString(value.endsAt) || typeof value.businessDate !== "string"
    || typeof value.timezone !== "string") return null;
  return {
    id: value.id, guestName: value.guestName, contactEmail: value.contactEmail, contactPhone: value.contactPhone,
    reservationDate: value.reservationDate, reservationTime: value.reservationTime, guestCount: value.guestCount,
    status: value.status, expiresAt: value.expiresAt, checkedInAt: value.checkedInAt,
    qrCodeToken: value.qrCodeToken, qrScanUrl: value.qrScanUrl, assignedTableIds,
    durationMinutes: value.durationMinutes, startsAt: value.startsAt, endsAt: value.endsAt,
    businessDate: value.businessDate, timezone: value.timezone,
  };
}
export const parseReservations = (value: unknown): Reservation[] | null => parseArray(value, parseReservation);

export function parseReservationSettings(value: unknown): ReservationSettings | null {
  if (!isRecord(value) || typeof value.today !== "string" || typeof value.timezone !== "string"
    || !(typeof value.durationMinutes === "number" || value.durationMinutes === null) || typeof value.graceMinutes !== "number"
    || typeof value.intervalMinutes !== "number" || (value.mode !== "FIXED" && value.mode !== "FLEXIBLE") || !Array.isArray(value.openingHours)) return null;
  type Weekday = ReservationSettings["openingHours"][number]["weekday"];
  const weekdays: Weekday[] = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"];
  const isWeekday = (weekday: string): weekday is Weekday => weekdays.some((candidate) => candidate === weekday);
  const openingHours = parseArray(value.openingHours, (entry) => {
    if (!isRecord(entry) || typeof entry.weekday !== "string" || !isWeekday(entry.weekday) || typeof entry.open !== "boolean" || !nullableString(entry.from)
      || !nullableString(entry.to) || !nullableString(entry.secondFrom) || !nullableString(entry.secondTo)) return null;
    return { weekday: entry.weekday, open: entry.open, from: entry.from, to: entry.to, secondFrom: entry.secondFrom, secondTo: entry.secondTo };
  });
  return openingHours === null ? null : { today: value.today, timezone: value.timezone, durationMinutes: value.durationMinutes, graceMinutes: value.graceMinutes, intervalMinutes: value.intervalMinutes, mode: value.mode, openingHours };
}

export function statusVariant(status: ReservationStatus): "success" | "warning" | "destructive" | "muted" {
  if (status === "CHECKED_IN") return "success";
  if (status === "PENDING" || status === "CONFIRMED") return "warning";
  if (status === "REJECTED" || status === "CANCELLED" || status === "NO_SHOW") return "destructive";
  return "muted";
}
export function toLocalDateTime(date: string, time: string): string {
  const parsed = new Date(`${date}T${time}`);
  return Number.isNaN(parsed.getTime()) ? `${date} ${time}` : parsed.toLocaleString("de-DE", { hour: "2-digit", minute: "2-digit", day: "2-digit", month: "2-digit" });
}
export function groupReservationsByStatus(reservations: Reservation[]): Record<ReservationStatus, Reservation[]> {
  const grouped: Record<ReservationStatus, Reservation[]> = {
    PENDING: [], CONFIRMED: [], CHECKED_IN: [], REJECTED: [], CANCELLED: [], NO_SHOW: [], EXPIRED: [], COMPLETED: [],
  };
  for (const reservation of reservations) grouped[reservation.status].push(reservation);
  return grouped;
}
export function partitionReservations(reservations: Reservation[]) {
  const actionable = reservations.filter(({ status }) => status === "PENDING" || status === "CONFIRMED" || status === "CHECKED_IN");
  const closed = reservations.filter(({ status }) => status === "REJECTED" || status === "CANCELLED" || status === "NO_SHOW" || status === "EXPIRED" || status === "COMPLETED");
  return { actionable, closed };
}
