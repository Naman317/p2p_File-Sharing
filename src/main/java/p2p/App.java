package p2p;

import p2p.controller.FileController;
import java.io.IOException;

/**
 * PeerLink - P2P File Sharing Application
 */
public class App {
    public static void main(String[] args) {
        try {

            // Start the API server on port 8081
            int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8081"));
            FileController fileController = new FileController(port);

            fileController.start();

            System.out.println("PeerLink server started on port 8081");
            System.out.println("UI available at http://localhost:3000");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down server...");
                fileController.stop();
            }));

            System.out.println("Server running. Press Ctrl+C or terminate process to stop.");
            try {
                Thread.currentThread().join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } catch (IOException e) {
            System.err.println("Error starting server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
