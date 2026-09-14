#!/bin/bash
set -euo pipefail

# ==============================================================================
# SECURITY NOTICE: DO NOT ADD 'set -x' TO THIS SCRIPT!
# This script handles PGP signing keys and passphrases from Secret Manager.
# Enabling bash xtrace (set -x) will echo credentials into CI logs.
# ==============================================================================
# Explicitly disable xtrace
set +x

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${REPO_DIR}"

# Ensure Java 17 is used on Kokoro Ubuntu 22.04 workers (which default to OpenJDK 11)
if [[ ! -d "/usr/lib/jvm/java-17-openjdk-amd64" ]]; then
  echo "ERROR: Kokoro worker missing expected OpenJDK 17 at /usr/lib/jvm/java-17-openjdk-amd64" >&2
  exit 1
fi
export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
export PATH="${JAVA_HOME}/bin:${PATH}"

# -----------------------------------------------------------------------------
# 1. Clean Staging Repository
# -----------------------------------------------------------------------------
EXIT_GATE_PROJECT="oss-exit-gate-prod"
EXIT_GATE_LOCATION="us"
EXIT_GATE_REPOSITORY="measurement-devrel--mavencentral"
PACKAGE_NAME="com.google.api-ads:data-manager-util"

# Delete existing package from the Exit Gate staging repository if present.
# This prevents upload failures on retries or re-releases of the same version.
if gcloud artifacts packages describe "${PACKAGE_NAME}" \
    --project="${EXIT_GATE_PROJECT}" \
    --location="${EXIT_GATE_LOCATION}" \
    --repository="${EXIT_GATE_REPOSITORY}" &>/dev/null; then
  echo "=== Deleting existing package '${PACKAGE_NAME}' from Exit Gate staging repository ==="
  gcloud artifacts packages delete "${PACKAGE_NAME}" \
    --project="${EXIT_GATE_PROJECT}" \
    --location="${EXIT_GATE_LOCATION}" \
    --repository="${EXIT_GATE_REPOSITORY}" \
    --quiet
else
  echo "=== Skipping deletion. Package '${PACKAGE_NAME}' not found in staging repository. This is normal. 🙂 ==="
fi

# -----------------------------------------------------------------------------
# 2. Clean, Test, Sign, and Publish to Artifact Registry
# -----------------------------------------------------------------------------
# Keyring / ADC handles authentication via the Kokoro BYOSA service account
# Fetch the PGP signing key from Secret Manager (or fallback to SIGNING_KEY if already set)
if [[ -z "${SIGNING_KEY:-}" ]]; then
  echo "=== Fetching GPG signing key from Secret Manager ==="
  export SIGNING_KEY="$(gcloud secrets versions access latest \
    --secret="bam-devrel-maven-gpg-key" \
    --project="bam-devrel-releases")"
fi

if [[ -z "${SIGNING_KEY:-}" ]]; then
  echo "ERROR: SIGNING_KEY is not set and could not be fetched from Secret Manager." >&2
  exit 1
fi

if [[ -z "${SIGNING_PASSWORD:-}" ]]; then
  echo "=== Fetching GPG signing password from Secret Manager ==="
  export SIGNING_PASSWORD="$(gcloud secrets versions access latest \
    --secret="bam-devrel-maven-gpg-password" \
    --project="bam-devrel-releases")"
fi

if [[ -z "${SIGNING_PASSWORD:-}" ]]; then
  echo "ERROR: SIGNING_PASSWORD is not set and could not be fetched from Secret Manager." >&2
  exit 1
fi

./gradlew clean check publish --no-daemon

# -----------------------------------------------------------------------------
# 3. DRY_RUN Check
# -----------------------------------------------------------------------------
if [[ "${DRY_RUN:-false}" == "true" ]]; then
  echo "=== DRY_RUN is enabled. Artifacts staged in Artifact Registry. ==="
  echo "Skipping GCS manifest upload to Exit Gate."
  exit 0
fi

# -----------------------------------------------------------------------------
# 4. Create Exit Gate Release Manifest
# -----------------------------------------------------------------------------
# Maven Central manifests require 'namespace' (groupId) and package 'name' (artifactId)
cat <<EOF > manifest.json
{
  "publish_all": false,
  "publishing_groups": [
    {
      "namespace": "com.google.api-ads",
      "packages": [
        {
          "name": "data-manager-util"
        }
      ]
    }
  ]
}
EOF

# -----------------------------------------------------------------------------
# 5. Trigger Exit Gate Release via GCS Manifest
# -----------------------------------------------------------------------------
EXIT_GATE_BUCKET="gs://oss-exit-gate-prod-projects-bucket/measurement-devrel/mavencentral/manifests"
MANIFEST_NAME="manifest-$(date +%Y%m%d%H%M%S).json"

echo "=== Uploading manifest to ${EXIT_GATE_BUCKET}/${MANIFEST_NAME} ==="
gcloud storage cp manifest.json "${EXIT_GATE_BUCKET}/${MANIFEST_NAME}"

echo "=== Release successfully triggered to OSS Exit Gate! ==="
