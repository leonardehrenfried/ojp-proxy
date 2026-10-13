package io.leonard.ojp;

import io.micronaut.scheduling.TaskExecutors;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Turns a blocking writer into an {@link InputStream}, which Micronaut sends as a chunked response
 * while the writer is still producing output.
 */
@Singleton
public class XmlStreamer {

    private static final int BUFFER_SIZE = 64 * 1024;

    @FunctionalInterface
    public interface Writer {
        void writeTo(OutputStream out) throws Exception;
    }

    private final ExecutorService executor;

    public XmlStreamer(@Named(TaskExecutors.BLOCKING) ExecutorService executor) {
        this.executor = executor;
    }

    public InputStream stream(Writer writer) throws IOException {
        var failure = new AtomicReference<Throwable>();
        var in = new PipedInputStream(BUFFER_SIZE);
        var out = new PipedOutputStream(in);
        executor.execute(() -> {
            try {
                writer.writeTo(out);
            } catch (Throwable t) {
                // must be recorded before the pipe is closed, so the reader sees it instead of a clean EOF
                failure.set(t);
            } finally {
                try {
                    out.close();
                } catch (IOException ignored) {
                    // reader already gone
                }
            }
        });
        return new FilterInputStream(in) {
            @Override
            public int read() throws IOException {
                return check(super.read());
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                return check(super.read(b, off, len));
            }

            private int check(int result) throws IOException {
                if (failure.get() != null) {
                    throw new IOException("Failed to write response", failure.get());
                }
                return result;
            }
        };
    }
}
