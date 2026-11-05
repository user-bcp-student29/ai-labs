# Proposal

## Why

Issue [user-bcp-student29/ai-labs#23](https://github.com/user-bcp-student29/ai-labs/issues/23) reports that crafted credentials can bypass authentication and obtain a login response as a valid user. This is a high-severity authentication risk; the login flow must reject those attempts without changing the successful login contract.

## What Changes

- Require credential matching to remain bound to the submitted username and password, so crafted input cannot authenticate as another account.
- Preserve successful `POST /auth/login` behavior: valid workshop credentials continue to receive `200`, `user.username`, and a token.
- Return `401` without a token when an injection-style username or password does not match an account.
- Scope the change to `auth/`; do not change the endpoint, request format, successful response contract, or other components.
- `docs/ENDPOINTS.md` documents API endpoints but has no `/auth/login` section. There is no documented login baseline to supersede; the success scenario follows the existing `AuthResourceTest`, and the rejection scenario follows Issue #23's acceptance criteria.

## Capabilities

### New Capabilities
- `auth-login`: Observable login success and credential-rejection behavior.

### Modified Capabilities
- None.

## Impact

- Affected component: `auth/`, specifically the observable behavior of `POST /auth/login` and its tests.
- No changes to API or web components, endpoint shape, request format, successful response fields, or dependencies.