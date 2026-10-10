package com.nada;

import com.nada.repository.GraphRepository;
import com.nada.repository.InMemoryGraphRepository;
import com.nada.server.ApiServer;
import com.nada.service.GraphService;
import java.io.IOException;

public class Main {

    public static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();

        int port = DEFAULT_PORT;
        String portEnv = System.getenv("PORT");
        if (portEnv != null && !portEnv.trim().isEmpty()) {
            try {
                port = Integer.parseInt(portEnv.trim());
            } catch (NumberFormatException e) {
                System.err.println("Invalid PORT environment variable. Falling back to " + DEFAULT_PORT);
            }
        }

        GraphRepository repository = new InMemoryGraphRepository();
        GraphService service = new GraphService(repository);
        ApiServer server = new ApiServer(port, service);

        try {
            server.start();
            long elapsed = System.currentTimeMillis() - startTime;
            System.out.println("====================================================");
            System.out.println("  Nada Network - People & Connections API Server");
            System.out.println("  Listening on http://localhost:" + server.getPort());
            System.out.println("  Server booted in " + elapsed + " ms");
            System.out.println("====================================================");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down Nada API server...");
                server.stop();
            }));
        } catch (IOException e) {
            System.err.println("Failed to start server on port " + port + ": " + e.getMessage());
            System.exit(1);
        }
    }
}
