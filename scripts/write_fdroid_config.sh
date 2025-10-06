#!/usr/bin/env bash
# Generate the fdroid/config.yml file using repository metadata and secrets.
set -euo pipefail

REPO_URL=${FDROID_REPO_URL:-"https://<username>.github.io/Drop-Android/fdroid/repo"}
REPO_NAME=${FDROID_REPO_NAME:-"ErikrafT Drop self-hosted repo"}
REPO_DESCRIPTION=${FDROID_REPO_DESCRIPTION:-"Automated builds published from GitHub Actions."}
REPO_ADDRESS=${FDROID_REPO_ADDRESS:-""}
KEY_ALIAS=${FDROID_KEY_ALIAS:?"FDROID_KEY_ALIAS secret not set"}

cat > fdroid/config.yml <<CONFIG
repo_url: ${REPO_URL}
repo_name: ${REPO_NAME}
repo_description: ${REPO_DESCRIPTION}
repo_address: ${REPO_ADDRESS}
repo_icon: icons/com.erikraft.drop.png
keystore: keystore.jks
repo_keyalias: ${KEY_ALIAS}
keypass: ${FDROID_KEY_PASSWORD:?"FDROID_KEY_PASSWORD secret not set"}
keystorepass: ${FDROID_KEYSTORE_PASSWORD:?"FDROID_KEYSTORE_PASSWORD secret not set"}
make_current_version_link: true
CONFIG
