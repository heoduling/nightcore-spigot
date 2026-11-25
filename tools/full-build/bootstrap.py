#!/usr/bin/env python3
"""Populate a Maven repository using the pinned public dependency manifest."""

import argparse
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import threading
import uuid


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", type=Path, required=True,
                        help="Maven local repository to populate")
    args = parser.parse_args()
    repository = args.repository.expanduser().resolve()
    curl = shutil.which("curl")
    if curl is None:
        parser.error("curl must be available on PATH")
    manifest = json.loads(Path(__file__).with_name("dependencies.json").read_text(encoding="utf-8"))
    marker_lock = threading.Lock()

    def fetch(entry):
        relative = Path(entry["path"])
        if relative.is_absolute() or relative.drive or ".." in relative.parts:
            raise ValueError(f"Invalid repository path {entry['path']}")
        destination = repository / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        cached = destination.is_file() and hashlib.sha256(destination.read_bytes()).hexdigest() == entry["sha256"]
        if not cached:
            temporary = destination.with_name(destination.name + "." + uuid.uuid4().hex + ".tmp")
            try:
                subprocess.run([
                    curl, "--fail", "--location", "--silent", "--show-error",
                    "--connect-timeout", "10", "--max-time", "45", "--retry", "2",
                    "--output", str(temporary), entry["url"],
                ], check=True, stdout=subprocess.DEVNULL)
                actual = hashlib.sha256(temporary.read_bytes()).hexdigest()
                if actual != entry["sha256"]:
                    raise ValueError(f"SHA-256 mismatch for {entry['path']}")
                os.replace(temporary, destination)
            finally:
                temporary.unlink(missing_ok=True)
        # These are verified local artifacts, independent of Maven repository IDs.
        marker = destination.parent / "_remote.repositories"
        line = destination.name + ">=\n"
        with marker_lock:
            existing = marker.read_text(encoding="utf-8") if marker.exists() else ""
            if line not in existing:
                with marker.open("a", encoding="utf-8") as stream:
                    stream.write(line)
        return cached

    cached_count = 0
    failures = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as executor:
        pending = {executor.submit(fetch, entry): entry for entry in manifest["files"]}
        for completed in concurrent.futures.as_completed(pending):
            entry = pending[completed]
            try:
                cached_count += completed.result()
            except Exception as error:
                failures.append(entry["path"])
                print(f"FAILED {entry['path']} ({entry['url']})\n{error}", flush=True)
    total = len(manifest["files"])
    print(f"Verified {total - len(failures)}/{total} files, reused {cached_count} cached files.")
    if failures:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
