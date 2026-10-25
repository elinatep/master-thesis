package solutions.andreas.study;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The register call in exactly the shape Qualtrics sends it.
 *
 * <p>Qualtrics' Web Service element types every body parameter as a string, so the two factors
 * arrive as {@code "true"} and {@code "false"} rather than as JSON booleans. The endpoint declares
 * them as {@code Boolean}, and whether that is accepted is a property of the JSON mapper, not
 * something either side states.
 *
 * <p>Worth a test because of where the failure would land: not here, but on a live survey, as a
 * 400 from a web service element that Qualtrics reports quietly - and every participant would be
 * redirected to the platform with no token at all.
 */
@SpringBootTest
@Import(MockMvcAutoConfiguration.class)
class QualtricsRegisterFormatTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void theFactorsAreAcceptedAsTheStringsQualtricsSends() throws Exception {
        mvc.perform(post("/api/handover")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"participantId":"5f1a2b3c4d5e6f7a8b9c0d1e",
                                 "aiAck":"true",
                                 "humanAck":"false",
                                 "name":"Joe Smith",
                                 "callbackUrl":"https://mtecethz.eu.qualtrics.com/jfe/form/SV_X"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }

    /** And that they survive the round trip as the cell that was assigned, not as a default. */
    @Test
    void theCellComesBackOutAsAssigned() throws Exception {
        String register = mvc.perform(post("/api/handover")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"participantId":"p-strings","aiAck":"false","humanAck":"true"}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = register.replaceAll(".*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/handover/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiAck").value(false))
                .andExpect(jsonPath("$.humanAck").value(true));
    }

    /**
     * An empty string is not a factor. Qualtrics sends one when an embedded-data field was never
     * set - a randomiser branch that did not fire, a typo in a field name - and that participant
     * must fail at the gate rather than be quietly dropped into a cell.
     */
    /**
     * A randomiser built on 0/1 rather than true/false. Both conventions appear in the same
     * survey - the cell label is written one way, the factors another - and which one a given
     * randomiser element holds is not visible from here.
     */
    @Test
    void theFactorsAreAcceptedAsZeroAndOne() throws Exception {
        mvc.perform(post("/api/handover").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"participantId":"p-01","aiAck":"0","humanAck":"1"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    /** 0 is false, not merely "not true": the cell has to come back the right way round. */
    @Test
    void zeroAndOneMapToTheCellTheyName() throws Exception {
        String token = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(mvc.perform(post("/api/handover").contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"participantId":"p-01-cell","aiAck":"0","humanAck":"1"}"""))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString())
                .get("token").asText();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/handover/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiAck").value(false))
                .andExpect(jsonPath("$.humanAck").value(true));
    }

    /**
     * Anything that is not one of the four accepted spellings is rejected rather than guessed at.
     * Guessing here would assign a condition nobody chose, and the run would look clean.
     */
    @Test
    void anUnrecognisedFactorIsRejected() throws Exception {
        mvc.perform(post("/api/handover").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"participantId":"p-junk","aiAck":"maybe","humanAck":"1"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anUnsetFactorIsStillRejected() throws Exception {
        mvc.perform(post("/api/handover")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"participantId":"p-empty","aiAck":"","humanAck":"true"}"""))
                .andExpect(status().isBadRequest());
    }
}
