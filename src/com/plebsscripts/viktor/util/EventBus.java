package com.plebsscripts.viktor.util;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EventBus {
    private static final EventBus INSTANCE = new EventBus();

    private final Map<Class<?>, List<EventHandler<?>>> handlers = new ConcurrentHashMap<>();

    public static EventBus instance() {
        return INSTANCE;
    }

    public <T> void subscribe(Class<T> eventType, EventHandler<T> handler) {
        handlers.computeIfAbsent(eventType, k -> new ArrayList<>()).add(handler);
    }

    public <T> void publish(T event) {
        List<EventHandler<?>> eventHandlers = handlers.get(event.getClass());
        if (eventHandlers != null) {
            for (EventHandler handler : eventHandlers) {
                handler.handle(event);
            }
        }
    }

    @FunctionalInterface
    public interface EventHandler<T> {
        void handle(T event);
    }
}
