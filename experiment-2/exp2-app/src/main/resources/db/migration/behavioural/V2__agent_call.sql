-- The call the two of them join.
--
-- Experiment 2's second leg is a live voice call between the participant and a human agent,
-- carried by Azure Communication Services. Both sides join a group call identified by a GUID, and
-- this is where that GUID lives: one per handover, assigned when the assistant transfers, so the
-- participant can start joining before an agent has picked up and the agent joins the call that is
-- already waiting rather than a new one.
--
-- On the handover rather than on the session, because a session has at most one handover but the
-- call belongs to the transfer: if a handover were ever retried as a new row, it would need its own
-- call, not the old one's.
--
-- No voice data is stored anywhere - the ethics application forbids it. This is the address of a
-- call, not a recording of one.
ALTER TABLE agent_handover ADD COLUMN call_id UUID;

-- Backfill before the NOT NULL, so this migration also applies to a database that already carries
-- pilot handovers. gen_random_uuid() is in core Postgres from 13 on; the server is 16.
UPDATE agent_handover SET call_id = gen_random_uuid() WHERE call_id IS NULL;

ALTER TABLE agent_handover ALTER COLUMN call_id SET NOT NULL;
