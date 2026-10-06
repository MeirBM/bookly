# Deploying Bookly

Backend on **Render** (web service + Key Value for Redis), PostgreSQL on **Neon**, frontend on
**Vercel**. Criteria 3.23–3.26. The backend ran on Railway until its trial ended; that history is
kept at the end, because the failures there are the reason several settings below exist.

Written as steps rather than prose because the order matters: the two halves each need the other's
URL, and doing it in the wrong order produces a CORS failure that looks like a broken frontend.

Everything Render needs is in [`render.yaml`](../render.yaml). Nothing in this document should be
copied into the repository as a value: every secret is entered in a platform's dashboard.

---

## Check this first

**Flyway's first migration runs `CREATE EXTENSION IF NOT EXISTS btree_gist`.** The exclusion
constraint that makes double booking impossible cannot be created without it, and some managed
databases withhold the privilege. Before anything else, connect to the Neon database and run:

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;
SELECT extname FROM pg_extension WHERE extname = 'btree_gist';
```

If that fails, stop and say so — the deployment cannot honour criterion 3.1, and pretending
otherwise would ship a booking system whose central guarantee is absent. Turn-3 spec, pitfall 1
names this as the thing to check before the deadline rather than on it. (Neon permits it, and V1
applies cleanly there.)

---

## 1. PostgreSQL on Neon

1. Create a project and note the branch the app will use (`production`).
2. **Connect** → turn **Connection pooling off** → copy the connection string. It is the *direct*
   one: the host has no `-pooler` in it.

   **Use the direct host, not the pooled one.** Flyway takes a Postgres advisory lock while it
   migrates, and a transaction-mode pooler does not preserve session state between statements.
3. Keep the string for step 2. It contains the password: it goes into Render's dashboard and
   nowhere else.

`DATABASE_URL` may be the `postgres://user:password@host/db?sslmode=require` form Neon hands out. <!-- allow-secret: placeholder -->
`DatabaseUrlEnvironmentPostProcessor` turns it into the JDBC URL, username and password Spring's
datasource needs, and requires TLS for any host that is not localhost unless the URL says
otherwise. A JDBC URL is left alone, which is what `docker compose` still uses. A URL that cannot
be parsed fails startup with a message that does not echo it — the value carries the password.

## 2. Backend and Redis on Render

1. **New → Blueprint** → select `MeirBM/bookly`. Render reads `render.yaml` and proposes:
   - `bookly-backend`: a web service, runtime `docker`, plan `free`, built from the root
     `Dockerfile` with the repository root as context, health check
     `/actuator/health/readiness`;
   - `bookly-redis`: a Key Value instance, plan `free`, `ipAllowList: []` (reachable only from
     Render's private network).
2. Enter the values marked `sync: false`:

   | Variable | Value |
   |---|---|
   | `DATABASE_URL` | the Neon direct string from step 1 |
   | `JWT_SECRET` | `openssl rand -base64 48` — paste into Render, never into the repo. To keep existing sessions valid across a move, reuse the old value |
   | `CORS_ALLOWED_ORIGINS` | set in step 4, once the frontend URL exists |

   These are already set by the Blueprint and should not need touching:

   | Variable | Value | Why |
   |---|---|---|
   | `REDIS_URL` | from `bookly-redis`'s connection string | one value carrying host and credentials |
   | `FORWARD_HEADERS_STRATEGY` | `framework` | see below |
   | `EXPOSE_API_DOCS` | `false` | the API document is a map of the attack surface |
   | `JAVA_TOOL_OPTIONS` | `-XX:MaxRAMPercentage=70 -XX:+UseSerialGC` | the free instance has 512 MB |

   **`FORWARD_HEADERS_STRATEGY` is not cosmetic.** Render terminates TLS at its edge, so without it
   `getRemoteAddr()` returns the proxy's address and every visitor on the internet shares one
   rate-limit bucket — one shell loop of 61 requests would then return `429` to everybody for the
   rest of the window, and the booking page is down. It is off by default precisely because
   enabling it *without* a trusted proxy in front lets a caller spoof `X-Forwarded-For` and mint
   itself an unlimited allowance. Only ever set it where something else sets that header.

   **Redis carries credentials in `REDIS_URL`, and host/port alone do not.** `REDIS_URL`
   (`redis://` or `rediss://`) is applied by `RedisUrlEnvironmentPostProcessor` only when it has
   text; otherwise `REDIS_HOST` and `REDIS_PORT` apply, which is what `docker compose` uses. A
   Redis that refuses the connection fails quietly in a specific way: `/actuator/health` reports
   `DOWN`, the API keeps serving, and the rate limiter fails open because that is what it is
   designed to do when the cache is unreachable. Nothing about the API's behaviour reveals that a
   security control has stopped applying. That is why readiness deliberately excludes Redis — a
   platform restarting a container that serves correctly hides the real problem — and why the
   *aggregate* `/actuator/health` is the one to read.

   Verify it rather than assume it: send more requests than the limit in a minute and look for a
   `429`. Seventy requests against a limit of sixty returning seventy `200`s is what a
   disconnected Redis looks like from outside.

3. Deploy and **read the log.** Confirm Flyway applied V1 to V8 and the app reports
   `Started BooklyBackendApplication`. Migrations run on application startup, so nothing needs a
   pre-deploy command (which the free plan does not offer). A container that restarts silently
   looks like a slow deploy; criterion 3.24 is satisfied by the log, not by the app answering.

```bash
curl -fsS https://<backend>.onrender.com/actuator/health            # {"status":"UP",...}
curl -fsS https://<backend>.onrender.com/actuator/health/readiness  # {"status":"UP"}
```

**Free-plan behaviour, so it is not mistaken for a fault.** The service sleeps after about 15
minutes without traffic, and the first request afterwards takes a minute or two while the JVM and
Flyway start. The "incoming HTTP request" lines in the log are that wake-up, not a restart loop.
Render's Key Value is `Available` once it runs — `Deployed` is a web-service status. Free Key Value
has no persistence, which costs only rate-limit counters.

## 3. Frontend on Vercel

Do this **after** the backend has a URL, and do step 4 after this one. The two halves each need the
other's address.

1. New project → import `MeirBM/bookly`.
2. **Root Directory**: `frontend`.
3. Environment variable `NEXT_PUBLIC_API_URL` = the Render backend URL, with no trailing slash —
   for this deployment, `https://bookly-backend-terp.onrender.com`.

   **Next inlines this at build time, not at runtime.** It must be set before the first build, and
   changing it later needs a redeploy rather than a restart. A frontend built without it silently
   falls back to `http://localhost:8080`, which fails only in the visitor's browser and looks like
   the API being down. After a redeploy, confirm which URL the deployed bundle contains rather than
   trusting the setting.
4. Deploy, and note the Vercel URL.

## 4. Close the loop

On Render, set `CORS_ALLOWED_ORIGINS` to the Vercel origin — scheme and host, **no trailing slash
and no path**, e.g. `https://bookly-pearl.vercel.app` — and let the service redeploy.

**An origin is scheme + host + port.** `bookly-pearl.vercel.app` is a hostname, not an origin, and
will never match the `Origin: https://bookly-pearl.vercel.app` a browser actually sends — the
application logs a warning naming any entry without a scheme. Check the startup line:

```
CORS allows origins: [https://bookly-pearl.vercel.app]
```

The value is a list, so several origins can be allowed at once:

```
CORS_ALLOWED_ORIGINS="https://bookly-pearl.vercel.app,http://localhost:3000"
```

Its default is `http://localhost:3000` alone. Setting it replaces the default, which is why a
locally-run frontend stops reaching the deployed API unless it is listed too. Use the production
domain, not a per-deployment `*-git-*.vercel.app` URL, which changes with every deploy.

**This step is not optional and its absence is not obvious.** Without it every dashboard screen
loads and then shows its error state, because the browser refuses the cross-origin call before the
API ever sees it. That exact failure cost this project a full round of debugging in turn 2 while 113
backend tests stayed green. A preflight tests it without a browser:

```bash
curl -si -X OPTIONS https://<backend>/api/auth/login \
  -H 'Origin: https://bookly-pearl.vercel.app' \
  -H 'Access-Control-Request-Method: POST' -H 'Access-Control-Request-Headers: content-type' \
  | grep -iE '^HTTP|access-control-allow-origin'      # 200 and the origin echoed back
```

## 5. Moving existing data

A new Neon database holds only the empty schema Flyway created. To bring data from another
PostgreSQL (this is how the Railway data came across):

```bash
scripts/migrate-db.sh '<source url>' '<target url>'
```

The script needs `pg_dump`, `pg_restore` and `psql` (`brew install libpq && brew link --force
libpq`), checks that the dump tool is not older than the source server, and **refuses to run if any
table other than `flyway_schema_history` on the target already has rows**, so it cannot overwrite
real data. It restores in one transaction: a failure leaves the target as it was.

- The source must be reachable from where the script runs. Railway's `DATABASE_URL` is internal;
  its `DATABASE_PUBLIC_URL` exists only once the Postgres service's TCP proxy is enabled
  (Settings → Networking). Disable the proxy again afterwards.
- Both URLs contain passwords. Run the script in your own terminal so they stay out of chat logs
  and shell history you share, and rotate a password that went anywhere else.
- `backup.dump` is the whole database, and is gitignored. Delete it when the move is confirmed.

Check the result by logging in with an account that existed before the move; a row count proves the
copy, a login proves the hashes survived it.

## 6. Verify what was actually deployed

```bash
curl -fsS https://<backend>/actuator/health
curl -s -o /dev/null -w '%{http_code}\n' https://<backend>/v3/api-docs   # must not be 200: EXPOSE_API_DOCS is false
```

(Unauthenticated, the document answers `401`, not `200` — that is the pass condition.)

Then in a browser, against the deployed frontend:

1. Register, create a business, add a service, an employee, link them, and give the employee hours.
2. Open `/book/<slug>` in a private window — no session — and complete a booking.
3. Confirm it appears in the dashboard list and in the right day and time on the calendar.

That last sequence is criterion 3.26, and it is the one worth doing by hand: it crosses both
deployments, the database, and the browser in one pass.

---

## Failure mode 6 — the two deployments drift apart, and the frontend is the one that moves

**Symptom.** A feature merged with CI green, and green on the merge commit, fails in production with
`No such endpoint.` — Bookly's own 404 for a route that does not exist. Everything else works.

**Cause.** Vercel and the backend host are separate GitHub integrations and they do not agree about
when a merge means "deploy". It happened with Railway: Vercel rebuilt on the turn-4 merge; Railway
did not. The frontend was calling an endpoint the API had never heard of. Render's auto-deploy is
likewise a per-service setting, so the same split is possible there.

**Why it is worth its own entry.** The split is quiet by construction. A frontend ahead of its API
degrades gracefully almost everywhere — a response field the old API omits arrives as `undefined`,
and a well-built component falls back rather than breaking — so only the one *write* that needs the
new route fails. Nothing else looks wrong, which is why this reaches a user rather than a test.

**The correction to the habit, not just the incident.** `git merge` is not a deploy, and CI green on
a merge commit says nothing about what is running. A turn is not deployed until something that only
exists in that turn has been observed answering in production. For turn 4 that check is one line,
and it needs no account:

```bash
# logoUrl is a turn-4 field. Absent means the backend predates the turn.
curl -fsS https://<backend>/api/public/businesses/<a-real-slug> | grep -q logoUrl \
  && echo "backend has turn 4" || echo "backend is behind"
```

**Fix.** In the Render dashboard, open `bookly-backend` → **Events**. If the latest deploy predates
the merge, press **Manual Deploy → Deploy latest commit**, or check Settings → Build & Deploy that
the branch is `main` and auto-deploy is on. Then read the log and confirm Flyway applied the turn's
migrations — for turn 4, `V6`, `V7` and `V8`.

## Failure mode 7 — a default that is an empty string is not a default

**Symptom.** After the move to Render, the browser suite failed in CI with only "Process from
config.webServer was not able to start. Exit code: 1", while 123 backend tests were green.

**Cause.** `spring.data.redis.url` was defaulted to `${REDIS_URL:}` so a provider URL could
override host and port. A placeholder default can only be an empty string, and Spring treats an
empty Redis URL as *invalid* rather than absent: `Invalid Redis URL ''`. The application refused to
start wherever `REDIS_URL` was not set — `docker compose` and CI. The integration tests missed it
because Testcontainers supplies Redis properties itself, and a manual run missed it because
`REDIS_URL` happened to be set.

**Fix and rule.** `RedisUrlEnvironmentPostProcessor` sets the property only when `REDIS_URL` has
text, and `RedisUrlConfigTest` reads the shipped `application.yml` and failed before the fix. Test
a new optional setting both **set** and **unset**; the unset case is the one a developer's own
environment hides. The same trap applies to any optional `${VAR:}` that feeds a typed property.

---

## History: the Railway deployment

The backend ran on Railway before Render. These are the arrangements that no longer apply, and the
failures behind them — worth reading before repeating any of them on another platform.

- **Root Directory did not take effect.** The original instruction was to set it to `backend`, and
  the build failed twice with `Railpack could not determine how to build the app`, listing the
  repository root with two applications in it. That listing was the diagnosis: the setting was not
  honoured. A deployment that depends on a platform setting behaving as documented fails at the
  worst moment, so there is **one** Dockerfile at the repository root, pinned by a `railway.json`
  (since removed; `render.yaml` now plays that part), and `docker-compose.yml` builds from the same context — the
  image the tests ran against has to be the image that ships.
- **Redis needed credentials.** A bare host and port were refused; `/actuator/health` read `DOWN`
  while the API served and the rate limiter silently failed open. Setting
  `SPRING_DATA_REDIS_URL` from Railway's `REDIS_URL` fixed it. `REDIS_URL` now does the same job
  on every platform.
- **An unresolved `${{...}}` reference left a variable empty, not missing**, and an empty value
  defeats a default: `${REDIS_PORT:6379}` falls back only when the variable is absent. The app
  refused to start with `Failed to bind properties under 'spring.data.redis.port'`. Deleting an
  empty variable is a fix; leaving it blank is not.
- **Railway's `DATABASE_URL` is `postgresql://...`**, which Spring's datasource does not accept, so
  the variables were assembled into a JDBC URL by hand. The URL conversion in step 1 removes that
  step.
- **The health check was set to `/actuator/health`**, which includes Redis. The Render deployment
  uses the readiness group instead, so a Redis outage degrades the rate limiter rather than
  restarting a container that serves correctly.
- **Port.** The platform assigns it at runtime; `server.port: ${PORT:8080}` handles Railway and
  Render alike. A fixed 8080 makes a container look unhealthy and get restarted, which reads as a
  slow deploy rather than a misconfiguration.

---

## What is not deployed

No CI deploy step. Deployment is triggered from the platforms' own GitHub integrations, and adding a
pipeline that pushes to production on every merge is more automation than three spiral turns can
justify verifying. Recorded here rather than left as an apparent omission.
