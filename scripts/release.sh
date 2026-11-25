#!/usr/bin/env bash
# Publishes a release and points the in-app updater at it, in the only safe order:
#   1. build the signed APK            4. download it back and check the SHA-256
#   2. create the GitHub release       5. write webapp/public/android-update.json
#   3. upload the APK                  (you then commit + push the webapp to go live)
#
# Usage: scripts/release.sh [--min-supported <versionCode>] [--webapp <path>]
#   Bump versionCode/versionName in app/build.gradle.kts and add a "## [x.y.z]" section to
#   CHANGELOG.md first. Needs keystore.properties, gh (authenticated), curl, python3.
#   --min-supported forces everyone below that versionCode to update (default: keep current value).
set -euo pipefail

cd "$(dirname "$0")/.."
REPO="ET-Bridge/android"
WEBAPP="../webapp"
MIN_SUPPORTED=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --min-supported) MIN_SUPPORTED="$2"; shift 2 ;;
    --webapp) WEBAPP="$2"; shift 2 ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done

die() { echo "error: $*" >&2; exit 1; }
for tool in gh curl python3 sha256sum; do command -v "$tool" >/dev/null || die "$tool is required"; done
[[ -f keystore.properties ]] || die "keystore.properties missing: release must be signed with the release key"
MANIFEST="$WEBAPP/public/android-update.json"
[[ -f "$MANIFEST" ]] || die "$MANIFEST not found (use --webapp)"

VERSION_CODE=$(sed -nE 's/^\s*versionCode = ([0-9]+).*/\1/p' app/build.gradle.kts)
VERSION_NAME=$(sed -nE 's/^\s*versionName = "([^"]+)".*/\1/p' app/build.gradle.kts)
TAG="v$VERSION_NAME"
APK_NAME="ETBridge-$TAG.apk"
[[ -n "$VERSION_CODE" && -n "$VERSION_NAME" ]] || die "couldn't read version from app/build.gradle.kts"

CURRENT_CODE=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["versionCode"])' "$MANIFEST")
(( VERSION_CODE > CURRENT_CODE )) || die "versionCode $VERSION_CODE must be greater than the published $CURRENT_CODE"
[[ -z "$(git status --porcelain)" ]] || die "working tree not clean; commit the release first"
gh release view "$TAG" --repo "$REPO" >/dev/null 2>&1 && die "release $TAG already exists"

# Release notes: the CHANGELOG section for this version.
NOTES_FILE=$(mktemp)
trap 'rm -f "$NOTES_FILE"' EXIT
python3 - "$VERSION_NAME" "$NOTES_FILE" <<'PY'
import re, sys
version, out = sys.argv[1], sys.argv[2]
text = open("CHANGELOG.md").read()
m = re.search(r"^## \[" + re.escape(version) + r"\][^\n]*\n(.*?)(?=^## \[|\Z)", text, re.S | re.M)
if not m:
    sys.exit(f"error: no '## [{version}]' section in CHANGELOG.md")
open(out, "w").write(m.group(1).strip() + "\n")
PY

echo "==> Building $TAG (versionCode $VERSION_CODE)"
./gradlew :app:assembleRelease --console=plain -q
mkdir -p release
cp app/build/outputs/apk/release/app-release.apk "release/$APK_NAME"
(cd release && sha256sum "$APK_NAME" > "$APK_NAME.sha256")
SHA=$(cut -d' ' -f1 "release/$APK_NAME.sha256")
SIZE=$(stat -c %s "release/$APK_NAME")

echo "==> Creating GitHub release $TAG"
git tag -a "$TAG" -m "ETBridge $VERSION_NAME" 2>/dev/null || true
git push origin "$TAG"
gh release create "$TAG" --repo "$REPO" --title "ETBridge $VERSION_NAME" --notes-file "$NOTES_FILE" \
  "release/$APK_NAME" "release/$APK_NAME.sha256"

APK_URL="https://github.com/$REPO/releases/download/$TAG/$APK_NAME"
echo "==> Verifying the uploaded asset"
REMOTE_SHA=$(curl -fsSL "$APK_URL" | sha256sum | cut -d' ' -f1)
[[ "$REMOTE_SHA" == "$SHA" ]] || die "uploaded APK hash $REMOTE_SHA != local $SHA; android-update.json NOT updated"

echo "==> Writing $MANIFEST"
python3 - "$MANIFEST" "$NOTES_FILE" "$VERSION_CODE" "$VERSION_NAME" "$APK_URL" "$SHA" "$SIZE" \
  "https://github.com/$REPO/releases/tag/$TAG" "$MIN_SUPPORTED" <<'PY'
import json, re, sys
path, notes_file, code, name, url, sha, size, release_url, min_supported = sys.argv[1:]
old = json.load(open(path))
# One short line per changelog bullet: the bold lead-in if there is one, else the first sentence.
bullets = []
for line in open(notes_file):
    if line.startswith("- "):
        bullets.append(line[2:].strip())
    elif bullets and line.startswith("  ") and line.strip():
        bullets[-1] += " " + line.strip()  # wrapped continuation of the previous bullet
notes = []
for item in bullets:
    bold = re.match(r"\*\*(.+?)\*\*", item)
    item = bold.group(1) if bold else re.split(r"(?<=[.!?])\s", item)[0]
    item = re.sub(r"[`*]", "", item).rstrip(".")
    notes.append(item[:117] + "…" if len(item) > 120 else item)
manifest = {
    "versionCode": int(code),
    "versionName": name,
    "minSupportedVersionCode": int(min_supported) if min_supported else old.get("minSupportedVersionCode", 0),
    "apkUrl": url,
    "sha256": sha,
    "sizeBytes": int(size),
    "notes": notes[:8],
    "releaseUrl": release_url,
}
with open(path, "w") as f:
    json.dump(manifest, f, indent=2, ensure_ascii=False)
    f.write("\n")
PY

cat <<EOF

Released $TAG. Users are NOT notified until the webapp is deployed:
  cd $WEBAPP && git add public/android-update.json && git commit -m "Android $VERSION_NAME" && git push
EOF
