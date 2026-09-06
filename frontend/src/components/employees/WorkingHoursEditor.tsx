"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { AsyncSection } from "@/components/AsyncSection";
import { Button } from "@/components/ui/Button";
import { Input, Select } from "@/components/ui/Input";
import { api, type Weekday } from "@/lib/api";

const WEEKDAYS: Weekday[] = [
  "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY",
];

export const weekdayLabel = (day: string) => day.charAt(0) + day.slice(1).toLowerCase();

/**
 * When one person works.
 *
 * <p>Two windows on one weekday are how a break is expressed, and the UI says so rather than
 * hiding the model behind a "break" concept the API does not have — a reader who understands the
 * shape can predict what the calendar will do.
 */
export function WorkingHoursEditor({
  businessId,
  employeeId,
  token,
  onFailure,
}: {
  businessId: string;
  employeeId: string;
  token: string;
  onFailure: (error: unknown) => void;
}) {
  const queryClient = useQueryClient();
  const [weekday, setWeekday] = useState<Weekday>("MONDAY");
  const [startsAt, setStartsAt] = useState("09:00");
  const [endsAt, setEndsAt] = useState("17:00");

  const hours = useQuery({
    queryKey: ["working-hours", businessId, employeeId],
    queryFn: () => api.listWorkingHours(token, businessId, employeeId),
    enabled: Boolean(token),
  });

  const invalidate = () =>
    void queryClient.invalidateQueries({ queryKey: ["working-hours", businessId, employeeId] });

  const add = useMutation({
    mutationFn: () =>
      api.addWorkingHours(token, businessId, employeeId, { weekday, startsAt, endsAt }),
    onSuccess: invalidate,
    onError: onFailure,
  });
  const remove = useMutation({
    mutationFn: (id: string) => api.deleteWorkingHours(token, businessId, id),
    onSuccess: invalidate,
    onError: onFailure,
  });

  return (
    <div>
      <p className="text-sm font-medium text-ink">Working hours</p>

      <div className="mt-2.5">
        <AsyncSection
          query={hours}
          label="working hours"
          skeletonRows={2}
          isEmpty={(data) => data.length === 0}
          empty={
            <span className="text-sm">
              No hours set, so this person is never offered a slot. Add two windows on one day to
              leave a break between them.
            </span>
          }
        >
          {(data) => (
            <ul className="flex flex-col gap-1.5">
              {data.map((window) => (
                <li key={window.id} className="flex items-center gap-3 text-sm">
                  <span className="w-24 font-medium text-ink">{weekdayLabel(window.weekday)}</span>
                  <span className="tabular-nums text-ink-muted">
                    {window.startsAt.slice(0, 5)}–{window.endsAt.slice(0, 5)}
                  </span>
                  <Button
                    variant="ghost"
                    size="sm"
                    type="button"
                    onClick={() => remove.mutate(window.id)}
                  >
                    Remove
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </AsyncSection>
      </div>

      <form
        className="mt-4 flex flex-wrap items-end gap-3"
        onSubmit={(event) => {
          event.preventDefault();
          add.mutate();
        }}
      >
        <label className="text-sm">
          <span className="mb-1.5 block font-medium text-ink">Day</span>
          <Select
            className="w-40"
            value={weekday}
            onChange={(event) => setWeekday(event.target.value as Weekday)}
          >
            {WEEKDAYS.map((day) => (
              <option key={day} value={day}>
                {weekdayLabel(day)}
              </option>
            ))}
          </Select>
        </label>
        <label className="text-sm">
          <span className="mb-1.5 block font-medium text-ink">From</span>
          <Input
            className="w-32"
            type="time"
            value={startsAt}
            onChange={(event) => setStartsAt(event.target.value)}
          />
        </label>
        <label className="text-sm">
          <span className="mb-1.5 block font-medium text-ink">To</span>
          <Input
            className="w-32"
            type="time"
            value={endsAt}
            onChange={(event) => setEndsAt(event.target.value)}
          />
        </label>
        <Button variant="secondary" type="submit" loading={add.isPending}>
          Add window
        </Button>
      </form>
    </div>
  );
}
