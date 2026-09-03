package com.boyben.holobuild.client;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The manual, inside the game.
 *
 * It exists because of a real failure: the Selection Wand needs SHIFT for the
 * second corner, nothing said so, and the wand was reported as broken. A README
 * on a website does not help somebody standing in a world with a wand in their
 * hand - the answer has to be reachable from where the question comes up, which
 * is the menu.
 */
public class GuideScreen extends Screen {

    private static final int PAGE_WIDTH = 300;
    private static final int TEXT_TOP = 66;
    private static final int LINE_HEIGHT = 12;

    /** Where to go back to when this closes - the picker, or nothing. */
    private final Screen parent;

    /**
     * Which page is open, kept between visits.
     *
     * Someone who closes the guide to try something out and comes back wants
     * the page they were reading, not the first one all over again.
     */
    private static int page = 0;

    private Button previous;
    private Button next;

    /**
     * How long a page takes to appear, and when the current one started.
     *
     * Short on purpose. A page that fades in slowly feels like the game is
     * thinking; this is just enough that the text arrives rather than snapping
     * into place mid-sentence.
     */
    private static final long TURN_MILLIS = 160;
    private long turnedAt;

    public GuideScreen(Screen parent) {
        super(Component.translatable("screen.holobuild.guide_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        page = GuideBook.clamp(page);
        turnedAt = Util.getMillis();

        int left = (width - PAGE_WIDTH) / 2;
        int bottom = height - 40;

        previous = Button.builder(Component.literal("◀"), b -> turn(-1))
                .bounds(left, bottom, 24, 20)
                .build();
        next = Button.builder(Component.literal("▶"), b -> turn(1))
                .bounds(left + PAGE_WIDTH - 24, bottom, 24, 20)
                .build();

        addRenderableWidget(previous);
        addRenderableWidget(next);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + PAGE_WIDTH / 2 - 50, bottom, 100, 20)
                .build());

        updateArrows();
    }

    private void turn(int by) {
        int wanted = GuideBook.clamp(page + by);
        if (wanted == page) {
            // Already at the end of the book. No sound, no fade - pretending
            // something happened is worse than nothing happening.
            return;
        }
        page = wanted;
        turnedAt = Util.getMillis();
        UiSounds.page();
        updateArrows();
    }

    /** Greys out an arrow that would lead off the end of the book. */
    private void updateArrows() {
        previous.active = page > 0;
        next.active = page < GuideBook.pageCount() - 1;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        // The arrow keys do the obvious thing, so the mouse is not the only way.
        if (key == 263) {          // left
            turn(-1);
            return true;
        }
        if (key == 262) {          // right
            turn(1);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int left = (width - PAGE_WIDTH) / 2;
        graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);

        GuideBook.Page current = GuideBook.PAGES.get(GuideBook.clamp(page));
        graphics.drawCenteredString(font, Component.translatable(current.titleKey())
                .withStyle(ChatFormatting.YELLOW), width / 2, 44, 0xFFFF55);

        float arrived = Fade.smooth(Fade.progress(turnedAt, Util.getMillis(), TURN_MILLIS));
        int colour = Fade.colour(0xDDDDDD, arrived, 255);

        int y = TEXT_TOP;
        for (String line : current.lineKeys()) {
            // Wrapped rather than trusted to fit. German says the same thing in
            // more letters than English, and the player can change the GUI
            // scale at any time - counting characters when writing the text
            // would only be right for one language at one size.
            for (FormattedCharSequence part : font.split(Component.translatable(line), PAGE_WIDTH)) {
                graphics.drawString(font, part, left, y, colour, false);
                y += LINE_HEIGHT;
            }
        }

        graphics.drawCenteredString(font,
                Component.translatable("screen.holobuild.guide_page",
                        page + 1, GuideBook.pageCount()),
                width / 2, height - 58, 0xAAAAAA);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
