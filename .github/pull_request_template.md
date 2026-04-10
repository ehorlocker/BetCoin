## Type of Change

- [ ] Feature
- [ ] Bug fix
- [ ] Refactor
- [ ] Test
- [ ] Documentation
- [ ] Chore/config

## Related Issue

Closes #

## Description

<!-- What does this PR do and why? -->

## Changes Made

<!-- List the specific changes made in this PR -->

-

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
- [ ] Tested on dev flavor (`./gradlew installDevDebug`)
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
