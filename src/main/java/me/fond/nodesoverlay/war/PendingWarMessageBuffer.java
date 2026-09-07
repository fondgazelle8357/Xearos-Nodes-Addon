package me.fond.nodesoverlay.war;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Retains server war messages received briefly during login or server transfer.
 * Messages are parsed before buffering, bounded to a small capacity, and
 * replayed once the map profile is active.
 */
public final class PendingWarMessageBuffer {

    private final int capacity;
    private final WarMessageParser parser = new WarMessageParser();
    private final ArrayDeque<String> messages = new ArrayDeque<>();

    public PendingWarMessageBuffer(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    public synchronized boolean offerIfWarMessage(String raw) {
        if (raw == null
                || parser.parse(raw).isEmpty() && parser.parseTownOutcome(raw).isEmpty()) {
            return false;
        }
        if (raw.equals(messages.peekLast())) {
            return true;
        }
        while (messages.size() >= capacity) {
            messages.removeFirst();
        }
        messages.addLast(raw);
        return true;
    }

    public synchronized List<String> drain() {
        List<String> result = new ArrayList<>(messages);
        messages.clear();
        return List.copyOf(result);
    }

    public synchronized void clear() {
        messages.clear();
    }

    public synchronized int size() {
        return messages.size();
    }
}
