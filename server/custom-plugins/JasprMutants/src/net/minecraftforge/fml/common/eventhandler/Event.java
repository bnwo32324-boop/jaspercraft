package net.minecraftforge.fml.common.eventhandler;

/**
 * JasperCraft port shim of Forge's Event (Forge 1.12.2 API shape). Cancellation and results behave like Forge: only
 * classes annotated {@link Cancelable} may be cancelled, only {@link HasResult} classes carry a result.
 */
public class Event {
    public enum Result { DENY, DEFAULT, ALLOW }

    private boolean isCanceled = false;
    private Result result = Result.DEFAULT;
    private EventPriority phase = null;

    public Event() {
    }

    public boolean isCancelable() {
        for (Class<?> c = this.getClass(); c != null && c != Event.class; c = c.getSuperclass()) {
            if (c.isAnnotationPresent(Cancelable.class)) return true;
        }
        return false;
    }

    public boolean isCanceled() {
        return this.isCanceled;
    }

    public void setCanceled(boolean cancel) {
        if (!this.isCancelable()) {
            throw new UnsupportedOperationException("Attempted to call Event#setCanceled() on a non-cancelable event of type: " + this.getClass().getCanonicalName());
        }
        this.isCanceled = cancel;
    }

    public boolean hasResult() {
        for (Class<?> c = this.getClass(); c != null && c != Event.class; c = c.getSuperclass()) {
            if (c.isAnnotationPresent(HasResult.class)) return true;
        }
        return false;
    }

    public Result getResult() {
        return this.result;
    }

    public void setResult(Result value) {
        this.result = value;
    }

    public EventPriority getPhase() {
        return this.phase;
    }

    public void setPhase(EventPriority value) {
        this.phase = value;
    }
}
