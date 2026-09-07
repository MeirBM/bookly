"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { FormError } from "@/components/FormError";
import { Button } from "@/components/ui/Button";
import { BusinessAvatar } from "@/components/ui/BusinessAvatar";
import { Card } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { ApiError, api, type Business } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";

/**
 * The logo, set by URL.
 *
 * <p>A URL rather than an upload, and the reason is worth stating where an owner might wonder: file
 * upload needs object storage, and a container with no persistent disk would lose the file on the
 * next deploy. A feature that works until it silently stops is worse than one that asks for a link.
 *
 * <p>The preview is the live component, not an approximation of it — so an address that will not
 * load shows the fallback here, before it shows it to a customer.
 */
export function LogoField({ business }: { business: Business }) {
  const { tokens } = useAuth();
  const token = tokens?.accessToken ?? "";
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState(business.logoUrl ?? "");
  const [failure, setFailure] = useState<string | null>(null);

  const save = useMutation({
    mutationFn: (logoUrl: string | null) => api.setBusinessLogo(token, business.id, logoUrl),
    onSuccess: (updated) => {
      setFailure(null);
      setDraft(updated.logoUrl ?? "");
      void queryClient.invalidateQueries({ queryKey: ["business", business.id] });
    },
    onError: (error) =>
      setFailure(
        error instanceof ApiError ? error.body.message : "Could not reach the server.",
      ),
  });

  const trimmed = draft.trim();
  const saved = business.logoUrl ?? "";

  return (
    <Card className="flex flex-col gap-4">
      <div>
        <h2 className="text-base font-semibold text-ink">Logo</h2>
        <p className="mt-1 text-sm text-ink-muted">
          Shown on your public booking page. Paste an <code className="font-mono text-xs">https</code>{" "}
          link to an image you already host — leave it empty to use the Bookly mark. The address is
          public, so avoid one with a password or a signed key in it.
        </p>
      </div>

      <form
        className="flex flex-wrap items-end gap-3"
        onSubmit={(event) => {
          event.preventDefault();
          save.mutate(trimmed === "" ? null : trimmed);
        }}
      >
        {/* The preview reflects what is typed, not what is saved, so the fallback appears the
            moment an address turns out not to resolve. */}
        <BusinessAvatar src={trimmed || null} name={business.name} size="sm" />
        <label className="min-w-0 flex-1 text-sm sm:max-w-md">
          <span className="mb-1.5 block font-medium text-ink">Image address</span>
          <Input
            type="url"
            inputMode="url"
            value={draft}
            placeholder="https://example.com/logo.png"
            onChange={(event) => setDraft(event.target.value)}
          />
        </label>
        <Button type="submit" loading={save.isPending} disabled={trimmed === saved}>
          Save logo
        </Button>
        {saved ? (
          <Button
            type="button"
            variant="secondary"
            disabled={save.isPending}
            onClick={() => save.mutate(null)}
          >
            Remove
          </Button>
        ) : null}
      </form>

      <FormError message={failure} />
    </Card>
  );
}
