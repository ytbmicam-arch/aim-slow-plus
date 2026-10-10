package io.github.aimslowplus.screen;

import io.github.aimslowplus.config.AimSlowConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.DoubleFunction;

/** Two-column settings screen. Changes apply live and are written to disk when the screen closes. */
public final class AimSlowConfigScreen extends Screen {
    private static final int COLUMN_WIDTH = 150;
    private static final int COLUMN_GAP = 10;
    private static final int ROW_HEIGHT = 24;
    private static final int TOP = 36;

    private final Screen parent;

    public AimSlowConfigScreen(Screen parent) {
        super(Component.translatable("aimslowplus.screen.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        AimSlowConfig c = AimSlowConfig.get();
        int left = this.width / 2 - COLUMN_WIDTH - COLUMN_GAP / 2;
        int right = this.width / 2 + COLUMN_GAP / 2;

        // ---- Left column ----
        addRenderableWidget(toggle(left, row(0), "enabled", c.enabled, v -> c.enabled = v));
        addRenderableWidget(slider(left, row(1), "strength", 0, 100, 1, c.slowdownStrength * 100.0,
                v -> String.format("%.0f%%", v), v -> c.slowdownStrength = v / 100.0));
        addRenderableWidget(slider(left, row(2), "bubble", AimSlowConfig.BUBBLE_MIN, AimSlowConfig.BUBBLE_MAX, 0.5,
                c.bubbleAngle, v -> String.format("%.1f\u00B0", v), v -> c.bubbleAngle = v));
        addRenderableWidget(slider(left, row(3), "min_multiplier", AimSlowConfig.MIN_MULT_MIN * 100.0,
                AimSlowConfig.MIN_MULT_MAX * 100.0, 1, c.minSensitivityMultiplier * 100.0,
                v -> String.format("%.0f%%", v), v -> c.minSensitivityMultiplier = v / 100.0));
        addRenderableWidget(slider(left, row(4), "range", AimSlowConfig.RANGE_MIN, AimSlowConfig.RANGE_MAX, 1,
                c.maxRange, v -> String.format("%.0f", v), v -> c.maxRange = v));

        // ---- Right column ----
        addRenderableWidget(slider(right, row(0), "activation", 0, 100, 1, c.activationSmoothness * 100.0,
                v -> v < 1 ? Component.translatable("aimslowplus.value.instant").getString() : String.format("%.0f%%", v),
                v -> c.activationSmoothness = v / 100.0));
        addRenderableWidget(slider(right, row(1), "release", 0, 100, 1, c.releaseSmoothness * 100.0,
                v -> v < 1 ? Component.translatable("aimslowplus.value.instant").getString() : String.format("%.0f%%", v),
                v -> c.releaseSmoothness = v / 100.0));
        addRenderableWidget(toggle(right, row(2), "players_only", c.playersOnly, v -> c.playersOnly = v));
        addRenderableWidget(toggle(right, row(3), "ignore_teammates", c.ignoreTeammates, v -> c.ignoreTeammates = v));
        addRenderableWidget(toggle(right, row(4), "line_of_sight", c.requireLineOfSight, v -> c.requireLineOfSight = v));

        // ---- Bottom buttons ----
        int bottomY = row(5) + 8;
        addRenderableWidget(Button.builder(Component.translatable("aimslowplus.button.reset"), button -> {
                    AimSlowConfig.get().resetToDefaults();
                    AimSlowConfig.save();
                    // Re-open a fresh screen so every widget shows the default values.
                    this.minecraft.setScreen(new AimSlowConfigScreen(this.parent));
                })
                .pos(left, bottomY)
                .size(COLUMN_WIDTH, 20)
                .tooltip(Tooltip.create(Component.translatable("aimslowplus.button.reset.tooltip")))
                .build());
        addRenderableWidget(Button.builder(Component.translatable("aimslowplus.button.done"), button -> this.onClose())
                .pos(right, bottomY)
                .size(COLUMN_WIDTH, 20)
                .build());
    }

    private static int row(int index) {
        return TOP + index * ROW_HEIGHT;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 14, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    /** Called by vanilla whenever this screen is replaced or closed, whatever the reason. */
    @Override
    public void removed() {
        AimSlowConfig.save();
    }

    // ------------------------------------------------------------------ widgets

    private static Button toggle(int x, int y, String key, boolean initial, Consumer<Boolean> setter) {
        boolean[] state = {initial};
        Button button = Button.builder(toggleLabel(key, initial), b -> {
                    state[0] = !state[0];
                    setter.accept(state[0]);
                    b.setMessage(toggleLabel(key, state[0]));
                })
                .pos(x, y)
                .size(COLUMN_WIDTH, 20)
                .tooltip(Tooltip.create(Component.translatable("aimslowplus.option." + key + ".tooltip")))
                .build();
        return button;
    }

    private static Component toggleLabel(String key, boolean value) {
        return Component.translatable("aimslowplus.option." + key,
                Component.translatable(value ? "aimslowplus.value.on" : "aimslowplus.value.off"));
    }

    private static ConfigSlider slider(int x, int y, String key, double min, double max, double step, double current,
                                       DoubleFunction<String> format, Consumer<Double> setter) {
        ConfigSlider slider = new ConfigSlider(x, y, COLUMN_WIDTH, 20, key, min, max, step, current, format, setter);
        slider.setTooltip(Tooltip.create(Component.translatable("aimslowplus.option." + key + ".tooltip")));
        return slider;
    }

    /** Slider whose real range is [min, max] (vanilla sliders work internally in [0, 1]). */
    private static final class ConfigSlider extends AbstractSliderButton {
        private final String key;
        private final double min;
        private final double max;
        private final double step;
        private final DoubleFunction<String> format;
        private final Consumer<Double> setter;

        ConfigSlider(int x, int y, int width, int height, String key, double min, double max, double step,
                     double current, DoubleFunction<String> format, Consumer<Double> setter) {
            super(x, y, width, height, Component.empty(), 0.0);
            this.key = key;
            this.min = min;
            this.max = max;
            this.step = step;
            this.format = format;
            this.setter = setter;
            this.value = toSlider(snap(current));
            updateMessage();
        }

        private double snap(double actual) {
            double snapped = Math.round((actual - min) / step) * step + min;
            return Math.max(min, Math.min(max, snapped));
        }

        private double toSlider(double actual) {
            return (actual - min) / (max - min);
        }

        private double actualValue() {
            return snap(min + this.value * (max - min));
        }

        @Override
        protected void updateMessage() {
            if (this.format == null) {
                return; // called by the super constructor before our fields exist
            }
            this.setMessage(Component.translatable("aimslowplus.option." + key, format.apply(actualValue())));
        }

        @Override
        protected void applyValue() {
            setter.accept(actualValue());
        }
    }
}
