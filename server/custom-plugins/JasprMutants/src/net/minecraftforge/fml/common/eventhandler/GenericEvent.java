package net.minecraftforge.fml.common.eventhandler;

import java.lang.reflect.Type;

/** JasperCraft port shim of Forge's GenericEvent: listeners declared for Event&lt;X&gt; only receive events of type X. */
public class GenericEvent<T> extends Event {
    private final Class<T> type;

    public GenericEvent(Class<T> type) {
        this.type = type;
    }

    public Type getGenericType() {
        return this.type;
    }
}
