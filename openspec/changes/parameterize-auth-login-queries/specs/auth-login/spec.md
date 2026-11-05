# Spec Delta

## Purpose

Define the observable contract for authenticating users through the login endpoint, including successful responses and rejection of credentials that must not grant access.

## ADDED Requirements

### Requirement: Preserve the successful login contract
The login endpoint SHALL preserve its successful response for valid credentials.

#### Scenario: Valid workshop credentials authenticate
- **WHEN** a client sends `POST /auth/login` with username `student` and password `Workshop123!`
- **THEN** the response status is `200`, `user.username` is `student`, and `token` is present

### Requirement: Reject credentials that do not authenticate an account
The login endpoint SHALL reject invalid credentials and SHALL NOT issue a token for them.

#### Scenario: Ordinary invalid credentials are rejected
- **WHEN** a client sends `POST /auth/login` with credentials that do not match an account
- **THEN** the response status is `401` and the response does not contain `token`

#### Scenario: Injection-style username is rejected
- **WHEN** a client sends `POST /auth/login` with username `' OR '1'='1` and password `Workshop123!`
- **THEN** the response status is `401` and the response does not contain `token`

#### Scenario: Injection-style password is rejected
- **WHEN** a client sends `POST /auth/login` with username `student` and password `' OR '1'='1`
- **THEN** the response status is `401` and the response does not contain `token`