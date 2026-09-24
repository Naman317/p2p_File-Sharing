package p2p.service;

import p2p.utils.UploadUtils;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Manages socket-based P2P file servers with automatic TTL lifecycle cleanup.
 */
public class FileSharer {

    private static final long DEFAULT_TTL_MINUTES = 30;

    private final long ttlMinutes;
    private final Map<Integer, ShareMetadata> availableFiles = new ConcurrentHashMap<>();
    private final Map<Integer, ServerSocket> activeServers = new ConcurrentHashMap<>();
    private final ExecutorService transferPool = Executors.newCachedThreadPool();
    private final ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean running = true;

    public FileSharer() {
        this(readTtlFromEnv());
    }

    public FileSharer(long ttlMinutes) {
        this.ttlMinutes = ttlMinutes;
    }

    private static long readTtlFromEnv() {
        String env = System.getenv("FILE_TTL_MINUTES");
        if (env != null) {
            try {
                return Long.parseLong(env.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return DEFAULT_TTL_MINUTES;
    }

    /**
     * Offers a file for sharing and schedules its automatic TTL expiration.
     *
     * @param filePath Absolute path of the file
     * @return Assigned port number (invite code)
     */
    public int offerFile(String filePath) {
        return offerFile(filePath, new File(filePath).getName());
    }

    /**
     * Offers a file for sharing with a custom display name.
     *
     * @param filePath Absolute path of the file on disk
     * @param displayName Clean filename presented to downloaders
     * @return Assigned port number (invite code)
     */
    public int offerFile(String filePath, String displayName) {
        File file = new File(filePath);
        ShareMetadata metadata = new ShareMetadata(filePath, displayName, file.length());

        int port;
        while (true) {
            port = UploadUtils.generateCode();
            if (availableFiles.putIfAbsent(port, metadata) == null) {
                scheduleExpiration(port, ttlMinutes);
                return port;
            }
        }
    }

    private void scheduleExpiration(int port, long minutes) {
        cleanupScheduler.schedule(() -> {
            if (running) {
                System.out.println("TTL expired for port " + port + ". Cleaning up resources...");
                revokeShare(port, true);
            }
        }, minutes, TimeUnit.MINUTES);
    }

    /**
     * Revokes an active share, closes the socket server, and optionally deletes the file.
     */
    public void revokeShare(int port, boolean deleteFileFromDisk) {
        ServerSocket serverSocket = activeServers.remove(port);
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }

        ShareMetadata metadata = availableFiles.remove(port);
        if (metadata != null && deleteFileFromDisk) {
            File file = new File(metadata.filePath());
            if (file.exists() && file.delete()) {
                System.out.println("Cleaned up expired file from disk: " + metadata.filePath());
            }
        }
    }

    /**
     * Starts listening for peer download connections on the specified port.
     */
    public void startFileServer(int port) {
        ShareMetadata metadata = availableFiles.get(port);
        if (metadata == null) {
            System.err.println("No file associated with port: " + port);
            return;
        }

        File file = new File(metadata.filePath());
        if (!file.exists()) {
            System.err.println("File not found on disk: " + metadata.filePath());
            return;
        }

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setReuseAddress(true);
            activeServers.put(port, serverSocket);
            System.out.println("Serving '" + metadata.fileName() + "' (" + metadata.fileSize() + " bytes) on port " + port + " [TTL: " + ttlMinutes + "m]");

            while (running && !serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("Peer connected from " + clientSocket.getRemoteSocketAddress() + " for port " + port);
                    transferPool.submit(new FileSenderHandler(clientSocket, metadata));
                } catch (SocketException e) {
                    break; // Server socket closed
                }
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("Error running file server on port " + port + ": " + e.getMessage());
            }
        } finally {
            activeServers.remove(port);
        }
    }

    /**
     * Shuts down all active file servers, transfer threads, and cleanup schedulers.
     */
    public void stopAll() {
        running = false;
        cleanupScheduler.shutdownNow();

        for (ServerSocket socket : activeServers.values()) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
        activeServers.clear();
        availableFiles.clear();
        transferPool.shutdownNow();
    }

    public ShareMetadata getMetadata(int port) {
        return availableFiles.get(port);
    }

    public record ShareMetadata(String filePath, String fileName, long fileSize, long createdAt) {
        public ShareMetadata(String filePath, String fileName, long fileSize) {
            this(filePath, fileName, fileSize, System.currentTimeMillis());
        }
    }

    /**
     * Worker task to stream file content over a peer socket connection.
     */
    private static class FileSenderHandler implements Runnable {
        private final Socket clientSocket;
        private final ShareMetadata metadata;

        public FileSenderHandler(Socket clientSocket, ShareMetadata metadata) {
            this.clientSocket = clientSocket;
            this.metadata = metadata;
        }

        @Override
        public void run() {
            File file = new File(metadata.filePath());
            try (clientSocket;
                 FileInputStream fis = new FileInputStream(file);
                 OutputStream out = clientSocket.getOutputStream()) {

                // Send filename and filesize headers, terminated by an empty line
                String headers = "Filename: " + metadata.fileName() + "\n" +
                                 "Filesize: " + metadata.fileSize() + "\n\n";
                out.write(headers.getBytes(StandardCharsets.UTF_8));
                out.flush();

                // Stream the file content
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                out.flush();

                System.out.println("File '" + metadata.fileName() + "' successfully sent to " + clientSocket.getRemoteSocketAddress());
            } catch (IOException e) {
                System.err.println("File transfer interrupted for " + clientSocket.getRemoteSocketAddress() + ": " + e.getMessage());
            }
        }
    }
}
