package com.github.wadoon.openjmllsp;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.tinylog.Logger;

import java.util.*;
import java.util.concurrent.CompletableFuture;

class OpenJmlTextDocumentService implements TextDocumentService {
    private final OpenJMLLanguageServer server;
    Map<String, Integer> versionForUri = Collections.synchronizedMap(new TreeMap<>());

    public OpenJmlTextDocumentService(OpenJMLLanguageServer openJMLLanguageServer) {
        this.server = openJMLLanguageServer;
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        Logger.info(params);
        versionForUri.put(params.getTextDocument().getUri(), params.getTextDocument().getVersion());
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        Logger.info(params);
        server.diagnosticHandler.invalidate(params.getTextDocument().getUri());
        versionForUri.put(params.getTextDocument().getUri(), params.getTextDocument().getVersion());
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        Logger.info(params);
        versionForUri.remove(params.getTextDocument().getUri());
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        Logger.info(params);
    }

    @Override
    public CompletableFuture<DocumentDiagnosticReport> diagnostic(DocumentDiagnosticParams params) {
        Logger.info(params);
        return server.diagnosticHandler.fileDiagnostic(params.getTextDocument().getUri());
    }

    @Override
    public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
        Logger.info(params);
        return CompletableFuture.completedFuture(new ArrayList<>());
    }

    @Override
    public CompletableFuture<CodeAction> resolveCodeAction(CodeAction unresolved) {
        Logger.info(unresolved);
        return CompletableFuture.completedFuture(new CodeAction("Do something"));
    }
}
