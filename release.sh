#!/bin/bash
set -e

if [ -z "$1" ]; then
    echo "Usage: ./release.sh <version>"
    echo "Example: ./release.sh 0.1.0"
    exit 1
fi

VERSION="$1"

echo "Releasing v${VERSION}..."

# Update mod.version in gradle.properties
sed -i "s/mod\.version=.*/mod.version=${VERSION}/" gradle.properties

# Commit and tag
git add gradle.properties
git commit -m "release: v${VERSION}"
git tag "v${VERSION}"

echo ""
echo "Tagged v${VERSION}. To publish:"
echo "  git push origin master v${VERSION}"
