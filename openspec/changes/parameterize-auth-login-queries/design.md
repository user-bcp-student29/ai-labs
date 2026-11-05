# Design

## Context

`LegacyDatabase.findByCredentials(username, password)` (`auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:31-32`) builds the credential lookup by concatenating both parameters directly into a SQL string executed with `Statement`:

```java
String sql = "SELECT id, username, display_name, role FROM legacy_users WHERE username='" + username + "' AND password='" + password + "'";
```

A value such as `' OR '1'='1` in either field changes the query's logic instead of being treated as data, which is the vulnerability in Issue #23. `AuthResource.login` (`auth/src/main/java/com/bcp/workshop/auth/AuthResource.java`) calls this method directly after basic null/blank checks and does not itself touch SQL; the endpoint, request/response shapes, and `LegacyTokenUtil` token creation are unrelated to the query construction. See proposal.md - Why for the full motivation.

## Goals / Non-Goals

**Goals:**
- Make `findByCredentials` treat `username` and `password` strictly as data values during the credential lookup, so no input value can alter the query's logic.
- Keep every externally observable behavior defined in `specs/auth-login/spec.md` unchanged for valid and invalid (non-injection) credentials.

**Non-Goals:**
- Redesigning authentication, token issuance (`LegacyTokenUtil`), password storage/hashing, or the `legacy_users` schema.
- Introducing a repository/service layering refactor beyond what is needed to isolate and parameterize the query.
- Changing `AuthResource`'s HTTP contract, `LoginRequest`/`UserRecord` shapes, or any component outside `auth/`.

## Protected Contracts

- `POST /auth/login` request shape (`username`, `password`, optional `client`) is unchanged.
- Success response: `200` with `token`, `expiresIn`, `user.username`, `user.displayName`, `user.role`, `debug` — unchanged, per `AuthResourceTest.logsInWithWorkshopUser` and `specs/auth-login/spec.md` Scenario "Valid workshop credentials authenticate".
- Existing error responses from `AuthResource.login` (`400` for missing fields, `401` for `user == null`, `500` on unexpected exception) are unchanged; only the data backing the `401` branch becomes correct for injection-style input.
- `findByCredentials(String username, String password)` method signature and `UserRecord`/`null` return contract with `AuthResource` are unchanged.

## Affected Components

- `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java`: query construction in `findByCredentials` only.
- `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`: add characterization and injection-rejection tests.
- No changes to `AuthResource.java`, `LegacyTokenUtil.java`, `LoginRequest.java`, `UserRecord.java`, `application.properties`, `pom.xml`, or any `api/`/`web/` file.

## Decisions

**Use `PreparedStatement` with bound parameters for `username` and `password`, keeping `Statement` for schema setup in `initialize()`.**
- Rationale: it directly removes the string-concatenation path the issue identifies, keeps the change minimal (one method body), and needs no new dependency — `java.sql.PreparedStatement` is already available via the existing `java.sql` imports.
- Alternative considered: parameterize the whole class (including `initialize()`’s DDL/seed statements). Rejected — those statements have no untrusted input and are out of scope per the proposal’s "scope the change to `auth/`" and specifically to `findByCredentials`.
- Alternative considered: introduce a repository/DAO abstraction layer now. Rejected as a non-goal; it would expand the change beyond what the issue requires and beyond what a single-method parameterization needs to be reviewable.

**Keep connection-per-call via `DriverManager.getConnection(DB_URL)`, unchanged.**
- Rationale: the current method already opens/closes its own connection per call with try-with-resources; the fix only needs to change `Statement` → `PreparedStatement` and how the SQL text/parameters are supplied. Changing connection management is unrelated to the vulnerability and would add risk without benefit.
- Alternative considered: introduce connection pooling. Rejected as out of scope (non-goal) and unrelated to the fix.

**Preserve the method's `UserRecord`/`null` return and exception-swallowing (`catch (Exception error) { error.printStackTrace(); }`) behavior exactly.**
- Rationale: `AuthResource` depends on `null` meaning "no match" to return `401`; changing error handling could alter the protected `401`/`500` contract split. Issue #23 does not ask for exception-handling changes.
- Alternative considered: let SQL exceptions propagate to `AuthResource`'s generic `catch (Exception error)` (which returns `500`). Rejected — it would change observable behavior for malformed-but-non-malicious input in ways not covered by the spec, and is unnecessary to fix the reported vulnerability.

## Incremental Strategy

1. Add characterization tests against current behavior (see below) and confirm they pass unmodified, establishing a safety net.
2. Change `findByCredentials` to build the SQL with `?` placeholders and bind `username`/`password` via `PreparedStatement.setString`, leaving every other line (connection handling, result mapping, exception handling) untouched.
3. Add the injection-rejection tests from `specs/auth-login/spec.md` (username and password variants) and run the full `AuthResourceTest` suite from `auth/`.
4. No phased rollout beyond this: the method is small, used only by `AuthResource.login`, and the change is a single, atomic commit-sized edit within `auth/`.

## Characterization Tests

Needed before changing `findByCredentials`, to lock in current behavior that must survive the fix:
- Valid credentials (`student` / `Workshop123!`) authenticate successfully — already covered by `AuthResourceTest.logsInWithWorkshopUser`; keep as the baseline/regression guard.
- A second known account (`trainer` / `Trainer123!`) authenticates successfully, to confirm the parameterized query still matches correctly-escaped, non-malicious credentials for more than one row.
- An ordinary wrong password for an existing username is rejected with `401` and no `token`, to confirm the non-injection rejection path is unaffected by the query change.

These characterize behavior the fix must not break. The two new scenarios from `specs/auth-login/spec.md` (injection-style username with a valid password; valid username with injection-style password) are the regression tests proving the vulnerability is closed; they are expected to fail against current code and pass after the fix.

## Risks / Trade-offs

- [Risk] A future edit to `findByCredentials` reintroduces string concatenation (e.g., to add a filter) → Mitigation: the injection-rejection tests in `AuthResourceTest` stay in the suite as permanent regression tests.
- [Risk] Case-sensitive or whitespace differences in how `PreparedStatement` binds values vs. the old inline string could change matching for edge-case stored data → Mitigation: characterization tests above pin both existing seeded accounts' success behavior before and after the change.
- [Trade-off] Keeping `Statement`-based DDL/seed logic in `initialize()` unchanged leaves that method's string-built SQL as-is → Acceptable: those statements carry no request-supplied input, so they are not part of the vulnerability and are explicitly out of scope.

## Rollback

The change is confined to one method body plus added tests, in one file each. Rollback is reverting `LegacyDatabase.findByCredentials` to its previous implementation (restoring the pre-change git revision of `LegacyDatabase.java`) and, if desired, removing the newly added test methods from `AuthResourceTest.java`; no data migration, schema change, or deployment coordination is involved since the `legacy_users` table and its seed data are untouched.
