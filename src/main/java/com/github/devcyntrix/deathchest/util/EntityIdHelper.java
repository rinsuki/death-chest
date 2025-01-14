package com.github.devcyntrix.deathchest.util;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

public final class EntityIdHelper {

    private static Class<?> entityClass = null;
    private static final Field entityCounter;

    static {
        try {
            entityClass = Class.forName("net.minecraft.world.entity.Entity");
        } catch (ClassNotFoundException ignored) {
        }
        entityCounter = Arrays.stream(entityClass.getDeclaredFields())
                .filter(field -> field.getType().equals(AtomicInteger.class))
                .findFirst()
                .orElse(null);
    }

    public static int increaseAndGet() {
        if (entityClass == null)
            return -1;
        try {
            if (entityCounter == null) {
                return -1;
            }
            if (!entityCounter.trySetAccessible())
                return -1;
            AtomicInteger integer = (AtomicInteger) entityCounter.get(null);
            return integer.incrementAndGet();
        } catch (IllegalAccessException ignored) {
        }
        return -1;
    }

}
