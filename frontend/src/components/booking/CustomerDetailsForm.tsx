"use client";

import { useState } from "react";
import { FormError } from "@/components/FormError";
import { Button } from "@/components/ui/Button";
import { Field } from "@/components/Field";
import { Input } from "@/components/ui/Input";

/**
 * The last thing between a visitor and a booking, so it asks for as little as it can.
 *
 * <p>Three fields, one of them optional and labelled as such. Validation is the browser's until
 * submit — the standard warns against validating aggressively while someone is still typing, and
 * an email half-entered is not yet wrong.
 */
export function CustomerDetailsForm({
  when,
  pending,
  failure,
  onSubmit,
}: {
  when: string;
  pending: boolean;
  failure: string | null;
  onSubmit: (details: { name: string; email: string; phone: string }) => void;
}) {
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");

  return (
    <form
      className="flex flex-col gap-4"
      onSubmit={(event) => {
        event.preventDefault();
        onSubmit({ name: name.trim(), email: email.trim(), phone: phone.trim() });
      }}
    >
      <div>
        <h2 className="text-lg font-semibold text-ink">Your details</h2>
        <p className="mt-1 text-sm text-ink-muted">
          Booking <span className="font-medium text-ink">{when}</span>. No account needed.
        </p>
      </div>

      <FormError message={failure} />

      <Field label="Name">
        <Input required autoComplete="name" value={name} onChange={(e) => setName(e.target.value)} />
      </Field>
      <Field label="Email" hint="We use this to find your booking if you get in touch.">
        <Input
          required
          type="email"
          autoComplete="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
      </Field>
      <Field label="Phone (optional)">
        <Input
          type="tel"
          autoComplete="tel"
          value={phone}
          onChange={(e) => setPhone(e.target.value)}
        />
      </Field>

      <Button type="submit" size="md" fullWidth loading={pending}>
        {pending ? "Booking…" : "Confirm booking"}
      </Button>
    </form>
  );
}
