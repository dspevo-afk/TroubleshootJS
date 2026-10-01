"""Export the frozen application and apply the test-only normal screen overlay.

Usage: python prepare.py REPOSITORY NEW_OS_TEMP_DIRECTORY GWT_JAR_DIRECTORY
The shipping checkout is read only. No build or browser run is started here.
"""
import hashlib
import json
import os
from pathlib import Path
import shutil
import stat
import subprocess
import sys
import tempfile
import zipfile

BASE = "41f02f1b9f6c32cb8cf4ffbf9e8bd2bb3ea2476b"
EXPORT = ["src", "war", "scripts", "tests", "AGENTS.md", ".gitignore", ".gitattributes"]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def no_reparse(path):
    for item in [path, *path.parents]:
        if item.exists() and item.lstat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT:
            raise ValueError("Reparse points are not allowed: " + str(item))


def main():
    if len(sys.argv) != 4:
        raise SystemExit(__doc__)
    repo, target, jars = [Path(value).absolute() for value in sys.argv[1:]]
    package = Path(__file__).resolve().parent
    no_reparse(target)
    if target.exists() or not target.is_relative_to(Path(tempfile.gettempdir()).resolve()) or target.is_relative_to(repo):
        raise ValueError("Destination must be a new owned directory below OS temp, outside the checkout")
    if subprocess.check_output(["git", "-C", str(repo), "rev-parse", BASE], text=True).strip() != BASE:
        raise ValueError("Frozen baseline is unavailable")
    manifest = json.loads((package / "overlay-manifest.json").read_text(encoding="utf-8"))
    for name, expected in manifest["packageFiles"].items():
        if digest(package / name) != expected:
            raise ValueError("Changed overlay file: " + name)
    target.mkdir(parents=True)
    archive = target / "baseline.zip"
    subprocess.run(["git", "-C", str(repo), "archive", "--format=zip", "-o", str(archive), BASE, *EXPORT], check=True)
    app = target / "app"
    app.mkdir()
    with zipfile.ZipFile(archive) as source:
        for name in source.namelist():
            if not (app / name).resolve().is_relative_to(app):
                raise ValueError("Archive path escaped export")
        source.extractall(app)
    baseline = json.loads((package / "baseline-inputs.json").read_text(encoding="utf-8"))
    for name, expected in baseline["files"].items():
        if digest(app / name) != expected:
            raise ValueError("Export differs from frozen baseline: " + name)
    subprocess.run(["git", "apply", "--check", str(package / "baseline.patch")], cwd=app, check=True)
    subprocess.run(["git", "apply", str(package / "baseline.patch")], cwd=app, check=True)
    # Git can preserve the export's CRLF context when applying an LF patch.
    # Reproduce the recorded bytes, not merely equivalent Java/XML text.
    for name, endings in manifest["patchedTextLineEndings"].items():
        output = app / name
        lines = output.read_bytes().replace(b"\r\n", b"\n").split(b"\n")
        if lines[-1] == b"" and endings[-1] != "N":
            lines.pop()
        if len(lines) != len(endings):
            raise ValueError("Patched line count differs from measured source")
        separators = {"C": b"\r\n", "L": b"\n", "N": b""}
        output.write_bytes(b"".join(line + separators[ending] for line, ending in zip(lines, endings)))
    for source in (package / "overlay").rglob("*"):
        if source.is_file():
            output = app / source.relative_to(package / "overlay")
            if output.exists():
                raise ValueError("New overlay file would overwrite baseline: " + str(output))
            output.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, output)
    dependencies = app / ".tools" / "gwt-2.7.0"
    dependencies.mkdir(parents=True)
    for name, expected in manifest["gwtJars"].items():
        if digest(jars / name) != expected:
            raise ValueError("GWT dependency changed: " + name)
        shutil.copyfile(jars / name, dependencies / name)
    for name, expected in manifest["compiledSourceFiles"].items():
        if digest(app / name) != expected:
            raise ValueError("Prepared source differs from measured harness: " + name)
    host = target / "host"
    host.mkdir()
    shutil.copyfile(package / "normal_screen_host.py", host / "normal_screen_host.py")
    (target / "prepare-receipt.json").write_text(json.dumps({
        "baselineCommit": BASE, "baselineArchiveSha256": digest(archive),
        "harnessIdentity": manifest["harnessIdentity"], "status": "PASS"
    }, indent=2) + "\n", encoding="utf-8")
    print("PASS: frozen baseline plus isolated harness prepared at " + str(app))


if __name__ == "__main__":
    main()
