package cn.gtnh.ae2wtx.command;

import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentTranslation;

import cn.gtnh.ae2wtx.AE2Wtx;
import cn.gtnh.ae2wtx.config.ModConfig;

/** Operator command for reloading runtime-safe dedicated-server settings. */
public final class AE2WtxCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "ae2wtx";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/ae2wtx reload";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length != 1 || !"reload".equalsIgnoreCase(args[0])) {
            sender.addChatMessage(new ChatComponentTranslation("commands.generic.usage", getCommandUsage(sender)));
            return;
        }
        try {
            ModConfig.syncValues(false);
            sender.addChatMessage(new ChatComponentTranslation(
                "ae2wtx.command.reload.success",
                ModConfig.wirelessMaxBandsPerOwner,
                ModConfig.wirelessMaxBandsPerWorld,
                ModConfig.wirelessTransceiverIdlePower));
        } catch (RuntimeException e) {
            AE2Wtx.LOG.error("Failed to reload ae2wtx configuration", e);
            sender.addChatMessage(new ChatComponentTranslation("ae2wtx.command.reload.failed"));
        }
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, "reload") : null;
    }
}
