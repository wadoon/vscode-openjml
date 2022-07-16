package com.github.wadoon.openjmllsp;

import org.eclipse.lsp4j.*;
import org.jmlspecs.annotation.NonNull;
import org.jmlspecs.openjml.Factory;
import org.jmlspecs.openjml.IAPI;
import org.tinylog.Logger;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.CRC32;

/**
 * @author Alexander Weigl
 * @version 1 (15.07.22)
 */
public class OpenJMLDiagnosticHandler {
    private final OpenJMLLanguageServer server;

    private final Map<String, List<Diagnostic<? extends JavaFileObject>>> cache
            = Collections.synchronizedMap(new HashMap<>());
    private final Map<String, Long> cacheHash = Collections.synchronizedMap(new HashMap<>());

    final List<String> knownOpenFiles = Collections.synchronizedList(new ArrayList<>(512));

    public OpenJMLDiagnosticHandler(OpenJMLLanguageServer openJMLLanguageServer) {
        this.server = openJMLLanguageServer;
    }

    public void invalidateAllResults() {
        cacheHash.clear();
        cache.clear();
    }

    public void remove(String uri) {
        invalidate(uri);
    }

    public void invalidate(String uri) {
        cacheHash.remove(uri);
        cache.remove(uri);
    }

    public CompletableFuture<WorkspaceDiagnosticReport> completeDiagnostics() {
        return triggerOpenJmlAsync()
                .thenApply(this::updateCache)
                .thenApply(this::constructWorkspaceDiagnosticReport);
    }

    private List<Diagnostic<? extends JavaFileObject>> updateCache(List<Diagnostic<? extends JavaFileObject>> diagnostics) {
        cache.clear();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
            String uri = diagnostic.getSource().toUri().toString().replace("file:/", "file:///");
            cache.computeIfAbsent(uri, k -> new LinkedList<>()).add(diagnostic);
            cacheHash.put(uri, getHash(uri));
        }
        return diagnostics;
    }

    private long getHash(String uri) {
        try (SeekableByteChannel in =
                     Files.newByteChannel(Paths.get(uri.replaceFirst("file://", "")))) {
            CRC32 crc = new CRC32();
            crc.reset();
            ByteBuffer buffer = ByteBuffer.allocate(4 * 1024);
            int len = 0;
            while ((len = in.read(buffer)) > 0) {
                crc.update(buffer);
            }
            return crc.getValue();
        } catch (IOException e) {
            Logger.error(e);
            return -1;
        }
    }

    private WorkspaceDiagnosticReport constructWorkspaceDiagnosticReport(List<Diagnostic<? extends JavaFileObject>> diagnostics) {
        Map<String, List<Diagnostic<? extends JavaFileObject>>> diagnosticMap = new HashMap<>();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
            String uri = diagnostic.getSource().toUri().toString().replace("file:/", "file:///");
            Logger.info("URI from javacc: {}", uri);
            diagnosticMap.computeIfAbsent(uri, k -> new LinkedList<>()).add(diagnostic);
        }

        List<WorkspaceDocumentDiagnosticReport> files = diagnosticMap.entrySet().stream().map(it -> {
                    List<org.eclipse.lsp4j.Diagnostic> items =
                            it.getValue().stream().map(this::translate).collect(Collectors.toList());
                    Integer version = server.textDocumentService.versionForUri.get(it.getKey());
                    return new WorkspaceDocumentDiagnosticReport(
                            new WorkspaceFullDocumentDiagnosticReport(items, it.getKey(), version));
                })
                .collect(Collectors.toList());
        Logger.info("Report {} files with {} issues", files.size(),
                files.stream().mapToInt(it -> it.getWorkspaceFullDocumentDiagnosticReport().getItems().size())
                        .sum());
        Logger.info(files);
        return new WorkspaceDiagnosticReport(files);
    }

    private org.eclipse.lsp4j.Diagnostic translate(Diagnostic<? extends JavaFileObject> diagnostic) {
        Range range;
        try {
            Logger.info("!!! {}", diagnostic.getSource());
            final int length = 10; // error in openjml: diagnostic.getEndPosition() - diagnostic.getStartPosition()
            final int line = (int) diagnostic.getLineNumber() - 1;
            final int column = (int) diagnostic.getColumnNumber() - 1;
            range = new Range(new Position(line, column), new Position(line, length + column));
        } catch (StackOverflowError e) {
            range = null;
            Logger.error("Stackoverflow caught", e);
        }

        String message = diagnostic.getMessage(Locale.getDefault());
        DiagnosticSeverity severity = null;
        switch (diagnostic.getKind()) {
            case ERROR:
                severity = DiagnosticSeverity.Error;
                break;
            case WARNING:
            case MANDATORY_WARNING:
                severity = DiagnosticSeverity.Warning;
                break;
            case NOTE:
                severity = DiagnosticSeverity.Hint;
                break;
            case OTHER:
                severity = DiagnosticSeverity.Information;
                break;
        }
        return new org.eclipse.lsp4j.Diagnostic(range, message, severity, "openjml");

    }

    public CompletableFuture<DocumentDiagnosticReport> fileDiagnostic(String uri) {
        if (validEntry(uri)) {
            Logger.info("URI {} is valid", uri);
            final List<Diagnostic<? extends JavaFileObject>> diagnostics = cache.get(uri);
            return CompletableFuture.supplyAsync(() -> {
                List<org.eclipse.lsp4j.Diagnostic> items = diagnostics.stream().map(this::translate)
                        .collect(Collectors.toList());
                Logger.info("Issues: {} for {}", items.size(), uri);
                return new DocumentDiagnosticReport(new RelatedFullDocumentDiagnosticReport(items));
            });
        }
        Logger.info("URI {} is not valid", uri);
        return triggerOpenJmlAsync()
                .thenApply(this::updateCache)
                .thenApply(seq -> {
                    List<org.eclipse.lsp4j.Diagnostic> items =
                            seq.stream()
                                    .filter(it -> uri.equals(it.getSource().toUri().toString()))
                                    .map(this::translate)
                                    .collect(Collectors.toList());
                    Logger.info("Issues: {} for {}", items.size(), uri);
                    return new DocumentDiagnosticReport(new RelatedFullDocumentDiagnosticReport(items));
                });
    }

    private boolean validEntry(String uri) {
        synchronized (cacheHash) {
            synchronized (cache) {
                if (cacheHash.containsKey(uri) && cache.containsKey(uri)) {
                    long hash = getHash(uri);
                    return cacheHash.get(uri) == hash;
                }
            }
        }
        return false;
    }

    private CompletableFuture<List<Diagnostic<? extends JavaFileObject>>> triggerOpenJmlAsync() {
        Supplier<List<Diagnostic<? extends JavaFileObject>>> task = () -> {
            CapturingListener listener = new CapturingListener();
            try {
                StringWriter sw = new StringWriter();
                @NonNull IAPI api = Factory.makeAPI(new PrintWriter(sw), listener, null);
                api.parseAndCheck(getJavaFiles());
                api.close();
            } catch (Exception e) {
                Logger.error("Catched", e);
            } catch (StackOverflowError e) {
                Logger.error("Catched!", e);
            }
            return listener.diagnostics;
        };
        return CompletableFuture.supplyAsync(task, ForkJoinPool.commonPool());
    }

    private File[] getJavaFiles() throws IOException {
        for (WorkspaceFolder workspaceFolder : server.workspaceRoot) {
            Logger.info("workspaceRoot: ", workspaceFolder);
        }
        List<Path> javaFiles =
                server.workspaceRoot.stream().flatMap(
                                p -> {
                                    try {
                                        return Files.walk(uri(p.getUri()))
                                                .filter(it -> it.getFileName().toString().endsWith(".java"));
                                    } catch (IOException e) {
                                        return Stream.empty();
                                    }
                                })
                        .collect(Collectors.toList());

        for (Path javaFile : javaFiles) {
            Logger.info("JavaFile: {}", javaFile);
        }

        /*
        List<Path> sourceRoots = new ArrayList<>();
        for (Path javaFile : javaFiles) {
            if (prefixIsIn(sourceRoots, javaFile)) {
                continue;
            }
            sourceRoots.add(getSourceRoot(javaFile));
        }*/

        File[] f = new File[javaFiles.size()];
        for (int i = 0; i < f.length; i++) {
            f[i] = javaFiles.get(i).toFile();
        }
        return f;
    }

    private Path uri(String uri) {
        return Paths.get(uri
                .replaceFirst("file://", "")
                .replaceFirst("file:/", ""));
    }

    private Path getSourceRoot(Path javaFile) throws IOException {
        return Files.readAllLines(javaFile).stream().filter(it -> it.trim().startsWith("package"))
                .findFirst()
                .map(this::countDots)
                .map(it -> goUpwards(javaFile, it))
                .orElse(null);
    }

    private Path goUpwards(Path javaFile, int count) {
        for (int i = 0; i < count; i++) {
            javaFile = javaFile.getParent();
        }
        return javaFile;
    }

    private int countDots(String it) {
        return it.chars().map(c -> c == '.' ? 1 : 0).sum();
    }

    private boolean prefixIsIn(List<Path> sourceRoots, Path javaFile) {
        return sourceRoots.stream().anyMatch(javaFile::startsWith);
    }
}

class CapturingListener implements DiagnosticListener<JavaFileObject> {
    List<Diagnostic<? extends JavaFileObject>> diagnostics = new ArrayList<>(1024);

    @Override
    public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
        diagnostics.add(diagnostic);
    }
}