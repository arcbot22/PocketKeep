# PocketKeep MVP

## Product decision

PocketKeep is the working recommendation for a free, offline home inventory and maintenance app. The external collaboration thread has not yet selected the final app concept. FixLog's narrower maintenance focus fits inside this proposal; ShiftLedger's shift and pay calculations belong to a separate project.

The initial user is a renter, homeowner, or hands-on maintenance worker who wants to find belongings, preserve item details, and remember upkeep without setting up an account.

The core journey is: add an item, find it later, schedule upkeep, record completion, and export a restorable backup.

## Status and evidence

This document defines desired release behavior. It is **not** a declaration that every requirement is implemented or tested. The current deliverable is a source prototype. Android compilation, installation, permission behavior, and device testing remain unverified in the authoring environment, which lacks the Android SDK and Gradle.

The implementation work covers a Compose inventory/tasks/history interface, local persistence, day-interval recurrence, photos, and user-directed backup export/import. Check the actual source and validation results before claiming a feature works. Reminder notifications are a release requirement, not a verified prototype capability.

## Required for the first usable release

| Area | Required behavior | Acceptance check |
| --- | --- | --- |
| Inventory | Create, read, edit, and delete named items; optional location, category, notes, serial number, purchase details, and photos | Create and edit an item, restart the app, and verify all stored fields and photos |
| Finding items | Search names, locations, and serial numbers; filter by category or location | Search known values and verify both matches and clear empty results |
| Maintenance | Link titled tasks to items, with due dates and optional recurrence | Create a task and verify its item link and due date after restart |
| Completion history | Preserve a dated completion entry and notes | Complete a one-off task and verify one history entry and no outstanding duplicate |
| Recurrence | Use explicit intervals of 7, 30, 90, or 365 days after completion | Complete a recurring task and verify exactly one history entry and one next due date |
| Due lists | Distinguish upcoming, due, and overdue work using local calendar dates | Verify yesterday, today, and tomorrow around a date boundary |
| Reminders | Offer local notifications with permission-aware behavior; no exact-minute promise | Verify permission granted and denied paths on a supported Android device |
| Full backup | Export and restore records and photos | Import into a clean installation and compare items, tasks, history, and photos |
| Import safety | Validate before replacing records and require confirmation for replacement | Reject invalid backups without changing existing records; verify cancellation |
| CSV | Export inventory and maintenance records in a documented tabular format | Open exported CSV and verify fields, quoting, and non-ASCII text |
| Offline use | No account, subscription, backend, or paid API required for core actions | Complete the core journey in airplane mode |
| Deletion | Explain linked data removal before deleting an item | Cancel deletion, then confirm deletion and verify the documented result |
| Accessibility | Support TalkBack, enlarged text, and touch targets of at least 48 dp | Exercise core screens with TalkBack and enlarged system text |

Day intervals must be labeled as days. A 30-day interval is not a calendar-month recurrence. A completion date plus the selected interval determines the next due date; the UI must not imply a different rule.

## Later candidates

Receipt photos, warranty dates, item duplication, bulk moves, richer recurrence, widgets, and encrypted backups may follow once basic persistence and restore are trustworthy.

## Explicit exclusions

The first release excludes cloud sync, shared accounts, OCR, AI diagnosis, market valuations, barcode product lookup, insurance submissions, subscriptions, advertising, and payroll calculations.

## Release gate

Before announcing a usable release, record a successful Android build and device installation, execute the acceptance checks above, verify backup restoration with photos, include the Apache 2.0 license text, and document the exact build toolchain. Publish reproducible setup instructions and disclose unresolved defects. Passing host-side tests alone does not satisfy the Android build or device checks.
