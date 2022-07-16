package com.github.wadoon.openjmllsp;

import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.jmlspecs.annotation.NonNull;
import org.jmlspecs.openjml.Factory;
import org.jmlspecs.openjml.IAPI;
import org.tinylog.Logger;

import java.util.List;
import java.util.concurrent.CompletableFuture;

class OpenJMLLanguageServer implements LanguageServer, LanguageClientAware {

    OpenJMLDiagnosticHandler diagnosticHandler = new OpenJMLDiagnosticHandler(this);

    LanguageClient client = null;

    List<WorkspaceFolder> workspaceRoot = null;

    OpenJmlTextDocumentService textDocumentService = new OpenJmlTextDocumentService(this);
    private WorkspaceService workspaceService = new OpenJmlWorkspaceService(this);

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        workspaceRoot = params.getWorkspaceFolders();

        ServerCapabilities capabilities = new ServerCapabilities();
        capabilities.setTextDocumentSync(TextDocumentSyncKind.Full);
        capabilities.setCodeActionProvider(true);
        capabilities.setDiagnosticProvider(new DiagnosticRegistrationOptions(true, true));

        try {
            Logger.info("Send greetings!");
            String version = getOpenJMLVersion();
            client.logMessage(new MessageParams(MessageType.Info, "OpenJml " + version));
        } catch (Exception e) {
            client.logMessage(new MessageParams(MessageType.Error,
                    "Error happened during loading OpenJml " + e.getMessage()));
        }

        return CompletableFuture.completedFuture(new InitializeResult(capabilities));
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        Logger.info("OpenJMLLanguageServer.shutdown");
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {
        Logger.info("OpenJMLLanguageServer.exit");
        System.exit(0);
    }

    @Override
    public void connect(LanguageClient client) {
        Logger.info("OpenJMLLanguageServer.connect");
        this.client = client;
    }

    public static @NonNull String getOpenJMLVersion() throws Exception {
        @NonNull IAPI api = Factory.makeAPI();
        return api.version();
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return textDocumentService;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return workspaceService;
    }
}