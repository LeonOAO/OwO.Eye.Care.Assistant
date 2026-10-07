"""Restore source files to version 1.0.1; does not change installed APK or preferences."""
from pathlib import Path
import zipfile
import json

audit = Path(__file__).resolve().parent
root = audit.parent
manifest = json.loads((audit / "Manifest.json").read_text(encoding="utf-8"))
with zipfile.ZipFile(audit / "Baseline.zip") as archive:
    for name in archive.namelist():
        target = root / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(archive.read(name))
for name in manifest["added"]:
    (root / name).unlink(missing_ok=True)
print("PASS: source restored to version 1.0.1")
