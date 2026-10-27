#!/usr/bin/env python3
"""Repair malformed SQL string concatenation in ChurchCare sync triggers."""
from pathlib import Path
import re
import sys

if len(sys.argv) != 2:
    raise SystemExit("Usage: fix-sync-trigger-sql.py PROJECT_DIR")
root = Path(sys.argv[1])
# The generated SQL contained NEW.<id>','UPSERT' instead of
# NEW.<id>,'UPSERT'. The extra apostrophe is introduced by this Java/Kotlin
# string fragment: + \"','UPSERT',' ...
pattern = re.compile(r'(\+\s*")\x27,\x27UPSERT\x27,\x27')
changed = []
for path in root.rglob('*'):
    if path.suffix not in {'.java', '.kt'} or not path.is_file():
        continue
    source = path.read_text(encoding='utf-8')
    fixed, count = pattern.subn(r"\1,'UPSERT','", source)
    if count:
        path.write_text(fixed, encoding='utf-8')
        changed.append((path, count))
if not changed:
    # Be strict: do not silently build an unpatched app if the source changes.
    raise SystemExit("ERROR: malformed sync-trigger SQL pattern was not found; refusing to continue.")
for path, count in changed:
    print(f"Fixed {count} malformed sync-trigger SQL fragment(s) in {path}")
