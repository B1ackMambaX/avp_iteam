package com.bank.avpiteam.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;

final class UiKit {
    private UiKit() {}

    static Dialog dialog(String title) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(title);
        dialog.setWidth("560px");
        dialog.setMaxWidth("calc(100vw - 32px)");
        dialog.addClassName("app-dialog");
        return dialog;
    }

    static Button iconButton(String label, VaadinIcon icon) {
        Button button = new Button(label, icon.create());
        button.addClassName("icon-button");
        return button;
    }

    static Span muted(String text) {
        Span span = new Span(text);
        span.addClassName("muted-text");
        return span;
    }

    static Span badge(String text, String style) {
        Span badge = new Span(text);
        badge.addClassNames("status-badge", style);
        return badge;
    }
}
