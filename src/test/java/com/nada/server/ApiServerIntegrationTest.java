package com.nada.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nada.repository.InMemoryGraphRepository;
import com.nada.service.GraphService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ApiServerIntegrationTest {

    private static ApiServer server;
    private static int port;
    private static HttpClient client;
    private static final ObjectMapper mapper = new ObjectMapper();

    @BeforeAll
    static void startServer() throws IOException {
        // Find an open port
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }

        InMemoryGraphRepository repository = new InMemoryGraphRepository();
        GraphService service = new GraphService(repository);
        server = new ApiServer(port, service);
        server.start();

        client = HttpClient.newHttpClient();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    @DisplayName("POST /people: successfully creates person with 201 Created and supports Hindi script")
    void testCreatePersonHindi() throws Exception {
        String json = "{\"name\": \"आशा\"}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url("/people")))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(response.statusCode()).isEqualTo(201);

        JsonNode body = mapper.readTree(response.body());
        assertThat(body.has("id")).isTrue();
        assertThat(body.get("name").asText()).isEqualTo("आशा");
    }

    @Test
    @DisplayName("POST /people: rejects empty name with 400 Bad Request")
    void testCreatePersonEmptyName() throws Exception {
        String json = "{\"name\": \"   \"}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url("/people")))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(400);

        JsonNode body = mapper.readTree(response.body());
        assertThat(body.has("error")).isTrue();
    }

    @Test
    @DisplayName("POST /knows: creates mutual connection with 204 No Content, idempotent on duplicate")
    void testCreateKnowsAndContacts() throws Exception {
        // Create 2 people
        HttpResponse<String> p1Resp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(url("/people")))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\": \"Asha\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        long id1 = mapper.readTree(p1Resp.body()).get("id").asLong();

        HttpResponse<String> p2Resp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(url("/people")))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\": \"Rohan\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        long id2 = mapper.readTree(p2Resp.body()).get("id").asLong();

        // Connect them
        String knowsJson = String.format("{\"a\": %d, \"b\": %d}", id1, id2);
        HttpRequest knowsReq = HttpRequest.newBuilder()
                .uri(URI.create(url("/knows")))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(knowsJson))
                .build();

        HttpResponse<String> knowsResp = client.send(knowsReq, HttpResponse.BodyHandlers.ofString());
        assertThat(knowsResp.statusCode()).isEqualTo(204);

        // Sending duplicate connection should also return 204
        HttpResponse<String> duplicateResp = client.send(knowsReq, HttpResponse.BodyHandlers.ofString());
        assertThat(duplicateResp.statusCode()).isEqualTo(204);

        // Verify GET /people/{id}/contacts
        HttpRequest contactsReq = HttpRequest.newBuilder()
                .uri(URI.create(url("/people/" + id1 + "/contacts")))
                .GET()
                .build();
        HttpResponse<String> contactsResp = client.send(contactsReq, HttpResponse.BodyHandlers.ofString());
        assertThat(contactsResp.statusCode()).isEqualTo(200);

        JsonNode contacts = mapper.readTree(contactsResp.body());
        assertThat(contacts.isArray()).isTrue();
        assertThat(contacts.size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("POST /knows: returns 400 when a equals b")
    void testKnowsSelfConnection() throws Exception {
        String json = "{\"a\": 1, \"b\": 1}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url("/knows")))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(400);

        JsonNode body = mapper.readTree(response.body());
        assertThat(body.has("error")).isTrue();
    }

    @Test
    @DisplayName("POST /knows: returns 404 with exact error format for unknown person")
    void testKnowsUnknownPerson() throws Exception {
        String json = "{\"a\": 1, \"b\": 99999}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url("/knows")))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(404);

        JsonNode body = mapper.readTree(response.body());
        assertThat(body.get("error").asText()).isEqualTo("no person 99999");
    }

    @Test
    @DisplayName("GET /people/{id}/contacts: returns 404 with exact error format for unknown person")
    void testContactsUnknownPerson() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url("/people/88888/contacts")))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(404);

        JsonNode body = mapper.readTree(response.body());
        assertThat(body.get("error").asText()).isEqualTo("no person 88888");
    }

    @Test
    @DisplayName("GET /path: returns 400 when parameters are missing or malformed")
    void testPathMalformedParams() throws Exception {
        HttpRequest reqMissing = HttpRequest.newBuilder()
                .uri(URI.create(url("/path?from=1")))
                .GET()
                .build();
        HttpResponse<String> respMissing = client.send(reqMissing, HttpResponse.BodyHandlers.ofString());
        assertThat(respMissing.statusCode()).isEqualTo(400);

        HttpRequest reqAlpha = HttpRequest.newBuilder()
                .uri(URI.create(url("/path?from=abc&to=1")))
                .GET()
                .build();
        HttpResponse<String> respAlpha = client.send(reqAlpha, HttpResponse.BodyHandlers.ofString());
        assertThat(respAlpha.statusCode()).isEqualTo(400);
    }
}
