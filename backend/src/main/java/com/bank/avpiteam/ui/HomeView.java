package com.bank.avpiteam.ui;

import com.bank.avpiteam.security.Authorities;
import com.bank.avpiteam.security.Role;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.context.SecurityContextHolder;

@Route(value = "", layout = MainLayout.class)
@PageTitle("Главная | Привязка операций")
@PermitAll
public class HomeView extends VerticalLayout {
    public HomeView() {
        addClassName("content-page");
        var auth = SecurityContextHolder.getContext().getAuthentication();
        Role role = Authorities.roleOf(auth.getAuthorities());
        add(new H1("Добро пожаловать, " + auth.getName()));
        if (role == Role.ADMIN) {
            Div cards = new Div();
            cards.addClassName("dashboard-cards");
            cards.add(card("Пользователи", "Создание учётных записей и управление доступом",
                    VaadinIcon.USERS, UsersView.class));
            cards.add(card("Настройки NLP", "Порог, словари, тестирование и история изменений",
                    VaadinIcon.COG, NlpSettingsView.class));
            add(cards);
        }
    }

    private static RouterLink card(String title, String description, VaadinIcon icon,
                                   Class<? extends com.vaadin.flow.component.Component> view) {
        RouterLink card = new RouterLink(view);
        card.add(icon.create(), new H2(title), new Paragraph(description), new Span("Открыть раздел →"));
        card.addClassName("dashboard-card");
        return card;
    }
}
