package cn.gtnh.ae2wtx.network;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-player token buckets used before packet work enters the server queue. */
final class ServerRateLimiter {

    private static final double MUTATIONS_PER_SECOND = 4.0D;
    private static final double MUTATION_BURST = 8.0D;
    private static final double LISTS_PER_SECOND = 2.0D;
    private static final double LIST_BURST = 4.0D;
    private static final long EXPIRE_AFTER_NANOS = 10L * 60L * 1_000_000_000L;

    private static final Map<UUID, PlayerBuckets> PLAYERS = new ConcurrentHashMap<>();

    private ServerRateLimiter() {}

    static boolean tryAcquire(UUID playerId, ServerTaskQueue.TaskType type) {
        long now = System.nanoTime();
        PlayerBuckets buckets = PLAYERS.computeIfAbsent(playerId, ignored -> new PlayerBuckets(now));
        return buckets.tryAcquire(type, now);
    }

    static void cleanup() {
        long now = System.nanoTime();
        PLAYERS.entrySet().removeIf(entry -> entry.getValue().expired(now));
    }

    static void clear() {
        PLAYERS.clear();
    }

    private static final class PlayerBuckets {

        private final Bucket mutations;
        private final Bucket lists;
        private long lastSeen;

        private PlayerBuckets(long now) {
            this.mutations = new Bucket(MUTATIONS_PER_SECOND, MUTATION_BURST, now);
            this.lists = new Bucket(LISTS_PER_SECOND, LIST_BURST, now);
            this.lastSeen = now;
        }

        private synchronized boolean tryAcquire(ServerTaskQueue.TaskType type, long now) {
            lastSeen = now;
            return (type == ServerTaskQueue.TaskType.LIST ? lists : mutations).tryAcquire(now);
        }

        private synchronized boolean expired(long now) {
            return now - lastSeen > EXPIRE_AFTER_NANOS;
        }
    }

    private static final class Bucket {

        private final double tokensPerNano;
        private final double capacity;
        private double tokens;
        private long lastRefill;

        private Bucket(double tokensPerSecond, double capacity, long now) {
            this.tokensPerNano = tokensPerSecond / 1_000_000_000.0D;
            this.capacity = capacity;
            this.tokens = capacity;
            this.lastRefill = now;
        }

        private boolean tryAcquire(long now) {
            long elapsed = Math.max(0L, now - lastRefill);
            tokens = Math.min(capacity, tokens + elapsed * tokensPerNano);
            lastRefill = now;
            if (tokens < 1.0D) {
                return false;
            }
            tokens -= 1.0D;
            return true;
        }
    }
}
