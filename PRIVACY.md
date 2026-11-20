# Privacy Policy for ReTube

**Last updated:** November 20, 2026

This policy applies to **ReTube**, an open-source YouTube client for Android
phones (package ID `io.github.danielnoam.retube`). ReTube is a fork of NewTube,
itself an unofficial fork of SmartTube. It is not affiliated with Google,
YouTube, SmartTube's or NewTube's developers.

## 1. Summary

ReTube has **no backend of its own** and its developer collects **no personal
data**. The app talks to YouTube/Google, to a few community services you can
turn off, and to GitHub for update checks. There are no analytics, trackers,
ads or crash reporting.

## 2. Data the developer collects

**None.** There is no developer-run server, no ReTube account, and no telemetry
or analytics library (no Firebase, Crashlytics, Sentry or ad SDKs).

## 3. YouTube / Google

- **Sign-in is optional.** Signing in uses Google's OAuth 2.0 device-code flow:
  you approve the app on Google's own page and your password never reaches the
  app or the developer.
- **Tokens stay on your device**, in the app's private storage. They are left
  out of Android's backup and phone-to-phone transfer and out of the app's own
  Backup & restore, so a restored or transferred install starts signed out.
  Backup files made by versions before 1.15.1 still contain them.
- **Signed-out identity.** Like YouTube's own apps, ReTube keeps the anonymous
  visitor ID YouTube assigns, so signed-out Home and search stay consistent.
  After a restore from a backup or a phone transfer, the app drops it and
  starts with a new one, so YouTube can't link the old and new device.
- **Watch history.** Watch-time reports go to YouTube only while you are signed
  in (they fill your account's history). Signed out, nothing about what you
  watch is reported. Settings can turn history off, and incognito playback
  never reports.
- Video streams, search and account data come directly from YouTube/Google
  servers and are subject to Google's own terms and privacy policy.

## 4. Community services (optional, per feature)

- **SponsorBlock** (`sponsor.ajay.app`), on by default: segments to skip.
- **DeArrow** (`sponsor.ajay.app`, `dearrow-thumb.ajay.app`), off by default:
  community titles and thumbnails.
- **Return YouTube Dislike** (`returnyoutubedislikeapi.com`), off by default:
  estimated dislike counts.

SponsorBlock and DeArrow are asked by the first 4 characters of a hash of the
video ID, and the server answers for every video that shares them, so it can't
tell which video you are watching. A DeArrow community thumbnail is then loaded
by video ID, but only for videos that have one. Return YouTube Dislike receives
the video ID. None of these requests contain account tokens or personal
identifiers, and each feature can be turned off in Settings.

## 5. Casting

When you cast to a TV, the app finds devices on your local network and talks to
YouTube's casting service. For direct casting to a Chromecast, the phone serves
the video to the receiver on your local network at an address with a random
secret, so other devices on the same Wi-Fi can't see or use it.

## 6. Update checks

The app checks ReTube's **GitHub Releases** for a newer version. Like any web
request, this shows your IP address to GitHub; it sends no account data or
identifiers, and nothing reaches the developer.

## 7. Backups and diagnostics

- **Backup & restore** writes your settings to a file you choose or to your own
  Google Drive. Sign-in tokens are never included.
- **Send diagnostic log** (Settings > About) shares the app's recent log only
  when you tap it and pick where to send it. Sign-in tokens, cookies, e-mail
  addresses, visitor IDs and your IP address are removed first. The IDs of
  videos you played stay in, because that is what a report is about.

## 8. No profiling, no advertising, no monetization

ReTube does not profile you, shows no ads, and has no in-app purchases.

## 9. Your rights (GDPR / CCPA)

The developer holds no records about you, so there is nothing to export or
delete on request. Data held by Google/YouTube or the community services above
is governed by their own policies.

## 10. Contact

Open an issue on the project's GitHub repository:
https://github.com/danielnoam/ReTube
