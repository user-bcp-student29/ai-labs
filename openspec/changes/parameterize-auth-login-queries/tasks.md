# Tasks

## 1. Characterization tests (safety net before the functional change)

- [x] 1.1 In `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`, add a test asserting `POST /auth/login` with `trainer`/`Trainer123!` returns `200`, `user.username` = `trainer`, and a non-null `token`; verify by running `mvn test` from `auth/` and confirming it passes against current (pre-change) code.
- [x] 1.2 In the same file, add a test asserting `POST /auth/login` with username `student` and an ordinary wrong password (e.g. `wrong-password`) returns `401` and no `token`; verify by running `mvn test` from `auth/` and confirming it passes against current (pre-change) code.
- [x] 1.3 Run `mvn test` from `auth/` and confirm all existing tests plus 1.1-1.2 pass before touching `LegacyDatabase.java`, establishing the pre-change baseline.

## 2. Incremental implementation

- [x] 2.1 In `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java`, change `findByCredentials` to build the SQL with `?` placeholders and bind `username`/`password` via `PreparedStatement.setString`, keeping the method signature, connection handling, result mapping, and exception handling unchanged as described in design.md - Decisions; verify by running `mvn test` from `auth/` and confirming the 1.1-1.3 characterization tests still pass unmodified.

## 3. Regression tests for the reported vulnerability

- [x] 3.1 In `AuthResourceTest.java`, add the "Injection-style username is rejected" scenario from `specs/auth-login/spec.md` (username `' OR '1'='1`, password `Workshop123!`) asserting `401` and no `token`; verify by running `mvn test` from `auth/` and confirming it fails against pre-change code and passes after task 2.1.
- [x] 3.2 In `AuthResourceTest.java`, add the "Injection-style password is rejected" scenario (username `student`, password `' OR '1'='1`) asserting `401` and no `token`; verify by running `mvn test` from `auth/` and confirming it fails against pre-change code and passes after task 2.1.

## 4. Full verification

- [x] 4.1 Run `mvn test` from `auth/` (or `python tools/lab.py test auth` from the repo root) and confirm the full `AuthResourceTest` suite passes, covering the preserved success contract (1.1, existing `logsInWithWorkshopUser`), the preserved non-injection rejection contract (1.2), and the closed injection vulnerability (3.1, 3.2).
- [x] 4.2 Confirm `LegacyDatabase.java` has no remaining string concatenation of `username` or `password` into SQL text, by inspection of `findByCredentials`.
