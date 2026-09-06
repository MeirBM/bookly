import { useQuery } from "@tanstack/react-query";
import { api } from "./api";
import { useAuth } from "./auth-context";

/**
 * The business itself, mainly for its time zone.
 *
 * <p>Every screen that renders a time needs it: an owner checking a shop in another country must
 * see the shop's clock, not their own, and a time shown in the wrong zone is worse than no time.
 */
export function useBusiness(businessId: string) {
  const { tokens } = useAuth();
  const token = tokens?.accessToken ?? "";
  return useQuery({
    queryKey: ["business", businessId],
    queryFn: () => api.getBusiness(token, businessId),
    enabled: Boolean(token && businessId),
  });
}
