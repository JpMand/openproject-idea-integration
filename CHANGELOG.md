<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# openproject-idea-integraiton Changelog

## [Unreleased]
### Changed
- Work package lists and work packages open in the new OpenProject tool window (bottom) instead of editor tabs: a row click previews the work package next to the list, "Open in New Tab" opens it in a tab (properties left, activity/relations/files right), "Open as New Tab" and "+" open more lists
- Quick filter dropdowns (project, type, status, priority, assignee) above the list
- New lists, "Reset" and saved queries without a project filter use the project configured for the task server
- Removed the "Work Packages…" action (the tool window replaces it)
- "% Complete" and "Total % complete" columns show a progress bar behind the value, and 0% when the work package has none
- "Spent time" cells turn red when more time was spent than the work (possible overtime); the tooltip shows both
- Statuses in their OpenProject colors: status cells of the list, the Status filter dropdowns, and a status badge in the work package preview and tab
- Type and priority cells of the list show their OpenProject colors like status cells, and the Type and Priority filter dropdowns show color swatches
- The list scrolls horizontally when its columns do not fit; columns keep a minimum width for percentages, durations and dates
- Work package page header: the parent hierarchy above the subject (each ancestor opens in a tab) with the active task marker, the type as a colored badge that changes the type, the id before the subject, and "Created by" and "Last update on" next to the status
- Work package properties in the groups of the OpenProject form, in up to three columns as the width allows; empty attributes are shown, and every writable attribute (per the work package's form schema) can be edited with a dropdown, date (and time) picker, text, number or duration field or checkbox
- Activity: the Comment button sits on the left under the comment box; links to attachments (e.g. "File notes.txt added") open the file in an editor like "Open" in the Files tab, instead of the browser
- Supports IntelliJ IDEA 2026.1 and later (no until-build); the Plugin Verifier checks the latest 2026.1, 2026.2 and 2026.3 releases

### Fixed
- Close Task with a status update no longer fails ("forbidden on EDT"): the status is set in the background under a modal progress, and failures are logged and shown by the Tasks plugin
- Close Task is confirmed with a notification (and a log entry) naming the work package and its new status; before, closing without a status update looked like nothing happened

### Added
- Initial scaffold created from [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)
- Build setup for IntelliJ IDEA 2026.2 and 2026.3, depending on the "Issue Trackers" (Tasks) plugin
- OpenProject API v3 client (HAL+JSON) on the IDE-bundled Ktor client and kotlinx.serialization
- Integration tests against OpenProject in Docker (scripts/openproject-it.sh)
- "OpenProject" task server for Tools | Tasks & Contexts: search (open/closed, assigned to me, project, saved query), state changes and time tracking
- Work package list and work package editor tabs (restored after restart), Log Time dialog, active task tracking and actions in Tools | OpenProject
- Work package table: configurable columns (drag, Configure View), header sorting, search, open/closed and "assigned to me" quick filters, filter bar with all server filters, saved queries, paging, active task banner
- Work package page: edit subject, status, description (Markdown with preview), assignee and priority; activity with comments; relations (add/remove); files (upload incl. drag & drop, download, open, delete); conflict detection
- Status bar widget showing the active task's work package; "Configure Server…" links on missing server or rejected API token
