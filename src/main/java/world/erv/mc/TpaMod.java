package world.erv.mc;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TpaMod implements ModInitializer {
	public static final String MOD_ID = "tpamod";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Map<UUID, UUID> requestMap = new HashMap<>();

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

        // COMMANDS
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                CommandManager.literal("tpa")
                    .then(CommandManager.argument("targetPlayer", EntityArgumentType.player())
                        .executes(TpaMod::executeTpa))
            );
            dispatcher.register(
                CommandManager.literal("tpaccept").executes(TpaMod::executeTpaccept)
            );
        });

        LOGGER.info("TpaMod initialized.");
    }

    private static int executeTpa(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        PlayerEntity sourcePlayer = source.getPlayer();
        PlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "targetPlayer");

        if (sourcePlayer == null) {
            return -1;
        }

        if (targetPlayer.equals(sourcePlayer)) {
            source.sendFeedback(
                () -> Text.literal("You teleported to yourself"),
                false
            );
            return Command.SINGLE_SUCCESS;
        }

        requestMap.put(targetPlayer.getUuid(), sourcePlayer.getUuid());

        targetPlayer.sendMessage(
            Text.literal("%s requested to teleport to you\nAccept with /tpaccept".formatted(
                sourcePlayer.getStringifiedName())
            ),
            false
        );

        source.sendFeedback(
            () -> Text.literal("Asked %s to teleport...".formatted(targetPlayer.getStringifiedName())),
            false
        );

        return Command.SINGLE_SUCCESS;
    }

    private static int executeTpaccept(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity acceptingPlayer = source.getPlayer();

        if (acceptingPlayer == null) {
            return -1;
        }

        if (!requestMap.containsKey(acceptingPlayer.getUuid())) {
            source.sendFeedback(
                () -> Text.literal("No pending request"),
                false
            );
            return Command.SINGLE_SUCCESS;
        }

        UUID teleportingPlayerUuid = requestMap.get(acceptingPlayer.getUuid());
        PlayerEntity teleportingPlayer = source.getServer().getPlayerManager().getPlayer(teleportingPlayerUuid);
        if (teleportingPlayer == null) {
            source.sendFeedback(
                () -> Text.literal("Failed: couldn't resolve request"),
            false
            );
            return -1;
        }

        source.sendFeedback(
            () -> Text.literal("Teleporting..."),
            false
        );

        teleportingPlayer.teleport(
            acceptingPlayer.getEntityWorld(),
            acceptingPlayer.getX(),
            acceptingPlayer.getY(),
            acceptingPlayer.getZ(),
            Collections.emptySet(),
            acceptingPlayer.getYaw(),
            acceptingPlayer.getPitch(),
            false
        );

        requestMap.remove(acceptingPlayer.getUuid());

        return Command.SINGLE_SUCCESS;
    }
}