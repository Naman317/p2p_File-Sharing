package p2p.utils;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Utility class for port generation and network checks.
 */
public final class UploadUtils {

    private static final int MIN_DYNAMIC_PORT = 49152;
    private static final int MAX_DYNAMIC_PORT = 65535;
    private static final int MAX_ATTEMPTS = 50;

    private UploadUtils() {
        // Utility class; prevent instantiation
    }

    /**
     * Checks if a port is available for binding.
     */
    public static boolean isPortAvailable(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setReuseAddress(true);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    /**
     * Generates a verified open port number in the dynamic range (49152 - 65535).
     * If all random attempts collide, falls back to an OS-allocated available port.
     */
    public static int generateCode() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            int port = ThreadLocalRandom.current().nextInt(MIN_DYNAMIC_PORT, MAX_DYNAMIC_PORT + 1);
            if (isPortAvailable(port)) {
                return port;
            }
        }

        // Fallback: let the OS select an available ephemeral port
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Could not find an available port for file sharing", e);
        }
    }
}