package p2p;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import p2p.service.FileSharer;
import p2p.utils.UploadUtils;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class AppTest {

    private FileSharer fileSharer;
    private Path tempFile;

    @BeforeEach
    void setUp() throws IOException {
        fileSharer = new FileSharer(30);
        tempFile = Files.createTempFile("peerlink-test-", ".txt");
        Files.writeString(tempFile, "PeerLink test content 12345");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (fileSharer != null) {
            fileSharer.stopAll();
        }
        if (tempFile != null) {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void testPortGeneration() {
        int port = UploadUtils.generateCode();
        assertTrue(port >= 1024 && port <= 65535, "Generated port should be in valid range: " + port);
    }

    @Test
    void testFileOfferAndTransfer() throws Exception {
        int port = fileSharer.offerFile(tempFile.toAbsolutePath().toString());
        assertTrue(port > 0);
        assertNotNull(fileSharer.getMetadata(port));
        assertEquals(tempFile.getFileName().toString(), fileSharer.getMetadata(port).fileName());

        // Start server in background thread
        Thread serverThread = new Thread(() -> fileSharer.startFileServer(port));
        serverThread.setDaemon(true);
        serverThread.start();

        // Give the server a moment to bind
        Thread.sleep(150);

        // Connect as a peer client and verify stream with multi-line headers
        try (Socket socket = new Socket("localhost", port);
             InputStream in = socket.getInputStream()) {

            ByteArrayOutputStream lineBaos = new ByteArrayOutputStream();
            String filename = null;
            long fileSize = -1;

            int b;
            while ((b = in.read()) != -1) {
                if (b == '\n') {
                    String line = lineBaos.toString(StandardCharsets.UTF_8).trim();
                    lineBaos.reset();
                    if (line.isEmpty()) {
                        break; // End of headers
                    }
                    if (line.startsWith("Filename: ")) {
                        filename = line.substring("Filename: ".length()).trim();
                    } else if (line.startsWith("Filesize: ")) {
                        fileSize = Long.parseLong(line.substring("Filesize: ".length()).trim());
                    }
                } else if (b != '\r') {
                    lineBaos.write(b);
                }
            }

            assertEquals(tempFile.getFileName().toString(), filename);
            assertEquals(Files.size(tempFile), fileSize);

            ByteArrayOutputStream contentBaos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int n;
            while ((n = in.read(buf)) != -1) {
                contentBaos.write(buf, 0, n);
            }

            String downloadedContent = contentBaos.toString(StandardCharsets.UTF_8);
            assertEquals("PeerLink test content 12345", downloadedContent);
        }
    }

    @Test
    void testRevokeShareCleanup() throws IOException {
        Path toDelete = Files.createTempFile("peerlink-cleanup-", ".tmp");
        int port = fileSharer.offerFile(toDelete.toAbsolutePath().toString());

        fileSharer.revokeShare(port, true);
        assertNull(fileSharer.getMetadata(port));
        assertFalse(Files.exists(toDelete), "File should be deleted on disk after revocation");
    }
}
