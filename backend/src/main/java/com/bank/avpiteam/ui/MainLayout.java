package com.bank.avpiteam.ui;

import com.bank.avpiteam.security.Permission;
import com.bank.avpiteam.security.Authorities;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.component.dependency.CssImport;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinServletRequest;
import com.vaadin.flow.server.VaadinServletResponse;

@PermitAll
@CssImport("./styles/app.css")
public class MainLayout extends AppLayout {
    public MainLayout() {
        getElement().getStyle().set("--vaadin-app-layout-drawer-overlay", "false");
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth == null ? "" : auth.getName();
        String role = auth == null ? "" : UiLabels.role(Authorities.roleOf(auth.getAuthorities()));
        VerticalLayout sidebar = new VerticalLayout();
        sidebar.addClassName("app-sidebar");
        sidebar.setPadding(false);
        sidebar.setSpacing(false);
        sidebar.add(link("Главная", VaadinIcon.HOME, HomeView.class));
        if (has(auth, Permission.MANAGE_USERS)) sidebar.add(link("Пользователи", VaadinIcon.USERS, UsersView.class));
        if (has(auth, Permission.MANAGE_NLP)) sidebar.add(link("Настройки NLP", VaadinIcon.COG, NlpSettingsView.class));
        Div spacer = new Div();
        spacer.getStyle().set("flex-grow", "1");
        sidebar.add(spacer);
        Span avatar = new Span(username.isEmpty() ? "?" : username.substring(0, 1).toUpperCase());
        avatar.addClassName("sidebar-avatar");
        Div identity = new Div(new Span(username), UiKit.muted(role));
        identity.addClassName("sidebar-identity");
        HorizontalLayout profile = new HorizontalLayout(avatar, identity);
        profile.addClassName("sidebar-profile");
        profile.setAlignItems(Alignment.CENTER);
        Button logout = new Button(VaadinIcon.SIGN_OUT.create());
        logout.getElement().setAttribute("aria-label", "Выйти");
        logout.getElement().setAttribute("title", "Выйти");
        logout.addClickListener(e -> {
            new SecurityContextLogoutHandler().logout(VaadinServletRequest.getCurrent().getHttpServletRequest(),
                    VaadinServletResponse.getCurrent().getHttpServletResponse(),
                    SecurityContextHolder.getContext().getAuthentication());
            UI.getCurrent().getSession().close();
            UI.getCurrent().getPage().setLocation("/login");
        });
        logout.addClassName("logout-icon");
        profile.add(logout);
        sidebar.add(profile);
        addToDrawer(sidebar);
        DrawerToggle toggle = new DrawerToggle();
        toggle.setAriaLabel("Открыть или закрыть меню");
        addToNavbar(toggle);
        setPrimarySection(Section.DRAWER);
        setDrawerOpened(true);
    }

    private static boolean has(Authentication auth, Permission permission) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(permission.name()));
    }

    private static RouterLink link(String label, VaadinIcon icon,
                                   Class<? extends com.vaadin.flow.component.Component> view) {
        RouterLink link = new RouterLink(view);
        link.add(icon.create(), new Span(label));
        link.addClassName("nav-link");
        return link;
    }

}
