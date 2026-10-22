-- Dummy data for the runtime-db JUnit suite. Deliberately varied (not uniform rows) so filter/sort/pagination/
-- aggregation scenarios have something real to distinguish between - e.g. a mix of active/inactive employees,
-- a range of salaries, and at least one employee with no department at all (tests the nullable association).

INSERT INTO department (name, location) VALUES
    ('Engineering', 'Bengaluru'),
    ('Sales', 'Mumbai');

INSERT INTO employee (name, salary, hiredate, active, department_id) VALUES
    ('Alice',   95000.00, '2021-03-15', true,  1),
    ('Bob',     72000.00, '2022-07-01', true,  1),
    ('Carol',   68000.00, '2020-01-10', false, 1),
    ('Dave',    55000.00, '2023-11-20', true,  2),
    ('Eve',     61000.00, '2019-06-05', false, 2),
    ('Frank',   48000.00, '2024-02-28', true,  NULL),
    ('Ivan',   140000.00, '2015-04-01', true,  NULL);

INSERT INTO employeeaward (employeeid, awardcode, awardedon) VALUES
    (1, 'EMPLOYEE_OF_MONTH', '2023-01-01'),
    (1, 'TOP_PERFORMER',     '2023-06-01'),
    (2, 'EMPLOYEE_OF_MONTH', '2023-04-01');

-- Self-referencing hierarchy (manager_id), now THREE levels deep: Ivan (CEO, no department, no manager -
-- a manager who isn't tied to any one department) sits above Alice and Dave; Bob/Carol report to Alice;
-- Eve reports to Dave. Frank stays entirely unmanaged, alongside having no department - two independent
-- "orphan" scenarios on the same row on purpose.
UPDATE employee SET manager_id = (SELECT id FROM employee WHERE name = 'Ivan') WHERE name IN ('Alice', 'Dave');
UPDATE employee SET manager_id = (SELECT id FROM employee WHERE name = 'Alice') WHERE name IN ('Bob', 'Carol');
UPDATE employee SET manager_id = (SELECT id FROM employee WHERE name = 'Dave') WHERE name = 'Eve';

-- One-to-One (shared PK style): only the two department leads have a profile, so tests also have a real
-- "association is absent" case to check - everyone else has no employee_profile row at all.
INSERT INTO employee_profile (employee_id, bio, address) VALUES
    (1, 'Engineering lead, 10+ years experience', '221B Baker Street'),
    (4, 'Sales lead for the Mumbai region', '42 MG Road');

-- One-to-One (separate unique-FK style, contrast with employee_profile above): badges for most, but not
-- all, employees - Frank and Ivan have no badge, giving tests an "association absent" case here too.
INSERT INTO employee_badge (employee_id, badge_number) VALUES
    (1, 'BDG-1001'),
    (2, 'BDG-1002'),
    (3, 'BDG-1003'),
    (4, 'BDG-1004'),
    (5, 'BDG-1005');

-- Self-referencing Many-to-Many (peer links, symmetric in meaning but stored as one directional row per
-- pair here): Bob/Carol collaborate closely (same department); so do Eve and Frank (cross-department).
INSERT INTO employee_peer (employee_id, peer_id) VALUES
    (2, 3),
    (5, 6);

-- One-to-Many (CASCADE) parent + child.
INSERT INTO project (name, startdate) VALUES
    ('Platform Migration', '2024-01-01'),
    ('Customer Portal Revamp', '2024-03-15');

INSERT INTO task (project_id, title, done) VALUES
    (1, 'Set up CI pipeline',          true),
    (1, 'Migrate database schema',     false),
    (1, 'Decommission legacy service', false),
    (2, 'Design new UI mockups',       true),
    (2, 'Implement checkout flow',     false);

/*
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 */

-- Many-to-Many: Alice sits on both projects (Lead on one, Advisor on the other) - a real employee-side
-- fan-out, not just one join row per employee.
INSERT INTO employee_project (employee_id, project_id, role) VALUES
    (1, 1, 'Lead'),
    (2, 1, 'Contributor'),
    (4, 2, 'Lead'),
    (5, 2, 'Contributor'),
    (1, 2, 'Advisor');

-- Many-to-Many as its own entity (task_assignment), contrast with the pure employee_project join table
-- above: each row has its own id and carries attributes (hours_logged) a flat join table couldn't hold
-- per-pair without ambiguity if the same employee/task pair ever needed more than one row.
INSERT INTO task_assignment (task_id, employee_id, assigned_on, hours_logged) VALUES
    (1, 2, '2024-01-05', 12.5),
    (1, 1, '2024-01-05', 3.0),
    (2, 2, '2024-01-20', 6.0),
    (4, 5, '2024-03-18', 9.5),
    (5, 5, '2024-03-25', 4.0);

-- Verified against the real fixtures - see 01-schema.sql's note.
INSERT INTO temporalemployee (name, active) VALUES
    ('Grace', true),
    ('Heidi', true);

INSERT INTO temporalemployeehistory (name, active) VALUES
    ('Grace', true);

-- Plain one-to-many child, cascade-delete target - see 01-schema.sql's temporalemployeeaward note.
INSERT INTO temporalemployeeaward (employee_id, awardcode, awardedon) VALUES
    (1, 'EMPLOYEE_OF_MONTH', '2024-06-01'),
    (1, 'TOP_PERFORMER', '2024-09-01');
