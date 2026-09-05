-- working_hours.starts_at / ends_at are wall-clock times, not instants, and the names said
-- otherwise. SchemaConventionsIT enforces that a column named like an instant is stored as
-- timestamptz, and it failed - correctly - on columns whose names promised something their
-- type does not deliver.
--
-- This settles the convention rather than carving out an exception:
--
--   *_at     an instant on the timeline, stored timestamptz
--   *_local  a wall-clock time in the business's own zone, stored time
--
-- Both halves are checkable, and neither name can be read as the other. Relaxing the test
-- instead would have let a genuine instant stored as `time` slip through later, which is the
-- bug this project can least afford: it is exactly how a booking ends up an hour out twice a
-- year and nobody notices until a customer arrives to a closed shop.
--
-- A separate migration rather than an edit to V2, because V2 is committed and committed
-- migrations are immutable here - the pre-commit hook refuses the edit mechanically.

ALTER TABLE working_hours RENAME COLUMN starts_at TO start_local;
ALTER TABLE working_hours RENAME COLUMN ends_at TO end_local;

ALTER TABLE working_hours RENAME CONSTRAINT working_hours_window_ordered
    TO working_hours_local_window_ordered;
