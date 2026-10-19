"""Build only MFCL's public API from a pinned, verified upstream source archive.

Requires Python 3.10+ and a JDK 17+ (JAVA_HOME or PATH). No third party modules.
Does not build or redistribute the MFCL runtime or legacy game files.
"""
import hashlib
import io
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import urllib.request
import zipfile

REVISION = "2c3a2e224c90925cf5f6458e825799b80e7b1deb"
SHA256 = "f47ef9a3550342afe9b57190423049adc88d00b7d91e18c3ce289efbba833a6e"
URL = f"https://codeload.github.com/gengyoubo/ModernForgeConnectLoader/zip/{REVISION}"
ROOT = Path(__file__).resolve().parents[1]


def java_tool(name):
    home = os.environ.get("JAVA_HOME")
    suffix = ".exe" if os.name == "nt" else ""
    candidate = Path(home) / "bin" / (name + suffix) if home else None
    if candidate and candidate.is_file():
        return str(candidate)
    found = shutil.which(name)
    if not found:
        raise SystemExit(f"Missing {name}: set JAVA_HOME to JDK 17 or newer.")
    return found


def main():
    javac, jar = java_tool("javac"), java_tool("jar")
    print(f"Downloading MFCL API source at {REVISION}")
    with urllib.request.urlopen(URL, timeout=60) as response:
        data = response.read()
    if hashlib.sha256(data).hexdigest() != SHA256:
        raise SystemExit("MFCL source archive SHA-256 mismatch; no files were compiled.")
    destination = ROOT / "libs" / "mfcl-0.1.0-api.jar"
    destination.parent.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="tcmodern-mfcl-api-") as temporary:
        work = Path(temporary)
        sources, classes = work / "sources", work / "classes"
        sources.mkdir()
        classes.mkdir()
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            prefix = f"ModernForgeConnectLoader-{REVISION}/src/api/java/"
            for entry in archive.infolist():
                if entry.filename.startswith(prefix) and entry.filename.endswith(".java"):
                    relative = Path(entry.filename[len(prefix):])
                    target = (sources / relative).resolve()
                    if not target.is_relative_to(sources.resolve()):
                        raise SystemExit("Invalid API source path in archive")
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes(archive.read(entry))
            (work / "LICENSE").write_bytes(archive.read(f"ModernForgeConnectLoader-{REVISION}/LICENSE"))
        source_files = sorted(sources.rglob("*.java"))
        if not source_files:
            raise SystemExit("No public MFCL API sources found")
        argument_file = work / "sources.txt"
        argument_file.write_text("\n".join('"' + p.as_posix() + '"' for p in source_files), encoding="utf-8")
        subprocess.run([javac, "--release", "17", "-encoding", "UTF-8", "-d", str(classes), "@" + str(argument_file)], check=True)
        metadata = classes / "META-INF"
        metadata.mkdir()
        shutil.copyfile(work / "LICENSE", metadata / "LICENSE-MFCL")
        (metadata / "mfcl-source.txt").write_text(f"Source: {URL}\nCommit: {REVISION}\nSHA-256: {SHA256}\nLicense: MPL-2.0\n", encoding="utf-8")
        subprocess.run([jar, "--create", "--file", str(destination), "-C", str(classes), "."], check=True)
    print(f"Prepared {destination}")


if __name__ == "__main__":
    main()
