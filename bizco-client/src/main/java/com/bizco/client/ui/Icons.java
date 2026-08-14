package com.bizco.client.ui;

import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Creates FontAwesome (Ikonli) icon nodes and icon-carrying buttons so every screen sizes
 * and colors icons through the shared {@code application.css} style classes instead of
 * repeating literal sizes/colors in Java.
 */
public final class Icons {

    private Icons() {
    }

    /** A bare icon glyph. Callers add a style class to control size/color via CSS. */
    public static FontIcon of(final Ikon icon) {
        return new FontIcon(icon);
    }

    /** An icon glyph pre-tagged with an extra style class, for CSS-driven sizing/coloring. */
    public static FontIcon of(final Ikon icon, final String styleClass) {
        final FontIcon fontIcon = of(icon);
        fontIcon.getStyleClass().add(styleClass);
        return fontIcon;
    }

    /** A text button with a leading icon glyph, sized via the shared "btn-icon-glyph" class. */
    public static Button button(final String text, final Ikon icon) {
        final Button button = new Button(text, of(icon, "btn-icon-glyph"));
        button.setGraphicTextGap(8);
        return button;
    }

    /** An icon-only button (window controls, compact toolbars); styleClasses drive size/color. */
    public static Button iconOnlyButton(final Ikon icon, final String... styleClasses) {
        final Button button = new Button();
        button.setGraphic(of(icon));
        button.getStyleClass().addAll(styleClasses);
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        return button;
    }
}
