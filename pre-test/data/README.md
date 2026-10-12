# Participant data goes here

Gitignored, deliberately — see the root `.gitignore`. The exports carry Prolific ids, which are
pseudonymous personal data under the ethics application.

Put the three exports here and point the analysis at them:

```
data/Intro.csv              Qualtrics → Survey 1 → Data & Analysis → Export → CSV
data/Outro.csv              the same, from Survey 2
data/behavioural-log.csv    the Export CSV button on <appUrl>/data
```

Then, from `pre-test`:

```bash
./scripts/check_join.py data/Intro.csv data/Outro.csv data/behavioural-log.csv
```

## Database backups also land here

`../scripts/backup_db.sh` writes three files per run, timestamped:

```
data/pretest-<UTC timestamp>.dump      restorable: pg_restore --no-owner -d <db> <file>
data/pretest-<UTC timestamp>.sql       the same content as plain SQL, readable without tooling
data/pretest-<UTC timestamp>.log.json  the behavioural log, as /data serves it
```

The dump is the only copy of the portal side - the claims participants actually filed. The `/data`
export covers the behavioural log and not that, and "what did they type into the claim form" is a
question the write-up may well need to answer.

Gitignored like everything else here, which keeps it out of git. It does not make it backed up:
copy it to an ETH drive or an external disk before anything on Azure is deleted.

This README is the only file in here that is committed.
