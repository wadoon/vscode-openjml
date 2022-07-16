package com.github.wadoon.openjmllsp;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.tinylog.Logger;

import java.util.concurrent.CompletableFuture;

/**
 * @author Alexander Weigl
 * @version 1 (15.07.22)
 */
public class OpenJmlWorkspaceService implements WorkspaceService {
    private final OpenJMLLanguageServer server;

    public OpenJmlWorkspaceService(OpenJMLLanguageServer server) {
        this.server = server;
    }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams params) {
        Logger.info("OpenJmlWorkspaceService.didChangeConfiguration");
        server.diagnosticHandler.invalidateAllResults();
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
        Logger.info("OpenJmlWorkspaceService.didChangeWatchedFiles");
        for (FileEvent change : params.getChanges()) {
            switch (change.getType()) {
                case Changed:
                    server.diagnosticHandler.invalidate(change.getUri());
                    break;
                case Deleted:
                    server.diagnosticHandler.remove(change.getUri());
                    break;
                case Created:
                    break;
            }
        }
    }

    @Override
    public CompletableFuture<WorkspaceDiagnosticReport> diagnostic(WorkspaceDiagnosticParams params) {
        Logger.info(params);
        return server.diagnosticHandler.completeDiagnostics();
    }
}
