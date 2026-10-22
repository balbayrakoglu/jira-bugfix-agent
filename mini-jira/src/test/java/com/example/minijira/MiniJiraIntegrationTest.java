package com.example.minijira;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MiniJiraIntegrationTest {

    private static final String SECRET = "it-secret";
    private static final List<Received> RECEIVED = new CopyOnWriteArrayList<>();
    private static final HttpServer RECEIVER = startReceiver();

    record Received(String signature, byte[] body) {
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:minijira-it;DB_CLOSE_DELAY=-1");
        registry.add("mini-jira.api-token", () -> "it-token");
        registry.add("mini-jira.webhook.secret", () -> SECRET);
        registry.add("mini-jira.webhook.url",
                () -> "http://localhost:" + RECEIVER.getAddress().getPort() + "/webhooks/jira");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void clear() {
        RECEIVED.clear();
    }

    @AfterAll
    static void stopReceiver() {
        RECEIVER.stop(0);
    }

    @Test
    void restApiRequiresCredentials() throws Exception {
        mockMvc.perform(get("/rest/api/2/myself")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/rest/api/2/myself").header("Authorization", basic("bugfix-bot", "wrong")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/rest/api/2/myself").header("Authorization", "Bearer it-token"))
                .andExpect(status().isOk());
    }

    @Test
    void createReadCommentAndWebhookFlow() throws Exception {
        String created = mockMvc.perform(post("/rest/api/2/issue")
                        .header("Authorization", basic("bugfix-bot", "it-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fields":{"summary":"NPE in OrderService","description":"stack",
                                 "issuetype":{"name":"Bug"},"priority":{"name":"High"},"labels":["ai-bugfix"]}}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String key = objectMapper.readTree(created).get("key").asText();
        assertThat(key).matches("SHOP-\\d+");

        JsonNode webhook = awaitWebhook("jira:issue_created");
        assertThat(webhook.at("/issue/key").asText()).isEqualTo(key);
        assertThat(webhook.at("/issue/fields/labels/0").asText()).isEqualTo("ai-bugfix");
        assertThat(webhook.at("/issue/fields/issuetype/name").asText()).isEqualTo("Bug");
        assertThat(webhook.at("/issue/fields/project/key").asText()).isEqualTo("SHOP");

        mockMvc.perform(post("/rest/api/2/issue/{key}/comment", key)
                        .header("Authorization", basic("bugfix-bot", "it-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"h3. analysis\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/rest/api/2/issue/{key}", key).header("Authorization", basic("bugfix-bot", "it-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.summary").value("NPE in OrderService"))
                .andExpect(jsonPath("$.fields.priority.name").value("High"))
                .andExpect(jsonPath("$.fields.comment.comments[0].author.displayName").value("bugfix-bot"))
                .andExpect(jsonPath("$.fields.comment.comments[0].body").value("h3. analysis"));

        mockMvc.perform(put("/rest/api/2/issue/{key}", key)
                        .header("Authorization", basic("bugfix-bot", "it-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"update\":{\"labels\":[{\"add\":\"ai-auto-fix\"}]}}"))
                .andExpect(status().isNoContent());
        assertThat(awaitWebhook("jira:issue_updated").at("/issue/fields/labels").toString()).contains("ai-auto-fix");
    }

    @Test
    void unknownIssueReturnsJiraStyleError() throws Exception {
        mockMvc.perform(get("/rest/api/2/issue/SHOP-9999").header("Authorization", basic("bugfix-bot", "it-token")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorMessages[0]").value("Issue does not exist: SHOP-9999"));
    }

    @Test
    void browserUiRendersPages() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());
        mockMvc.perform(get("/issues/new").param("template", "npe")).andExpect(status().isOk());
        mockMvc.perform(get("/webhooks")).andExpect(status().isOk());
    }

    private JsonNode awaitWebhook(String event) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            for (Received received : RECEIVED) {
                JsonNode json = objectMapper.readTree(received.body());
                if (event.equals(json.path("webhookEvent").asText())) {
                    assertThat(received.signature()).isEqualTo(sign(received.body()));
                    return json;
                }
            }
            Thread.sleep(100);
        }
        throw new AssertionError("No " + event + " webhook received");
    }

    private static HttpServer startReceiver() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/webhooks/jira", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                RECEIVED.add(new Received(exchange.getRequestHeaders().getFirst("X-Hub-Signature"), body));
                exchange.sendResponseHeaders(202, -1);
                exchange.close();
            });
            server.start();
            return server;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sign(byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    }

    private static String basic(String user, String token) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + token).getBytes(StandardCharsets.UTF_8));
    }
}
