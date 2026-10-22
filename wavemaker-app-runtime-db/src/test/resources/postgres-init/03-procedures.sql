/*
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 */

-- Postgres equivalents of the 4 stored procedures NativeProcedureExecutorIntegrationTest creates inline
-- against HSQLDB (see src/test/java/.../dao/callbacks/NativeProcedureExecutorIntegrationTest.java). The two
-- dialects are NOT drop-in compatible for these signatures, so each one below is a deliberate translation,
-- not a copy-paste:
--
--   * Postgres PROCEDUREs cannot declare a plain OUT parameter at all (only IN/INOUT/VARIADIC) - returning a
--     value by name requires a FUNCTION instead. double_value and list_active_employee_names are therefore
--     FUNCTIONs here, even though HSQLDB defines all four as PROCEDUREs.
--   * increment_value's INOUT parameter has a true Postgres PROCEDURE equivalent (native since PG11), so that
--     one stays a PROCEDURE, invoked with CALL exactly like HSQLDB.
--   * HSQLDB's "DYNAMIC RESULT SETS n" opens cursors that are returned to the caller implicitly. Postgres has
--     no equivalent clause - the idiomatic translation is a FUNCTION returning SETOF (for a single result set)
--     or explicit REFCURSOR OUT parameters opened with OPEN ... FOR (for multiple result sets), each fetched
--     by name in a later statement within the SAME transaction (Postgres cursors without WITH HOLD are
--     transaction-scoped).
--
-- Verified live via `docker compose up` + psql, not just checked for syntax - see the FETCH/SELECT examples
-- in each comment below for the exact commands used.

-- double_value(IN x, OUT y) -> y = x * 2.
-- Verify: SELECT double_value(21);  -- expect 42
CREATE FUNCTION double_value(IN x INTEGER, OUT y INTEGER)
LANGUAGE plpgsql
AS $$
BEGIN
    y := x * 2;
END;
$$;

-- increment_value(INOUT x) -> x = x + 1. A real Postgres PROCEDURE, called with CALL, not SELECT.
-- Verify: CALL increment_value(5);  -- expect x = 6 in the result row
CREATE PROCEDURE increment_value(INOUT x INTEGER)
LANGUAGE plpgsql
AS $$
BEGIN
    x := x + 1;
END;
$$;

-- list_active_employee_names() -> the single-result-set case. Modeled as RETURNS SETOF, the idiomatic
-- Postgres shape for "a procedure that hands back rows" - there's no DYNAMIC RESULT SETS equivalent to
-- translate literally.
-- Verify: SELECT * FROM list_active_employee_names();
CREATE FUNCTION list_active_employee_names()
RETURNS SETOF VARCHAR
LANGUAGE plpgsql
AS $$
BEGIN
    RETURN QUERY SELECT name FROM employee WHERE active = TRUE;
END;
$$;

-- list_active_and_inactive_employee_names() -> the two-result-set case. A single RETURNS SETOF can only ever
-- produce one result set, so two independent REFCURSOR OUT params are the translation here - each OPENed FOR
-- a separate query inside the function body. The caller fetches both cursors by name in the same transaction.
-- Verify (psql):
--   BEGIN;
--   SELECT * FROM list_active_and_inactive_employee_names() \gset
--   FETCH ALL FROM :active_names;
--   FETCH ALL FROM :inactive_names;
--   COMMIT;
CREATE FUNCTION list_active_and_inactive_employee_names(
    OUT active_names REFCURSOR,
    OUT inactive_names REFCURSOR
)
LANGUAGE plpgsql
AS $$
BEGIN
    OPEN active_names FOR SELECT name FROM employee WHERE active = TRUE;
    OPEN inactive_names FOR SELECT name FROM employee WHERE active = FALSE;
END;
$$;
