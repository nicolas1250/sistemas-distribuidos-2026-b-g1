<!-- HU-STATUS TEMPLATE - do NOT remove the <!-- ... --> markers or the table headers.
     Your weekly grade is read AUTOMATICALLY from this file:
       13-week/hu-status/README.md  (inside YOUR fork). English. -->

# Weekly Status - Week 13

<!-- CONFIG-START - must match your profile repo (username/username) CONFIG -->
- FULL_NAME: Nicolas Obregon Rojas
- GITHUB_USER: nicolas1250
- TEAM: CineSync Platform
- SPRINT_GOAL: Threat model the boundary between csp-api-gateway and csp-ticketing-api (STRIDE) and turn the three highest risks into security stories with a negative authorization test in the pipeline.
<!-- CONFIG-END -->

## 1. User stories worked this week
| HU ID | Title | Status (todo/doing/done) | Evidence (PR or commit URL) |
|---|---|---|---|
| W13-WORKSHOP | Threat modeling of csp-api-gateway to csp-ticketing-api | done | https://github.com/code-corhuila/csp-docs/pull/290 |
| SEC-S-291 | [security] ticketing-api accepts only tokens signed by csp-auth-api (S, risk 9, P0) | todo | https://github.com/code-corhuila/csp-docs/issues/291 |
| SEC-T-292 | [security] ticket PDF link can only point to CineSync object storage (T, risk 6, P1) | todo | https://github.com/code-corhuila/csp-docs/issues/292 |
| SEC-E-293 | [security] internal ticketing routes callable only by allowed services (E, risk 6, P1) | todo | https://github.com/code-corhuila/csp-docs/issues/293 |

## 2. My individual contribution
- CP0: claimed the boundary csp-api-gateway to csp-ticketing-api in issue #279 and created the workshop README with the CONFIG block.
- CP1: mapped the attack surface of CineSync (five trust boundaries, eight assets, six entry points of TB3) in `13-week/workshop/01-attack-surface.md`.
- CP2: wrote the STRIDE analysis of TB3 in two pushes on the branch `security/stride-nicolas1250` (PR #290). Highest risk is S (9). The row S uses the library book (Cano, p. 40).
- CP3: opened three `[security]` stories (#291, #292, #293) with the labels `security` and P0/P1.
- CP4: added `security.yml` (gitleaks, OWASP dependency-check, negative test) and `TicketingAuthzNegativeTest` in `13-week/workshop/04-security-ci/`.
- CP5: peer review comment on the STRIDE PR of Yeferson (#277), about the per-sub rate limit and the shared IP bucket in the Gateway.
- CP6: re-checked the six STRIDE rows against `develop` of csp-ticketing-api after `csp-ticketing-api#36` (token validation) and added a status section to PR #290. S and E fell from risk 9 and 6 to 3; T to 3; I (6) is now the highest open risk. No teammate had commented on the PR.
- CP7: refined the three stories with a comment each (state after #36) and linked #293 with #285 and #287, and #291 with #284.
- CP8: ran `TicketingAuthzNegativeTest` against the real service from `develop` (PostgreSQL, RabbitMQ and the migrations of csp-ticketing-db in Docker): PASS, both cases return 401. Validated `security.yml` with actionlint.
- CP9: implementation plan written in the reflection document.

## 3. Blockers and risks
- The token validation (`csp-ticketing-api#36`, closes #12) is only in `develop`; it is not in `qa` or `main` yet, so stories #291 and #293 stay open.
- `pdfUrl` of `POST /internal/tickets/{ticketId}/pdf` is not checked for the `s3://` prefix (#292), and `/actuator/health` still has `show-details: always` (threat I, risk 6).
- GitHub Actions is out of service, so the security gates only run locally. csp-ticketing-api has no `deploy/compose.yml` to start the service in a pipeline.
- The issues are not yet in the team board: my `gh` token lacks the project scope.

## 4. Plan for next week
- Promote the token validation to `qa` and run the negative test there (#291, #293).
- Change `show-details` to `when-authorized` and add a test for `/actuator/health` (threat I).
- Check the `s3://` prefix of `pdfUrl` with an integration test (#292).

## 5. Compliance self-check
- [x] Conventional Commits - `type(scope): summary`
- [ ] Per-environment HU branch + PR to that environment (hu-xxx-dev -> develop, ...)
- [x] Testable acceptance criteria
- [ ] Tests added/updated (unit / integration)
- [x] DDD / hexagonal boundaries respected (domain has no I/O)
- [x] No secrets; config via environment variables

## 6. Evidence links
- Claim issue: https://github.com/code-corhuila/csp-docs/issues/279
- STRIDE pull request: https://github.com/code-corhuila/csp-docs/pull/290
- STRIDE push 1: https://github.com/code-corhuila/csp-docs/commit/e427ca7
- STRIDE push 2: https://github.com/code-corhuila/csp-docs/commit/70f8e3c
- Security stories: https://github.com/code-corhuila/csp-docs/issues/291 , https://github.com/code-corhuila/csp-docs/issues/292 , https://github.com/code-corhuila/csp-docs/issues/293
- Peer review comment: https://github.com/code-corhuila/csp-docs/pull/277#pullrequestreview-5692268822
- Fork commits: 557ef93 (CP0), e0443b4 (CP1), 693e972 (CP3), 4a6547d (CP4), c478f93 (CP7), 5df1937 (CP8)
- STRIDE update (CP6): https://github.com/code-corhuila/csp-docs/commit/83731fd
- Workshop folder: https://github.com/nicolas1250/sistemas-distribuidos-2026-b-g1/tree/main/13-week/workshop
