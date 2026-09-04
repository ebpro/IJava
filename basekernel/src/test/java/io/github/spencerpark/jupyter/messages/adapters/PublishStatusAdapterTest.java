package io.github.spencerpark.jupyter.messages.adapters;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.spencerpark.jupyter.messages.publish.PublishStatus;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PublishStatusAdapterTest {

    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(PublishStatus.class, PublishStatusAdapter.INSTANCE)
            .create();

    @Test
    public void deserializesBusyState() {
        PublishStatus status = gson.fromJson("{\"execution_state\":\"busy\"}", PublishStatus.class);
        assertEquals(PublishStatus.BUSY, status);
    }

    @Test
    public void deserializesIdleState() {
        PublishStatus status = gson.fromJson("{\"execution_state\":\"idle\"}", PublishStatus.class);
        assertEquals(PublishStatus.IDLE, status);
    }

    @Test
    public void deserializesStartingState() {
        PublishStatus status = gson.fromJson("{\"execution_state\":\"starting\"}", PublishStatus.class);
        assertEquals(PublishStatus.STARTING, status);
    }

    @Test
    public void ignoresLegacyExecutionResultKey() {
        PublishStatus status = gson.fromJson("{\"execution_result\":\"busy\"}", PublishStatus.class);
        assertNull(status);
    }

    @Test
    public void missingExecutionStateReturnsNull() {
        PublishStatus status = gson.fromJson("{}", PublishStatus.class);
        assertNull(status);
    }

    @Test
    public void unknownExecutionStateReturnsNull() {
        PublishStatus status = gson.fromJson("{\"execution_state\":\"bogus\"}", PublishStatus.class);
        assertNull(status);
    }
}
