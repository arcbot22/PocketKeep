# Accessibility and usability

This document defines review criteria, not evidence of a completed device audit.

## Primary flows

- Add an item with only its name required. Optional details must not block saving.
- Keep item creation, task creation, due lists, and completion usable offline.
- Present clear empty states and a visible action to create the first record.
- Explain each validation error beside its field and preserve entered values.
- Distinguish a full backup from a CSV history export. Never describe export as backup unless restore is supported.
- Restore must validate the selected file before changing current data.
- Confirmation of destructive operations must name the affected record and consequences.

## Accessibility acceptance criteria

- TalkBack can identify and activate every action in logical order.
- Icon-only actions have descriptive content descriptions; decorative images are excluded from semantics.
- All interactive targets are at least 48 dp in each dimension.
- Large system font and display sizes do not hide labels, errors, or primary actions. Long forms scroll.
- Status has a text label, such as “Overdue,” as well as color.
- Normal text reaches 4.5:1 contrast; large text and meaningful UI boundaries reach 3:1.
- Dialogs and forms expose a clear title and dismiss action without losing unrelated records.
- Dates follow device locale; due dates do not shift because of a timezone change.
- Monetary values include an explicit currency when the feature is present.
- Saving reports success; errors remain visible and do not claim a record was saved.

## Manual device checklist

Run on an emulator or physical Android device; record Android version, device, app commit, and results.

1. Enable TalkBack. Add an item, create a task, and complete it using spoken navigation.
2. Set font size and display size to their largest available settings. Repeat the primary flow and inspect dialogs.
3. Test an empty item name, long item names, long notes, and narrow display width.
4. Restart the process and verify saved records remain available.
5. If reminders are implemented, deny notification permission and verify due lists remain usable.
6. If backup is implemented, test cancellation, malformed input, successful restore, and media retention.
7. If delete is implemented, verify confirmation and that unrelated data is retained.

No device execution has been recorded in this document. Features listed conditionally must be assessed only when implemented.
