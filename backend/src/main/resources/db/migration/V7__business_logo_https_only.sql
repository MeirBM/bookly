-- V6's CHECK was described in its own comment as the guarantee, with the service as convenience on
-- top. A review pointed out it was nothing of the kind: '^https?://[^[:space:]]+$' is a prefix test,
-- so it accepted https://a"onerror=alert(1)//x and https://a<script> — both of which the Java
-- validator rejects outright. For everything except the scheme the layering was inverted, and the
-- comment claiming otherwise was the worse half of the defect. Not reachable through the API today,
-- because setLogo is the only writer; reachable the moment a seed script, a support UPDATE or an
-- import endpoint becomes the second one.
--
-- Two changes. http is dropped: Bookly is served over TLS, so a cleartext image is blocked or
-- fails to upgrade and the owner silently gets the fallback mark instead of their logo. And the
-- characters that cannot appear in a URL at all are refused, so nothing that could break out of an
-- HTML attribute can reach the column by any path.
--
-- The character list is deliberately a subset of what java.net.URI already rejects, never a
-- superset. A constraint stricter than the validator would turn a bad request into a 500 and put
-- the offending row in the logs, which trades a clear 400 for an incident.

-- Any existing http logo is cleared rather than migrated to https: guessing that a host serves TLS
-- would produce a broken image, and null is the state this feature already renders correctly.
-- Recorded here as the explicit decision CLAUDE.md requires for a migration that drops data.
UPDATE businesses SET logo_url = NULL WHERE logo_url IS NOT NULL AND logo_url !~* '^https://';

ALTER TABLE businesses DROP CONSTRAINT businesses_logo_url_is_http;

ALTER TABLE businesses ADD CONSTRAINT businesses_logo_url_is_https
    CHECK (logo_url IS NULL
           OR (logo_url ~* '^https://'
               AND logo_url !~ '[[:space:][:cntrl:]"<>`]'
               AND char_length(logo_url) <= 2048));
