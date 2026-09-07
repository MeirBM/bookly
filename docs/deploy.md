# Deploying Bookly

Backend, PostgreSQL and Redis on **Railway**; frontend on **Vercel**. Criteria 3.23–3.26.

Written as steps rather than prose because the order matters: the two halves each need the other's
URL, and doing it in the wrong order produces a CORS failure that looks like a broken frontend.

---

## Check this first

**Flyway's first migration runs `CREATE EXTENSION IF NOT EXISTS btree_gist`.** The exclusion
constraint that makes double booking impossible cannot be created without it, and some managed
databases withhold the privilege. Before anything else, connect to the Railway database and run:

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;
SELECT extname FROM pg_extension WHERE extname = 'btree_gist';
```

If that fails, stop and say so — the deployment cannot honour criterion 3.1, and pretending
otherwise would ship a booking system whose central guarantee is absent. Turn-3 spec, pitfall 1
names this as the thing to check before the deadline rather than on it.

---

## 1. Backend, PostgreSQL and Redis on Railway

1. New project → **Deploy from GitHub repo** → `MeirBM/bookly`.
2. **Leave Root Directory unset.** The backend builds from the repository root: `Dockerfile` and
   `railway.json` are both there, and `railway.json` pins the builder to `DOCKERFILE` so nothing
   has to be inferred.

   This is deliberate, and it is the second arrangement rather than the first. The original
   instruction was to set Root Directory to `backend`, and the build failed twice with:

   ```
   ✖ Railpack could not determine how to build the app.
     The app contents that Railpack analyzed contains:
     ./
     ├── backend/
     ├── frontend/
     ...
   ```

   That listing is the diagnosis: Railpack was reading the repository root, saw two applications
   and a pile of documentation, and could not pick one — the setting was not taking effect. A
   deployment that depends on a platform setting behaving as documented is a deployment that fails
   at the worst moment, so the build no longer depends on one.

   There is **one** Dockerfile, at the root, and `docker-compose.yml` builds from the same context.
   A second copy under `backend/` would be two files that must agree, and the image the tests ran
   against has to be the image that ships or the tests verified something else.

   `railway.json` also sets the health check to `/actuator/health`, so a container that boots but
   cannot reach its database is reported as failed rather than counted as live — turn-3 pitfall 9,
   where a deployed Flyway failure is invisible unless someone reads the log.
3. Add **PostgreSQL** and **Redis** to the project (New → Database).
4. On the backend service, set these variables. The `${{...}}` forms are Railway references, so
   nothing is copied by hand and nothing is written down here:

   | Variable | Value |
   |---|---|
   | `DATABASE_URL` | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` |
   | `DATABASE_USER` | `${{Postgres.PGUSER}}` |
   | `DATABASE_PASSWORD` | `${{Postgres.PGPASSWORD}}` |
   | `SPRING_DATA_REDIS_URL` | `${{Redis.REDIS_URL}}` |
   | `JWT_SECRET` | generate with `openssl rand -base64 48` — paste into Railway, never into the repo |
   | `EXPOSE_API_DOCS` | `false` |
   | `FORWARD_HEADERS_STRATEGY` | `framework` |
   | `CORS_ALLOWED_ORIGINS` | set in step 3, once the frontend URL exists |

   **Redis needs credentials, and `REDIS_HOST` plus `REDIS_PORT` do not carry them.** Railway's
   Redis requires a password; connecting with a bare host and port is refused, and the failure is
   quiet in a specific way — `/actuator/health` reports `DOWN`, the API keeps serving perfectly,
   and the rate limiter fails open because that is what it is designed to do when the cache is
   unreachable. Nothing about the API's behaviour reveals that a security control has stopped
   applying. Setting `SPRING_DATA_REDIS_URL` from Railway's own `REDIS_URL` carries user, password,
   host and port in one value, and Spring binds it by relaxed naming with no code change.

   Verify it rather than assume it: send more requests than the limit in a minute and look for a
   `429`. Seventy requests against a limit of sixty returning seventy `200`s is what a
   disconnected Redis looks like from outside.

   **A Railway reference that does not resolve leaves the variable empty, not missing**, and an
   empty value defeats a default: `${REDIS_PORT:6379}` falls back to 6379 only when `REDIS_PORT`
   is absent. Present-but-blank yields `""`, and the application refuses to start with

   ```
   Failed to bind properties under 'spring.data.redis.port' to int:
       Value: "${REDIS_PORT:6379}"
       Reason: A null value cannot be assigned to a primitive type
   ```

   The usual cause is a reference naming a service that does not exist under that name — check the
   Redis service's actual name in the project and make `${{Redis.REDISPORT}}` match it, or delete
   the variable entirely and let the default apply. **Deleting an empty variable is a fix; leaving
   it blank is not.** The same applies to every `${{...}}` reference in the table above, so it is
   worth expanding each one in the Railway UI and confirming it shows a value before deploying.

   `FORWARD_HEADERS_STRATEGY` is not cosmetic. Railway terminates TLS at its edge, so without it
   `getRemoteAddr()` returns the proxy's address and every visitor on the internet shares one
   rate-limit bucket — one shell loop of 61 requests would then return `429` to everybody for the
   rest of the window, and the booking page is down. It is off by default precisely because
   enabling it *without* a trusted proxy in front lets a caller spoof `X-Forwarded-For` and mint
   itself an unlimited allowance. Only ever set it where something else sets that header.

   `DATABASE_URL` is deliberately the JDBC form. Railway's own `DATABASE_URL` is a
   `postgresql://user:pass@host/db` connection string, which Spring's datasource does not accept —
   pasting it produces a driver error that reads like a network problem.

5. Generate a domain (Settings → Networking → Generate Domain) and note the URL.
6. **Read the deploy log.** Confirm Flyway applied V1 to V5 and the app reports
   `Started BooklyBackendApplication`. A container that restarts silently looks like a slow deploy;
   criterion 3.24 is satisfied by the log, not by the app answering.

```bash
curl -fsS https://<backend>.up.railway.app/actuator/health   # {"status":"UP",...}
```

## 2. Frontend on Vercel

Do this **after** the backend has a URL, and do step 3 after this one. The two halves each need the
other's address, and doing them out of order produces a CORS failure that looks like a broken
frontend rather than a missing variable.

1. New project → import `MeirBM/bookly`.
2. **Root Directory**: `frontend`. Vercel's equivalent setting is reliable; this is the only
   place one is needed.
3. Environment variable `NEXT_PUBLIC_API_URL` = the Railway backend URL from step 1, with no
   trailing slash — for this deployment, `https://bookly-production-a85b.up.railway.app`.

   **Next inlines this at build time, not at runtime.** It must be set before the first build, and
   changing it later needs a redeploy rather than a restart. A frontend built without it silently
   falls back to `http://localhost:8080`, which fails only in the visitor's browser and looks like
   the API being down.
4. Deploy, and note the Vercel URL.

## 3. Close the loop

Back on Railway, set `CORS_ALLOWED_ORIGINS` to the Vercel origin — scheme and host, **no trailing
slash and no path**, e.g. `https://bookly-xyz.vercel.app` — and redeploy the backend.

**An origin is scheme + host + port.** `bookly-pearl.vercel.app` is a hostname, not an origin, and
will never match the `Origin: https://bookly-pearl.vercel.app` a browser actually sends — the
application logs a warning naming any entry without a scheme. Check the startup line:

```
CORS allows origins: [https://bookly-pearl.vercel.app, http://localhost:3000]
```

The value is a list, so both origins can be allowed at once while you are still working locally:

```
CORS_ALLOWED_ORIGINS="https://bookly-xyz.vercel.app,http://localhost:3000"
```

Its default is `http://localhost:3000` alone, which is why a locally-run frontend already reaches
the deployed API and a deployed one will not until this is set.

**This step is not optional and its absence is not obvious.** Without it every dashboard screen
loads and then shows its error state, because the browser refuses the cross-origin call before the
API ever sees it. That exact failure cost this project a full round of debugging in turn 2 while 113
backend tests stayed green.

## 4. Verify what was actually deployed

```bash
curl -fsS https://<backend>/actuator/health
curl -fsS https://<backend>/v3/api-docs        # must be 403: EXPOSE_API_DOCS is false
```

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

**Cause.** Vercel and Railway are separate GitHub integrations and they do not agree about when a
merge means "deploy". Vercel rebuilt on the turn-4 merge; Railway did not. The frontend was calling
an endpoint the API had never heard of.

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

**Fix.** In the Railway project, open the backend service → **Deployments**. If the latest deployment
predates the merge, either press **Deploy** on the current `main`, or check Settings → Source that
the watched branch is `main` and automatic deploys are enabled. Then read the deploy log and confirm
Flyway applied the turn's migrations — for turn 4, `V6`, `V7` and `V8`.

---

## What is not deployed

No CI deploy step. Deployment is triggered from the platforms' own GitHub integration, and adding a
pipeline that pushes to production on every merge is more automation than three spiral turns can
justify verifying. Recorded here rather than left as an apparent omission.
