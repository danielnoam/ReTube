<div align="center">

<img src=".github/assets/icon.png" width="112" height="112" alt="ReTube icon">

# ReTube

### A personal fork of NewTube, built on SmartTube.

A YouTube client for Android phones and tablets. ReTube is
[@danielnoam](https://github.com/danielnoam)'s fork of
[NewTube](https://github.com/aleixrodriala/newtube) by [@aleixrodriala](https://github.com/aleixrodriala),
which is built on [SmartTube](https://github.com/yuliskov/SmartTube) by [@yuliskov](https://github.com/yuliskov).

</div>

> [!NOTE]
> **ReTube is NewTube's and SmartTube's work underneath.** The touch interface, the player and
> offline saving come from [NewTube](https://github.com/aleixrodriala/newtube). The engine that
> talks to YouTube, the account sign-in and the SponsorBlock, DeArrow and Return YouTube Dislike
> integrations come from [SmartTube](https://github.com/yuliskov/SmartTube). ReTube is an
> independent fork, not endorsed by either project's developer. If you find it useful,
> [support SmartTube](https://github.com/yuliskov/SmartTube#donation).

## What this fork changes

- Its own package name (`io.github.danielnoam.retube`), so it installs next to NewTube,
  SmartTube or the YouTube app.
- Privacy fixes and Android 7.1 (API 25) as the minimum version.
- A selection of upstream fixes and pull requests merged early.
- Watch later: a snackbar after saving, an optional Watch later tab, and a Watch later button on
  Home, Subscriptions and Search thumbnails.
- Reorderable bottom-bar tabs (Settings > Tabs > Order).

For everything ReTube inherits (features, how sign-in works, the FAQ), see
[NewTube's README](docs/UPSTREAM_README_NewTube.md).

## Download

APKs are on this repository's [Releases](https://github.com/danielnoam/newtube/releases) page.
Pick the `arm64-v8a` APK for a modern phone, or `universal` if unsure.

Until the fork has its own release key, releases are debug builds signed with a throwaway key,
so uninstall the previous release before installing a new one.

## Building

Build instructions are in [docs/BUILDING.md](docs/BUILDING.md).

## Credits

| Project | By | Used for |
|:--|:--|:--|
| [NewTube](https://github.com/aleixrodriala/newtube) | [@aleixrodriala](https://github.com/aleixrodriala) and contributors | The phone app: touch interface, player, offline saving |
| [SmartTube](https://github.com/yuliskov/SmartTube) | [@yuliskov](https://github.com/yuliskov) and contributors | The engine, accounts and integrations: everything under the hood |
| [SponsorBlock](https://sponsor.ajay.app) · [DeArrow](https://dearrow.ajay.app) | Ajay Ramachandran and contributors | Segment and title data (CC BY-NC-SA 4.0) |
| [Return YouTube Dislike](https://returnyoutubedislike.com) | RYD contributors | Dislike counts |
| [androidx Media3](https://developer.android.com/media/media3) | Google (Apache-2.0) | Playback |

Full notices are in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). NewTube's README is kept at
[docs/UPSTREAM_README_NewTube.md](docs/UPSTREAM_README_NewTube.md) and SmartTube's at
[docs/UPSTREAM_README_SmartTube.md](docs/UPSTREAM_README_SmartTube.md).

## License

[MIT](LICENSE), the same as NewTube and SmartTube. © yuliskov (SmartTube), NewTube contributors and
ReTube contributors.
