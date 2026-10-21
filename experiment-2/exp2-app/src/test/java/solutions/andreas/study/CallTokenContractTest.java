package solutions.andreas.study;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The participant's call token requires the participant header.
 *
 * <p>Written after the first live run failed on exactly this: the browser sent the request without
 * it, the server answered 400 "Missing X-Participant-Id header", and the participant watched
 * "connecting" while the agent sat in an empty call. The only record of it was one line in the
 * server log.
 *
 * <p>This asserts the server's half of that contract. It would not have caught the browser
 * forgetting to send it - that needs the two running together - but it pins down which side is
 * supposed to do what, so a later change cannot quietly make the endpoint anonymous and move the
 * failure somewhere harder to see.
 */
@SpringBootTest
@Import(MockMvcAutoConfiguration.class)
class CallTokenContractTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void withoutTheParticipantHeaderItIsRejected() throws Exception {
        mvc.perform(post("/api/call/token")).andExpect(status().isBadRequest());
    }
}
