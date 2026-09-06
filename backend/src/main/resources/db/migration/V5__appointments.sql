-- Turn 3: customers, appointments, and the constraint that makes double booking impossible
-- rather than merely unlikely.

-- Needed to mix an equality column with a range column in one exclusion constraint. It is an
-- extension, and some managed databases withhold the privilege to create one - turn-3 spec,
-- pitfall 1. Verified on the deployment target before this was relied on, not after.
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- A customer belongs to one business. The same person booking two salons is two rows, because
-- one salon has no business knowing the other's clientele.
CREATE TABLE customers (
    id          UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    business_id UUID        NOT NULL REFERENCES businesses (id) ON DELETE CASCADE,
    full_name   TEXT        NOT NULL,
    email       TEXT        NOT NULL,
    phone       TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT customers_name_not_blank CHECK (length(btrim(full_name)) > 0)
);

-- Case-insensitive, matching the users table: one email is one customer of this business.
CREATE UNIQUE INDEX customers_business_email_key ON customers (business_id, lower(email));

CREATE TABLE appointments (
    id           UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    business_id  UUID        NOT NULL REFERENCES businesses (id) ON DELETE CASCADE,
    employee_id  UUID        NOT NULL REFERENCES employees (id) ON DELETE CASCADE,
    service_id   UUID        NOT NULL REFERENCES services (id) ON DELETE RESTRICT,
    customer_id  UUID        NOT NULL REFERENCES customers (id) ON DELETE CASCADE,
    -- Instants. The customer chose a wall-clock time in the business's zone; what is stored is
    -- the moment that referred to.
    starts_at    TIMESTAMPTZ NOT NULL,
    -- Written at booking time from the service's duration then, so changing a service later does
    -- not silently move appointments already made - criterion 3.9.
    ends_at      TIMESTAMPTZ NOT NULL,
    status       TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT appointments_window_ordered CHECK (ends_at > starts_at),
    CONSTRAINT appointments_status_known
        CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'COMPLETED', 'NO_SHOW'))
);

-- A service cannot be deleted while appointments reference it (ON DELETE RESTRICT above):
-- losing the record of what someone booked is worse than refusing to tidy up the catalogue.

CREATE INDEX appointments_business_start_idx ON appointments (business_id, starts_at);
CREATE INDEX appointments_employee_start_idx ON appointments (employee_id, starts_at);
CREATE INDEX appointments_customer_idx ON appointments (customer_id);

-- The guarantee. Not a lock, not a check-then-insert: the database refuses the second row, so it
-- holds for a repair script and any future code path as well as for the service that exists today.
--
-- Half-open '[)' so an appointment ending at 10:00 does not collide with one starting at 10:00 -
-- back-to-back booking is the normal case for a barber, and '[]' would make it impossible.
--
-- The WHERE clause names exactly the statuses that occupy time. Omit CANCELLED and a cancelled
-- appointment blocks its old slot for ever; include it and a cancelled slot cannot be rebooked.
ALTER TABLE appointments
    ADD CONSTRAINT appointments_no_overlap
        EXCLUDE USING gist (
            employee_id WITH =,
            tstzrange(starts_at, ends_at, '[)') WITH &&
        ) WHERE (status IN ('PENDING', 'CONFIRMED'));

-- The audit trail: every status change, including creation, so a dispute about what happened has
-- an answer that does not depend on anyone's memory.
CREATE TABLE appointment_status_history (
    id             UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    appointment_id UUID        NOT NULL REFERENCES appointments (id) ON DELETE CASCADE,
    from_status    TEXT,
    to_status      TEXT        NOT NULL,
    changed_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    note           TEXT
);

CREATE INDEX appointment_status_history_appointment_idx
    ON appointment_status_history (appointment_id, changed_at);
