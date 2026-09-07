package me.fond.nodesoverlay;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import me.fond.nodesoverlay.gui.AccessUnavailableScreen;
import me.fond.nodesoverlay.gui.NodesOverlaySettingsScreen;
import me.fond.nodesoverlay.gui.SyncWarningHud;
import me.fond.nodesoverlay.integration.xaero.TerritoryWaypointManager;
import me.fond.nodesoverlay.model.PortRecord;
import me.fond.nodesoverlay.model.TerritoryView;
import me.fond.nodesoverlay.port.PortInfoParser;
import me.fond.nodesoverlay.sync.SyncStatus;
import me.fond.nodesoverlay.war.PendingWarMessageBuffer;
import me.fond.nodesoverlay.war.TerritoryInfoParser;
import me.fond.nodesoverlay.war.TownWarOutcome;
import me.fond.nodesoverlay.war.WarDebugMessages;
import me.fond.nodesoverlay.war.WarEvent;
import me.fond.nodesoverlay.war.WarMessage;
import me.fond.nodesoverlay.war.WarMessageParser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.PriorityQueue;

public final class NodesOverlay implements ClientModInitializer {

    public static final String MOD_ID = "nodesoverlay";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final WarMessageParser WAR_MESSAGE_PARSER = new WarMessageParser();
    private static final PortInfoParser PORT_INFO_PARSER = new PortInfoParser();
    private static final TerritoryInfoParser TERRITORY_INFO_PARSER = new TerritoryInfoParser();
    private static final PendingWarMessageBuffer PENDING_WAR_MESSAGES =
            new PendingWarMessageBuffer(128);
    private static final PriorityQueue<ScheduledWarMessage> SCHEDULED_WAR_MESSAGES =
            new PriorityQueue<>(Comparator
                    .comparingLong(ScheduledWarMessage::dueNanos)
                    .thenComparingLong(ScheduledWarMessage::sequence));
    private static final KeyBinding.Category KEY_CATEGORY =
            KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));

    private static KeyBinding settingsKey;
    private static long scheduledWarSequence;

    @Override
    public void onInitializeClient() {
        NodesOverlayRuntime.initialize(LOGGER);
        registerConnectionLifecycle();
        registerTickCallback();
        registerChatListeners();
        registerCommands();
        registerHudRenderer();
        LOGGER.info("Nodes Overlay initialized");
    }

    private static void registerConnectionLifecycle() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            PORT_INFO_PARSER.reset();
            TERRITORY_INFO_PARSER.reset();
            PENDING_WAR_MESSAGES.clear();
            String serverAddress = resolveServerAddress(client);
            NodesOverlayRuntime.activate(serverAddress);
            LOGGER.info(
                    "Activated Nodes Overlay profile for {} (enabled={})",
                    serverAddress,
                    NodesOverlayRuntime.settings().enabled
            );
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            PORT_INFO_PARSER.reset();
            TERRITORY_INFO_PARSER.reset();
            PENDING_WAR_MESSAGES.clear();
            clearScheduledWarMessages();
            NodesOverlayRuntime.deactivate();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            PENDING_WAR_MESSAGES.clear();
            clearScheduledWarMessages();
            NodesOverlayRuntime.deactivate();
        });
    }

    private static void registerTickCallback() {
        settingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.nodesoverlay.open_settings",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
                KEY_CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            NodesOverlayRuntime.tick(client);
            if (NodesOverlayRuntime.accessAllowed()) {
                replayPendingWarMessages();
                tickScheduledWarMessages(client);
            } else {
                if (NodesOverlayRuntime.accessDenied()) {
                    PENDING_WAR_MESSAGES.clear();
                }
                clearScheduledWarMessages();
            }
            NodesOverlayRuntime.flushHighlights();
            while (settingsKey.wasPressed()) {
                if (NodesOverlayRuntime.serverAddress() != null) {
                    openSettings(client);
                }
            }
        });
    }

    private static void registerChatListeners() {
        ClientReceiveMessageEvents.CHAT.register((
                message,
                signedMessage,
                sender,
                params,
                receptionTimestamp
        ) -> handleIncomingMessage(message));
        ClientReceiveMessageEvents.GAME.register((message, overlay) ->
                handleIncomingMessage(message));
    }

    private static void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("xnma")
                        .requires(source -> NodesOverlayRuntime.configurationAccessAllowed())
                        .executes(context -> openSettings(MinecraftClient.getInstance()))
                        .then(ClientCommandManager.literal("settings")
                                .executes(context -> openSettings(MinecraftClient.getInstance())))
                        .then(ClientCommandManager.literal("refresh")
                                .executes(context -> {
                                    if (!NodesOverlayRuntime.isActive()) {
                                        feedback("The Nodes Overlay is disabled for this server.");
                                        return 0;
                                    }
                                    NodesOverlayRuntime.refreshNow();
                                    feedback("Refreshing map data in the background.");
                                    return 1;
                                }))
                        .then(ClientCommandManager.literal("status")
                                .executes(context -> {
                                    showStatus();
                                    return 1;
                                }))
                        .then(ClientCommandManager.literal("clear-cache")
                                .executes(context -> {
                                    if (!NodesOverlayRuntime.isActive()) {
                                        feedback("Enable Nodes Overlay before clearing its cache.");
                                        return 0;
                                    }
                                    NodesOverlayRuntime.clearCache();
                                    feedback("Cleared this server's cache; refreshing now.");
                                    return 1;
                                }))
                        .then(ClientCommandManager.literal("toggle")
                                .executes(context -> {
                                    boolean enabled = !NodesOverlayRuntime.settings().enabled;
                                    NodesOverlayRuntime.settings().enabled = enabled;
                                    NodesOverlayRuntime.saveSettings();
                                    feedback("Nodes Overlay " + (enabled ? "enabled." : "disabled."));
                                    return 1;
                                }))
                        .then(ClientCommandManager.literal("waypoint")
                                .then(ClientCommandManager.literal("clear")
                                        .executes(context -> clearNodeWaypoints()))
                                .then(ClientCommandManager.literal("remove")
                                        .then(ClientCommandManager.argument(
                                                        "territory",
                                                        IntegerArgumentType.integer(0)
                                                )
                                                .executes(context -> removeNodeWaypoint(
                                                        IntegerArgumentType.getInteger(
                                                                context,
                                                                "territory"
                                                        )
                                                ))))
                                .then(ClientCommandManager.literal("toggle")
                                        .then(ClientCommandManager.argument(
                                                        "territory",
                                                        IntegerArgumentType.integer(0)
                                                )
                                                .executes(context -> toggleNodeWaypoint(
                                                        IntegerArgumentType.getInteger(
                                                                context,
                                                                "territory"
                                                        )
                                                )))))
                        .then(ClientCommandManager.literal("test-war")
                                .then(ClientCommandManager.literal("attack")
                                        .executes(context -> testWarAttack(-1))
                                        .then(ClientCommandManager.argument(
                                                        "defend_after_seconds",
                                                        IntegerArgumentType.integer(0, 300)
                                                )
                                                .executes(context -> testWarAttack(
                                                        IntegerArgumentType.getInteger(
                                                                context,
                                                                "defend_after_seconds"
                                                        )
                                                ))))
                                .then(ClientCommandManager.literal("attack-defended")
                                        .executes(context -> testWarAttackDefended(0))
                                        .then(ClientCommandManager.argument(
                                                        "delay_seconds",
                                                        IntegerArgumentType.integer(0, 300)
                                                )
                                                .executes(context -> testWarAttackDefended(
                                                        IntegerArgumentType.getInteger(
                                                                context,
                                                                "delay_seconds"
                                                        )
                                                ))))
                                .then(ClientCommandManager.literal("chunk")
                                        .executes(context -> testWarChunkCaptured(0)))
                                .then(ClientCommandManager.literal("chunk-captured")
                                        .executes(context -> testWarChunkCaptured(0))
                                        .then(ClientCommandManager.argument(
                                                        "delay_seconds",
                                                        IntegerArgumentType.integer(0, 300)
                                                )
                                                .executes(context -> testWarChunkCaptured(
                                                        IntegerArgumentType.getInteger(
                                                                context,
                                                                "delay_seconds"
                                                        )
                                                ))))
                                .then(ClientCommandManager.literal("chunk-defended")
                                        .executes(context -> testWarChunkDefended(0))
                                        .then(ClientCommandManager.argument(
                                                        "delay_seconds",
                                                        IntegerArgumentType.integer(0, 300)
                                                )
                                                .executes(context -> testWarChunkDefended(
                                                        IntegerArgumentType.getInteger(
                                                                context,
                                                                "delay_seconds"
                                                        )
                                                ))))
                                .then(ClientCommandManager.literal("chunk-liberated")
                                        .executes(context -> testWarChunkLiberated(0))
                                        .then(ClientCommandManager.argument(
                                                        "delay_seconds",
                                                        IntegerArgumentType.integer(0, 300)
                                                )
                                                .executes(context -> testWarChunkLiberated(
                                                        IntegerArgumentType.getInteger(
                                                                context,
                                                                "delay_seconds"
                                                        )
                                                ))))
                                .then(ClientCommandManager.literal("territory")
                                        .executes(context -> testWarTerritoryCaptured(
                                                currentTerritoryId(),
                                                0
                                        ))
                                        .then(ClientCommandManager.argument(
                                                         "territory",
                                                         IntegerArgumentType.integer(0)
                                                 )
                                                .executes(context -> testWarTerritoryCaptured(
                                                        IntegerArgumentType.getInteger(
                                                                context,
                                                                "territory"
                                                        ),
                                                        0
                                                ))))
                                .then(territoryWarTestCommand("territory-captured", false))
                                .then(territoryWarTestCommand("territory-liberated", true))
                                .then(ClientCommandManager.literal("suite")
                                        .executes(context -> testWarSuite(currentTerritoryId(), 2))
                                        .then(ClientCommandManager.argument(
                                                        "territory",
                                                        IntegerArgumentType.integer(0)
                                                )
                                                .executes(context -> testWarSuite(
                                                        IntegerArgumentType.getInteger(
                                                                context,
                                                                "territory"
                                                        ),
                                                        2
                                                ))
                                                .then(ClientCommandManager.argument(
                                                                "step_seconds",
                                                                IntegerArgumentType.integer(0, 60)
                                                        )
                                                        .executes(context -> testWarSuite(
                                                                IntegerArgumentType.getInteger(
                                                                        context,
                                                                        "territory"
                                                                ),
                                                                IntegerArgumentType.getInteger(
                                                                        context,
                                                                        "step_seconds"
                                                                )
                                                        ))
                                                )))
                                .then(ClientCommandManager.literal("clear")
                                        .executes(context -> clearWarTests())))
                        .then(ClientCommandManager.literal("diagnose")
                                .executes(context -> diagnoseCurrentArea()))
                ));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> territoryWarTestCommand(
            String literal,
            boolean liberated
    ) {
        return ClientCommandManager.literal(literal)
                .executes(context -> runTerritoryWarTest(
                        currentTerritoryId(),
                        0,
                        liberated
                ))
                .then(ClientCommandManager.argument(
                                "territory",
                                IntegerArgumentType.integer(0)
                        )
                        .executes(context -> runTerritoryWarTest(
                                IntegerArgumentType.getInteger(context, "territory"),
                                0,
                                liberated
                        ))
                        .then(ClientCommandManager.argument(
                                        "delay_seconds",
                                        IntegerArgumentType.integer(0, 300)
                                )
                                .executes(context -> runTerritoryWarTest(
                                        IntegerArgumentType.getInteger(context, "territory"),
                                        IntegerArgumentType.getInteger(context, "delay_seconds"),
                                        liberated
                                ))));
    }

    private static int runTerritoryWarTest(
            int territoryId,
            int delaySeconds,
            boolean liberated
    ) {
        return liberated
                ? testWarTerritoryLiberated(territoryId, delaySeconds)
                : testWarTerritoryCaptured(territoryId, delaySeconds);
    }

    @SuppressWarnings("deprecation")
    private static void registerHudRenderer() {
        HudRenderCallback.EVENT.register(new SyncWarningHud());
    }

    private static int openSettings(MinecraftClient client) {
        if (client == null || NodesOverlayRuntime.serverAddress() == null) {
            feedback("Join a world or server before opening Nodes Overlay settings.");
            return 0;
        }
        if (!NodesOverlayRuntime.configurationAccessAllowed()) {
            client.setScreen(new AccessUnavailableScreen(client.currentScreen));
            return 0;
        }
        client.setScreen(new NodesOverlaySettingsScreen(client.currentScreen));
        return 1;
    }

    private static void handleIncomingMessage(Text text) {
        if (!NodesOverlayRuntime.sessionEnabled() || text == null) {
            return;
        }
        String raw = text.getString();
        if (!NodesOverlayRuntime.accessAllowed()) {
            if (!NodesOverlayRuntime.accessDenied()) {
                PENDING_WAR_MESSAGES.offerIfWarMessage(raw);
            }
            return;
        }
        try {
            PORT_INFO_PARSER.accept(raw).ifPresent(observation -> {
                PortRecord port = NodesOverlayRuntime.observePort(observation);
                feedback("Updated port " + port.name() + " at "
                        + port.x() + ", " + port.z() + " from /port info.");
            });
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not process /port info line: {}", raw, exception);
        }
        try {
            TERRITORY_INFO_PARSER.accept(raw).ifPresent(observation -> {
                if (NodesOverlayRuntime.observeTerritory(observation)) {
                    String occupier = observation.occupied()
                            ? observation.occupierTown()
                            : "none";
                    feedback("Updated territory " + observation.territoryId()
                            + " occupation from in-game info: " + occupier + ".");
                }
            });
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not process territory info line: {}", raw, exception);
        }
        handleWarMessage(raw);
    }

    private static void replayPendingWarMessages() {
        for (String raw : PENDING_WAR_MESSAGES.drain()) {
            handleWarMessage(raw);
        }
    }

    private static void handleWarMessage(String raw) {
        if (!NodesOverlayRuntime.isActive() || raw == null) {
            return;
        }
        try {
            var townOutcome = WAR_MESSAGE_PARSER.parseTownOutcome(raw);
            if (townOutcome.isPresent()) {
                TownWarOutcome outcome = townOutcome.orElseThrow();
                if (NodesOverlayRuntime.warTracker().completeAttacksForTown(
                        outcome.town(),
                        NodesOverlayRuntime.snapshot()
                )) {
                    NodesOverlayRuntime.markersChanged();
                }
                NodesOverlayRuntime.refreshSoon();
                return;
            }
            WAR_MESSAGE_PARSER.parse(raw).ifPresent(message -> {
                WarEvent event = applyWarMessage(message);
                if (event.territoryId() < 0) {
                    LOGGER.warn(
                            "War message could not be matched to a territory; retained at chunk ({}, {}): {}",
                            event.chunkX(),
                            event.chunkZ(),
                            raw
                    );
                }
                if (message instanceof WarMessage.ChunkCaptured
                        || message instanceof WarMessage.ChunkDefended
                        || message instanceof WarMessage.ChunkLiberated
                        || message instanceof WarMessage.TerritoryCaptured
                        || message instanceof WarMessage.TerritoryLiberated) {
                    NodesOverlayRuntime.refreshSoon();
                }
                notifyTrackedTerritory(event);
            });
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not process war message: {}", raw, exception);
        }
    }

    private static WarEvent applyWarMessage(WarMessage message) {
        MinecraftClient client = MinecraftClient.getInstance();
        String localPlayer = client.player == null
                ? ""
                : client.player.getName().getString();
        WarEvent event = NodesOverlayRuntime.warTracker().apply(
                message,
                NodesOverlayRuntime.snapshot(),
                localPlayer
        );
        NodesOverlayRuntime.warChanged(event);
        return event;
    }

    private static int clearNodeWaypoints() {
        TerritoryWaypointManager.clear();
        feedback("Removed all node waypoints.");
        return 1;
    }

    private static int removeNodeWaypoint(int territoryId) {
        boolean existed = TerritoryWaypointManager.has(territoryId);
        TerritoryWaypointManager.remove(territoryId);
        if (NodesOverlayRuntime.warTracker().hasActiveAttackForTerritory(territoryId)) {
            NodesOverlayRuntime.warTracker().removeWaypointForTerritory(territoryId);
            existed = true;
        }
        feedback(existed
                ? "Removed the waypoint for territory " + territoryId + "."
                : "No node waypoint exists for territory " + territoryId + ".");
        return existed ? 1 : 0;
    }

    private static int toggleNodeWaypoint(int territoryId) {
        TerritoryView territory = NodesOverlayRuntime.snapshot().territory(territoryId);
        if (territory == null) {
            feedback("Territory " + territoryId + " is not in the current data.");
            return 0;
        }
        if (TerritoryWaypointManager.has(territoryId)) {
            TerritoryWaypointManager.remove(territoryId);
            feedback("Removed the node waypoint for territory " + territoryId + ".");
            return 1;
        }
        boolean created = TerritoryWaypointManager.create(territory, null);
        feedback(created
                ? "Created a removable node waypoint for territory " + territoryId + "."
                : "Xaero's waypoint session is unavailable.");
        return created ? 1 : 0;
    }

    private static int testWarAttack(int defendAfterSeconds) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return 0;
        }
        int x = client.player.getBlockX();
        int y = client.player.getBlockY();
        int z = client.player.getBlockZ();
        TerritoryView territory = NodesOverlayRuntime.snapshot().territoryAtBlock(x, z);
        String town = reportedTown(territory);
        String player = debugPlayerName();
        emitClientWarMessage(WarDebugMessages.attack(player, town, x, y, z));
        if (defendAfterSeconds >= 0) {
            scheduleClientWarMessage(
                    WarDebugMessages.attackDefended(player, x, y, z),
                    defendAfterSeconds
            );
        }
        int territoryId = territory == null ? -1 : territory.territory().id();
        feedback("Added test attack at (" + x + ", " + y + ", " + z
                + "), territory " + territoryId
                + (defendAfterSeconds < 0
                ? "."
                : "; defense in " + defendAfterSeconds + " seconds."));
        return 1;
    }

    private static int testWarAttackDefended(int delaySeconds) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return 0;
        }
        scheduleClientWarMessage(
                WarDebugMessages.attackDefended(
                        debugPlayerName(),
                        client.player.getBlockX(),
                        client.player.getBlockY(),
                        client.player.getBlockZ()
                ),
                delaySeconds
        );
        feedback(scheduledFeedback("attack defense", delaySeconds));
        return 1;
    }

    private static int testWarChunkCaptured(int delaySeconds) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return 0;
        }
        int chunkX = client.player.getChunkPos().x;
        int chunkZ = client.player.getChunkPos().z;
        TerritoryView territory = NodesOverlayRuntime.snapshot().territoryAtChunk(chunkX, chunkZ);
        scheduleClientWarMessage(
                WarDebugMessages.chunkCaptured(
                        debugPlayerName(),
                        reportedTown(territory),
                        chunkX,
                        chunkZ
                ),
                delaySeconds
        );
        feedback(scheduledFeedback(
                "chunk capture at (" + chunkX + ", " + chunkZ + ")",
                delaySeconds
        ));
        return 1;
    }

    private static int testWarChunkDefended(int delaySeconds) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return 0;
        }
        int chunkX = client.player.getChunkPos().x;
        int chunkZ = client.player.getChunkPos().z;
        TerritoryView territory = NodesOverlayRuntime.snapshot().territoryAtChunk(chunkX, chunkZ);
        scheduleClientWarMessage(
                WarDebugMessages.chunkDefended(
                        debugPlayerName(),
                        debugOccupier(territory),
                        chunkX,
                        chunkZ
                ),
                delaySeconds
        );
        feedback(scheduledFeedback(
                "chunk defense at (" + chunkX + ", " + chunkZ + ")",
                delaySeconds
        ));
        return 1;
    }

    private static int testWarChunkLiberated(int delaySeconds) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return 0;
        }
        int chunkX = client.player.getChunkPos().x;
        int chunkZ = client.player.getChunkPos().z;
        TerritoryView territory = NodesOverlayRuntime.snapshot().territoryAtChunk(chunkX, chunkZ);
        scheduleClientWarMessage(
                WarDebugMessages.chunkLiberated(
                        debugPlayerName(),
                        debugOccupier(territory),
                        chunkX,
                        chunkZ
                ),
                delaySeconds
        );
        feedback(scheduledFeedback(
                "chunk liberation at (" + chunkX + ", " + chunkZ + ")",
                delaySeconds
        ));
        return 1;
    }

    private static int testWarTerritoryCaptured(int territoryId, int delaySeconds) {
        TerritoryView territory = NodesOverlayRuntime.snapshot().territory(territoryId);
        if (territory == null) {
            feedback("Stand in a territory or provide a valid territory ID.");
            return 0;
        }
        scheduleClientWarMessage(
                WarDebugMessages.territoryCaptured(
                        debugPlayerName(),
                        reportedTown(territory),
                        territoryId
                ),
                delaySeconds
        );
        feedback(scheduledFeedback(
                "territory capture for " + territoryId,
                delaySeconds
        ));
        return 1;
    }

    private static int testWarTerritoryLiberated(int territoryId, int delaySeconds) {
        TerritoryView territory = NodesOverlayRuntime.snapshot().territory(territoryId);
        if (territory == null) {
            feedback("Stand in a territory or provide a valid territory ID.");
            return 0;
        }
        scheduleClientWarMessage(
                WarDebugMessages.territoryLiberated(
                        debugPlayerName(),
                        debugOccupier(territory),
                        territoryId
                ),
                delaySeconds
        );
        feedback(scheduledFeedback(
                "territory liberation for " + territoryId,
                delaySeconds
        ));
        return 1;
    }

    private static int testWarSuite(int territoryId, int stepSeconds) {
        TerritoryView territory = NodesOverlayRuntime.snapshot().territory(territoryId);
        if (territory == null) {
            feedback("Stand in a territory or provide a valid territory ID.");
            return 0;
        }
        for (WarDebugMessages.TimedMessage message : WarDebugMessages.fullLifecycle(
                debugPlayerName(),
                reportedTown(territory),
                debugOccupier(territory),
                territoryId,
                territory.territory().coreX(),
                128,
                territory.territory().coreZ(),
                territory.territory().coreChunkX(),
                territory.territory().coreChunkZ(),
                stepSeconds
        )) {
            scheduleClientWarMessage(message.text(), message.delaySeconds());
        }
        feedback("Scheduled all war chat messages for territory " + territoryId
                + " at " + stepSeconds + "-second intervals.");
        return 1;
    }

    private static int clearWarTests() {
        clearScheduledWarMessages();
        NodesOverlayRuntime.warTracker().clear();
        NodesOverlayRuntime.changed();
        feedback("Cleared all live and test war events.");
        return 1;
    }

    private static void scheduleClientWarMessage(String raw, int delaySeconds) {
        if (delaySeconds <= 0) {
            emitClientWarMessage(raw);
            return;
        }
        long dueNanos = System.nanoTime() + delaySeconds * 1_000_000_000L;
        SCHEDULED_WAR_MESSAGES.add(new ScheduledWarMessage(
                dueNanos,
                scheduledWarSequence++,
                raw
        ));
    }

    private static void tickScheduledWarMessages(MinecraftClient client) {
        if (client == null || client.player == null) {
            return;
        }
        long now = System.nanoTime();
        while (!SCHEDULED_WAR_MESSAGES.isEmpty()
                && SCHEDULED_WAR_MESSAGES.peek().dueNanos() <= now) {
            emitClientWarMessage(SCHEDULED_WAR_MESSAGES.remove().text());
        }
    }

    private static void emitClientWarMessage(String raw) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || raw == null || raw.isBlank()) {
            return;
        }
        Text message = Text.literal(raw);
        client.inGameHud.getChatHud().addMessage(message);
        handleIncomingMessage(message);
    }

    private static void clearScheduledWarMessages() {
        SCHEDULED_WAR_MESSAGES.clear();
        scheduledWarSequence = 0L;
    }

    private static String scheduledFeedback(String description, int delaySeconds) {
        return delaySeconds <= 0
                ? "Emulated " + description + "."
                : "Scheduled " + description + " in " + delaySeconds + " seconds.";
    }

    private static int diagnoseCurrentArea() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return 0;
        }
        int chunkX = client.player.getChunkPos().x;
        int chunkZ = client.player.getChunkPos().z;
        TerritoryView territory = NodesOverlayRuntime.snapshot().territoryAtChunk(chunkX, chunkZ);
        feedback("Chunk (" + chunkX + ", " + chunkZ + "): "
                + (territory == null
                ? "not present in world.json"
                : "territory " + territory.territory().id()
                + ", " + territory.state()
                + ", owner " + territory.ownerTownName()));
        feedback("Loaded: " + NodesOverlayRuntime.snapshot().territories().size()
                + " territories, " + NodesOverlayRuntime.ports().size() + " ports; "
                + NodesOverlayRuntime.syncStatus().message() + ".");
        return territory == null ? 0 : 1;
    }

    private static int currentTerritoryId() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return -1;
        }
        TerritoryView territory = NodesOverlayRuntime.snapshot().territoryAtBlock(
                client.player.getBlockX(),
                client.player.getBlockZ()
        );
        return territory == null ? -1 : territory.territory().id();
    }

    private static String reportedTown(TerritoryView territory) {
        return territory == null || territory.owner() == null
                ? "Unknown"
                : territory.owner().name();
    }

    private static String debugOccupier(TerritoryView territory) {
        return territory == null || territory.occupier() == null
                ? "NodesTestOccupier"
                : territory.occupier().name();
    }

    private static String debugPlayerName() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client == null || client.player == null
                ? "NodesTest"
                : client.player.getName().getString();
    }

    private static void notifyTrackedTerritory(WarEvent event) {
        if (event.territoryId() < 0
                || !NodesOverlayRuntime.settings().trackedTerritories.contains(event.territoryId())) {
            return;
        }
        String detail = switch (event.kind()) {
            case ATTACK -> "is under attack by " + event.attackingPlayer()
                    + " at (" + event.blockX() + ", " + event.blockY() + ", " + event.blockZ() + ")";
            case ATTACK_DEFENDED -> "had its attack at (" + event.blockX() + ", "
                    + event.blockY() + ", " + event.blockZ() + ") defended by "
                    + event.attackingPlayer();
            case CHUNK_CAPTURE -> "had chunk (" + event.chunkX() + ", " + event.chunkZ()
                    + ") captured by " + event.attackingPlayer();
            case CHUNK_DEFENDED -> "had chunk (" + event.chunkX() + ", " + event.chunkZ()
                    + ") defended by " + event.attackingPlayer();
            case CHUNK_LIBERATED -> "had chunk (" + event.chunkX() + ", " + event.chunkZ()
                    + ") liberated by " + event.attackingPlayer();
            case TERRITORY_CAPTURE -> "was captured by " + event.attackingPlayer();
            case TERRITORY_LIBERATED -> "was liberated by " + event.attackingPlayer();
        };
        feedback("Tracked territory " + event.territoryId() + " " + detail + ".");
    }

    private static void showStatus() {
        SyncStatus sync = NodesOverlayRuntime.syncStatus();
        String server = NodesOverlayRuntime.serverAddress() == null
                ? "no server"
                : NodesOverlayRuntime.serverAddress();
        feedback("Profile: " + server + " | overlay "
                + (NodesOverlayRuntime.isActive() ? "enabled" : "disabled"));
        feedback("Data: " + sync.message() + " | "
                + NodesOverlayRuntime.snapshot().territories().size() + " territories, "
                + NodesOverlayRuntime.snapshot().nodes().size() + " nodes, "
                + NodesOverlayRuntime.ports().size() + " ports, "
                + NodesOverlayRuntime.snapshot().towns().size() + " towns");
        feedback("War: " + NodesOverlayRuntime.warTracker().debugSummary());
    }

    private static void feedback(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null) {
            client.player.sendMessage(
                    Text.literal("[Nodes Overlay] " + message),
                    false
            );
        } else {
            LOGGER.info(message);
        }
    }

    private static String resolveServerAddress(MinecraftClient client) {
        ServerInfo server = client.getCurrentServerEntry();
        if (server != null && server.address != null && !server.address.isBlank()) {
            return server.address.trim();
        }
        return client.isInSingleplayer() ? "singleplayer" : "unknown";
    }

    private record ScheduledWarMessage(long dueNanos, long sequence, String text) {
    }
}
