package io.leonard.ojp;

import io.micronaut.scheduling.TaskExecutors;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;

/**
 * Turns a blocking writer into an {@link InputStream}, which Micronaut sends as a chunked response
 * while the writer is still producing output.
 *
 * <p>This deliberately does not use {@link java.io.PipedInputStream}: it remembers the last reader
 * thread and fails with "Read end dead" once that thread is gone, which happens whenever the response
 * body is read from short-lived (virtual) threads.
 */
@Singleton
public class XmlStreamer {

    private static final int CHUNK_SIZE = 64 * 1024;
    private static final int MAX_QUEUED_CHUNKS = 4;
    private static final byte[] EOF = new byte[0];

    @FunctionalInterface
    public interface Writer {
        void writeTo(OutputStream out) throws Exception;
    }

    private final ExecutorService executor;

    public XmlStreamer(@Named(TaskExecutors.BLOCKING) ExecutorService executor) {
        this.executor = executor;
    }

    public InputStream stream(Writer writer) {
        var pipe = new Pipe();
        executor.execute(() -> {
            try (var out = new BufferedOutputStream(pipe.sink(), CHUNK_SIZE)) {
                writer.writeTo(out);
            } catch (Throwable t) {
                // must be recorded before EOF is queued, so the reader sees it instead of a clean end
                pipe.failure = t;
            } finally {
                pipe.finish();
            }
        });
        return pipe.source();
    }

    /** A bounded hand-over of byte chunks that works regardless of which threads read and write. */
    private static final class Pipe {

        private final BlockingQueue<byte[]> chunks = new ArrayBlockingQueue<>(MAX_QUEUED_CHUNKS);
        private volatile boolean readerClosed;
        private volatile Throwable failure;

        void finish() {
            if (!readerClosed) {
                try {
                    chunks.put(EOF);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        OutputStream sink() {
            return new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    write(new byte[] {(byte) b}, 0, 1);
                }

                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    if (readerClosed) {
                        throw new IOException("Reader closed the stream");
                    }
                    if (len == 0) {
                        return; // an empty chunk would be mistaken for EOF
                    }
                    try {
                        chunks.put(Arrays.copyOfRange(b, off, off + len));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new InterruptedIOException();
                    }
                }
            };
        }

        InputStream source() {
            return new InputStream() {
                private byte[] current;
                private int pos;
                private boolean done;

                @Override
                public int read() throws IOException {
                    var b = new byte[1];
                    return read(b, 0, 1) == -1 ? -1 : b[0] & 0xff;
                }

                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                    if (len == 0) {
                        return 0;
                    }
                    if (!fill()) {
                        return -1;
                    }
                    var n = Math.min(len, current.length - pos);
                    System.arraycopy(current, pos, b, off, n);
                    pos += n;
                    return n;
                }

                private boolean fill() throws IOException {
                    if (done) {
                        return false;
                    }
                    if (current != null && pos < current.length) {
                        return true;
                    }
                    try {
                        current = chunks.take();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new InterruptedIOException();
                    }
                    pos = 0;
                    if (current == EOF) {
                        done = true;
                        if (failure != null) {
                            throw new IOException("Failed to write response", failure);
                        }
                        return false;
                    }
                    return true;
                }

                @Override
                public void close() {
                    readerClosed = true;
                    // unblocks a writer waiting for space; its next write then fails
                    chunks.clear();
                }
            };
        }
    }
}
