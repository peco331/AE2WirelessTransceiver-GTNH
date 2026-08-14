package cn.gtnh.ae2wtx.client.screen;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;

import cn.gtnh.ae2wtx.network.NetworkHandler;
import cn.gtnh.ae2wtx.network.SetWirelessFrequencyPacket;

/**
 * Frequency input screen, matching ExtendedAE_Plus' FrequencyInputScreen:
 * centered layout, title, numeric-only input field, confirm/cancel, enter/esc.
 */
public class FrequencyInputGui extends GuiScreen {

    private final int x;
    private final int y;
    private final int z;
    private final long currentFrequency;

    private GuiTextField frequencyInput;

    public FrequencyInputGui(int x, int y, int z, long currentFrequency) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.currentFrequency = currentFrequency;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        frequencyInput = new GuiTextField(fontRendererObj, centerX - 100, centerY - 10, 200, 20);
        frequencyInput.setMaxStringLength(19); // long max is 19 digits
        frequencyInput.setText(String.valueOf(currentFrequency));
        frequencyInput.setFocused(true);

        buttonList.add(new GuiButton(0, centerX - 105, centerY + 20, 100, 20,
            StatCollector.translateToLocal("extendedae_plus.screen.frequency_input.confirm")));
        buttonList.add(new GuiButton(1, centerX + 5, centerY + 20, 100, 20,
            StatCollector.translateToLocal("gui.cancel")));
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            onConfirm();
        } else if (button.id == 1) {
            mc.displayGuiScreen(null);
        }
    }

    private void onConfirm() {
        try {
            String text = frequencyInput.getText();
            if (text == null || text.isEmpty()) {
                text = "0";
            }
            long frequency = Long.parseLong(text);
            if (frequency < 0) {
                frequency = 0;
            }
            NetworkHandler.CHANNEL.sendToServer(new SetWirelessFrequencyPacket(x, y, z, frequency));
            mc.displayGuiScreen(null);
        } catch (NumberFormatException ignored) {
            // invalid input, do nothing
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        // only digits allowed (matching EAEP's numeric filter)
        if (Character.isDigit(typedChar) || keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE
            || keyCode == Keyboard.KEY_LEFT || keyCode == Keyboard.KEY_RIGHT || keyCode == Keyboard.KEY_HOME
            || keyCode == Keyboard.KEY_END) {
            if (frequencyInput.textboxKeyTyped(typedChar, keyCode)) {
                return;
            }
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            onConfirm();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        frequencyInput.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawCenteredString(fontRendererObj,
            StatCollector.translateToLocal("extendedae_plus.screen.frequency_input.title"), this.width / 2,
            this.height / 2 - 40, 0xFFFFFF);
        frequencyInput.drawTextBox();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
