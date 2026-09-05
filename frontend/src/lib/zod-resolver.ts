import type { FieldErrors, FieldValues, Resolver } from "react-hook-form";
import type { ZodType } from "zod";

/**
 * Bridges a Zod schema to react-hook-form.
 *
 * <p>This replaces `@hookform/resolvers`, which was pulled in for this one function. That package
 * declares 25 optional peer dependencies, and npm handles one of them inconsistently: `npm install`
 * correctly skips the optional `ajv` peer, while `npm ci` then refuses the resulting lock file for
 * not containing it. CI installs with `npm ci`, so the build gate failed on a package we barely
 * used. Twenty lines we can read is a better answer than an unused dependency added to satisfy a
 * tool, and it removes a supply-chain edge we had no reason to carry.
 */
export function zodResolver<TValues extends FieldValues>(
  schema: ZodType<TValues>,
): Resolver<TValues> {
  return async (values) => {
    const parsed = schema.safeParse(values);

    if (parsed.success) {
      return { values: parsed.data, errors: {} };
    }

    const errors: Record<string, { type: string; message: string }> = {};
    for (const issue of parsed.error.issues) {
      const path = issue.path.join(".");
      // First issue per field wins: showing one actionable message beats stacking
      // several that say the same thing in different words.
      if (path && !(path in errors)) {
        errors[path] = { type: issue.code, message: issue.message };
      }
    }
    return { values: {}, errors: errors as FieldErrors<TValues> };
  };
}
