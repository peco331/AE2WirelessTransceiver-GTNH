package cn.gtnh.ae2wtx.client.screen;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Keyboard;

import cn.gtnh.ae2wtx.gui.LabeledContainer;
import cn.gtnh.ae2wtx.network.LabelApplyPacket;
import cn.gtnh.ae2wtx.network.LabelDeletePacket;
import cn.gtnh.ae2wtx.network.LabelListRequestPacket;
import cn.gtnh.ae2wtx.network.LabelListResponsePacket;
import cn.gtnh.ae2wtx.network.NetworkHandler;

/** Label management GUI for the labeled wireless transceiver. */
public class LabeledTransceiverGui extends GuiContainer {

    private static final ResourceLocation BG = new ResourceLocation("ae2wtx", "textures/gui/label_gui.png");

    private final EntityPlayer player;
    private final int x;
    private final int y;
    private final int z;

    private GuiTextField labelField;
    private List<LabelListResponsePacket.Entry> networks = new ArrayList<>();

    public LabeledTransceiverGui(LabeledContainer container, EntityPlayer player, int x, int y, int z) {
        super(container);
        this.player = player;
        this.x = x;
        this.y = y;
        this.z = z;
        this.xSize = 176;
        this.ySize = 140;
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        labelField = new GuiTextField(fontRendererObj, guiLeft + 12, guiTop + 16, 152, 16);
        labelField.setMaxStringLength(64);
        labelField.setFocused(true);
        buttonList.add(new GuiButton(0, guiLeft + 12, guiTop + 36, 50, 18, "Join"));
        buttonList.add(new GuiButton(1, guiLeft + 64, guiTop + 36, 50, 18, "Clear"));
        buttonList.add(new GuiButton(2, guiLeft + 116, guiTop + 36, 48, 18, "Delete"));
        buttonList.add(new GuiButton(3, guiLeft + 12, guiTop + 116, 152, 18, "Refresh"));
        NetworkHandler.CHANNEL.sendToServer(new LabelListRequestPacket());
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }

    public void setNetworkList(List<LabelListResponsePacket.Entry> entries) {
        this.networks = entries;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        String label = labelField.getText() == null ? "" : labelField.getText().trim();
        switch (button.id) {
            case 0: // join/create
                if (!label.isEmpty()) {
                    NetworkHandler.CHANNEL.sendToServer(new LabelApplyPacket(x, y, z, label));
                }
                break;
            case 1: // clear/leave
                NetworkHandler.CHANNEL.sendToServer(new LabelApplyPacket(x, y, z, ""));
                break;
            case 2: // delete network
                if (!label.isEmpty()) {
                    NetworkHandler.CHANNEL.sendToServer(new LabelDeletePacket(label));
                }
                break;
            case 3: // refresh list
                NetworkHandler.CHANNEL.sendToServer(new LabelListRequestPacket());
                break;
            default:
                break;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (labelField.textboxKeyTyped(typedChar, keyCode)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        labelField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        mc.getTextureManager().bindTexture(BG);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRendererObj.drawString("Labeled Transceiver", 8, 6, 0x404040);
        labelField.drawTextBox();
        int line = 60;
        for (LabelListResponsePacket.Entry e : networks) {
            fontRendererObj.drawString(e.label + "  (ch " + e.channel + ")", 12, line, 0x303030);
            line += 10;
            if (line > 112) {
                break;
            }
        }
    }
}
