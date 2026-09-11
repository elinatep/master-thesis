package solutions.andreas.study.admin;

import solutions.andreas.portal.core.user.AccountProvisioner;
import solutions.andreas.portal.core.user.AppUserRepository;
import solutions.andreas.portal.core.user.CurrentUser;
import solutions.andreas.study.EventRepository;
import solutions.andreas.study.StudySessionRepository;
import solutions.andreas.study.TranscriptEntryRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DEV/TEST admin tooling: reset business and/or behavioural data, either for everyone or just
 * the current participant. Destructive and unauthenticated — fine for the controlled study
 * setup; to be locked down later. The frontend reloads fresh (as a new Qualtrics entry) after
 * each call, which re-provisions the current participant (re-seeding them if they were wiped).
 *
 * "Reset business data" means re-seed the demo policies; "reset behavioural data" means delete
 * the logs (sessions cascade to their events + transcripts).
 *
 * <p>Study-owned: it spans both sides — behavioural logs <em>and</em> the portal's seeded data — so
 * it can only live here. Reaching into the portal (its provisioner and account repository) is the
 * permitted direction; the reverse would not compile.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AccountProvisioner accounts;
    private final CurrentUser currentUser;
    private final AppUserRepository users;
    private final StudySessionRepository sessions;
    private final EventRepository events;
    private final TranscriptEntryRepository transcripts;

    public AdminController(AccountProvisioner accounts, CurrentUser currentUser, AppUserRepository users,
            StudySessionRepository sessions, EventRepository events, TranscriptEntryRepository transcripts) {
        this.accounts = accounts;
        this.currentUser = currentUser;
        this.users = users;
        this.sessions = sessions;
        this.events = events;
        this.transcripts = transcripts;
    }

    /** Everything, all participants: wipe all behavioural logs and all business users + their data. */
    @PostMapping("/reset/all")
    @Transactional
    public void resetAll() {
        transcripts.deleteAllInBatch();
        events.deleteAllInBatch();
        sessions.deleteAllInBatch();
        users.deleteAllInBatch(); // cascades to policies -> documents + claims
    }

    /** Current participant: re-seed their business data and delete their behavioural logs. */
    @PostMapping("/reset/me")
    @Transactional
    public void resetMe() {
        var user = currentUser.get();
        accounts.seedFor(user);
        sessions.deleteByParticipantId(user.getAccountRef());
    }

    /** Current participant: re-seed their business data only. */
    @PostMapping("/reset/me/business")
    @Transactional
    public void resetMeBusiness() {
        accounts.seedFor(currentUser.get());
    }

    /** Current participant: delete their behavioural logs only. */
    @PostMapping("/reset/me/behavioural")
    @Transactional
    public void resetMeBehavioural() {
        sessions.deleteByParticipantId(currentUser.get().getAccountRef());
    }
}
