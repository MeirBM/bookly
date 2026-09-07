-- V7 was written to close two things and closed one and a half. Its data migration clears rows
-- that are not https, but a row already holding https://user:pass@host/logo.png passes the new
-- CHECK untouched and goes on being republished by the public booking endpoint to anyone who asks
-- - which is the exact harm the accompanying spec revision gave as its reason for banning userinfo.
-- Caught by the test-writer reading the revision against the migration, which is the one pairing
-- that finds this class of gap: whoever wrote the migration already believes it does what it says.
--
-- No such row can exist in production, because the logo feature has not shipped. That is a reason
-- to write this migration calmly, not a reason to skip it - the developer databases where the
-- feature was exercised under V6 rules are exactly where such a row does exist.

UPDATE businesses
SET logo_url = NULL
WHERE logo_url IS NOT NULL
  AND logo_url ~ '^[^:]+://[^/]*@';

ALTER TABLE businesses DROP CONSTRAINT businesses_logo_url_is_https;

-- Same rule as V7: every character class here is a subset of what java.net.URI already refuses, so
-- the constraint can never be the one to reject a value the validator accepted. A CHECK stricter
-- than the service turns a clean 400 into a 500 with the offending row in the logs.
ALTER TABLE businesses ADD CONSTRAINT businesses_logo_url_is_https
    CHECK (logo_url IS NULL
           OR (logo_url ~* '^https://'
               AND logo_url !~ '[[:space:][:cntrl:]"<>`]'
               AND logo_url !~ '^[^:]+://[^/]*@'
               AND char_length(logo_url) <= 2048));
