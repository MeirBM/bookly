-- Two guarantees the security review asked for, both moved into the database so they hold for
-- any writer rather than only for the one Java method that exists today.

-- 1. Identical working windows were unbounded. Twenty thousand copies of "Monday 00:00-23:59"
--    were all accepted, and each one multiplied the cost of every availability request. The
--    engine now merges overlapping windows before stepping, so the *cost* is bounded either
--    way; this stops the rows accumulating in the first place.
DELETE FROM working_hours a
USING working_hours b
WHERE a.ctid > b.ctid
  AND a.employee_id = b.employee_id
  AND a.weekday = b.weekday
  AND a.start_local = b.start_local
  AND a.end_local = b.end_local;

ALTER TABLE working_hours
    ADD CONSTRAINT working_hours_no_duplicate_window
        UNIQUE (employee_id, weekday, start_local, end_local);

-- 2. employee_services is the one table in V2 that cannot say which tenant a row belongs to,
--    so "an employee and a service on the same row belong to the same business" was enforced
--    only in application code. There is no way to write a bad row today - EmployeeDirectory
--    resolves both sides with (id, business_id) - but a bulk import, a repair script, or a
--    second write path added later would silently grant another tenant's service on your
--    employee, and availability would attribute your employee to it.
--
--    A trigger rather than a composite foreign key: the columns needed for
--    (employee_id, business_id) -> employees(id, business_id) would have to be carried on the
--    link table, and a plain JPA @ManyToMany join table cannot populate an extra column. The
--    trigger makes the bad row unrepresentable without forcing the mapping to become an entity
--    whose only purpose is to satisfy the constraint.
CREATE OR REPLACE FUNCTION employee_services_same_business() RETURNS trigger AS $$
DECLARE
    employee_business UUID;
    service_business  UUID;
BEGIN
    SELECT business_id INTO employee_business FROM employees WHERE id = NEW.employee_id;
    SELECT business_id INTO service_business FROM services WHERE id = NEW.service_id;
    IF employee_business IS DISTINCT FROM service_business THEN
        RAISE EXCEPTION
            'employee % and service % belong to different businesses',
            NEW.employee_id, NEW.service_id
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER employee_services_same_business_trg
    BEFORE INSERT OR UPDATE ON employee_services
    FOR EACH ROW EXECUTE FUNCTION employee_services_same_business();
