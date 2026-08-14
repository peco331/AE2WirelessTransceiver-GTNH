package cn.gtnh.ae2wtx.client.screen;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import cn.gtnh.ae2wtx.gui.LabeledContainer;
import cn.gtnh.ae2wtx.network.LabelApplyPacket;
import cn.gtnh.ae2wtx.network.LabelDeletePacket;
import cn.gtnh.ae2wtx.network.LabelListRequestPacket;
import cn.gtnh.ae2wtx.network.LabelListResponsePacket;
import cn.gtnh.ae2wtx.network.NetworkHandler;

/**
 * Label management GUI for the labeled wireless transceiver, matching
 * ExtendedAE_Plus' LabeledWirelessTransceiverScreen layout (256x156):
 * search box, New/Del/Set/DC icon buttons, scrollable label list, info panel.
 */
public class LabeledTransceiverGui extends GuiContainer {

    private static final ResourceLocation TEX = new ResourceLocation("ae2wtx", "textures/gui/lable_wireless_transceiver_gui.png");
    private static final int BTN_U = 2;
    private static final int BTN_V = 159;
    private static final int BTN_W = 28;
    private static final int BTN_H = 16;

    private static final int LIST_X = 9;
    private static final int LIST_Y = 27;
    private static final int LIST_W = 110;
    private static final int LIST_H = 114;
    private static final int ROW_H = 11;
    private static final int VISIBLE_ROWS = LIST_H / ROW_H;
    private static final int SCROLL_X = 123;
    private static final int SCROLL_Y = 21;
    private static final int SCROLL_W = 6;
    private static final int SCROLL_H = 121;
    private static final int INFO_MAX_WIDTH = 116;

    private final EntityPlayer player;
    private final int x;
    private final int y;
    private final int z;

    private GuiTextField searchBox;
    private final List<Entry> entries = new ArrayList<>();
    private final List<Entry> filtered = new ArrayList<>();
    private int scrollOffset = 0;
    private int selectedIndex = -1;
    private String lastSelectedLabel = "";
    private String currentLabel = "";
    private String currentOwner = "";
    private int onlineCount = 0;
    private int usedChannels = 0;
    private int maxChannels = 0;

    public LabeledTransceiverGui(LabeledContainer container, EntityPlayer player, int x, int y, int z) {
        super(container);
        this.player = player;
        this.x = x;
        this.y = y;
        this.z = z;
        this.xSize = 256;
        this.ySize = 156;
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        int sx = guiLeft + 134;
        int sy = guiTop + 23;
        searchBox = new GuiTextField(fontRendererObj, sx, sy, 116, 9);
        searchBox.setMaxStringLength(64);
        searchBox.setEnableBackgroundDrawing(false);
        searchBox.setVisible(true);
        searchBox.setFocused(false);
        searchBox.setCanLoseFocus(true);

        int startX = guiLeft + 145;
        int startY = guiTop + 101;
        int hGap = 30;
        int vGap = 8;
        int secondColX = startX + BTN_W + hGap;
        int secondRowY = startY + BTN_H + vGap;

        buttonList.add(new IconButton(0, startX, startY, StatCollector.translateToLocal("gui.extendedae_plus.labeled_wireless.button.new")));
        buttonList.add(new IconButton(1, secondColX, startY, StatCollector.translateToLocal("gui.extendedae_plus.labeled_wireless.button.delete")));
        buttonList.add(new IconButton(2, startX, secondRowY, StatCollector.translateToLocal("gui.extendedae_plus.labeled_wireless.button.set")));
        buttonList.add(new IconButton(3, secondColX, secondRowY, StatCollector.translateToLocal("gui.extendedae_plus.labeled_wireless.button.refresh")));

        requestList();
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }

    public void updateList(List<LabelListResponsePacket.Entry> list, String currentLabel, String ownerName,
        int usedChannels, int maxChannels, int onlineCount) {
        String prevSelected = getSelectedLabel();
        this.entries.clear();
        for (LabelListResponsePacket.Entry e : list) {
            this.entries.add(new Entry(e.label, e.channel));
        }
        this.currentLabel = currentLabel == null ? "" : currentLabel;
        this.currentOwner = ownerName == null ? "" : ownerName;
        this.onlineCount = onlineCount;
        this.usedChannels = usedChannels;
        this.maxChannels = maxChannels;
        if (prevSelected != null && !prevSelected.isEmpty()) {
            this.lastSelectedLabel = prevSelected;
        } else if (this.currentLabel != null && !this.currentLabel.isEmpty()) {
            this.lastSelectedLabel = this.currentLabel;
        } else {
            this.lastSelectedLabel = "";
        }
        applyFilter();
    }

    /* ===================== actions ===================== */

    private void requestList() {
        NetworkHandler.CHANNEL.sendToServer(new LabelListRequestPacket(x, y, z));
    }

    private void sendSet(String label) {
        if (label == null) {
            label = "";
        }
        NetworkHandler.CHANNEL.sendToServer(new LabelApplyPacket(x, y, z, label));
        this.lastSelectedLabel = label;
        this.searchBox.setText("");
        requestList();
    }

    private void sendDelete() {
        String label = getSelectedLabel();
        if (label == null || label.isEmpty()) {
            label = searchBox.getText();
        }
        if (label == null) {
            label = "";
        }
        NetworkHandler.CHANNEL.sendToServer(new LabelDeletePacket(label));
        this.lastSelectedLabel = "";
        requestList();
    }

    private void sendDisconnect() {
        NetworkHandler.CHANNEL.sendToServer(new LabelApplyPacket(x, y, z, ""));
        this.lastSelectedLabel = "";
        requestList();
    }

    private String getSelectedLabel() {
        if (selectedIndex >= 0 && selectedIndex < filtered.size()) {
            return filtered.get(selectedIndex).label;
        }
        return "";
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case 0: // New
                sendSet(searchBox.getText());
                break;
            case 1: // Delete
                sendDelete();
                break;
            case 2: // Set
                sendSet(getSelectedLabel());
                break;
            case 3: // DC
                sendDisconnect();
                break;
            default:
                break;
        }
    }

    /* ===================== input ===================== */

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (searchBox.textboxKeyTyped(typedChar, keyCode)) {
            applyFilter();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        searchBox.mouseClicked(mouseX, mouseY, mouseButton);
        if (isMouseInList(mouseX, mouseY)) {
            int localY = mouseY - (guiTop + LIST_Y);
            int row = localY / ROW_H;
            int idx = scrollOffset + row;
            if (idx >= 0 && idx < filtered.size()) {
                selectedIndex = idx;
                lastSelectedLabel = filtered.get(idx).label;
            }
        } else if (isMouseInScrollbar(mouseX, mouseY)) {
            updateScrollByMouse(mouseY);
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (wheel != 0) {
            int mx = org.lwjgl.input.Mouse.getEventX() * this.width / mc.displayWidth;
            int my = this.height - org.lwjgl.input.Mouse.getEventY() * this.height / mc.displayHeight - 1;
            if (isMouseInList(mx, my) || isMouseInScrollbar(mx, my)) {
                int maxOffset = Math.max(0, filtered.size() - VISIBLE_ROWS);
                scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset + (wheel > 0 ? -1 : 1)));
            }
        }
    }

    /* ===================== rendering ===================== */

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        mc.getTextureManager().bindTexture(TEX);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);

        // list area
        drawRect(guiLeft + 9, guiTop + 27, guiLeft + 119, guiTop + 141, 0x20FFFFFF);
        // scrollbar area
        drawRect(guiLeft + 123, guiTop + 21, guiLeft + 129, guiTop + 142, 0x20000000);
        // info area
        drawRect(guiLeft + 134, guiTop + 41, guiLeft + 250, guiTop + 93, 0x10FFFFFF);

        searchBox.drawTextBox();
        renderList();
        renderScrollBar();
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        float scale = getScale();
        drawScaledText(StatCollector.translateToLocal("block.extendedae_plus.labeled_wireless_transceiver"), 8, 8, scale, 0x404040);
        drawScaledText(StatCollector.translateToLocal("gui.extendedae_plus.labeled_wireless.info"), 134, 8, scale, 0x404040);
        drawButtonTexts();
    }

    private void drawButtonTexts() {
        // foreground layer is already translated to the GUI origin, so these
        // are RELATIVE coordinates (matching the button positions in initGui)
        int startX = 145;
        int startY = 101;
        int hGap = 30;
        int vGap = 8;
        int secondColX = startX + BTN_W + hGap;
        int secondRowY = startY + BTN_H + vGap;
        drawButtonText(StatCollector.translateToLocal("gui.extendedae_plus.labeled_wireless.button.new"), startX, startY);
        drawButtonText(StatCollector.translateToLocal("gui.extendedae_plus.labeled_wireless.button.delete"), secondColX, startY);
        drawButtonText(StatCollector.translateToLocal("gui.extendedae_plus.labeled_wireless.button.set"), startX, secondRowY);
        drawButtonText(StatCollector.translateToLocal("gui.extendedae_plus.labeled_wireless.button.refresh"), secondColX, secondRowY);
    }

    private void drawButtonText(String text, int x, int y) {
        String s = fontRendererObj.trimStringToWidth(text, BTN_W - 4);
        int tx = x + (BTN_W - fontRendererObj.getStringWidth(s)) / 2;
        int ty = y + (BTN_H - fontRendererObj.FONT_HEIGHT) / 2 + 1;
        fontRendererObj.drawString(s, tx, ty, 0xFFFFFF);
    }

    private void renderList() {
        int baseX = guiLeft + LIST_X;
        int baseY = guiTop + LIST_Y;
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int idx = scrollOffset + row;
            if (idx >= filtered.size()) {
                break;
            }
            int y = baseY + row * ROW_H;
            if (idx == selectedIndex) {
                drawRect(baseX, y, baseX + LIST_W, y + ROW_H, 0x40FFFFFF);
            }
            String text = fontRendererObj.trimStringToWidth(filtered.get(idx).label, LIST_W - 2);
            int ty = y + (ROW_H - fontRendererObj.FONT_HEIGHT) / 2;
            fontRendererObj.drawString(text, baseX + 2, ty, 0x404040);
        }

        // info panel
        int infoX = guiLeft + 134;
        int infoY = guiTop + 41;
        float infoScale = getScale();
        String labelLine = StatCollector.translateToLocal("gui.extendedae_plus.labeled_wireless.current_label") + ": "
            + (currentLabel.isEmpty() ? "-" : currentLabel);
        String ownerLine = StatCollector.translateToLocal("gui.extendedae_plus.labeled_wireless.current_owner") + ": "
            + (currentOwner.isEmpty() ? StatCollector.translateToLocal("extendedae_plus.jade.owner.public") : currentOwner);
        String onlineLine = StatCollector.translateToLocal("gui.extendedae_plus.labeled_wireless.online_count") + ": " + onlineCount;
        String channelLine = maxChannels <= 0
            ? StatCollector.translateToLocalFormatted("extendedae_plus.jade.channels", usedChannels)
            : StatCollector.translateToLocalFormatted("extendedae_plus.jade.channels_of", usedChannels, maxChannels);
        drawScaledText(trim(labelLine), infoX, infoY, infoScale, 0x404040);
        drawScaledText(trim(ownerLine), infoX, infoY + 12, infoScale, 0x404040);
        drawScaledText(trim(onlineLine), infoX, infoY + 24, infoScale, 0x404040);
        drawScaledText(trim(channelLine), infoX, infoY + 36, infoScale, 0x404040);
    }

    private String trim(String text) {
        int maxWidth = (int) (INFO_MAX_WIDTH / Math.max(0.0001F, getScale()));
        return fontRendererObj.trimStringToWidth(text, maxWidth);
    }

    private void drawScaledText(String text, int x, int y, float scale, int color) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        GL11.glScalef(scale, scale, 1.0F);
        fontRendererObj.drawString(text, 0, 0, color);
        GL11.glPopMatrix();
    }

    private void renderScrollBar() {
        int total = filtered.size();
        if (total <= VISIBLE_ROWS) {
            drawRect(guiLeft + SCROLL_X, guiTop + SCROLL_Y, guiLeft + SCROLL_X + SCROLL_W, guiTop + SCROLL_Y + SCROLL_H, 0x20000000);
            return;
        }
        int maxOffset = total - VISIBLE_ROWS;
        drawRect(guiLeft + SCROLL_X, guiTop + SCROLL_Y, guiLeft + SCROLL_X + SCROLL_W, guiTop + SCROLL_Y + SCROLL_H, 0x20000000);
        int knobH = Math.max(10, (int) ((double) VISIBLE_ROWS / total * SCROLL_H));
        int knobY = guiTop + SCROLL_Y + (int) ((SCROLL_H - knobH) * (scrollOffset / (double) maxOffset));
        drawRect(guiLeft + SCROLL_X, knobY, guiLeft + SCROLL_X + SCROLL_W, knobY + knobH, 0x80FFFFFF);
    }

    private boolean isMouseInList(int mouseX, int mouseY) {
        return mouseX >= guiLeft + LIST_X && mouseX < guiLeft + LIST_X + LIST_W
            && mouseY >= guiTop + LIST_Y && mouseY < guiTop + LIST_Y + LIST_H;
    }

    private boolean isMouseInScrollbar(int mouseX, int mouseY) {
        return mouseX >= guiLeft + SCROLL_X && mouseX < guiLeft + SCROLL_X + SCROLL_W
            && mouseY >= guiTop + SCROLL_Y && mouseY < guiTop + SCROLL_Y + SCROLL_H;
    }

    private void updateScrollByMouse(int mouseY) {
        int total = filtered.size();
        if (total <= VISIBLE_ROWS) {
            return;
        }
        int maxOffset = total - VISIBLE_ROWS;
        int relativeY = mouseY - (guiTop + SCROLL_Y);
        relativeY = Math.max(0, Math.min(SCROLL_H, relativeY));
        int knobH = Math.max(10, (int) ((double) VISIBLE_ROWS / total * SCROLL_H));
        double ratio = (relativeY - knobH / 2.0D) / (double) (SCROLL_H - knobH);
        ratio = Math.max(0.0D, Math.min(1.0D, ratio));
        scrollOffset = (int) Math.round(ratio * maxOffset);
    }

    private void applyFilter() {
        String q = searchBox.getText() == null ? "" : searchBox.getText().trim();
        filtered.clear();
        if (q.isEmpty()) {
            filtered.addAll(entries);
        } else {
            for (Entry e : entries) {
                if (e.label.contains(q)) {
                    filtered.add(e);
                }
            }
        }
        scrollOffset = 0;
        selectedIndex = -1;
        if (lastSelectedLabel != null && !lastSelectedLabel.isEmpty()) {
            for (int i = 0; i < filtered.size(); i++) {
                if (filtered.get(i).label.equals(lastSelectedLabel)) {
                    selectedIndex = i;
                    ensureSelectionVisible();
                    break;
                }
            }
        }
    }

    private void ensureSelectionVisible() {
        if (selectedIndex < 0) {
            return;
        }
        int maxOffset = Math.max(0, filtered.size() - VISIBLE_ROWS);
        if (selectedIndex < scrollOffset) {
            scrollOffset = selectedIndex;
        } else if (selectedIndex >= scrollOffset + VISIBLE_ROWS) {
            scrollOffset = Math.min(maxOffset, selectedIndex - VISIBLE_ROWS + 1);
        }
    }

    private boolean isEnglish() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.getLanguageManager() != null && mc.getLanguageManager().getCurrentLanguage() != null
            && "en_US".equals(mc.getLanguageManager().getCurrentLanguage().getLanguageCode());
    }

    private float getScale() {
        return isEnglish() ? 0.75F : 1.0F;
    }

    private static final class Entry {

        final String label;
        final long channel;

        Entry(String label, long channel) {
            this.label = label;
            this.channel = channel;
        }
    }

    /** Icon button drawn from the GUI texture sheet (2,159 base / 2,177 hover / 2,195 pressed). */
    private static class IconButton extends GuiButton {

        IconButton(int id, int x, int y, String text) {
            super(id, x, y, BTN_W, BTN_H, text);
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY) {
            if (!visible) {
                return;
            }
            boolean hovered = mouseX >= xPosition && mouseY >= yPosition && mouseX < xPosition + width
                && mouseY < yPosition + height;
            int v = BTN_V;
            if (hovered) {
                v = 177;
            }
            TextureManager tm = mc.getTextureManager();
            tm.bindTexture(TEX);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            drawTexturedModalRect(xPosition, yPosition, BTN_U, v, BTN_W, BTN_H);
        }
    }
}
