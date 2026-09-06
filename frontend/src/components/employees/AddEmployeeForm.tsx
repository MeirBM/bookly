"use client";

import { useState } from "react";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";

/** Deliberately one field: adding a person should not be a form to fill in. */
export function AddEmployeeForm({
  pending,
  onAdd,
}: {
  pending: boolean;
  onAdd: (fullName: string) => void;
}) {
  const [name, setName] = useState("");

  return (
    <form
      className="flex flex-wrap items-end gap-3"
      onSubmit={(event) => {
        event.preventDefault();
        const trimmed = name.trim();
        if (trimmed) {
          onAdd(trimmed);
          setName("");
        }
      }}
    >
      <label className="min-w-0 flex-1 text-sm sm:max-w-xs">
        <span className="mb-1.5 block font-medium text-ink">Employee name</span>
        <Input
          aria-label="Employee name"
          value={name}
          placeholder="e.g. Dana Levi"
          onChange={(event) => setName(event.target.value)}
        />
      </label>
      <Button type="submit" loading={pending} disabled={!name.trim()}>
        Add employee
      </Button>
    </form>
  );
}
