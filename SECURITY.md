# Security policy

PocketKeep is under development and does not yet have a supported production release.
Security fixes will initially target the default development branch. Do not assume a
published APK, security audit, or release signing process exists until the repository
provides a verified release and its build instructions.

## Reporting a vulnerability

A private reporting channel has not yet been configured. After an authorized public
GitHub repository is established, maintainers should enable GitHub private vulnerability
reporting before inviting sensitive reports. Use that channel when it is available.

Until then, do not disclose working exploits, credentials, private inventories, receipts,
serial numbers, photographs, or personal backup files in public issues or the collaboration
thread. A public issue may request a private contact channel without revealing the
vulnerability. Ordinary non-sensitive bugs can use the project's normal issue tracker
once one exists.

Useful reports include the affected version or commit, Android version, reproduction
steps using synthetic data, expected and actual behavior, and a description of the impact.
Maintainers cannot promise a response time until an actively monitored private channel
has been established.

## Data and build boundaries

- Inventory and maintenance records should remain on the device unless the user explicitly
  exports or shares them. Verify the implementation before making a privacy guarantee.
- Exported backups may contain personal information. Store and share them as carefully
  as the original records; do not assume exports are encrypted unless the format says so.
- Imported archives and image attachments are untrusted input. Validate versions, paths,
  sizes, and record references before applying a restore; preserve existing data on failure.
- Keep SDK paths, signing keys, keystore passwords, API credentials, and personal test
  data outside source control. The root `.gitignore` excludes common local files but is
  not a substitute for reviewing changes before committing.
- Use debug signing only for local development. A public release requires an explicit
  signing and distribution process; never publish a private signing key.

## Before the first release

Configure private reporting, review permissions and Android backup behavior, validate
backup/restore handling, check dependencies, and document the release signing and update
process. These are release requirements, not claims that those checks have passed.
