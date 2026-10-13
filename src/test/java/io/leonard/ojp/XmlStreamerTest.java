package io.leonard.ojp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class XmlStreamerTest {

    final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    final XmlStreamer streamer = new XmlStreamer(executor);

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    @Test
    void streamsWrittenBytes() throws Exception {
        var big = "x".repeat(1_000_000); // larger than the pipe buffer
        try (var in = streamer.stream(out -> out.write(big.getBytes(StandardCharsets.UTF_8)))) {
            assertEquals(big, new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void propagatesWriterFailure() throws Exception {
        try (var in = streamer.stream(out -> {
            out.write("<partial>".getBytes(StandardCharsets.UTF_8));
            throw new IllegalStateException("boom");
        })) {
            var e = assertThrows(IOException.class, in::readAllBytes);
            assertEquals("boom", e.getCause().getMessage());
        }
    }
}
