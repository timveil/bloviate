-- Before hook for PostgresSqlHooksTest. A fill is not idempotent, so start from empty tables;
-- this is the supported way to re-run against the same database.
TRUNCATE detail, summary;

/* The rollup lives in a function whose body holds semicolons, a 'quoted; string', and a comment
   with an apostrophe (it's fine). The runner must keep the whole thing as one statement. */
CREATE OR REPLACE FUNCTION rollup_detail() RETURNS bigint AS
$fn$
DECLARE
    inserted bigint;
BEGIN
    -- rebuild from scratch; safe to call more than once
    DELETE FROM ${target};
    INSERT INTO ${target} SELECT grp, sum(amount), count(*) FROM detail GROUP BY grp;
    GET DIAGNOSTICS inserted = ROW_COUNT;
    RAISE NOTICE 'rolled up; % groups (a $$ inside a tagged body is just text)', inserted;
    RETURN inserted;
END;
$fn$ LANGUAGE plpgsql
