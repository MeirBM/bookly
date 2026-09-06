-- A logo is a URL, not an upload. Object storage is the honest answer to file upload, and a
-- Railway container's disk does not survive a deploy: an uploaded file would work in testing and
-- disappear in production, which is worse than not offering it.
ALTER TABLE businesses ADD COLUMN logo_url text;

-- The scheme is enforced here as well as in the service, for the same reason the appointment
-- overlap rule lives in the database: an owner-supplied string rendered into an <img src> on a
-- public page is the injection surface of this feature, and a check that a future code path can
-- forget to call is not a guarantee. javascript: and data: URLs cannot reach the column at all.
ALTER TABLE businesses ADD CONSTRAINT businesses_logo_url_is_http
    CHECK (logo_url IS NULL OR (logo_url ~* '^https?://[^[:space:]]+$'
                                AND char_length(logo_url) <= 2048));
