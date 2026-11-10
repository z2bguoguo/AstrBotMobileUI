#!/usr/bin/env bash
set -euo pipefail
umask 077
repo="${1:?Usage: setup-signing.sh owner/repo}"
command -v gh >/dev/null
command -v keytool >/dev/null
gh auth status >/dev/null
signing_dir="${ASTRBOT_SIGNING_DIR:-$HOME/.astrbot-mobile-signing}"
mkdir -p "$signing_dir"
if [ -e "$signing_dir/release.jks" ]; then
  echo "Refusing to replace an existing release key at $signing_dir" >&2
  exit 1
fi
# Never print passwords; files remain owner-readable only for offline backup.
openssl rand -hex 32 > "$signing_dir/store-password"
cp "$signing_dir/store-password" "$signing_dir/key-password"
printf '%s' 'astrbot-mobile' > "$signing_dir/key-alias"
keytool -genkeypair -noprompt -keystore "$signing_dir/release.jks" -storetype JKS \
  -alias astrbot-mobile -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass:file "$signing_dir/store-password" -keypass:file "$signing_dir/key-password" \
  -dname 'CN=AstrBot Mobile UI' >/dev/null 2>&1
base64 < "$signing_dir/release.jks" | gh secret set ANDROID_KEYSTORE_BASE64 --repo "$repo"
gh secret set ANDROID_STORE_PASSWORD --repo "$repo" < "$signing_dir/store-password"
gh secret set ANDROID_KEY_PASSWORD --repo "$repo" < "$signing_dir/key-password"
gh secret set ANDROID_KEY_ALIAS --repo "$repo" < "$signing_dir/key-alias"
echo "Persistent signing configured. Back up $signing_dir offline. Do not regenerate it."
