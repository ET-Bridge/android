# Changelog

All notable changes to ETBridge for Android. Versions follow `versionName` in
`app/build.gradle.kts`; `versionCode` increases with every release.

## [Unreleased]

### Fixed
- **More tab reopened on the last screen.** After opening Settings (or About, Campus…) from More and
  switching tabs, tapping More showed that screen again instead of the More menu. More now always
  opens on its menu; Attendance, Timetable and Academics still come back where you left them.

## [1.2.0] — 2026-11-25

### Added
- **In-app updates.** ETBridge checks for new releases in the background (at most twice a day, never
  delaying start-up) and offers to update: *Install* downloads the APK from GitHub Releases with a
  progress bar, verifies its checksum and signing key, and opens Android's installer. *What's new*
  shows the release notes. Also in *Settings → Check for updates*. Releases can mark older versions
  unsupported, which makes the update prompt mandatory.

## [1.1.0] — 2026-11-24

### Fixed
- **ETLab moved its API.** ETLab retired `api.students.etlab.app` (it now returns HTTP 503
  "Service Suspended"); ETBridge now uses `api2.students.etlab.app`. Version 1.0.0 can no longer
  sign in or sync.
- **Attendance calendar showed the wrong subjects.** The calendar, subject class history and
  duty-leave matching used ETLab's *scheduled* timetable, so "Minor" slots, lab blocks and
  swapped hours showed the planned subject (and many marked hours as unmarked). They now use
  ETLab's attendance records (`/attendancebyday`, what the official app shows), so every marked
  hour shows the subject actually taught and its real status. Monthly totals now match ETLab.
- History now covers the whole semester (five months instead of four).

### Changed
- Sessions stay signed in: ETLab tokens expire after 3 days, and ETBridge now adopts the
  refreshed token ETLab sends with each response.
- Clear message when ETLab itself is down instead of a generic HTTP error.
- If ETLab moves its API again, ETBridge can follow without an app update: it reads the current
  host from a small file on etbridge.nimd.me (no personal data is sent) and honours
  per-college API hosts if ETLab publishes them. Only `etlab.app` hosts are ever accepted.

## [1.0.0] — 2026-11-16

First release: a native Android version of the ETBridge web app.

### Added
- Sign in with any ETLab institute (searchable college list or custom code) or explore the demo.
- Dashboard: live "now / next" class card, overall attendance with bunk margin, upcoming exams and
  deadlines, quick links, lowest subjects and recent notices.
- Attendance: per-subject cards, calendar view, subject detail with class history, covered topics
  and a what-if simulator.
- Duty leaves: log hours per day, projected attendance, automatic reconciliation with ETLab;
  stored only on the device.
- Timetable: week strip and swipeable day pager with substitutions, special classes and holidays.
- Academics: series exams (upcoming and published, with class average), assignments and other work
  with attachment download and online submission, study materials, university results, faculty.
- Courses with syllabus coverage, outcomes, execution and source books; academic analysis with
  SGPA trend and courses at risk; campus (activity points, fees, surveys); notices and circulars.
- Home-screen widgets (next class, attendance), 4-hourly background sync, attendance / new-content /
  class-reminder notifications, app lock, Material You and dark/light themes, offline cache.
- Accessibility: TalkBack roles and spoken state, headings, layouts that hold up at large font sizes.
