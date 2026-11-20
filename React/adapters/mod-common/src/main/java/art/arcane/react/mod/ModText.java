package art.arcane.react.mod;

import art.arcane.volmlib.util.plugin.ComponentText;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/** React's rich text as vanilla components, with colours, decorations, hover text and clicks. Fonts are dropped. */
final class ModText {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private ModText() {
    }

    static MutableComponent component(ComponentText text) {
        return converted(MINI_MESSAGE.deserialize(text.miniMessage()));
    }

    private static MutableComponent converted(net.kyori.adventure.text.Component source) {
        MutableComponent out = Component.literal(source instanceof net.kyori.adventure.text.TextComponent text ? text.content() : "");
        out.setStyle(style(source.style()));
        for (net.kyori.adventure.text.Component child : source.children()) {
            out.append(converted(child));
        }
        return out;
    }

    private static Style style(net.kyori.adventure.text.format.Style source) {
        Style style = Style.EMPTY;
        if (source.color() != null) {
            style = style.withColor(TextColor.fromRgb(source.color().value()));
        }
        style = style.withBold(decoration(source, TextDecoration.BOLD))
                .withItalic(decoration(source, TextDecoration.ITALIC))
                .withUnderlined(decoration(source, TextDecoration.UNDERLINED))
                .withStrikethrough(decoration(source, TextDecoration.STRIKETHROUGH))
                .withObfuscated(decoration(source, TextDecoration.OBFUSCATED));
        net.kyori.adventure.text.event.ClickEvent click = source.clickEvent();
        if (click != null && click.payload() instanceof net.kyori.adventure.text.event.ClickEvent.Payload.Text text) {
            ClickEvent event = VanillaApi.click(click.action(), text.value());
            if (event != null) {
                style = style.withClickEvent(event);
            }
        }
        net.kyori.adventure.text.event.HoverEvent<?> hover = source.hoverEvent();
        if (hover != null && hover.value() instanceof net.kyori.adventure.text.Component shown) {
            style = style.withHoverEvent(VanillaApi.showText(converted(shown)));
        }
        return style;
    }

    private static Boolean decoration(net.kyori.adventure.text.format.Style source, TextDecoration decoration) {
        return switch (source.decoration(decoration)) {
            case TRUE -> Boolean.TRUE;
            case FALSE -> Boolean.FALSE;
            case NOT_SET -> null;
        };
    }
}
