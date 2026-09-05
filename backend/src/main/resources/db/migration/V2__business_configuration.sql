-- Turn 2: what a business is made of, and when its people work.
--
-- business_id is carried on every table here even where it could be reached through a
-- join. Turn-2 spec, pitfall 7: the tenant guard proves the caller belongs to the
-- business in the path, not that the row does — so every lookup filters on both, and
-- that is only possible if the column is present.

CREATE TABLE services (
    id               UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    business_id      UUID        NOT NULL REFERENCES businesses (id) ON DELETE CASCADE,
    name             TEXT        NOT NULL,
    duration_minutes INTEGER     NOT NULL,
    price_minor      BIGINT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT services_duration_positive CHECK (duration_minutes > 0),
    CONSTRAINT services_price_not_negative CHECK (price_minor IS NULL OR price_minor >= 0),
    CONSTRAINT services_name_unique_per_business UNIQUE (business_id, name)
);
CREATE INDEX services_business_idx ON services (business_id);

CREATE TABLE employees (
    id          UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    business_id UUID        NOT NULL REFERENCES businesses (id) ON DELETE CASCADE,
    full_name   TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT employees_name_not_blank CHECK (length(btrim(full_name)) > 0)
);
CREATE INDEX employees_business_idx ON employees (business_id);

-- Who can perform what. An employee not linked to a service contributes no slots for it
-- (criterion 2.6), which is the whole reason this table exists rather than a flag.
CREATE TABLE employee_services (
    employee_id UUID NOT NULL REFERENCES employees (id) ON DELETE CASCADE,
    service_id  UUID NOT NULL REFERENCES services (id) ON DELETE CASCADE,
    PRIMARY KEY (employee_id, service_id)
);
CREATE INDEX employee_services_service_idx ON employee_services (service_id);

-- A break is two rows for the same weekday, not a separate concept: the intersection then
-- handles breaks, split shifts and part days without a second table. Local times, because
-- "Tuesday 09:00" is what the owner means and it stays true across a DST change.
CREATE TABLE working_hours (
    id          UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    business_id UUID        NOT NULL REFERENCES businesses (id) ON DELETE CASCADE,
    employee_id UUID        NOT NULL REFERENCES employees (id) ON DELETE CASCADE,
    -- ISO-8601: Monday = 1 ... Sunday = 7, matching java.time.DayOfWeek.getValue().
    weekday     SMALLINT    NOT NULL,
    starts_at   TIME        NOT NULL,
    ends_at     TIME        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT working_hours_weekday_valid CHECK (weekday BETWEEN 1 AND 7),
    CONSTRAINT working_hours_window_ordered CHECK (ends_at > starts_at)
);
CREATE INDEX working_hours_employee_weekday_idx ON working_hours (employee_id, weekday);

-- Absolute instants, unlike working hours: a holiday or a vacation is a span of real time.
-- A row with no employee applies to the whole business, which is how a public holiday is
-- expressed without a second table.
CREATE TABLE blocked_times (
    id          UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    business_id UUID        NOT NULL REFERENCES businesses (id) ON DELETE CASCADE,
    employee_id UUID        REFERENCES employees (id) ON DELETE CASCADE,
    starts_at   TIMESTAMPTZ NOT NULL,
    ends_at     TIMESTAMPTZ NOT NULL,
    reason      TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT blocked_times_window_ordered CHECK (ends_at > starts_at)
);
CREATE INDEX blocked_times_lookup_idx ON blocked_times (business_id, starts_at, ends_at);
CREATE INDEX blocked_times_employee_idx ON blocked_times (employee_id);
