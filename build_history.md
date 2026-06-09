# Astrohark Android Build History

This file tracks the builds created for the Astrohark Android application.

## [2026-06-01] - Debug Build (Clean Rebuild)

A clean rebuild was performed. Permitted domains configured inside `network_security_config.xml` to allow cleartext HTTP traffic for server IPs (`167.71.226.248` and `159.89.167.222`).

* **Date & Time:** June 01, 2026 - 12:08:35 PM (IST)
* **File Name:** `app-debug.apk`
* **File Path:** `astroapp/android/app/build/outputs/apk/debug/app-debug.apk`
* **Size:** 123,430,092 Bytes (~117.71 MB)
* **Status:** Clean Rebuild Successful

---

## [2026-06-01] - Debug Build (Updated Code Rebuild)

A rebuild was performed containing major chat fixes (Image & Voice notes), including server response formatting alignments (`fileUrl` vs `url`) and socket response parsing fixes (`isMe` bubbled alignments) in `ChatViewModel.kt`.

* **Date & Time:** June 01, 2026 - 01:06:42 PM (IST)
* **File Name:** `app-debug.apk`
* **File Path:** `astroapp/android/app/build/outputs/apk/debug/app-debug.apk`
* **Size:** 123,430,092 Bytes (~117.71 MB)
* **Status:** Rebuild Successful

---

## [2026-06-01] - Debug Build (Ringtone & Notification Dismiss Fixes)

A clean rebuild was performed after fixing the persistent ringtone sound and top notification issue. `ChatActivity.kt` and `IncomingCallActivity.kt` were updated to dismiss call notifications (`callerId.hashCode()`, `callId.hashCode()`, `9999`) and stop background sound loops of `CallForegroundService`.

* **Date & Time:** June 01, 2026 - 01:37:12 PM (IST)
* **File Name:** `app-debug.apk`
* **File Path:** `astroapp/android/app/build/outputs/apk/debug/app-debug.apk`
* **Size:** 123,430,092 Bytes (~117.71 MB)
* **Status:** Rebuild Successful

---

## [2026-06-01] - Debug Build (MediaPlayer Error -38 Fixes)

A clean rebuild was performed after fixing the MediaPlayer error -38 issue by adding preparation guards and wrapping playback methods in try-catch blocks within `ChatAudioPlayer.kt`.

* **Date & Time:** June 01, 2026 - 02:02:40 PM (IST)
* **File Name:** `app-debug.apk`
* **File Path:** `astroapp/android/app/build/outputs/apk/debug/app-debug.apk`
* **Size:** 120,646,062 Bytes (~115.06 MB)
* **Status:** Rebuild Successful

---

## [2026-06-09] - Debug Build (Branding, Flow & Calculation Updates)

A clean rebuild was performed containing all 11 branding and functional changes, including top/bottom bar UI redesigns, splash screen updates, default consult tab, introductory ₹20 wallet options, astrologer profile click flows, and KP astrology significator union calculations.

* **Date & Time:** June 09, 2026 - 11:30:00 PM (IST)
* **File Name:** `app-debug.apk`
* **File Path:** `astroapp/android/app/build/outputs/apk/debug/app-debug.apk`
* **Status:** Rebuild Pending/Running
