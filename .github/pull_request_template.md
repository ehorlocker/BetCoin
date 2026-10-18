<!-- PR title: [BET-<n>] <Jira ticket title> -->

## Ticket

- Jira: [BET-](https://ericisboring.atlassian.net/browse/BET-)
- Closes #

## Type of Change

- [ ] Feature
- [ ] Bug fix
- [ ] Refactor
- [ ] Test
- [ ] Documentation
- [ ] Chore/config

## Description

<!-- What does this PR do and why? -->

## Changes Made

<!-- List the specific changes made in this PR -->

-

## Bug Fix Details

<!-- Bug fixes only; delete this section for other PRs. -->

- **Root cause:**
- **Steps to reproduce (before fix):**
- **How the fix addresses it:**
- [ ] Regression test added that fails without this fix

## Feature Details

<!-- Features only; delete this section for other PRs. -->

- **Spec reference:** <!-- e.g. Section 5.3 — Bet Resolution -->
- **Acceptance criteria met:**
  - [ ]

## Architecture Layers Modified

- [ ] Database/Entity
- [ ] DAO/Repository
- [ ] ViewModel
- [ ] UI/Composable
- [ ] Navigation
- [ ] DI Module

## Testing Checklist

- [ ] Unit tests pass (`./gradlew test`)
- [ ] Instrumented tests pass (`./gradlew connectedAndroidTest`)
- [ ] Verified on the `BetCoin_Test` emulator (`./gradlew installDevDebug`)
- [ ] New tests added for new functionality
- [ ] Manual testing performed (describe below)

## Manual Testing Description

<!-- What did you manually verify? Describe the scenarios you tested. -->

## Data Integrity Considerations

<!-- Complete this section if your changes touch the data layer, transactions, or currency logic. -->

- [ ] Transaction boundaries verified (`@Transaction` on atomic operations)
- [ ] Currency stored as `Long` (no floating-point arithmetic)
- [ ] PIN handling follows security rules (hashed, never logged/stored in plaintext)

## Screenshots

<!-- If this PR includes UI changes, add before/after screenshots. -->
