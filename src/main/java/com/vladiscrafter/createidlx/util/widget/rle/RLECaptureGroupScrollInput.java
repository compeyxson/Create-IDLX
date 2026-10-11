package com.vladiscrafter.createidlx.util.widget.rle;

import com.simibubi.create.AllKeys;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.foundation.gui.widget.Label;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.vladiscrafter.createidlx.util.attachedLabel.AttachedLabelPart.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.apache.commons.lang3.math.Fraction;
import org.lwjgl.glfw.GLFW;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class RLECaptureGroupScrollInput extends ScrollInput {

    protected CaptureGroup pcg;
    protected CaptureGroup cg;

    protected Function<Number, Component> formatter;
    protected HashMap<Class<?>, Number> cache;
    protected BiConsumer<CaptureGroup, CaptureGroup> onScroll;

    protected final Component title = Component.literal("Capture Group Value");
    protected final Component scrollToModifyChars = Component.literal("Scroll to Modify the Amount");
    protected final Component scrollToModifyDecimal = Component.literal("Scroll to Modify the Decimal Fraction");
    protected final Component scrollToModifyPercent = Component.literal("Scroll to Modify the Percentage");
    protected final Component scrollToModifyPropDen = Component.literal("Scroll to Modify the Denominator");
    protected final Component scrollToModifyPropNum = Component.literal("Ctrl to Modify the Numerator");
    protected final Component typeTitle = Component.literal("Capture Group Type");
    protected final Component altToCycleType = Component.literal("Alt to Select the Capture Group Type");
    protected final Component roundingTitle = Component.literal("Capture Group Type");
    protected final Component middleToCycleRounding = Component.literal("Middle-Click to Cycle Between Rounding Options");

    protected final Integer NEW_INTEGER = 0;
    protected final Fraction NEW_FRACTION = Fraction.getFraction(1, 2);
    protected final BigDecimal NEW_DECIMAL = BigDecimal.valueOf(0.5);

    public RLECaptureGroupScrollInput(int xIn, int yIn, int widthIn, int heightIn, CaptureGroup captureGroup, HashMap<Class<?>, Number> cache) {
        super(xIn, yIn, widthIn, heightIn);

        this.cg = captureGroup;
        this.pcg = cacheCaptureGroup(cg);

        this.cache = cache;

        updateFormatter();

        this.setFGColor(0xFFFFFF);
        this.setFocused(false);
    }

    private CaptureGroup cacheCaptureGroup(CaptureGroup cg) {
        return new CaptureGroup(cg.valueType(), cg.value(), cg.operation(), cg.rounding());
    }

    public RLECaptureGroupScrollInput withScrollCallback(BiConsumer<CaptureGroup, CaptureGroup> onScroll) {
        this.onScroll = onScroll;
        return this;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double pScrollX, double pScrollY) {
        if (!visible || !active || !isMouseOver(mouseX, mouseY)) return false;
        int direction = pScrollY > 0 ? 1 : -1;

        if (AllKeys.altDown()) {
            return cycleValueTypes(direction * -1);
        } else return updateState(direction);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || !active || !isMouseOver(mouseX, mouseY)) return false;

        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) return cycleRounding(!AllKeys.shiftDown() ? 1 : -1);
        else return false;
    }

    private boolean cycleValueTypes(int direction) {
        CaptureGroupValueType priorValueType = cg.valueType();

        int newValueTypeO = cg.valueType().ordinal() + direction;
        if (newValueTypeO >= 0 && newValueTypeO <= 3)
            cg.setValueType(CaptureGroupValueType.values()[newValueTypeO]);

        if (!priorValueType.equals(cg.valueType())) {
            if (!soundPlayed)
                Minecraft.getInstance()
                        .getSoundManager()
                        .play(SimpleSoundInstance.forUI(AllSoundEvents.SCROLL_VALUE.getMainEvent(),
                                (direction == 1 ? 0.75f : 0.65f)));
            soundPlayed = true;
            changeValueType(cg.valueType());
            onChanged();
            return true;
        }

        return false;
    }

    private boolean cycleRounding(int direction) {
        CaptureGroupRounding priorRounding = cg.rounding();

        int newRoundingO = cg.rounding().ordinal() + direction;
        if (newRoundingO >= 0 && newRoundingO <= 2)
            cg.setRounding(CaptureGroupRounding.values()[newRoundingO]);
        else if (newRoundingO > 2) cg.setRounding(CaptureGroupRounding.values()[0]);
        else cg.setRounding(CaptureGroupRounding.values()[2]);

        if (!priorRounding.equals(cg.rounding())) {
            if (!soundPlayed)
                Minecraft.getInstance()
                        .getSoundManager()
                        .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK,
                                (direction == 1 ? 1.25f : 1.15f)));
            soundPlayed = true;
            updateFormatter();
            onChanged();
            return true;
        }

        return false;
    }

    private boolean updateState(int direction) {
        BigDecimal stepBD = switch (cg.valueType()) {
            case FR_DECIMAL, FR_PERCENT -> BigDecimal.valueOf(AllKeys.shiftDown() ? 0.1 : 0.01);
            default -> BigDecimal.valueOf(AllKeys.shiftDown() ? 10 : 1);
        };
        BigDecimal step = stepBD.multiply(BigDecimal.valueOf(direction));

        Number priorState = cg.value();
        if (cg.valueType() == CaptureGroupValueType.CHARS) {
            cg.setValue(Math.clamp(priorState.intValue() + step.intValue(), 0, 99999));
        } else if (cg.valueType() == CaptureGroupValueType.FR_PROPER) {
            cg.setValue(Fraction.getFraction(
                    Math.clamp(((Fraction) priorState).getNumerator() + (AllKeys.ctrlDown() ? step.intValue() : 0), 0, ((Fraction) priorState).getDenominator()),
                    Math.max(Math.max(1, ((Fraction) priorState).getNumerator()), ((Fraction) priorState).getDenominator() + (AllKeys.ctrlDown() ? 0 : step.intValue()))));
        } else if ((cg.valueType() == CaptureGroupValueType.FR_DECIMAL || cg.valueType() == CaptureGroupValueType.FR_PERCENT)
                && (((BigDecimal) priorState).add(step).compareTo(BigDecimal.ONE) <= 0 && ((BigDecimal) priorState).add(step).compareTo(BigDecimal.ZERO) >= 0))
                cg.setValue(((BigDecimal) priorState).add(step));

        if (!priorState.equals(cg.value())) {
            if (!soundPlayed)
                Minecraft.getInstance()
                        .getSoundManager()
                        .play(SimpleSoundInstance.forUI(AllSoundEvents.SCROLL_VALUE.getMainEvent(),
                                cg.valueType() == CaptureGroupValueType.FR_PROPER ? (((!AllKeys.ctrlDown()) ? 1.2f : 1.0f) + 0.2f * direction * (AllKeys.ctrlDown() ? 1 : -1))
                                : cg.valueType() == CaptureGroupValueType.FR_DECIMAL || cg.valueType() == CaptureGroupValueType.FR_PERCENT ? (1.0f + 0.1f * (cg.value().floatValue() * 10f))
        : 1.2f + 0.1f * direction));
            soundPlayed = true;
            onChanged();
            return true;
        }

        return false;
    }

    @Override
    public RLECaptureGroupScrollInput writingTo(Label label) {
        this.displayLabel = label;
        if (label != null) writeToLabel();
        return this;
    }

    @Override
    protected void writeToLabel() {
        displayLabel.text = formatter.apply(cg.value());
    }

    public Number getValue() {
        return cg.value();
    }

    public RLECaptureGroupScrollInput setValue(Number value) {
        if (value instanceof BigDecimal stateBD) this.cg.setValue(stateBD.max(BigDecimal.ZERO).min(BigDecimal.ONE));
        else this.cg.setValue(value);

        updateTooltip();
        if (displayLabel != null) writeToLabel();
        return this;
    }

    private void changeValueType(CaptureGroupValueType newType) {
        if (!Objects.equals(cache.get(cg.value().getClass()), cg.value())) cache.put(cg.value().getClass(), cg.value());

        cg.setValue(switch (newType) {
            case CHARS -> cache.getOrDefault(Integer.class, NEW_INTEGER);
            case FR_PROPER -> cache.getOrDefault(Fraction.class, (cache.containsKey(BigDecimal.class) ? Fraction.getFraction(cache.get(BigDecimal.class).doubleValue()) : NEW_FRACTION));
            default -> cache.getOrDefault(BigDecimal.class, (cache.containsKey(Fraction.class) ? BigDecimal.valueOf(cache.get(Fraction.class).doubleValue()) : NEW_DECIMAL));
        });

        updateFormatter();
    }

    private void updateFormatter() {
        this.formatter = switch (cg.valueType()) {
            case FR_DECIMAL -> (v) -> formatDecimal((BigDecimal) v);
            case FR_PERCENT -> (v) -> formatPercent(v.floatValue());
            case FR_PROPER -> (v) -> formatFraction((Fraction) v);
            default -> (v) -> formatChars(v.intValue());
        };
    }
    
    @Override
    public void onChanged() {
        if (displayLabel != null) writeToLabel();
        if (onScroll != null) onScroll.accept(pcg, cg);
        pcg = cacheCaptureGroup(cg);
        updateTooltip();
    }

    @Override
    protected void updateTooltip() {
        toolTip.clear();
        if (title == null) return;
        toolTip.add(title.plainCopy().withStyle(s -> s.withColor(HEADER_RGB.getRGB())));
        if (hint != null) toolTip.add(hint.plainCopy().withStyle(s -> s.withColor(HINT_RGB.getRGB())));

        toolTip.add((switch (cg.valueType()) {
            case FR_DECIMAL -> scrollToModifyDecimal;
            case FR_PERCENT -> scrollToModifyPercent;
            case FR_PROPER -> scrollToModifyPropDen;
            default -> scrollToModifyChars;
        }).plainCopy().withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));

        if (cg.valueType() == CaptureGroupValueType.FR_PROPER) toolTip.add(scrollToModifyPropNum.plainCopy().
                withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));

        if (cg.valueType() != CaptureGroupValueType.FR_PROPER)
            toolTip.add(shiftScrollsFaster.plainCopy().withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
        toolTip.add(CommonComponents.EMPTY);

        toolTip.add(typeTitle.plainCopy().withStyle(s -> s.withColor(HEADER_RGB.getRGB())));
        for (CaptureGroupValueType vt : CaptureGroupValueType.values())
            toolTip.add(Component.empty()
                        .append((vt == cg.valueType() ? "-" : "") + "> ")
                        .append(Component.literal(vt.name().toLowerCase()))
                        .withStyle(vt == cg.valueType() ? ChatFormatting.WHITE : ChatFormatting.GRAY));
        toolTip.add(altToCycleType.plainCopy().withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
        toolTip.add(CommonComponents.EMPTY);

        toolTip.add(roundingTitle.plainCopy().withStyle(s -> s.withColor(HEADER_RGB.getRGB())));
        for (CaptureGroupRounding r : CaptureGroupRounding.values())
            toolTip.add(Component.empty()
                    .append((r == cg.rounding() ? "-" : "") + "> ")
                    .append(Component.literal(r.name().toLowerCase()))
                            .append(" " + getRoundingChar(r))
                    .withStyle(r == cg.rounding() ? ChatFormatting.WHITE : ChatFormatting.GRAY));
        toolTip.add(middleToCycleRounding.plainCopy().withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
    }

    private Component formatChars(int v) {
        return Component.literal(String.format("%s%d", getRoundingChar(), v));
    }

    private Component formatDecimal(BigDecimal v) {
        return Component.literal(String.format("%s%s", getRoundingChar(),
                v.stripTrailingZeros().toPlainString() + (v.stripTrailingZeros().scale() < 1 ? ".0" : "")));
    }

    private Component formatPercent(float v) {
        return Component.literal(String.format("%s%d%%", getRoundingChar(), Math.round(v * 100)));
    }

    private Component formatFraction(Fraction v) {
        return Component.literal(String.format("%s%s", getRoundingChar(), v.toString()));
    }

    private String getRoundingChar() {
        return getRoundingChar(cg.rounding());
    }

    private /*Component*/String getRoundingChar(CaptureGroupRounding rounding) {
        return switch (rounding) {
            case CEIL -> Component.literal("↑").getString();
            case FLOOR -> Component.literal("↓").getString();
            default -> Component.literal("⇵").getString();
        };
    }
}
