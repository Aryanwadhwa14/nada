package com.nada.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nada.exception.PersonNotFoundException;
import com.nada.model.CreatePersonRequest;
import com.nada.model.ErrorResponse;
import com.nada.model.KnowsRequest;
import com.nada.model.PathResponse;
import com.nada.model.Person;
import com.nada.service.GraphService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ApiServer {

    private static final Pattern CONTACTS_PATTERN = Pattern.compile("^/people/([^/]+)/contacts/?$");

    private final int port;
    private final GraphService graphService;
    private final ObjectMapper objectMapper;
    private HttpServer server;

    public ApiServer(int port, GraphService graphService) {
        this.port = port;
        this.graphService = graphService;
        this.objectMapper = new ObjectMapper();
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newCachedThreadPool());

        server.createContext("/people", new PeopleHandler());
        server.createContext("/knows", new KnowsHandler());
        server.createContext("/path", new PathHandler());

        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    public int getPort() {
        if (server != null) {
            return server.getAddress().getPort();
        }
        return port;
    }

    // Handler for /people and /people/{id}/contacts
    private class PeopleHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String path = exchange.getRequestURI().getPath();
                String method = exchange.getRequestMethod().toUpperCase();

                if ("POST".equals(method) && ("/people".equals(path) || "/people/".equals(path))) {
                    handleCreatePerson(exchange);
                } else if ("GET".equals(method)) {
                    Matcher matcher = CONTACTS_PATTERN.matcher(path);
                    if (matcher.matches()) {
                        String idStr = matcher.group(1);
                        handleGetContacts(exchange, idStr);
                    } else {
                        sendError(exchange, 404, "endpoint not found");
                    }
                } else {
                    sendError(exchange, 405, "method not allowed");
                }
            } catch (Exception e) {
                sendError(exchange, 500, "internal server error: " + e.getMessage());
            }
        }

        private void handleCreatePerson(HttpExchange exchange) throws IOException {
            String body = readRequestBody(exchange);
            if (body == null || body.trim().isEmpty()) {
                sendError(exchange, 400, "request body cannot be empty");
                return;
            }

            JsonNode root;
            try {
                root = objectMapper.readTree(body);
            } catch (JsonProcessingException e) {
                sendError(exchange, 400, "invalid JSON payload");
                return;
            }

            if (!root.has("name") || root.get("name").isNull()) {
                sendError(exchange, 400, "missing required field 'name'");
                return;
            }

            String name = root.get("name").asText();
            if (name.trim().isEmpty()) {
                sendError(exchange, 400, "name must not be empty or blank");
                return;
            }

            try {
                Person person = graphService.createPerson(name);
                sendJson(exchange, 201, person);
            } catch (IllegalArgumentException e) {
                sendError(exchange, 400, e.getMessage());
            }
        }

        private void handleGetContacts(HttpExchange exchange, String idStr) throws IOException {
            long id;
            try {
                id = Long.parseLong(idStr);
            } catch (NumberFormatException e) {
                sendError(exchange, 400, "invalid person id: " + idStr);
                return;
            }

            try {
                List<Person> contacts = graphService.getDirectContacts(id);
                sendJson(exchange, 200, contacts);
            } catch (PersonNotFoundException e) {
                sendError(exchange, 404, e.getMessage());
            }
        }
    }

    // Handler for POST /knows
    private class KnowsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendError(exchange, 405, "method not allowed");
                    return;
                }

                String body = readRequestBody(exchange);
                if (body == null || body.trim().isEmpty()) {
                    sendError(exchange, 400, "request body cannot be empty");
                    return;
                }

                JsonNode root;
                try {
                    root = objectMapper.readTree(body);
                } catch (JsonProcessingException e) {
                    sendError(exchange, 400, "invalid JSON payload");
                    return;
                }

                if (!root.has("a") || !root.has("b")) {
                    sendError(exchange, 400, "missing required body field: 'a' and 'b' are required");
                    return;
                }

                JsonNode aNode = root.get("a");
                JsonNode bNode = root.get("b");

                if (!aNode.isIntegralNumber() || !bNode.isIntegralNumber()) {
                    sendError(exchange, 400, "'a' and 'b' must be integers");
                    return;
                }

                long a = aNode.asLong();
                long b = bNode.asLong();

                if (a == b) {
                    sendError(exchange, 400, "cannot create connection where a equals b");
                    return;
                }

                try {
                    graphService.createConnection(a, b);
                    sendEmpty(exchange, 204);
                } catch (PersonNotFoundException e) {
                    sendError(exchange, 404, e.getMessage());
                } catch (IllegalArgumentException e) {
                    sendError(exchange, 400, e.getMessage());
                }
            } catch (Exception e) {
                sendError(exchange, 500, "internal server error: " + e.getMessage());
            }
        }
    }

    // Handler for GET /path?from=1&to=5
    private class PathHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendError(exchange, 405, "method not allowed");
                    return;
                }

                Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI());
                String fromStr = queryParams.get("from");
                String toStr = queryParams.get("to");

                if (fromStr == null || toStr == null || fromStr.trim().isEmpty() || toStr.trim().isEmpty()) {
                    sendError(exchange, 400, "from and to parameters are required");
                    return;
                }

                long fromId;
                long toId;
                try {
                    fromId = Long.parseLong(fromStr.trim());
                    toId = Long.parseLong(toStr.trim());
                } catch (NumberFormatException e) {
                    sendError(exchange, 400, "from and to parameters must be valid integers");
                    return;
                }

                try {
                    PathResponse response = graphService.findShortestPath(fromId, toId);
                    sendJson(exchange, 200, response);
                } catch (PersonNotFoundException e) {
                    sendError(exchange, 404, e.getMessage());
                } catch (IllegalArgumentException e) {
                    sendError(exchange, 400, e.getMessage());
                }
            } catch (Exception e) {
                sendError(exchange, 500, "internal server error: " + e.getMessage());
            }
        }
    }

    // Utility methods
    private String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void sendJson(HttpExchange exchange, int statusCode, Object body) throws IOException {
        byte[] bytes = objectMapper.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private void sendEmpty(HttpExchange exchange, int statusCode) throws IOException {
        exchange.sendResponseHeaders(statusCode, -1);
        exchange.close();
    }

    private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        sendJson(exchange, statusCode, new ErrorResponse(message));
    }

    private Map<String, String> parseQueryParams(URI uri) {
        Map<String, String> queryMap = new HashMap<>();
        String rawQuery = uri.getRawQuery();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return queryMap;
        }

        for (String param : rawQuery.split("&")) {
            String[] pair = param.split("=", 2);
            if (pair.length == 2) {
                queryMap.put(pair[0], pair[1]);
            } else if (pair.length == 1) {
                queryMap.put(pair[0], "");
            }
        }
        return queryMap;
    }
}
