package net.minecraftforge.fml.common.eventhandler;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * JasperCraft port shim of Forge's EventBus. Same contract as Forge 1.12.2: register(Class) subscribes the static
 * {@link SubscribeEvent} methods, register(Object) the instance ones; post() calls listeners in priority order
 * (registration order within a priority), skips cancelled events unless receiveCanceled, honours generic event types
 * (RegistryEvent.Register&lt;Item&gt;) and returns whether the event was cancelled. Listener exceptions propagate like Forge.
 */
public class EventBus {
    private static final class Listener {
        final Object owner;
        final Class<?> eventType;
        final Type genericArgument;
        final EventPriority priority;
        final boolean receiveCanceled;
        final MethodHandle handle;
        final int order;

        Listener(Object owner, Class<?> eventType, Type genericArgument, EventPriority priority, boolean receiveCanceled, MethodHandle handle, int order) {
            this.owner = owner;
            this.eventType = eventType;
            this.genericArgument = genericArgument;
            this.priority = priority;
            this.receiveCanceled = receiveCanceled;
            this.handle = handle;
            this.order = order;
        }
    }

    private final List<Listener> listeners = new CopyOnWriteArrayList<Listener>();
    private final Map<Class<?>, List<Listener>> byEventClass = new ConcurrentHashMap<Class<?>, List<Listener>>();
    private final List<Object> registered = new CopyOnWriteArrayList<Object>();
    private int order;

    public synchronized void register(Object target) {
        if (this.registered.contains(target)) return;
        this.registered.add(target);
        boolean isClass = target instanceof Class;
        Class<?> clazz = isClass ? (Class<?>) target : target.getClass();
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        for (Method method : clazz.getMethods()) {
            SubscribeEvent sub = method.getAnnotation(SubscribeEvent.class);
            if (sub == null) continue;
            boolean isStatic = Modifier.isStatic(method.getModifiers());
            if (isClass != isStatic) continue;
            Class<?>[] params = method.getParameterTypes();
            if (params.length != 1 || !Event.class.isAssignableFrom(params[0])) {
                throw new IllegalArgumentException("Method " + method + " has @SubscribeEvent annotation but requires " + params.length + " arguments. Event handler methods must require a single argument of an Event subclass.");
            }
            Type generic = null;
            if (GenericEvent.class.isAssignableFrom(params[0])) {
                Type t = method.getGenericParameterTypes()[0];
                if (t instanceof ParameterizedType) {
                    Type arg = ((ParameterizedType) t).getActualTypeArguments()[0];
                    if (arg instanceof ParameterizedType) arg = ((ParameterizedType) arg).getRawType();
                    generic = arg;
                }
            }
            try {
                method.setAccessible(true);
                MethodHandle mh = lookup.unreflect(method);
                if (!isStatic) mh = mh.bindTo(target);
                this.listeners.add(new Listener(target, params[0], generic, sub.priority(), sub.receiveCanceled(), mh, this.order++));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot subscribe " + method, e);
            }
        }
        this.byEventClass.clear();
    }

    public synchronized void unregister(Object target) {
        this.registered.remove(target);
        List<Listener> keep = new ArrayList<Listener>();
        for (Listener l : this.listeners) if (l.owner != target) keep.add(l);
        this.listeners.clear();
        this.listeners.addAll(keep);
        this.byEventClass.clear();
    }

    private List<Listener> listenersFor(Class<?> eventClass) {
        List<Listener> list = this.byEventClass.get(eventClass);
        if (list == null) {
            List<Listener> found = new ArrayList<Listener>();
            for (Listener l : this.listeners) if (l.eventType.isAssignableFrom(eventClass)) found.add(l);
            Collections.sort(found, (a, b) -> a.priority != b.priority ? a.priority.compareTo(b.priority) : Integer.compare(a.order, b.order));
            list = Collections.unmodifiableList(found);
            this.byEventClass.put(eventClass, list);
        }
        return list;
    }

    public boolean post(Event event) {
        List<Listener> list = this.listenersFor(event.getClass());
        Type generic = event instanceof GenericEvent ? ((GenericEvent<?>) event).getGenericType() : null;
        for (Listener l : list) {
            if (l.genericArgument != null && generic != null && !l.genericArgument.equals(generic)) continue;
            if (event.isCanceled() && !l.receiveCanceled) continue;
            event.setPhase(l.priority);
            try {
                l.handle.invoke(event);
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }
        return event.isCancelable() && event.isCanceled();
    }
}
