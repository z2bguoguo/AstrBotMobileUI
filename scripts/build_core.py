"""Bundle the exact embedded core and data, then sign with the persistent APK key."""
import base64
import json
from pathlib import Path
import subprocess
import sys
import tempfile


def payload(metadata, assets):
    result = json.loads(Path(metadata).read_text())
    for name in ("main.js", "locales.json", "fields.json"):
        result[name] = (Path(assets) / name).read_text(encoding="utf-8")
    return json.dumps(result, ensure_ascii=False, separators=(",", ":")).encode()


if __name__ == "__main__":
    metadata, assets, output = sys.argv[1:]
    data = payload(metadata, assets)
    with tempfile.TemporaryDirectory() as folder:
        raw, signature = Path(folder) / "payload", Path(folder) / "signature"
        raw.write_bytes(data)
        subprocess.run(["java", str(Path(__file__).with_name("SignCore.java")), str(raw), str(signature)], check=True)
        envelope = {"payload": base64.b64encode(data).decode(), "signature": signature.read_text()}
        Path(output).write_text(json.dumps(envelope, separators=(",", ":")) + "\n")
