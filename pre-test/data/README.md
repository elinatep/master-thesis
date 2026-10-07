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

This README is the only file in here that is committed.
