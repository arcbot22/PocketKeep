# Contributing to PocketKeep

PocketKeep is an early source prototype. Contributions should make one complete user action work reliably rather than expand the feature list before the basics are verified.

## Choose and coordinate work

Check existing issues and the collaboration discussion before starting. Describe the intended behavior, acceptance criteria, and files you expect to change. Coordinate ownership when other contributors or AI agents are working in the same tree. Do not imply that posting a proposal means another contributor has accepted it.

The first milestone is local inventory, maintenance history, and a tested backup round trip. See [docs/MVP.md](docs/MVP.md). Accounts, paid APIs, cloud services, analytics, and monetization are outside the initial scope.

## Implementation expectations

- Use Kotlin and follow the surrounding Android code style.
- Keep records and images in app-local storage unless the user explicitly exports them.
- Request permissions only when needed for a user action and handle denial gracefully.
- Keep task recurrence deterministic and specify whether an interval means days or calendar months.
- Validate imports before replacing existing records; a failed import must preserve existing data.
- Never commit real personal inventories, receipts, credentials, or private photos. Use clearly fictional examples.
- Keep accessibility in mind: meaningful labels, TalkBack navigation, large text, and adequate touch targets.
- Review dependency licenses and avoid unnecessary dependencies or services.

## Validation and pull requests

Build the Android project with the documented toolchain. Run available tests, then exercise the affected flow on an emulator or device. Add meaningful tests for persistence, recurrence, and backup parsing when changing those behaviors.

A pull request should state the user-visible change, why it is needed, what was tested, the device or API level used, and any remaining limitations. If you could not build or run it, say so explicitly. Do not present a source inspection or a host-side check as an Android device test.

AI assistance is welcome. Contributors remain responsible for reviewing generated code, confirming it works, and ensuring that submitted material can be distributed under the project's Apache 2.0 license. The repository's license file governs the actual grant.

## Reporting problems

Include reproduction steps, expected and actual results, app version or commit, and Android version. Redact personal information from screenshots and logs. Report backup failures with fictional minimal examples whenever possible.
