#!/bin/bash
# macOS build script. For Windows, use build-app.ps1 instead.
#
# Builds "University Driver.app" into /Applications, and refreshes a Desktop shortcut
# to it so it can still be launched with a double-click from there.
#
# Rebuild after any code change with:
#   ./packaging/build-app.sh
#
# Requires JDK 21 (the same Homebrew openjdk@21 install university-core's Maven
# toolchain already pins) - jpackage ships with the JDK itself, no extra install needed.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
JAVA_HOME="/opt/homebrew/Cellar/openjdk@21/21.0.12/libexec/openjdk.jdk/Contents/Home"
APP_NAME="University Driver"
# /Applications, not ~/Desktop: iCloud's "Desktop & Documents" sync re-tags anything
# placed on the Desktop with extended attributes (e.g. com.apple.FinderInfo) shortly
# after it lands there, which invalidates the ad-hoc code signature and makes macOS
# refuse to launch it ("app is damaged") - see the Desktop symlink step below instead.
DEST_DIR="/Applications"

echo "==> Building the shaded jar (mvn package)"
(cd "$PROJECT_DIR" && mvn -q clean package)

JAR="$PROJECT_DIR/target/university-core-1.0-SNAPSHOT.jar"
WORK_DIR="$(mktemp -d)"
STAGE="$WORK_DIR/input"
BUILD_DIR="$WORK_DIR/dist"
ICON="$WORK_DIR/AppIcon.icns"

trap 'rm -rf "$WORK_DIR"' EXIT

mkdir -p "$STAGE"
cp "$JAR" "$STAGE/"
cp "$SCRIPT_DIR/AppIcon.icns" "$ICON"

# jpackage's own ad-hoc codesign step fails on anything built under this project's own
# tree: the whole project sits inside the (iCloud-synced) Desktop, and every file in
# it - including this project directory itself - already carries extended attributes
# (e.g. com.apple.FinderInfo, com.apple.provenance) that codesign refuses to sign over.
# Staging and building entirely under $TMPDIR instead avoids that at build time.
xattr -cr "$STAGE" "$ICON"

echo "==> Running jpackage"
"$JAVA_HOME/bin/jpackage" \
  --type app-image \
  --input "$STAGE" \
  --dest "$BUILD_DIR" \
  --name "$APP_NAME" \
  --main-jar "$(basename "$JAR")" \
  --main-class de.lino.thma.Launcher \
  --icon "$ICON" \
  --app-version 1.0 \
  --vendor "Lino Alessio Kauschinger" \
  --mac-package-name "UniDriver"

echo "==> Installing the signed app to $DEST_DIR"
rm -rf "$DEST_DIR/$APP_NAME.app"
cp -R "$BUILD_DIR/$APP_NAME.app" "$DEST_DIR/$APP_NAME.app"

echo "==> Refreshing the Desktop shortcut"
# A symlink, not a copy: copying the .app itself onto the (iCloud-synced) Desktop is
# exactly what breaks the signature in the first place (see above). Finder follows a
# symlink to an .app and launches the real target directly, so this still gives a
# double-clickable icon on the Desktop without the app itself ever living there.
# rm first, not just `ln -sf`: if a real .app directory (e.g. from an older build) is
# already sitting there, `ln -sf` would create the symlink *inside* it instead of
# replacing it.
rm -rf "$HOME/Desktop/$APP_NAME.app"
ln -s "$DEST_DIR/$APP_NAME.app" "$HOME/Desktop/$APP_NAME.app"

echo "==> Done: $DEST_DIR/$APP_NAME.app (Desktop shortcut refreshed)"
