#!/usr/bin/env python3
"""Check that the three exports of one pre-test run actually join.

    ./scripts/check_join.py survey1.csv survey2.csv behavioural-log.csv

The redirect design splits one participant across three files: Survey 1 holds consent and the
randomised arm, Survey 2 holds the emotion measures, and the platform holds what they actually did.
Nothing joins them but the Prolific id, carried Prolific -> Survey 1 -> handover -> redirect ->
Survey 2. Any link in that chain can break silently: a renamed embedded-data field, a callbackUrl
pointing at the wrong survey, a participant who closed the tab on the outcome screen.

A broken join is only visible when you try to merge the exports, which is normally after data
collection. This says so on four pilot rows instead.

It reports, and exits non-zero if anything is wrong:

  * ids present in one file and missing from another, each direction named separately, because
    they mean different things (see the report itself)
  * the arm disagreeing between the randomiser, the platform, and what Survey 2 was told
  * duplicate ids within a file
  * sessions the platform never saw completed

Standard library only - no pandas, nothing to install.
"""

import argparse
import csv
import os
import sys
from collections import Counter, defaultdict

# How many offending ids to print before truncating. Enough to recognise a pattern, few enough to
# keep the report readable on a real run where something went wrong for everybody.
SHOW = 10


def read_qualtrics(path):
    """Rows from a Qualtrics CSV export as dicts.

    Qualtrics writes three header rows: the column names, then the question text, then a JSON blob
    of import ids. csv.DictReader takes the first as the header and hands back the other two as
    data, so a naive read reports two extra participants whose ids are the words "PROLIFIC_PID"
    and a fragment of JSON.

    The ImportId row is the one that can be recognised on its own, and it is always the second data
    row - so finding it there is what says the question-text row above it is a header too. Matching
    each row independently would leave the question-text row behind, because nothing in it is
    distinguishable from a response. An export configured without these rows (Qualtrics can do
    that) has no ImportId row, nothing is dropped, and it reads correctly.
    """
    with open(path, newline="", encoding="utf-8-sig") as fh:
        rows = list(csv.DictReader(fh))
    if len(rows) >= 2 and _is_import_id_row(rows[1]):
        return rows[2:]
    if rows and _is_import_id_row(rows[0]):
        return rows[1:]
    return rows


def _is_import_id_row(row):
    return "ImportId" in " ".join(str(v) for v in row.values() if v)


def read_plain(path):
    with open(path, newline="", encoding="utf-8-sig") as fh:
        return list(csv.DictReader(fh))


def pick_column(rows, candidates, path, what):
    """First candidate column that exists, matched case-insensitively."""
    if not rows:
        return None
    lookup = {k.lower(): k for k in rows[0]}
    for c in candidates:
        if c.lower() in lookup:
            return lookup[c.lower()]
    print(f"ERROR: no {what} column in {path}.", file=sys.stderr)
    print(f"       Looked for: {', '.join(candidates)}", file=sys.stderr)
    print(f"       Found:      {', '.join(list(rows[0])[:25])}", file=sys.stderr)
    return None


def drop_previews(rows, path):
    """Qualtrics preview and test responses, which are not participants.

    Piloting a survey inside Qualtrics writes rows with the same shape as real ones. Counting them
    inflates Survey 1 and produces ids that are missing everywhere else - which reads as a broken
    join rather than as your own preview.
    """
    if not rows or "Status" not in rows[0]:
        return rows, 0
    kept = [r for r in rows if str(r.get("Status", "")).strip() in ("0", "IP Address", "")]
    dropped = len(rows) - len(kept)
    if dropped:
        print(f"  ({dropped} preview/test response(s) ignored in {path})")
    return kept, dropped


def ids_and_arms(rows, id_col, arm_col):
    """{id: arm} plus a count per id, so duplicates are visible rather than silently collapsed."""
    arms = {}
    counts = Counter()
    for r in rows:
        pid = (r.get(id_col) or "").strip()
        if not pid:
            continue
        counts[pid] += 1
        arms[pid] = (r.get(arm_col) or "").strip() if arm_col else ""
    return arms, counts


def show(label, ids, explanation):
    if not ids:
        return False
    listed = sorted(ids)
    print(f"\n  {label}: {len(listed)}")
    print(f"    {explanation}")
    for pid in listed[:SHOW]:
        print(f"      {pid}")
    if len(listed) > SHOW:
        print(f"      … and {len(listed) - SHOW} more")
    return True


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("survey1", help="Qualtrics CSV export of Survey 1 (consent, briefing, arm)")
    ap.add_argument("survey2", help="Qualtrics CSV export of Survey 2 (emotion measures)")
    ap.add_argument("platform", help="CSV from <appUrl>/data (Export CSV)")
    ap.add_argument("--keep-previews", action="store_true",
                    help="count Qualtrics preview/test responses as participants")
    args = ap.parse_args()

    s1 = read_qualtrics(args.survey1)
    s2 = read_qualtrics(args.survey2)
    pf = read_plain(args.platform)

    print("Reading:")
    print(f"  Survey 1  {args.survey1}   {len(s1)} response(s)")
    print(f"  Survey 2  {args.survey2}   {len(s2)} response(s)")
    print(f"  Platform  {args.platform}   {len(pf)} row(s)")

    if not args.keep_previews:
        s1, _ = drop_previews(s1, args.survey1)
        s2, _ = drop_previews(s2, args.survey2)

    s1_id = pick_column(s1, ["PROLIFIC_PID", "participantId", "prolificPid"], args.survey1,
                        "participant id")
    s2_id = pick_column(s2, ["participantId", "PROLIFIC_PID", "prolificPid"], args.survey2,
                        "participant id")
    pf_id = pick_column(pf, ["participantId"], args.platform, "participant id")
    if not (s1_id and s2_id and pf_id):
        return 2

    s1_arm = pick_column(s1, ["arm"], args.survey1, "arm") if s1 else None
    s2_arm = pick_column(s2, ["arm"], args.survey2, "arm") if s2 else None
    pf_arm = pick_column(pf, ["arm"], args.platform, "arm") if pf else None

    s1_arms, s1_counts = ids_and_arms(s1, s1_id, s1_arm)
    s2_arms, s2_counts = ids_and_arms(s2, s2_id, s2_arm)

    # The platform file is one row per EVENT, so an id legitimately appears many times. Collapse to
    # one entry per participant, and note separately whether a session of theirs was ever completed.
    pf_arms = {}
    pf_completed = defaultdict(bool)
    for r in pf:
        pid = (r.get(pf_id) or "").strip()
        if not pid:
            continue
        pf_arms[pid] = (r.get(pf_arm) or "").strip() if pf_arm else ""
        if (r.get("kind") or "").strip() == "SESSION":
            pf_completed[pid] |= "completed=true" in (r.get("detail") or "").lower()

    print(f"\nParticipants:")
    print(f"  Survey 1  {len(s1_arms)}")
    print(f"  Survey 2  {len(s2_arms)}")
    print(f"  Platform  {len(pf_arms)}")

    a, b, c = set(s1_arms), set(s2_arms), set(pf_arms)
    complete = a & b & c
    print(f"\n  Joined across all three: {len(complete)}")

    problems = False

    for name, counts, path in (("Survey 1", s1_counts, args.survey1),
                               ("Survey 2", s2_counts, args.survey2)):
        dupes = {pid for pid, n in counts.items() if n > 1}
        problems |= show(
            f"Duplicate ids in {name}", dupes,
            "The same participant has more than one response. Decide which to keep before analysis.")

    problems |= show(
        "In Survey 1, never reached the platform", a - c,
        "Redirect out of Survey 1 failed, or they abandoned at the handover. Check the Web Service.")

    problems |= show(
        "On the platform, never reached Survey 2", c - b,
        "They did the task and did not arrive at the post-measures: abandoned on the outcome "
        "screen, or callbackUrl points at the wrong survey.")

    problems |= show(
        "In Survey 2, with no platform record", b - c,
        "They answered the post-measures without doing the task. Someone opened Survey 2's link "
        "directly - add the empty-participantId branch to Survey 2's flow.")

    problems |= show(
        "In Survey 2, with no Survey 1 response", b - a,
        "The join key differs between the surveys: Survey 1's PROLIFIC_PID is not what reached "
        "Survey 2's participantId.")

    problems |= show(
        "Reached the platform but never completed a session", {p for p in c if not pf_completed[p]},
        "No session marked complete - they left before clicking through the outcome screen. "
        "Exclude these, or the post-measures belong to an unfinished task.")

    # Arm agreement. Survey 1 assigned it, the platform enforced it, Survey 2 was told it on the
    # URL. All three disagreeing in different ways means different bugs, so they are named apart.
    mismatched = []
    for pid in sorted(complete):
        one, two, plat = s1_arms.get(pid, ""), s2_arms.get(pid, ""), pf_arms.get(pid, "")
        if len({one, two, plat}) > 1:
            mismatched.append((pid, one, plat, two))
    if mismatched:
        problems = True
        print(f"\n  Arm disagrees: {len(mismatched)}")
        print("    Survey 1 assigned it, the platform ran it, Survey 2 was told it on the URL.")
        print(f"    {'participant':<24} {'survey1':<9} {'platform':<9} survey2")
        for pid, one, plat, two in mismatched[:SHOW]:
            print(f"    {pid:<24} {one or '-':<9} {plat or '-':<9} {two or '-'}")
        if len(mismatched) > SHOW:
            print(f"    … and {len(mismatched) - SHOW} more")

    if complete:
        print("\n  Arm balance (participants joined across all three):")
        for arm, n in sorted(Counter(pf_arms[p] for p in complete).items()):
            print(f"    {arm or '(blank)':<8} {n}")

    print()
    if problems:
        print("RESULT: the join is not clean. Each section above says what that case means.")
        return 1
    print(f"RESULT: clean. {len(complete)} participant(s) join across all three files, arms agree.")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except BrokenPipeError:
        # Piping into head or less closes the pipe early. Python would otherwise print a traceback
        # on the way out, which on a report like this reads as the check having crashed.
        os._exit(0)
