package cn.gtnh.ae2wtx.client.screen;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Keyboard;

import cn.gtnh.ae2wtx.gui.FrequencyContainer;
import cn.gtnh.ae2wtx.network.NetworkHandler;
import cn.gtnh.ae2wtx.network.SetWirelessFrequencyPacket;

/** Frequency input GUI for the plain wireless transceiver. */
public class FrequencyInputGui extends GuiContainer {

    private static final ResourceLocation BG = new ResourceLocation("ae2wtx", "textures/gui/freq_gui.png");

    private final EntityPlayer player;
    private final int x;
    private final int y;
    private final int z;

    private GuiTextField freqField;

    public FrequencyInputGui(FrequencyContainer container, EntityPlayer player, int x, int y, int z) {
        super(container);
        this.player = player;
        this.x = x;
        this.y = y;
        this.z = z;
        this.xSize = 176;
        this.ySize = 64;
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        freqField = new GuiTextField(fontRendererObj, guiLeft + 30, guiTop + 20, 100, 16);
        freqField.setMaxStringLength(19);
        freqField.setText("1");
        freqField.setFocused(true);
        buttonList.add(new GuiButton(0, guiLeft + 134, guiTop + 18, 40, 20, "OK"));
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            long freq;
            try {
                freq = Long.parseLong(freqField.getText().trim());
            } catch (NumberFormatException ignored) {
                freq = 0L;
            }
            if (freq < 0) {
                freq = 0;
            }
            NetworkHandler.CHANNEL.sendToServer(new SetWirelessFrequencyPacket(x, y, z, freq));
            mc.thePlayer.closeScreen();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (freqField.textboxKeyTyped(typedChar, keyCode)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        freqField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        mc.getTextureManager().bindTexture(BG);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRendererObj.drawString("Frequency", 8, 6, 0x404040);
        freqField.drawTextBox();
    }
}
