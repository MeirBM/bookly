import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Standalone output keeps the self-hosted runtime image small: the server plus only the
  // dependencies it actually traced, rather than all of node_modules. It is what the Dockerfile
  // and the browser test harness run.
  //
  // Not on Vercel, though. Vercel builds its own output for its runtime, and asking for standalone
  // at the same time makes the two disagree. VERCEL is set by their builder, so each environment
  // gets the output it expects and neither has to know about the other.
  output: process.env.VERCEL ? undefined : "standalone",
};

export default nextConfig;
