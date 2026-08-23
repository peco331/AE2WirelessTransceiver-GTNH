package cn.gtnh.ae2wtx.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import cn.gtnh.ae2wtx.AE2Wtx;

/** Bounded and rate-limited hand-off from Netty threads to the server thread. */
public final class ServerTaskQueue {

    public enum TaskType {
        MUTATION,
        LIST
    }

    private static final int MAX_QUEUED_TASKS = 4096;
    private static final int MAX_QUEUED_TASKS_PER_PLAYER = 256;
    private static final int MAX_TASKS_PER_TICK = 128;

    private static final Object SLOT_LOCK = new Object();
    private static final ConcurrentLinkedQueue<QueuedTask> TASKS = new ConcurrentLinkedQueue<>();
    private static final Map<UUID, Integer> PLAYER_TASK_COUNTS = new HashMap<>();
    private static int taskCount;

    private ServerTaskQueue() {}

    /** Queue immutable packet work for the server thread. */
    public static boolean enqueue(EntityPlayerMP sender, TaskType type, Consumer<EntityPlayerMP> action) {
        if (sender == null || type == null || action == null) {
            return false;
        }
        MinecraftServer server = MinecraftServer.getServer();
        UUID playerId = sender.getUniqueID();
        if (server == null || playerId == null || !ServerRateLimiter.tryAcquire(playerId, type)) {
            return false;
        }

        synchronized (SLOT_LOCK) {
            int playerCount = PLAYER_TASK_COUNTS.getOrDefault(playerId, 0);
            if (taskCount >= MAX_QUEUED_TASKS || playerCount >= MAX_QUEUED_TASKS_PER_PLAYER) {
                return false;
            }
            taskCount++;
            PLAYER_TASK_COUNTS.put(playerId, playerCount + 1);
        }
        TASKS.offer(new QueuedTask(server, playerId, action));
        return true;
    }

    /** Called by {@link ServerTaskQueueTickHandler} on the server thread. */
    static void drain(MinecraftServer currentServer) {
        if (currentServer == null) {
            return;
        }
        for (int processed = 0; processed < MAX_TASKS_PER_TICK; processed++) {
            QueuedTask queued = TASKS.poll();
            if (queued == null) {
                return;
            }
            releaseSlot(queued.playerId);
            if (queued.server != currentServer) {
                continue;
            }
            EntityPlayerMP player = findOnlinePlayer(currentServer, queued.playerId);
            if (player == null || player.isDead || player.playerNetServerHandler == null) {
                continue;
            }
            try {
                queued.action.accept(player);
            } catch (RuntimeException e) {
                AE2Wtx.LOG.error("Rejected ae2wtx server task for player {}", queued.playerId, e);
            }
        }
    }

    static void cleanRateLimiters() {
        ServerRateLimiter.cleanup();
    }

    public static void shutdown() {
        TASKS.clear();
        synchronized (SLOT_LOCK) {
            PLAYER_TASK_COUNTS.clear();
            taskCount = 0;
        }
        ServerRateLimiter.clear();
    }

    private static EntityPlayerMP findOnlinePlayer(MinecraftServer server, UUID playerId) {
        if (server.getConfigurationManager() == null) {
            return null;
        }
        for (Object value : server.getConfigurationManager().playerEntityList) {
            if (value instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) value;
                if (playerId.equals(player.getUniqueID())) {
                    return player;
                }
            }
        }
        return null;
    }

    private static void releaseSlot(UUID playerId) {
        synchronized (SLOT_LOCK) {
            taskCount = Math.max(0, taskCount - 1);
            int remaining = PLAYER_TASK_COUNTS.getOrDefault(playerId, 0) - 1;
            if (remaining <= 0) {
                PLAYER_TASK_COUNTS.remove(playerId);
            } else {
                PLAYER_TASK_COUNTS.put(playerId, remaining);
            }
        }
    }

    private static final class QueuedTask {

        private final MinecraftServer server;
        private final UUID playerId;
        private final Consumer<EntityPlayerMP> action;

        private QueuedTask(MinecraftServer server, UUID playerId, Consumer<EntityPlayerMP> action) {
            this.server = server;
            this.playerId = playerId;
            this.action = action;
        }
    }
}
