#!/usr/bin/env bash
# Prints latest stable versions for Dpad dependencies from live registry metadata.
set -euo pipefail

central() { # groupPath artifact
  curl -sf "https://repo1.maven.org/maven2/$1/$2/maven-metadata.xml" \
    | grep -o '<version>[^<]*</version>' | sed 's/<[^>]*>//g' \
    | grep -Eiv '(alpha|beta|rc|m[0-9]|dev|eap|snapshot)' | tail -1
}
google() { # groupPath (e.g. com/android/library)
  curl -sf "https://dl.google.com/dl/android/maven2/$1/group-index.xml"
}

echo "kotlin                = $(central org/jetbrains/kotlin kotlin-gradle-plugin)"
echo "agp                   = $(curl -sf "https://dl.google.com/dl/android/maven2/com/android/library/group-index.xml" | grep -o 'versions="[^"]*"' | sed 's/versions="//;s/"$//' | tr ',' '\n' | grep -Eiv 'alpha|beta|rc' | tail -1)"
echo "wire                  = $(central com/squareup/wire wire-runtime)"
echo "kotlinx-coroutines    = $(central org/jetbrains/kotlinx kotlinx-coroutines-core)"
echo "kermit                = $(central co/touchlab kermit)"
echo "bcpkix                = $(central org/bouncycastle bcpkix-jdk18on)"
echo "turbine               = $(central app/cash/turbine turbine)"
echo "kotlinx-serialization  = $(central org/jetbrains/kotlinx kotlinx-serialization-json)"
echo "datastore-preferences  = $(central androidx/datastore datastore-preferences-core)"
echo "okio                   = $(central com/squareup/okio okio)"
echo "koin                   = $(central io/insert-koin koin-core)"
