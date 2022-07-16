package com.github.wadoon.openjmllsp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.tinylog.Logger;

public class App {
    public static void main(String[] args) throws Exception {
        Logger.info("OpenJML version: {}", OpenJMLLanguageServer.getOpenJMLVersion());

        if (args.length >= 2 && args[0].equals("--client")) {
            int port = Integer.parseInt(args[1]);
            Logger.info("openjml-lsp started in client mode. Connection to port {} will be established", port);
            connectToServer(port);
        } else if (args.length >= 2 && args[0].equals("--server")) {
            int port = Integer.parseInt(args[1]);
            Logger.info("openjml-lsp started in server mode and awaits connections at port {}", port);
            startServer(port);
        } else {
            System.err.println("openjml-lsp started in local mode");
            startLspServer(System.in, System.out);
        }
    }

    private static void startServer(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket socket = serverSocket.accept();
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                startLspServer(in, out);
            }
        } catch (IOException e) {
            Logger.error(e);
        }
    }


    private static void connectToServer(int port) {
        try {
            Socket socket = new Socket("localhost", port);
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    socket.close();
                } catch (IOException ignore) {
                }
            }));
            startLspServer(in, out);
        } catch (IOException e) {
            Logger.error("IO exception in client mode", e);
        }
    }

    private static void startLspServer(InputStream in, OutputStream out) {
        OpenJMLLanguageServer server = new OpenJMLLanguageServer();
        Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(server, in, out);
        LanguageClient client = launcher.getRemoteProxy();
        server.connect(client);
        launcher.startListening();
    }
}
