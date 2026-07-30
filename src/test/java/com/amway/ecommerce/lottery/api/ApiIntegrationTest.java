package com.amway.ecommerce.lottery.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.amway.ecommerce.lottery.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * End-to-end HTTP layer tests: authentication, role grading, request validation,
 * error responses and the admin dynamic-config flow - all through the real filter
 * chain and controllers against real MySQL + Redis.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper om;

    // --- auth ---------------------------------------------------------------

    @Test
    void loginSucceedsWithSeededAccount() throws Exception {
        mvc.perform(login("alice", "alice123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        mvc.perform(login("alice", "nope"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void drawWithoutTokenReturns401() throws Exception {
        mvc.perform(post("/api/activities/1/draws")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"times\":1}"))
                .andExpect(status().isUnauthorized());
    }

    // --- role grading -------------------------------------------------------

    @Test
    void normalUserIsForbiddenFromAdminEndpoints() throws Exception {
        String token = token("alice", "alice123");
        mvc.perform(get("/api/admin/activities").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void adminCanListActivities() throws Exception {
        String token = token("admin", "admin123");
        mvc.perform(get("/api/admin/activities").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("SPRING2026"));
    }

    // --- draw + validation --------------------------------------------------

    @Test
    void userCanDrawAndQuotaDecrements() throws Exception {
        register("itdraw", "itdraw123");
        String token = token("itdraw", "itdraw123");

        mvc.perform(post("/api/activities/1/draws")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"times\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcomes[0].result").exists());

        mvc.perform(get("/api/activities/1/my-quota").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.used").value(1));
    }

    @Test
    void drawWithTooManyTimesFailsValidation() throws Exception {
        register("itvalidate", "itvalidate123");
        String token = token("itvalidate", "itvalidate123");

        mvc.perform(post("/api/activities/1/draws")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"times\":99}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // --- admin dynamic config flow -----------------------------------------

    @Test
    void adminConfigFlowValidatesProbabilitySumOnActivation() throws Exception {
        String token = token("admin", "admin123");

        long activityId = createActivity(token, "IT-CFG", 5);

        // one real prize summing to 1000 != 10000 -> cannot activate
        addPrize(token, activityId, "測試獎", "PRIZE", 1000, 5);
        mvc.perform(activate(token, activityId, "IT config", 5))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PROBABILITY_CONFIG"));

        // add another real prize so non-THANKS prizes sum to 10000 -> activation succeeds
        addPrize(token, activityId, "填充獎", "PRIZE", 9000, 0);
        mvc.perform(activate(token, activityId, "IT config", 5))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    // --- helpers ------------------------------------------------------------

    private MockHttpServletRequestBuilder login(String username, String password) throws Exception {
        return post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("username", username, "password", password)));
    }

    private void register(String username, String password) throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isCreated());
    }

    private String token(String username, String password) throws Exception {
        String body = mvc.perform(login(username, password))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(body).path("data").path("accessToken").asText();
    }

    private long createActivity(String token, String code, int perUserLimit) throws Exception {
        String body = mvc.perform(post("/api/admin/activities")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "code", code, "name", code, "perUserDrawLimit", perUserLimit))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = om.readTree(body).path("data");
        return data.path("id").asLong();
    }

    private void addPrize(String token, long activityId, String name, String type, int prob, int stock)
            throws Exception {
        mvc.perform(post("/api/admin/activities/" + activityId + "/prizes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "name", name, "type", type, "probability", prob, "totalStock", stock))))
                .andExpect(status().isCreated());
    }

    private MockHttpServletRequestBuilder activate(String token, long activityId, String name, int perUserLimit)
            throws Exception {
        return put("/api/admin/activities/" + activityId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of(
                        "name", name, "status", "ACTIVE", "perUserDrawLimit", perUserLimit)));
    }
}
