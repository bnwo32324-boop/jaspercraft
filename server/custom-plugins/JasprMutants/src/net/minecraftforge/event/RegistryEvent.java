package net.minecraftforge.event;

import com.google.common.collect.ImmutableList;
import java.util.List;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.eventhandler.GenericEvent;
import net.minecraftforge.registries.IForgeRegistry;

/**
 * JasperCraft port shim of Forge's RegistryEvent. chat.jaspr.mutants.JasprMutants posts Register events in Forge's
 * order (items, entities, sound events, then recipes at init) with registries that write into Paper's NMS registries.
 */
public class RegistryEvent<T> extends GenericEvent<T> {
    RegistryEvent(Class<T> clazz) {
        super(clazz);
    }

    public static class Register<T> extends RegistryEvent<T> {
        private final IForgeRegistry<T> registry;
        private final ResourceLocation name;

        public Register(ResourceLocation name, IForgeRegistry<T> registry) {
            super(registry.getRegistrySuperType());
            this.name = name;
            this.registry = registry;
        }

        public IForgeRegistry<T> getRegistry() {
            return this.registry;
        }

        public ResourceLocation getName() {
            return this.name;
        }
    }

    /** Posted only by Forge when a save references unknown registry names; never posted on this server (fixed ids). */
    public static class MissingMappings<T> extends RegistryEvent<T> {
        private final ImmutableList<Mapping<T>> mappings;

        public MissingMappings(Class<T> type, List<Mapping<T>> mappings) {
            super(type);
            this.mappings = ImmutableList.copyOf(mappings);
        }

        public ImmutableList<Mapping<T>> getMappings() {
            return this.mappings;
        }

        public ImmutableList<Mapping<T>> getAllMappings() {
            return this.mappings;
        }

        public enum Action { DEFAULT, IGNORE, WARN, FAIL, REMAP }

        public static class Mapping<T> {
            public final ResourceLocation key;
            public final int id;
            private Action action = Action.DEFAULT;
            private T target;

            public Mapping(ResourceLocation key, int id) {
                this.key = key;
                this.id = id;
            }

            public void ignore() {
                this.action = Action.IGNORE;
            }

            public void warn() {
                this.action = Action.WARN;
            }

            public void fail() {
                this.action = Action.FAIL;
            }

            public void remap(T target) {
                this.action = Action.REMAP;
                this.target = target;
            }

            public Action getAction() {
                return this.action;
            }

            public T getTarget() {
                return this.target;
            }
        }
    }
}
