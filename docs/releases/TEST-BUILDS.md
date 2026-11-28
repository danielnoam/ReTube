# Test builds

Try a pull request on the phone before it is released. Copied from Waypage's test builds.

## Using it
1. Open a pull request and add the label **`test`** (or **`test-android`**; ReTube has no iOS app, so they
   are the same), or put "manual testing" in the PR title.
2. About 10-15 minutes later the pre-release `test-<branch>` appears on the Releases page, with the
   signed APKs. Every push to the branch remakes it.
3. On the phone: **Settings > About > Test builds**, tap the build, tap again to install. It installs over
   ReTube and keeps your data and sign-in.
4. **Back to the release**, on the same screen, installs the latest `dn-*` release again.
5. Removing the label, closing the PR or deleting the branch deletes the pre-release and its tag.

## How it works
- `.github/workflows/test-builds.yml` watches labels and starts `test-build.yml` on the branch (and
  removes the pre-release). `test-build.yml` gates on the label or title, builds `assembleStmobileRelease`
  with the fork key, then `tools/test-build.sh create` publishes the pre-release.
- Same versionCode as the latest release: the build passes `-PforkRevision=N` of the latest `dn-*`
  release (versionCode = `11701 * 100 + N`), so it installs over that release and the release installs
  back over it. Its versionName is `<x.y.z>-<N>-test.<sha7>`.
- `-PtestBranch/-PtestCommit/-PtestPr` become `BuildConfig.TEST_BRANCH/COMMIT/PR`. A test build says so in
  About and doesn't check for updates by itself.
- The pre-release is never GitHub's "latest", so `releases/latest/download/newtube.json` (the updater)
  never offers it. Pages, Discord, VirusTotal and the update feed skip pre-releases.
- The notes begin with `version:`, `code:`, `branch:`, `commit:`, `pr:`; the app reads them
  (`update/TestBuilds.java`).

## Rules
- **Don't change the base version in `smarttubetv/build.gradle` on a feature branch.** A raised version
  can't go back to the release without an uninstall (the page warns). The release PR does the bump.
- Needs the `SIGNING_KEY`, `KEY_STORE_PASSWORD`, `ALIAS`, `KEY_PASSWORD` secrets (as `fork-release.yml`);
  without them nothing is built, since an unsigned APK can't install over ReTube.
- Create the `test` and `test-android` labels once in the repository.
- Before this system is merged to `main`, GitHub can't start `test-build.yml` by name, so adding the label
  alone builds nothing: push to the labelled branch instead (that run's gate sees the label). After the
  merge the label works on its own.
