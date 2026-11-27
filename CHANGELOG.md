# Changelog

All notable changes to ETBridge for Android. Versions follow `versionName` in
`app/build.gradle.kts`; `versionCode` increases with every release.

## [1.3.0] — 2026-11-27

### Added
- **Customize Home** (Settings → Home screen, or the button at the bottom of Home): choose which cards
  appear and in what order, from 16 cards including new ones: today's timetable, latest marks,
  CGPA & SGPA, duty leaves, new study materials, fee dues, activity points and open surveys.
  Reset brings back the original layout (with Undo).
- **Assignment deadlines on Home** now include overdue work (up to 30 days late, marked in red) and
  homework/tutorials, and show "All caught up" when nothing is open.
- **Vibrant palette**: a brighter violet, pink and teal look with colourful icons and gradient cards.
  Settings → Appearance now offers Classic, Vibrant and Material You.
- **App info** in More: version, update check and release notes, privacy policy, how ETBridge works,
  documentation, source code, release history and feedback, all in one place.
- **Redesigned widgets**: an attendance ring with bunk margin and your lowest subjects, and a next-class
  card with a status badge, times, teacher and today's hours. Both adapt to their size and follow the
  app's palette; tapping opens the matching tab.

### Fixed
- **Sending feedback no longer shows a false error.** Web3Forms often replies with an HTML page;
  ETBridge now recognises that as success. Feedback also includes the app version and device model.

## [1.2.2] — 2026-11-27

### Fixed
- **Campus crashed when switching tabs.** ETLab returns one survey row per feedback session with the
  same survey id, and the Surveys list used that id as its item key, so opening Duty leaves or
  Surveys (which pre-loads the neighbouring tab) closed the app. Every list keyed by ETLab ids
  (notices, circulars, exams, assignments, materials, courses, duty leaves, dashboard cards) now
  tolerates duplicate ids.

## [1.2.1] — 2026-11-25

### Fixed
- **More tab reopened on the last screen.** After opening Settings (or About, Campus…) from More and
  switching tabs, tapping More showed that screen again instead of the More menu. More now always
  opens on its menu; Attendance, Timetable and Academics still come back where you left them.
- **About screen** named ETLab's retired API host; it now shows `api2.students.etlab.app`, and
  "Source code on GitHub" opens the Android app's repository.

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
