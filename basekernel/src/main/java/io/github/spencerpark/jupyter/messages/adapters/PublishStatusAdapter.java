package io.github.spencerpark.jupyter.messages.adapters;

import com.google.gson.*;
import io.github.spencerpark.jupyter.messages.publish.PublishStatus;

import java.lang.reflect.Type;

public class PublishStatusAdapter implements JsonDeserializer<PublishStatus> {
    public static final PublishStatusAdapter INSTANCE = new PublishStatusAdapter();

    private PublishStatusAdapter() { }

    @Override
    public PublishStatus deserialize(JsonElement element, Type type, JsonDeserializationContext ctx) throws JsonParseException {
        if (element == null || !element.isJsonObject())
            return null;

        JsonObject object = element.getAsJsonObject();
        JsonElement stateElement = object.get("execution_state");
        if (stateElement == null || stateElement.isJsonNull())
            return null;

        PublishStatus.State state;
        try {
            state = ctx.deserialize(stateElement, PublishStatus.State.class);
        } catch (JsonParseException | IllegalArgumentException e) {
            return null;
        }
        if (state == null)
            return null;
        return PublishStatus.forState(state);
    }
}
