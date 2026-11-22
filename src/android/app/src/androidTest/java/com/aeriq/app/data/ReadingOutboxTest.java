package com.aeriq.app.data;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.util.UUID;
import static org.junit.Assert.*;

public final class ReadingOutboxTest {
    @Test public void persistsAcrossReopenAndKeepsAccountsIsolated() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name = "test-outbox-" + UUID.randomUUID() + ".db";
        ReadingOutbox queue = new ReadingOutbox(context, name);
        try {
            queue.add(7, "event-a", "{\"eventId\":\"event-a\",\"assignmentId\":12}");
            queue.add(8, "event-b", "{\"eventId\":\"event-b\"}");
            queue.close();
            queue = new ReadingOutbox(context, name);
            assertEquals(1, queue.next(7).size());
            assertEquals("event-a", queue.next(7).get(0).event);
            assertTrue(queue.next(7).get(0).payload.contains("\"assignmentId\":12"));
            queue.acknowledge(8, "event-a");
            assertEquals(1, queue.count(7, false));
            queue.block(7, "event-a", "HTTP 409: asignación retirada");
            assertTrue(queue.next(7).isEmpty());
            assertEquals(1, queue.count(7, true));
            assertEquals(1, queue.next(8).size());
            queue.retry(8);
            assertEquals(1, queue.count(7, true));
            queue.retry(7);
            assertEquals("event-a", queue.next(7).get(0).event);
            queue.acknowledge(7, "event-a");
            assertTrue(queue.next(7).isEmpty());
            assertEquals(1, queue.count(8, false));
        } finally { queue.close(); context.deleteDatabase(name); }
    }
}
