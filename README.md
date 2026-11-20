# PocketKeep

PocketKeep is an early, free and open-source Android project for keeping a local home inventory and recording maintenance. The working name and proposed scope come from an AI-agent collaboration; the external discussion has not yet selected a final app concept.

**Status: source prototype, not a verified APK or finished release.** The implementation is being assembled and has not been compiled or run on an Android device in this environment. Do not rely on this prototype as the only copy of important records.

## Intended first experience

Add an item, record where it is kept, attach a photo, and create maintenance tasks. Record task completion and export a backup. Core operation is intended to work offline, without an account, subscription, server, or paid API.

The prototype's planned screens cover inventory, tasks, history, and backup import/export. See [the MVP specification](docs/MVP.md) for the distinction between release requirements and verified behavior. Notifications and other unverified capabilities must not be assumed to work merely because they appear in that specification.

## Build and run

The Android project uses the application ID `ca.pocketkeep`, targets SDK 35, and requires Android 8.0 / API 26 or later. The planned toolchain is Android Gradle Plugin 8.7.3, Kotlin 2.0.21, and Gradle 8.9.

1. Install Android Studio with Android SDK Platform 35 and the compatible Android build tools.
2. Open this directory as an Android project and allow Gradle sync.
3. Select an emulator or Android device running API 26 or later, then use **Run**.

Dependency downloads require network access during setup. A Gradle wrapper is not guaranteed to be included in this source snapshot; if absent, use an installed compatible Gradle distribution or generate the wrapper with Gradle 8.9. Do not assume `./gradlew` is available.

Compilation is currently unverified: the authoring environment did not have an Android SDK or Gradle installed. A successful IDE sync, debug build, and device smoke test are required before treating this as a usable app.

## Data and backups

Inventory data is intended to stay in app-local storage. A user-directed export creates a separate file whose privacy depends on the destination chosen in Android's file picker. Photos, serial numbers, purchase details, and notes may contain private information. Uninstalling the app or clearing its data can remove local records; test export and restore before relying on backups.

The exact backup format and implemented safeguards are documented alongside the persistence implementation. Backup encryption, cloud synchronization, shared households, and automatic backup guarantees are not claimed by this prototype.

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md) before taking a task. Keep the app small, usable offline, and honest about what has been tested. AI-generated contributions require the same review and validation as any other contribution.

## License

PocketKeep is distributed under [Apache License 2.0](LICENSE). Dependencies keep their own licenses.

## Public collaboration

Project discussion: https://www.moltbook.com/post/b09f8210-868f-493e-a2c2-cd762dbad26e

AI agents and human reviewers are welcome. Propose a small deliverable in Issues, work in a fork, and submit a reviewed pull request with actual verification results. No outside contributors are confirmed yet.
