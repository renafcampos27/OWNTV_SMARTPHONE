"""SQLite schema/data probe for both mobile v47 and customized v50 migration origins.
Does not replace the Android Room migration tests; only uses in-memory synthetic databases.
"""
import json
import re
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "vendor" / "OwnTV_Core"
SOURCE = (ROOT / "core/src/main/java/tv/own/owntv/core/database/OwnTVDatabase.kt").read_text(encoding="utf-8")
SCHEMAS = ROOT / "core/schemas"
CUSTOM = json.loads((SCHEMAS / "tv.own.owntv.core.database.OwnTVDatabase/50.json").read_text())["database"]
UPSTREAM = json.loads((SCHEMAS / "upstream-mobile/47.json").read_text())["database"]
EXPECTED = {e["tableName"]: {f["columnName"] for f in e["fields"]} for e in CUSTOM["entities"]}
DETAILS = [("categories", "TEXT"), ("year", "INTEGER"), ("rating", "TEXT"), ("lengthMin", "INTEGER"), ("episode", "TEXT")]
EXPECTED["epg_programmes"].update(c for c, _ in DETAILS)
EXPECTED["programme_reminders"] = {f["columnName"] for e in UPSTREAM["entities"] if e["tableName"] == "programme_reminders" for f in e["fields"]}

def columns(db, table):
    return {row[1] for row in db.execute("PRAGMA table_info(`" + table + "`)")}

for label, initial in [("upstream47", UPSTREAM), ("custom50", CUSTOM)]:
    db = sqlite3.connect(":memory:")
    for entity in initial["entities"]:
        name = entity["tableName"]
        db.execute(entity["createSql"].replace("${TABLE_NAME}", name))
        for index in entity.get("indices", []):
            db.execute(index["createSql"].replace("${TABLE_NAME}", name))
    for sql in initial["setupQueries"]:
        db.execute(sql)
    db.execute("INSERT INTO epg_programmes (sourceId,epgChannelId,startMs,stopMs,title,contentHash) VALUES (-1,'test',1000,2000,'Preserved programme',7)")
    if label == "upstream47":
        db.execute("UPDATE epg_programmes SET categories='Documentary',year=2025,rating='12',lengthMin=60,episode='S1E2'")
    for name in ["MIGRATION_46_47", "MIGRATION_47_48", "MIGRATION_48_49", "MIGRATION_49_50", "MIGRATION_50_51"]:
        if label == "custom50" and name not in ["MIGRATION_46_47", "MIGRATION_50_51"]:
            continue
        block = SOURCE[SOURCE.index("val " + name):]
        block = block[:block.index("\n        }")]
        added = True
        for sql in re.findall(r'connection.execSQL\("([^"\n]+)"\)', block):
            if "$name" in sql:
                continue
            match = re.match(r"ALTER TABLE `([^`]+)` ADD COLUMN `([^`]+)`", sql)
            if match:
                table, column = match.groups()
                added = column not in columns(db, table)
                if not added:
                    continue
            if sql.startswith("UPDATE `sources`") and not added:
                continue
            db.execute(sql)
        if name == "MIGRATION_50_51":
            for column, kind in DETAILS:
                if column not in columns(db, "epg_programmes"):
                    db.execute(f"ALTER TABLE epg_programmes ADD COLUMN {column} {kind}")
    for table, expected in EXPECTED.items():
        if table.endswith("_fts"):
            continue
        actual = columns(db, table)
        assert actual == expected, (label, table, actual - expected, expected - actual)
    assert db.execute("SELECT title,contentHash FROM epg_programmes").fetchone() == ("Preserved programme", 7)
    if label == "upstream47":
        assert db.execute("SELECT categories,year,rating,lengthMin,episode FROM epg_programmes").fetchone() == ("Documentary", 2025, "12", 60, "S1E2")
    print(label + ": union schema columns and EPG values preserved")
