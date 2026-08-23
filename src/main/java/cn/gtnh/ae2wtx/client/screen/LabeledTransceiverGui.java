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
import cn.gtnh.ae2wtx.wireless.LabelNetworkRegistry;

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
    /** Retry a dropped or throttled list request after two seconds. */
    private static final int PAGE_REQUEST_TIMEOUT_TICKS = 40;

    private final EntityPlayer player;
    private final LabeledContainer container;
    private final int dimension;
    private final int x;
    private final int y;
    private final int z;

    private GuiTextField searchBox;
    private GuiButton newButton;
    private GuiButton deleteButton;
    private GuiButton setButton;
    private GuiButton disconnectButton;
    private final List<Entry> entries = new ArrayList<>();
    private final List<Entry> filtered = new ArrayList<>();
    private int scrollOffset = 0;
    /** Last clicked row (used by Set/DC). */
    private int selectedIndex = -1;
    /** Multi-selection set for Delete (indices into the filtered list). */
    private final java.util.Set<Integer> selectedIndices = new java.util.HashSet<>();
    private String lastSelectedLabel = "";
    private String currentLabel = "";
    private String currentOwner = "";
    private String inspectedLabel = "";
    private boolean showingSelectedBand = false;
    private int onlineCount = 0;
    private int endpointCount = 0;
    private int usedChannels = 0;
    private int maxChannels = 0;
    private int networkChannels = 0;
    private int nextRequestId = 0;
    private int latestRequestId = 0;
    private int currentPage = 0;
    private int pageSize = LabelListRequestPacket.DEFAULT_PAGE_SIZE;
    private int totalEntries = 0;
    private int pageCount = 1;
    private int pendingSearchTicks = -1;
    private boolean pageRequestInFlight = false;
    private int pageRequestAge = 0;
    private int activeRequestPage = 0;
    private int activeRequestScrollOffset = 0;
    private String activeRequestQuery = "";
    private String activeRequestInspectLabel = "";
    private boolean queuedPageRequest = false;
    private int queuedRequestPage = 0;
    private int queuedRequestScrollOffset = 0;
    private String queuedRequestQuery = "";
    private String queuedRequestInspectLabel = "";
    /** initGui runs before vanilla assigns the server window id. */
    private boolean initialRequestPending = true;

    public LabeledTransceiverGui(LabeledContainer container, EntityPlayer player, int x, int y, int z) {
        super(container);
        this.player = player;
        this.container = container;
        this.dimension = player.worldObj.provider.dimensionId;
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
        // search box at the EAEP original position (top right, borderless)
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

        newButton = new IconButton(0, startX, startY,
            StatCollector.translateToLocal("gui.extendedae_plus.labeled_wireless.button.new"));
        deleteButton = new IconButton(1, secondColX, startY,
            StatCollector.translateToLocal("gui.extendedae_plus.labeled_wireless.button.delete"));
        setButton = new IconButton(2, startX, secondRowY,
            StatCollector.translateToLocal("gui.extendedae_plus.labeled_wireless.button.set"));
        disconnectButton = new IconButton(3, secondColX, secondRowY,
            StatCollector.translateToLocal("gui.extendedae_plus.labeled_wireless.button.refresh"));
        buttonList.add(newButton);
        buttonList.add(deleteButton);
        buttonList.add(setButton);
        buttonList.add(disconnectButton);
        initialRequestPending = true;
        updateActionButtons();
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }

    public void updateList(List<LabelListResponsePacket.Entry> list, String currentLabel, String ownerName,
        String inspectedLabel, int usedChannels, int maxChannels, int onlineCount, int endpointCount,
        int networkChannels, int page, int responsePageSize, int responseTotalEntries, int responsePageCount) {
        pageRequestInFlight = false;
        pageRequestAge = 0;
        // A newer scroll/search request arrived while this one was in flight.
        // Its state is authoritative, so do not briefly render the older page.
        if (queuedPageRequest) {
            sendQueuedPageRequest();
            return;
        }
        String prevSelected = getSelectedLabel();
        java.util.Set<String> prevSelectedLabels = new java.util.HashSet<>();
        for (int index : selectedIndices) {
            if (index >= 0 && index < filtered.size()) {
                prevSelectedLabels.add(filtered.get(index).label);
            }
        }
        this.entries.clear();
        for (LabelListResponsePacket.Entry e : list) {
            this.entries.add(new Entry(e.label, e.channel));
        }
        this.currentLabel = currentLabel == null ? "" : currentLabel;
        this.currentOwner = ownerName == null ? "" : ownerName;
        this.inspectedLabel = inspectedLabel == null ? "" : inspectedLabel;
        this.showingSelectedBand = LabelNetworkRegistry.normalizeLabel(activeRequestInspectLabel) != null;
        this.onlineCount = onlineCount;
        this.endpointCount = endpointCount;
        this.usedChannels = usedChannels;
        this.maxChannels = maxChannels;
        this.networkChannels = networkChannels;
        this.currentPage = page;
        this.pageSize = responsePageSize;
        this.totalEntries = responseTotalEntries;
        this.pageCount = responsePageCount;
        if (prevSelected != null && !prevSelected.isEmpty()) {
            this.lastSelectedLabel = prevSelected;
        } else if (this.currentLabel != null && !this.currentLabel.isEmpty()) {
            this.lastSelectedLabel = this.currentLabel;
        } else {
            this.lastSelectedLabel = "";
        }
        applyFilter();
        for (int i = 0; i < filtered.size(); i++) {
            if (prevSelectedLabels.contains(filtered.get(i).label)) {
                selectedIndices.add(i);
            }
        }
        int maxOffset = Math.max(0, filtered.size() - VISIBLE_ROWS);
        this.scrollOffset = Math.max(0, Math.min(maxOffset, activeRequestScrollOffset));
        this.activeRequestScrollOffset = 0;
    }

    /* ===================== actions ===================== */

    private void requestList() {
        requestPage(0, 0);
    }

    private void requestPage(int page, int desiredScrollOffset) {
        queuedRequestPage = Math.max(0, page);
        queuedRequestScrollOffset = Math.max(0, desiredScrollOffset);
        queuedRequestQuery = searchBox == null || searchBox.getText() == null ? "" : searchBox.getText();
        queuedRequestInspectLabel = getSelectedLabel();
        if (LabelNetworkRegistry.normalizeLabel(queuedRequestInspectLabel) == null) {
            queuedRequestInspectLabel = lastSelectedLabel == null ? "" : lastSelectedLabel;
        }
        queuedPageRequest = true;
        if (!pageRequestInFlight) {
            sendQueuedPageRequest();
        }
    }

    /** Keep one request in flight and coalesce rapid scrolling/searching to the newest desired page. */
    private void sendQueuedPageRequest() {
        if (!queuedPageRequest) {
            return;
        }
        activeRequestPage = queuedRequestPage;
        activeRequestScrollOffset = queuedRequestScrollOffset;
        activeRequestQuery = queuedRequestQuery;
        activeRequestInspectLabel = queuedRequestInspectLabel;
        queuedPageRequest = false;
        int requestId = ++nextRequestId;
        latestRequestId = requestId;
        pageRequestInFlight = true;
        pageRequestAge = 0;
        NetworkHandler.CHANNEL.sendToServer(
            new LabelListRequestPacket(
                dimension,
                x,
                y,
                z,
                container.windowId,
                requestId,
                activeRequestPage,
                pageSize,
                activeRequestQuery,
                activeRequestInspectLabel));
    }

    /** Reject delayed responses belonging to another block, window, or request. */
    public boolean acceptsResponse(int responseDimension, int responseX, int responseY, int responseZ,
        int responseWindowId, int responseRequestId) {
        return responseDimension == dimension && responseX == x && responseY == y && responseZ == z
            && responseWindowId == container.windowId && responseRequestId == latestRequestId;
    }

    private void sendSet(String label) {
        String normalized = LabelNetworkRegistry.normalizeLabel(label);
        if (normalized == null) {
            return;
        }
        NetworkHandler.CHANNEL
            .sendToServer(new LabelApplyPacket(dimension, x, y, z, container.windowId, normalized));
        this.lastSelectedLabel = normalized;
        this.searchBox.setText("");
        requestPage(0, 0);
    }

    /**
     * Delete every selected band (multi-select: click rows to toggle), or the
     * search-box text as a fallback when nothing is selected.
     */
    private void sendDelete() {
        java.util.List<String> labels = new ArrayList<>();
        for (int idx : selectedIndices) {
            if (idx >= 0 && idx < filtered.size()) {
                labels.add(filtered.get(idx).label);
            }
        }
        if (labels.isEmpty()) {
            String t = searchBox.getText();
            if (t != null && !t.isEmpty()) {
                labels.add(t);
            }
        }
        if (!labels.isEmpty()) {
            NetworkHandler.CHANNEL
                .sendToServer(new LabelDeletePacket(dimension, x, y, z, container.windowId, labels));
        }
        this.lastSelectedLabel = "";
        this.selectedIndices.clear();
        this.selectedIndex = -1;
        requestPage(currentPage, scrollOffset);
    }

    private void sendDisconnect() {
        NetworkHandler.CHANNEL.sendToServer(new LabelApplyPacket(dimension, x, y, z, container.windowId, ""));
        this.lastSelectedLabel = "";
        requestPage(currentPage, scrollOffset);
    }

    private String getSelectedLabel() {
        if (selectedIndex >= 0 && selectedIndex < filtered.size()) {
            return filtered.get(selectedIndex).label;
        }
        return "";
    }

    private void clearSelectionAndRefresh() {
        if (selectedIndex < 0 && selectedIndices.isEmpty()) {
            return;
        }
        selectedIndex = -1;
        selectedIndices.clear();
        lastSelectedLabel = "";
        requestPage(currentPage, scrollOffset);
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
        String before = searchBox.getText();
        if (searchBox.textboxKeyTyped(typedChar, keyCode)) {
            if (!java.util.Objects.equals(before, searchBox.getText())) {
                pendingSearchTicks = 5;
            }
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        // NetHandlerPlayClient assigns the server window id only after
        // displayGuiScreen/initGui returns. Sending in initGui therefore used
        // window id 0 and the server correctly rejected the first list request.
        if (initialRequestPending && container.windowId != 0) {
            initialRequestPending = false;
            requestList();
        }
        if (pendingSearchTicks > 0 && --pendingSearchTicks == 0) {
            pendingSearchTicks = -1;
            requestPage(0, 0);
        }
        if (pageRequestInFlight && ++pageRequestAge >= PAGE_REQUEST_TIMEOUT_TICKS) {
            pageRequestInFlight = false;
            pageRequestAge = 0;
            if (!queuedPageRequest) {
                queuedRequestPage = activeRequestPage;
                queuedRequestScrollOffset = activeRequestScrollOffset;
                queuedRequestQuery = activeRequestQuery;
                queuedRequestInspectLabel = activeRequestInspectLabel;
                queuedPageRequest = true;
            }
        }
        if (!pageRequestInFlight && queuedPageRequest) {
            sendQueuedPageRequest();
        }
        updateActionButtons();
    }

    private void updateActionButtons() {
        boolean validSearch = searchBox != null && LabelNetworkRegistry.normalizeLabel(searchBox.getText()) != null;
        boolean validSelection = LabelNetworkRegistry.normalizeLabel(getSelectedLabel()) != null;
        if (newButton != null) {
            newButton.enabled = validSearch;
        }
        if (setButton != null) {
            setButton.enabled = validSelection;
        }
        if (deleteButton != null) {
            deleteButton.enabled = validSearch || !selectedIndices.isEmpty();
        }
        if (disconnectButton != null) {
            disconnectButton.enabled = LabelNetworkRegistry.normalizeLabel(currentLabel) != null;
        }
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
                // Windows-style: plain click = single select,
                // ctrl+click = toggle multi-selection
                if (isCtrlKeyDown()) {
                    if (selectedIndices.contains(idx)) {
                        selectedIndices.remove(idx);
                    } else {
                        selectedIndices.add(idx);
                    }
                } else {
                    selectedIndices.clear();
                    selectedIndices.add(idx);
                }
                selectedIndex = idx;
                lastSelectedLabel = filtered.get(idx).label;
                requestPage(currentPage, scrollOffset);
            } else {
                clearSelectionAndRefresh();
            }
        } else if (isMouseInScrollbar(mouseX, mouseY)) {
            updateScrollByMouse(mouseY);
        } else {
            clearSelectionAndRefresh();
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
                if (wheel > 0) {
                    if (scrollOffset > 0) {
                        scrollOffset--;
                    } else if (currentPage > 0) {
                        requestPage(currentPage - 1, Math.max(0, pageSize - VISIBLE_ROWS));
                    }
                } else if (scrollOffset < maxOffset) {
                    scrollOffset++;
                } else if (currentPage + 1 < pageCount) {
                    requestPage(currentPage + 1, 0);
                }
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
            if (selectedIndices.contains(idx)) {
                drawRect(baseX, y, baseX + LIST_W, y + ROW_H, 0x60FFFFFF);
            }
            String text = fontRendererObj.trimStringToWidth(filtered.get(idx).label, LIST_W - 2);
            int ty = y + (ROW_H - fontRendererObj.FONT_HEIGHT) / 2;
            fontRendererObj.drawString(text, baseX + 2, ty, 0x404040);
        }

        // info panel
        int infoX = guiLeft + 134;
        int infoY = guiTop + 41;
        float infoScale = getScale();
        String displayLabel = inspectedLabel.isEmpty() ? currentLabel : inspectedLabel;
        String labelKey = showingSelectedBand ? "gui.ae2wtx.labeled_wireless.selected_label"
            : "gui.extendedae_plus.labeled_wireless.current_label";
        String labelLine = StatCollector.translateToLocal(labelKey) + ": "
            + (displayLabel.isEmpty() ? "-" : displayLabel);
        String ownerLine = StatCollector.translateToLocal("gui.extendedae_plus.labeled_wireless.current_owner") + ": "
            + (currentOwner.isEmpty() ? StatCollector.translateToLocal("extendedae_plus.jade.owner.public") : currentOwner);
        String onlineLine = StatCollector.translateToLocal("gui.extendedae_plus.labeled_wireless.online_count") + ": " + onlineCount;
        boolean inspectingOtherBand = showingSelectedBand && !displayLabel.equals(currentLabel);
        String channelLine = inspectingOtherBand
            ? StatCollector.translateToLocal("gui.ae2wtx.labeled_wireless.endpoint_count") + ": " + endpointCount
            : maxChannels <= 0
                ? StatCollector.translateToLocalFormatted("extendedae_plus.jade.channels", usedChannels)
                : StatCollector.translateToLocalFormatted("extendedae_plus.jade.channels_of", usedChannels, maxChannels);
        // whole-frequency (label) usage across ALL endpoints; denominator is the
        // real capacity granted by the ME network (32 dense, ∞ in infinite mode)
        boolean infinite = maxChannels <= 0 || maxChannels >= 1_000_000;
        String networkPrefix = StatCollector.translateToLocal("extendedae_plus.jade.channels_network_label");
        String networkValue;
        int networkColor = 0x404040;
        if (infinite) {
            networkValue = networkChannels + "/\u221E";
        } else {
            networkValue = networkChannels + "/" + maxChannels;
            // over capacity -> red, exactly full -> yellow
            if (networkChannels > maxChannels) {
                networkColor = 0xE05555;
            } else if (networkChannels == maxChannels) {
                networkColor = 0xE0E055;
            }
        }
        // 5 rows at 10px pitch so the last row stays inside the EAEP info panel
        // (panel spans y=41..92; 41+4*10+10=91 < 92)
        drawScaledText(trim(labelLine), infoX, infoY, infoScale, 0x404040);
        drawScaledText(trim(ownerLine), infoX, infoY + 10, infoScale, 0x404040);
        drawScaledText(trim(onlineLine), infoX, infoY + 20, infoScale, 0x404040);
        drawScaledText(trim(channelLine), infoX, infoY + 30, infoScale, 0x404040);
        drawScaledText(trim(networkPrefix), infoX, infoY + 40, infoScale, 0x404040);
        drawScaledText(trim(networkValue), infoX + (int) (fontRendererObj.getStringWidth(networkPrefix) * infoScale),
            infoY + 40, infoScale, networkColor);
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
        int total = totalEntries;
        if (total <= VISIBLE_ROWS) {
            drawRect(guiLeft + SCROLL_X, guiTop + SCROLL_Y, guiLeft + SCROLL_X + SCROLL_W, guiTop + SCROLL_Y + SCROLL_H, 0x20000000);
            return;
        }
        int maxOffset = Math.max(1, total - VISIBLE_ROWS);
        drawRect(guiLeft + SCROLL_X, guiTop + SCROLL_Y, guiLeft + SCROLL_X + SCROLL_W, guiTop + SCROLL_Y + SCROLL_H, 0x20000000);
        int knobH = Math.max(10, (int) ((double) VISIBLE_ROWS / total * SCROLL_H));
        int globalOffset = Math.min(maxOffset, currentPage * pageSize + scrollOffset);
        int knobY = guiTop + SCROLL_Y + (int) ((SCROLL_H - knobH) * (globalOffset / (double) maxOffset));
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
        int total = totalEntries;
        if (total <= VISIBLE_ROWS) {
            return;
        }
        int maxOffset = Math.max(0, total - VISIBLE_ROWS);
        int relativeY = mouseY - (guiTop + SCROLL_Y);
        relativeY = Math.max(0, Math.min(SCROLL_H, relativeY));
        int knobH = Math.max(10, (int) ((double) VISIBLE_ROWS / total * SCROLL_H));
        double ratio = (relativeY - knobH / 2.0D) / (double) (SCROLL_H - knobH);
        ratio = Math.max(0.0D, Math.min(1.0D, ratio));
        int globalOffset = (int) Math.round(ratio * maxOffset);
        // A page only has (pageSize - visibleRows) valid first-row offsets.
        // Map the otherwise unreachable gap at each page boundary to the next
        // page, so a short final page (for example item 65 of 65) remains
        // reachable by dragging the scrollbar all the way down.
        int safePageSize = Math.max(1, pageSize);
        int requestedPage = Math.min(pageCount - 1, (globalOffset + VISIBLE_ROWS - 1) / safePageSize);
        int requestedLocalOffset = Math.max(0, globalOffset - requestedPage * safePageSize);
        if (requestedPage == currentPage) {
            scrollOffset = Math.max(0, Math.min(Math.max(0, filtered.size() - VISIBLE_ROWS), requestedLocalOffset));
        } else {
            requestPage(requestedPage, requestedLocalOffset);
        }
    }

    private void applyFilter() {
        filtered.clear();
        filtered.addAll(entries);
        scrollOffset = 0;
        selectedIndex = -1;
        selectedIndices.clear();
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
