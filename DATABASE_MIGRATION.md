# EduERP database migration review

The reviewed migration is [V1__create_eduerp_application_schema.sql](src/main/resources/db/migration/V1__create_eduerp_application_schema.sql).

It creates the nine tables mapped by the JPA entities:

`users`, `students`, `teachers`, `courses`, `course_enrollments`, `schedules`, `attendance`, `grades`, and `messages`.

It does not create, alter, rename, truncate, or delete `connection_test`. It contains no credentials and no data inserts.

## Safety properties

- The migration starts with a MySQL preflight procedure.
- The preflight checks every EduERP application table name in the active database.
- If any application table already exists, it raises an error before the first application `CREATE TABLE` statement.
- Existing `connection_test` is deliberately excluded and remains untouched.
- Tables use explicit primary keys, unique constraints, foreign keys, and InnoDB.
- Foreign keys have no delete/update cascade because the entities do not request database cascades.
- `spring.jpa.hibernate.ddl-auto=validate` remains unchanged.
- Flyway runs before Hibernate validation.
- The only Flyway metadata table added by the migration process is `flyway_schema_history`.
- Demo data initialization remains disabled unless `DEMO_PASSWORD` is explicitly supplied.

## Before approval: read-only inspection

Set local process environment variables without committing them. Do not put values in this document or in the migration file.

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/edu_erp"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "<local-password>"
```

Use MySQL Workbench or `mysqlsh` to inspect the target database:

```sql
USE edu_erp;
SELECT DATABASE();
SHOW TABLES;
SHOW CREATE TABLE connection_test;

SELECT table_name, table_type
FROM information_schema.tables
WHERE table_schema = 'edu_erp'
ORDER BY table_name;
```

Do not run the migration until the `connection_test` definition and the V1 SQL have been reviewed.

## Manual execution after approval

The backend is configured with `FLYWAY_ENABLED=true` and `FLYWAY_BASELINE_ON_MIGRATE=true` by default. With the environment variables set, manually starting the backend will:

1. Connect to `edu_erp`.
2. Create Flyway’s `flyway_schema_history` metadata table if needed.
3. Baseline the pre-existing non-empty schema at version `0`.
4. Execute V1.
5. Run Hibernate with `ddl-auto=validate`.

```powershell
cd C:\Users\rohit\OneDrive\Rohith_Projects\edu-erp-backend
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.12.1"
$env:DB_URL = "jdbc:mysql://localhost:3306/edu_erp"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "<local-password>"
$env:JWT_SECRET = "<local-secret>"
$env:FLYWAY_ENABLED = "true"
.\mvnw.cmd spring-boot:run
```

If the preflight detects an existing application table, startup must stop. Review that table manually; do not change the migration to overwrite it.

## Post-migration validation

After the migration succeeds, stop the backend and run these read-only checks:

```sql
USE edu_erp;
SHOW TABLES;
SELECT version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;

SELECT table_name, column_name, column_type, is_nullable, column_key, extra
FROM information_schema.columns
WHERE table_schema = 'edu_erp'
  AND table_name IN (
    'users', 'students', 'teachers', 'courses', 'course_enrollments',
    'schedules', 'attendance', 'grades', 'messages', 'connection_test'
  )
ORDER BY table_name, ordinal_position;

SELECT table_name, constraint_name, constraint_type
FROM information_schema.table_constraints
WHERE table_schema = 'edu_erp'
ORDER BY table_name, constraint_name;
```

Confirm that `connection_test` is still present and that its columns/data are unchanged. Then start the backend a second time; Flyway should report V1 already applied and Hibernate validation should pass without DDL changes.

## Rollback/backup note

MySQL DDL may implicitly commit. Take a database backup before approval and execution. This migration intentionally has no destructive rollback script; do not attempt to roll back by dropping tables without a separately reviewed recovery plan.
