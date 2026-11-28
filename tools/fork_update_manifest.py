#!/usr/bin/env python3
"""Write newtube.json for a ReTube fork release (fork-release.yml), the manifest the in-app updater
reads from releases/latest/download/newtube.json. Same format as tools/update_manifest.py (see its
docstring), but the version comes from the tag (dn-<version>-<N>) and the changelog from the commit
subjects since the previous dn-* tag, since the fork keeps no CHANGELOG of its own.

    tools/fork_update_manifest.py --tag dn-1.17.1-3 --repo danielnoam/newtube --dist dist --out dist/newtube.json
"""
import argparse
import json
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
ABIS = ["arm64-v8a", "armeabi-v7a", "x86"]
MAX_LINES = 8


def git(*args):
    return subprocess.run(["git", *args], cwd=ROOT, capture_output=True, text=True).stdout.strip()


def changelog():
    """Commits since the previous dn-* tag (the release is built from HEAD; its own tag may not exist yet)."""
    previous = git("describe", "--tags", "--abbrev=0", "--match", "dn-*", "HEAD^")
    span = f"{previous}..HEAD" if previous else "HEAD"
    lines = git("log", "--no-merges", "--format=%s", span).splitlines()
    return [line for line in lines if line][:MAX_LINES] or ["Bug fixes and improvements"]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--tag", required=True, help="release tag, e.g. dn-1.17.1-3")
    ap.add_argument("--repo", required=True, help="owner/name the release is published on")
    ap.add_argument("--dist", required=True, help="folder with the release APKs")
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    m = re.fullmatch(r"dn-(\d+\.\d+\.\d+)-(\d+)", args.tag)
    if not m or not 0 < int(m.group(2)) < 100:
        sys.exit(f"tag {args.tag} is not dn-<x.y.z>-<1..99>")
    base, revision = m.group(1), int(m.group(2))

    gradle = (ROOT / "smarttubetv" / "build.gradle").read_text(encoding="utf-8")
    code = re.search(r"versionCode\s+(\d+)\s*\*\s*100\s*\+\s*forkRevision", gradle)
    name = re.search(r'baseName\s*=\s*forkRevision\s*>\s*0\s*\?\s*"([\d.]+)-', gradle)
    if not code or not name or name.group(1) != base:
        sys.exit(f"build.gradle's stmobile version is not {base}: retag to match it")
    version_name = f"{base}-{revision}"
    version_code = int(code.group(1)) * 100 + revision

    dist = pathlib.Path(args.dist)
    url = f"https://github.com/{args.repo}/releases/download/{args.tag}"
    package = {}
    for abi in ABIS:
        apk = dist / f"ReTube_{version_name}_{abi}.apk"
        if apk.is_file():
            package[f"downloadUrlList_{abi}"] = [f"{url}/{apk.name}"]
            package[f"downloadSize_{abi}"] = apk.stat().st_size
    universal = dist / f"ReTube_{version_name}_universal.apk"
    if not universal.is_file():
        sys.exit(f"{universal.name} is missing from {dist}")
    package["downloadUrlList"] = [f"{url}/{universal.name}"]
    package["downloadSize"] = universal.stat().st_size

    manifest = {"package": package,
                version_name: {"versionCode": version_code, "changelog": changelog()}}
    pathlib.Path(args.out).write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
