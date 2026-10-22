package com.example.bugfixagent;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.bugfixagent.processing.TicketQueue;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JiraWebhookIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    TicketQueue queue;

    @Test
    void queuesLabelledBugWithValidSignature() throws Exception {
        when(queue.enqueueNew("SHOP-12")).thenReturn(TicketQueue.EnqueueResult.QUEUED);
        String body = event("jira:issue_created", "SHOP-12", "Bug", "ai-bugfix");

        mockMvc.perform(post("/webhooks/jira")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature", sign(body))
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(content().string("QUEUED"));
        verify(queue).enqueueNew("SHOP-12");
    }

    @Test
    void rejectsInvalidSignature() throws Exception {
        String body = event("jira:issue_created", "SHOP-12", "Bug", "ai-bugfix");

        mockMvc.perform(post("/webhooks/jira")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature", "sha256=00")
                        .content(body))
                .andExpect(status().isUnauthorized());
        verify(queue, never()).enqueueNew(anyString());
    }

    @Test
    void ignoresIssuesWithoutTriggerLabel() throws Exception {
        String body = event("jira:issue_updated", "SHOP-13", "Bug", "frontend");

        mockMvc.perform(post("/webhooks/jira")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature", sign(body))
                        .content(body))
                .andExpect(status().isOk());
        verify(queue, never()).enqueueNew(anyString());
    }

    @Test
    void adminApiRequiresToken() throws Exception {
        mockMvc.perform(get("/api/bugs")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/bugs").header("X-Admin-Token", "test-admin-token")).andExpect(status().isOk());
    }

    private static String event(String type, String key, String issueType, String label) {
        return """
                {"webhookEvent":"%s","issue":{"key":"%s","fields":{
                  "issuetype":{"name":"%s"},"project":{"key":"SHOP"},"labels":["%s"]}}}
                """.formatted(type, key, issueType, label);
    }

    private static String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
