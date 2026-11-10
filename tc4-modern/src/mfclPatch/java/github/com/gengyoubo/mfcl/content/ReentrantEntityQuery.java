package github.com.gengyoubo.mfcl.content;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** Breaks cycles when host entity properties query a legacy block's collision shape. */
final class ReentrantEntityQuery {
    private final ThreadLocal<Set<Object>> active = ThreadLocal.withInitial(
            () -> Collections.newSetFromMap(new IdentityHashMap<>()));

    boolean get(Object entity, BooleanSupplier query) {
        Set<Object> entities = active.get();
        // A nested snapshot cannot safely determine pushability until the outer query returns.
        if (!entities.add(entity)) return false;
        try {
            return query.getAsBoolean();
        } finally {
            entities.remove(entity);
            if (entities.isEmpty()) active.remove();
        }
    }
}
