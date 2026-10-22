"""Read-only check of current generated Room schemas and platform DAO query contracts.

Run after Android/Windows compilation: python -B scripts/verify-library-parity.py
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
QUERY = re.compile(r'@(?:androidx\.room\.)?Query\s*\(\s*("""[\s\S]*?"""|"(?:\\.|[^"\\])*")\s*\)')


def read(relative):
    return (ROOT / relative).read_text(encoding="utf-8")


def queries(relative):
    text = read(relative)
    result = {}
    for match in QUERY.finditer(text):
        signature = re.search(r"\bfun\s+(\w+)\s*\(", text[match.end():])
        if signature is None:
            raise ValueError(f"Query without a function in {relative}")
        raw = match.group(1)
        sql = raw[3:-3] if raw.startswith('"""') else json.loads(raw)
        name = signature.group(1)
        result.setdefault(name, []).append(" ".join(sql.replace("$LOCAL_KEY_PREFIX", "local:").split()))
    return {key: sorted(values) for key, values in result.items()}


def generated_schema(relative):
    text = read(relative)
    identity = re.search(r'RoomOpenDelegate\((\d+), "([0-9a-f]+)"', text)
    if identity is None:
        raise ValueError(f"Generated Room identity missing: {relative}")
    statements = []
    for raw in re.findall(r'connection\.execSQL\(("(?:\\.|[^"\\])*")\)', text):
        sql = json.loads(raw)
        if sql.startswith("CREATE "):
            statements.append(" ".join(sql.split()))
    if not statements:
        raise ValueError(f"Generated Room schema missing: {relative}")
    return (int(identity.group(1)), identity.group(2)), sorted(statements)


def verify():
    android = queries("app/src/main/kotlin/com/k4music/android/Database.kt")
    windows = queries("windows/src/main/kotlin/com/k4music/desktop/data/K4Dao.kt")
    windows.update(queries("windows/src/main/kotlin/com/k4music/desktop/data/K4Database.kt"))
    # Desktop local metadata uses different parameter names; verify its SQL against Android too.
    metadata, = windows.pop("updateLocalMetadata")
    for before, after in ((":artists", ":artistsText"), (":duration", ":durationText"), (":artwork", ":thumbnailUrl")):
        metadata = metadata.replace(before, after)
    # These UPDATE statements contain identifiers/parameters only; normalize operator spacing.
    windows["updateSongMetadata"] = [re.sub(r"\s*([=,])\s*", r"\1", metadata)]
    android["updateSongMetadata"] = [re.sub(r"\s*([=,])\s*", r"\1", sql) for sql in android["updateSongMetadata"]]
    # Android cache completion updates Format. Desktop downloads use a separate file index.
    android.pop("updateDownloadedLength")
    if android != windows:
        missing = sorted(android.keys() - windows.keys())
        extra = sorted(windows.keys() - android.keys())
        changed = sorted(key for key in android.keys() & windows.keys() if android[key] != windows[key])
        raise ValueError(f"DAO drift: missing={missing}, extra={extra}, changed={changed}")

    phone = generated_schema("app/build/generated/ksp/debug/kotlin/com/k4music/android/DatabaseInitializer_Impl.kt")
    desktop = generated_schema("windows/build/generated/ksp/main/kotlin/com/k4music/desktop/data/K4Database_Impl.kt")
    if phone != desktop:
        raise ValueError("Generated Android/Windows schemas or identity hashes differ")
    exported = json.loads(read("app/schemas/com.k4music.android.DatabaseInitializer/30.json"))["database"]
    if phone[0] != (exported["version"], exported["identityHash"]):
        raise ValueError("Generated schema does not match the committed Android schema version/hash")
    print(f"PASS: {sum(map(len, android.values()))} shared DAO query contracts; {len(phone[1])} schema statements; Room version {phone[0][0]} and identity match.")


if __name__ == "__main__":
    try:
        verify()
    except (OSError, ValueError, KeyError) as error:
        raise SystemExit(f"Library parity failed: {error}") from error
