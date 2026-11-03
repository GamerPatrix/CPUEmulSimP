package sk.uniza.fri.cp.App;

import javafx.application.Platform;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Odovzdanie súborov už bežiacej inštancii programu. Pri dvojkliku na .asm/.schx súbor
 * sa súbor otvorí v bežiacom programe (program sa s ním spojí cez lokálny socket) - inak
 * by každý dvojklik spustil novú inštanciu a program a schéma by skončili v rôznych oknách.
 * Port bežiacej inštancie sa ukladá do Preferences užívateľa.
 */
final class SingleInstance {

    private static final Preferences PREFS = Preferences.userNodeForPackage(SingleInstance.class);
    private static final String KEY_PORT = "instancePort";
    private static final String HELLO = "CPUEmulSim-open";
    private static final String REPLY_OK = "OK";
    private static final int CONNECT_TIMEOUT_MS = 500;
    private static final int READ_TIMEOUT_MS = 3000;

    private SingleInstance() {
    }

    /**
     * Pokus o odovzdanie súborov bežiacej inštancii.
     *
     * @param files Súbory na otvorenie
     * @return true ak súbory prevzala bežiaca inštancia (táto sa má ukončiť), false ak žiadna nebeží
     */
    static boolean sendToRunningInstance(List<File> files) {
        int port = PREFS.getInt(KEY_PORT, -1);
        if (port <= 0) return false;

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);

            Writer out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
            out.write(HELLO + "\n");
            for (File file : files) out.write(file.getAbsolutePath() + "\n");
            out.flush();
            socket.shutdownOutput();

            // odpoveď overí, že na porte naozaj počúva CPUEmulSim (port mohol medzitým dostať iný program)
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            return REPLY_OK.equals(in.readLine());
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Spustenie počúvania na súbory od ďalších spustení programu.
     *
     * @param opener Otvorenie súboru - volá sa na FX vlákne
     */
    static void listen(Consumer<File> opener) {
        ServerSocket server;
        try {
            server = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
        } catch (IOException e) {
            return; // bez počúvania - každé spustenie bude samostatné ako doteraz
        }
        PREFS.putInt(KEY_PORT, server.getLocalPort());
        try {
            // okamžitý zápis - inak sa Preferences zapíšu až o ~30 s a ďalšie spustenie by port nevidelo
            PREFS.flush();
        } catch (BackingStoreException ignored) {
        }

        Thread thread = new Thread(() -> {
            while (true) {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(READ_TIMEOUT_MS);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    if (!HELLO.equals(in.readLine())) continue;

                    List<File> files = new ArrayList<>();
                    String line;
                    while ((line = in.readLine()) != null) {
                        if (!line.isEmpty()) files.add(new File(line));
                    }

                    Writer out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
                    out.write(REPLY_OK + "\n");
                    out.flush();

                    Platform.runLater(() -> files.forEach(opener));
                } catch (IOException ignored) {
                    // chybné spojenie - čaká sa na ďalšie
                }
            }
        }, "SingleInstance");
        thread.setDaemon(true);
        thread.start();
    }
}
