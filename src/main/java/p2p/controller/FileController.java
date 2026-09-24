package p2p.controller;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUpload;
import org.apache.commons.fileupload.UploadContext;
import org.apache.commons.io.IOUtils;
import p2p.service.FileSharer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Controller providing HTTP endpoints for file uploading and downloading.
 */
public class FileController {

    private final FileSharer fileSharer;
    private final HttpServer server;
    private final String uploadDir;
    private final ExecutorService executorService;

    public FileController(int port) throws IOException {
        this.fileSharer = new FileSharer();
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.uploadDir = System.getProperty("java.io.tmpdir") + File.separator + "peerlink-uploads";
        this.executorService = Executors.newFixedThreadPool(10);

        File dir = new File(uploadDir);
        if (!dir.exists() && !dir.mkdirs()) {
            System.err.println("Warning: Could not create upload directory: " + uploadDir);
        }

        server.createContext("/upload", new UploadHandler());
        server.createContext("/download", new DownloadHandler());
        server.createContext("/", new DefaultHandler());

        server.setExecutor(executorService);
    }

    public void start() {
        server.start();
        System.out.println("API server started on port " + server.getAddress().getPort());
    }

    public void stop() {
        server.stop(0);
        fileSharer.stopAll();
        executorService.shutdown();
        System.out.println("API server stopped");
    }

    /**
     * Helper to apply common CORS headers.
     */
    private static void applyCorsHeaders(Headers headers) {
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        headers.set("Access-Control-Expose-Headers", "Content-Disposition, Content-Length");
    }

    /**
     * Handles CORS preflight and unmatched routes.
     */
    private static class DefaultHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            applyCorsHeaders(exchange.getResponseHeaders());

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            byte[] response = "Not Found".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(404, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        }
    }

    /**
     * Handles multipart file uploads by streaming directly to disk without memory buffering.
     */
    private class UploadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Headers headers = exchange.getResponseHeaders();
            applyCorsHeaders(headers);

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\": \"Method Not Allowed\"}");
                return;
            }

            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || !contentType.toLowerCase().startsWith("multipart/")) {
                sendJsonResponse(exchange, 400, "{\"error\": \"Content-Type must be multipart/form-data\"}");
                return;
            }

            try {
                UploadContext context = new HttpExchangeUploadContext(exchange);
                FileUpload upload = new FileUpload();
                FileItemIterator iter = upload.getItemIterator(context);

                File savedFile = null;
                String cleanFileName = "unnamed-file";

                while (iter.hasNext()) {
                    FileItemStream item = iter.next();
                    if (!item.isFormField()) {
                        String originalName = item.getName();
                        if (originalName != null && !originalName.trim().isEmpty()) {
                            cleanFileName = new File(originalName).getName(); // Sanitize against directory traversal
                        }

                        String uniqueFilename = UUID.randomUUID() + "_" + cleanFileName;
                        savedFile = new File(uploadDir, uniqueFilename);

                        try (InputStream in = item.openStream();
                             FileOutputStream out = new FileOutputStream(savedFile)) {
                            IOUtils.copy(in, out);
                        }
                        break;
                    }
                }

                if (savedFile == null || !savedFile.exists()) {
                    sendJsonResponse(exchange, 400, "{\"error\": \"No file uploaded in request\"}");
                    return;
                }

                int port = fileSharer.offerFile(savedFile.getAbsolutePath(), cleanFileName);
                new Thread(() -> fileSharer.startFileServer(port), "FileServer-" + port).start();

                sendJsonResponse(exchange, 200, "{\"port\": " + port + "}");

            } catch (Exception e) {
                System.err.println("Upload processing failed: " + e.getMessage());
                sendJsonResponse(exchange, 500, "{\"error\": \"Failed to upload file: " + e.getMessage() + "\"}");
            }
        }
    }

    /**
     * Streams file directly from the peer socket to the HTTP response.
     */
    private static class DownloadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Headers headers = exchange.getResponseHeaders();
            applyCorsHeaders(headers);

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\": \"Method Not Allowed\"}");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String portStr = path.substring(path.lastIndexOf('/') + 1);

            int port;
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                sendJsonResponse(exchange, 400, "{\"error\": \"Invalid port number\"}");
                return;
            }

            try (Socket socket = new Socket("localhost", port);
                 InputStream socketInput = socket.getInputStream()) {

                // Read socket header lines terminated by an empty line
                ByteArrayOutputStream lineBaos = new ByteArrayOutputStream();
                String filename = "downloaded-file";
                long fileSize = -1;

                int b;
                while ((b = socketInput.read()) != -1) {
                    if (b == '\n') {
                        String line = lineBaos.toString(StandardCharsets.UTF_8).trim();
                        lineBaos.reset();
                        if (line.isEmpty()) {
                            break; // Empty line ends socket headers
                        }
                        if (line.startsWith("Filename: ")) {
                            filename = line.substring("Filename: ".length()).trim();
                        } else if (line.startsWith("Filesize: ")) {
                            try {
                                fileSize = Long.parseLong(line.substring("Filesize: ".length()).trim());
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    } else if (b != '\r') {
                        lineBaos.write(b);
                    }
                }

                headers.set("Content-Type", "application/octet-stream");
                headers.set("Content-Disposition", "attachment; filename=\"" + filename + "\"");

                if (fileSize > 0) {
                    headers.set("Content-Length", String.valueOf(fileSize));
                    exchange.sendResponseHeaders(200, fileSize);
                } else {
                    exchange.sendResponseHeaders(200, 0); // Chunked transfer
                }

                try (OutputStream responseOut = exchange.getResponseBody()) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = socketInput.read(buffer)) != -1) {
                        responseOut.write(buffer, 0, bytesRead);
                    }
                    responseOut.flush();
                }

            } catch (IOException e) {
                System.err.println("Download error from peer port " + port + ": " + e.getMessage());
                try {
                    sendJsonResponse(exchange, 500, "{\"error\": \"Could not download file from peer: " + e.getMessage() + "\"}");
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static void sendJsonResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static class HttpExchangeUploadContext implements UploadContext {
        private final HttpExchange exchange;

        public HttpExchangeUploadContext(HttpExchange exchange) {
            this.exchange = exchange;
        }

        @Override
        public String getCharacterEncoding() {
            return "UTF-8";
        }

        @Override
        public String getContentType() {
            return exchange.getRequestHeaders().getFirst("Content-Type");
        }

        @Override
        public int getContentLength() {
            return (int) contentLength();
        }

        @Override
        public long contentLength() {
            String lengthHeader = exchange.getRequestHeaders().getFirst("Content-Length");
            if (lengthHeader != null) {
                try {
                    return Long.parseLong(lengthHeader.trim());
                } catch (NumberFormatException ignored) {
                }
            }
            return -1;
        }

        @Override
        public InputStream getInputStream() {
            return exchange.getRequestBody();
        }
    }
}
