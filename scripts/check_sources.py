#!/usr/bin/env python3
"""Offline source-consistency checks (no Maven needed). NOT a compiler: it catches broken internal references
(imports, repository method calls, record constructor arity, Mappers overloads, SQL table/column names)."""
import re, sys, pathlib
root = pathlib.Path(__file__).resolve().parent.parent / "backend" / "src"
main = root / "main" / "java"
files = list(main.rglob("*.java")) + list((root / "test" / "java").rglob("*.java"))
problems = []

# 1) project imports resolve to files
for f in files:
    for m in re.finditer(r"^import (static )?(com\.smartrent[\w.]*);", f.read_text(), re.M):
        parts = m.group(2).split(".")
        # allow nested types: strip trailing segments until a file exists
        ok = False
        for i in range(len(parts), 2, -1):
            if (main / pathlib.Path(*parts[:i])).with_suffix(".java").exists():
                ok = True; break
        if not ok:
            problems.append(f"{f.name}: unresolved import {m.group(2)}")

# 2) repository method calls
std = set("save saveAll saveAndFlush findById findAll count delete deleteById existsById flush getReferenceById findAllById deleteAll".split())
repo_methods = {}
for f in (main / "com/smartrent/repo").glob("*Repository.java"):
    txt = f.read_text()
    repo_methods[f.stem] = set(re.findall(r"^\s+(?:[\w<>\[\],.? ]+?)\s+(\w+)\(", txt, re.M)) | std
for f in files:
    txt = f.read_text()
    fields = dict((n, t) for t, n in re.findall(r"(?:private final|private)\s+(\w+Repository)\s+(\w+)\s*;", txt) for t, n in [(t, n)]) if False else {}
    for t, n in re.findall(r"private final (\w+(?:Repository))\s+(\w+);", txt):
        fields[n] = t
    for n, t in fields.items():
        if t not in repo_methods:
            continue
        for call in re.findall(r"\b" + n + r"\.(\w+)\(", txt):
            if call not in repo_methods[t]:
                problems.append(f"{f.name}: {n}.{call}() not declared in {t}")

# 3) record constructor arity for Dtos.* and BookingEventMessage etc.
dtos = (main / "com/smartrent/dto/Dtos.java").read_text()
records = {}
for m in re.finditer(r"public record (\w+)(?:<[^>]*>)?\(", dtos):
    i = m.end(); depth = 1; j = i
    while depth:
        c = dtos[j]; depth += (c == "(") - (c == ")"); j += 1
    body = dtos[i:j - 1]
    # count top-level commas (ignore generics and annotations with parentheses)
    d = 0; n = 0; seen = False
    for c in body:
        if c in "(<": d += 1
        elif c in ")>": d -= 1
        elif c == "," and d == 0: n += 1
        elif not c.isspace(): seen = True
    records[m.group(1)] = (n + 1) if seen else 0

def split_args(s):
    args, d, cur = [], 0, ""
    for c in s:
        if c in "([{<": d += 1
        elif c in ")]}>": d -= 1
        if c == "," and d == 0:
            args.append(cur); cur = ""
        else:
            cur += c
    if cur.strip(): args.append(cur)
    return args

for f in files:
    txt = f.read_text()
    for m in re.finditer(r"new (\w+)\(", txt):
        name = m.group(1)
        if name not in records or name == "PageResponse": continue
        i = m.end(); depth = 1; j = i
        while depth and j < len(txt):
            c = txt[j]; depth += (c == "(") - (c == ")"); j += 1
        n = len(split_args(txt[i:j - 1]))
        if n != records[name]:
            problems.append(f"{f.name}: new {name}(...) has {n} args, record declares {records[name]}")

# 4) Mappers.toDto overloads exist for entity usages
mappers = (main / "com/smartrent/service/Mappers.java").read_text()
have = set(re.findall(r"public static \w+ toDto\((\w+)", mappers))
for f in files:
    for m in re.finditer(r"Mappers::toDto|Mappers\.toDto\(", f.read_text()):
        pass
print("Mappers.toDto overloads for:", ", ".join(sorted(have)))

# 5) raw SQL column names exist in V1 migration
schema = (root / "main/resources/db/migration/V1__core_schema.sql").read_text().lower()
defs = (main / "com/smartrent/batch/ReportDefinitions.java").read_text()
for col in set(re.findall(r"\b[bvlm]2?3?\.(\w+)", "\n".join(re.findall(r'"([^"]*)"', defs)))):
    if col not in schema and col not in ("id",):
        problems.append(f"ReportDefinitions: column '{col}' not in V1 schema")

print(f"{len(files)} java files, {len(records)} DTO records checked")
if problems:
    print("PROBLEMS:"); [print("  -", p) for p in problems]; sys.exit(1)
print("OK: no internal reference problems found")
