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
* **Status:** Rebuild Successful

---

## [2026-06-10] - Debug Build (Super Admin Profile & Dynamic Feedback Email Routing)

A clean rebuild was performed after adding the Super Admin profile settings tab (allowing display name and notification email updates) and configuring dynamic email routing in the feedback system based on the database super admin email, alongside removing the dropdown for issue types to allow typing in Feedback & Support.

* **Date & Time:** June 10, 2026 - 12:10:00 AM (IST)
* **File Name:** `app-debug.apk`
* **File Path:** `astroapp/android/app/build/outputs/apk/debug/app-debug.apk`
* **Status:** Rebuild Successful

---

## [2026-06-10] - Debug Build (SMTP Config Menu Integration)

A clean rebuild was performed containing the updated codebase after adding the SMTP configuration settings and menus to the Super Admin dashboard.

* **Date & Time:** June 10, 2026 - 08:50:00 AM (IST)
* **File Name:** `app-debug.apk`
* **File Path:** `astroapp/android/app/build/outputs/apk/debug/app-debug.apk`
* **Status:** Clean Rebuild Successful

---

## [2026-06-10] - Debug Build (Avatar Menu Icon Overlay Removal)

A clean rebuild was performed after removing the small overlay menu (hamburger) icon from the top-left user avatar on the home screen.

* **Date & Time:** June 10, 2026 - 09:14:00 AM (IST)
* **File Name:** `app-debug.apk`
* **File Path:** `astroapp/android/app/build/outputs/apk/debug/app-debug.apk`
* **Status:** Clean Rebuild Successful

---

## [2026-06-15] - Debug Build (Service Cards Centering & Alignment Fix)

A clean rebuild was performed after modifying the service cards (Free Horoscope, Daily Horoscope, Horoscope Match, Astro Academy) layout on the home screen. The container `Row` was modified to disable horizontal scrolling and apply `Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)` when there are 4 or fewer items, centering the cards perfectly and resolving the empty space on the right.

* **Date & Time:** June 15, 2026 - 05:33:00 PM (IST)
* **File Name:** `app-debug.apk`
* **File Path:** `astroapp/android/app/build/outputs/apk/debug/app-debug.apk`
* **Size:** 123,777,848 Bytes (~118.04 MB)
* **Status:** Rebuild Successful

---

## [2026-06-21] - Release Build v18.0 (KP Chart Dialog in Call & Video Call UI)

A clean rebuild was performed after adding the KP Chart dialog support to the astrologer call and video call screen. The `KpChartDialog` from the chat package is imported and loaded as a Compose overlay in `CallActivity.kt`. Standard Rasi Chart button icon has been replaced with the custom `R.drawable.ic_chart` drawable to match the chat activity controls. App version code was bumped to `18` and version name to `"18.0"`.

* **Date & Time:** June 21, 2026 - 10:55 PM (IST)
* **File Name:** `app-debug.apk`, `app-release.aab`
* **File Path:** `astroapp/android/app/build/outputs/apk/debug/app-debug.apk` and `astroapp/android/app/build/outputs/bundle/release/app-release.aab`
* **Size (APK):** 123,777,936 Bytes (~118.04 MB)
* **Size (AAB):** 92,946,695 Bytes (~88.64 MB)
* **Status:** Build and Packaging Successful
