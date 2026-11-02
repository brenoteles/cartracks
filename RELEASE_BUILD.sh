#!/bin/bash

set -e

export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64/

JAVA_VERSION=`java --version`
if [[ $JAVA_VERSION != "openjdk 21."* ]]; then
  echo -e "OpenJDK version should be 21.X"
fi

git diff --exit-code || echo -e "\nWARNING WARNING WARNING WARNING WARNING: this branch contains uncommit changes."

if [[ $(git log "$(git describe --tags --abbrev=0)..HEAD" --no-merges --oneline | wc -l) -gt 0 ]]; then
  echo -e "No version tag for this commit; please use RELEASE.sh";
fi

RELEASE_STORE_PASSWORD=
read -s -p "Enter store password: " RELEASE_STORE_PASSWORD
echo ""
read -s -p "Enter key alias: " RELEASE_KEY_ALIAS
echo ""
read -s -p "Enter key password: " RELEASE_KEY_PASSWORD
echo ""

./gradlew --no-configuration-cache \
  clean \
  assembleReproducibleRelease \
  -Drelease_store_file=`ls ../*.jks` \
  -Drelease_store_file="../keystore_opentracks.jks" \
  -Drelease_store_password="$RELEASE_STORE_PASSWORD" \
  -Drelease_key_alias="$RELEASE_KEY_ALIAS" \
  -Drelease_key_password="$RELEASE_KEY_PASSWORD"
