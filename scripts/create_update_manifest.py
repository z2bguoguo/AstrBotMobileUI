"""Create the public updater manifest from the final signed APK."""
import hashlib
import json
from pathlib import Path
import sys
from urllib.parse import quote


def create(apk, code, version, tag):
    path = Path(apk)
    version_code = int(code)
    if not 0 < version_code <= 2147483647:
        raise ValueError("Invalid Android version code")
    return {
        "versionCode": version_code,
        "versionName": version,
        "url": "https://github.com/z2bguoguo/AstrBotMobileUI/releases/download/"
        + quote(tag, safe="") + "/" + quote(path.name, safe=""),
        "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        "size": path.stat().st_size,
    }


if __name__ == "__main__":
    apk, code, version, tag, output = sys.argv[1:]
    Path(output).write_text(json.dumps(create(apk, code, version, tag), indent=2) + "\n", encoding="utf-8")
