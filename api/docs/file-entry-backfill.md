# File entry backfill

`file_entry` backfill is an explicit migration tool. It is not part of the production application startup path and is not compiled into the default application artifact.

## Preconditions

- Apply the `file_entry` schema before running the tool.
- Keep `BELLA_FILE_ENTRY_READ_MODE=closure` while backfill is running.
- Run only one backfill process for a space and ID range at a time.
- Use the same MySQL connection environment variables as the API service.

## Run

From the `api` directory:

```bash
MYSQL_HOST=localhost \
MYSQL_PORT=3306 \
MYSQL_DATABASE=bella_file_api \
MYSQL_USER=root \
MYSQL_PASSWORD=root \
mvn -Pfile-entry-backfill-tool compile exec:java \
  -Dexec.args="--space-code=<space-code> --min-id=0 --max-id=9223372036854775807 --batch-size=1000 --verify"
```

The profile adds `src/tool/java` only for this command. Normal `mvn package` and the production Spring Boot application do not include or invoke the tool.

Each batch runs in its own transaction. For a large space, choose bounded `min-id` and `max-id` ranges and run them sequentially so that a single operation does not retain an unbounded transaction or in-memory result set. The lower bound is inclusive and the upper bound is exclusive.

Add `--verify` only to the final range. It runs `compareSpace` across the whole space and fails the command if active files and entries are inconsistent. Earlier ranges should omit it. A failed final verification does not roll back already committed batches.

## Rollout

1. Backfill all ID ranges for one space.
2. Add `--verify` to the final range and confirm the full-space consistency check passes.
3. Enable `BELLA_FILE_ENTRY_READ_MODE=compare` and monitor mismatches.
4. Switch to `BELLA_FILE_ENTRY_READ_MODE=entry` only after comparison remains clean.
