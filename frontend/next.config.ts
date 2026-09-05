import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Standalone output keeps the runtime image small: the server plus only the
  // dependencies it actually traced, rather than all of node_modules.
  output: "standalone",
};

export default nextConfig;
