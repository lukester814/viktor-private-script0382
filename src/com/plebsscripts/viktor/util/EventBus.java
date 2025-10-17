package com.plebsscripts.viktor.util;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EventBus {
    private static final EventBus INSTANCE = new EventBus();

    private final Map<Class<?>, List<EventHandler<?>>> handlers = new ConcurrentHashMap<Class<?>, List<EventHandler<?>>>();

    public static EventBus instance() {
        return INSTANCE;
    }

    public <T> void subscribe(Class<T> eventType, EventHandler<T> handler) {
        List<EventHandler<?>> list = handlers.get(eventType);
        if (list == null) {
            list = new ArrayList<EventHandler<?>>();
            handlers.put(eventType, list);
        }
        list.add(handler);
    }

    @SuppressWarnings("unchecked")
    public <T> void publish(T event) {
        List<EventHandler<?>> eventHandlers = handlers.get(event.getClass());
        if (eventHandlers != null) {
            for (EventHandler<?> handler : eventHandlers) {
                ((EventHandler<T>) handler).handle(event);
            }
        }
    }

    public interface EventHandler<T> {
        void handle(T event);
    }
}