package com.bank.avpiteam.ui;

import com.bank.avpiteam.security.CreateUserRequest;
import com.bank.avpiteam.security.CreatedUser;
import com.bank.avpiteam.security.PasswordResult;
import com.bank.avpiteam.security.Role;
import com.bank.avpiteam.security.UpdateUserRequest;
import com.bank.avpiteam.security.UserAdminService;
import com.bank.avpiteam.security.UserView;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.security.core.context.SecurityContextHolder;

@Route(value = "admin/users", layout = MainLayout.class)
@PageTitle("Пользователи | Привязка операций")
@RolesAllowed("ADMIN")
public class UsersView extends VerticalLayout {
    private final UserAdminService service;
    private final Grid<UserView> grid = new Grid<>(UserView.class, false);
    private final PagedGrid<UserView> pagedGrid = new PagedGrid<>(grid);

    public UsersView(UserAdminService service) {
        this.service = service;
        addClassName("content-page");
        H1 title = new H1("Пользователи");
        Button create = UiKit.iconButton("Создать пользователя", VaadinIcon.PLUS);
        create.addClickListener(e -> createDialog());
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        HorizontalLayout heading = new HorizontalLayout(title, create);
        heading.addClassName("page-heading");
        heading.setWidthFull();
        heading.setAlignItems(Alignment.CENTER);
        add(heading);
        configureGrid();
        VerticalLayout card = new VerticalLayout(new H2("Учётные записи"), pagedGrid);
        card.addClassName("surface-card");
        add(card);
        refresh();
    }

    private void configureGrid() {
        grid.addColumn(UserView::username).setHeader("Логин").setWidth("190px").setFlexGrow(1);
        grid.addColumn(u -> UiLabels.role(u.role())).setHeader("Роль").setWidth("190px").setFlexGrow(1);
        grid.addComponentColumn(u -> UiKit.badge(u.enabled() ? "Активен" : "Заблокирован",
                u.enabled() ? "badge-success" : "badge-neutral")).setHeader("Статус").setWidth("150px").setFlexGrow(1);
        grid.addComponentColumn(u -> {
            Button button = UiKit.iconButton("Настроить", VaadinIcon.EDIT);
            button.addClickListener(e -> editDialog(u));
            return button;
        }).setHeader("Действия").setWidth("170px").setFlexGrow(0);
    }

    private void refresh() { pagedGrid.setRows(service.list()); }

    private void createDialog() {
        Dialog dialog = UiKit.dialog("Новый пользователь");
        TextField username = new TextField("Логин");
        username.setHelperText("От 3 до 64 символов: латиница, цифры, точка, дефис или подчёркивание.");
        username.setWidthFull();
        ComboBox<Role> role = roleField();
        role.setValue(Role.READER);
        PasswordField password = new PasswordField("Пароль");
        password.setHelperText("Оставьте пустым, чтобы система создала пароль автоматически.");
        password.setWidthFull();
        Span error = formError();
        Button save = new Button("Создать", e -> {
            try {
                CreatedUser result = service.create(new CreateUserRequest(username.getValue(),
                        role.getValue() == null ? null : role.getValue().name(), password.getValue()), actor());
                dialog.close();
                refresh();
                if (result.generatedPassword() != null) showPassword(result.generatedPassword());
                else UiFeedback.success("Пользователь создан");
            } catch (RuntimeException ex) { error.setText(UiFeedback.message(ex)); }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        VerticalLayout fields = dialogBody(new Paragraph("Новый сотрудник получит права выбранной роли."),
                username, role, password, error);
        dialog.add(fields);
        dialog.getFooter().add(new Button("Отмена", e -> dialog.close()), save);
        dialog.open();
    }

    private void editDialog(UserView user) {
        Dialog dialog = UiKit.dialog("Пользователь: " + user.username());
        ComboBox<Role> role = roleField();
        role.setValue(Role.valueOf(user.role()));
        Span error = formError();
        Button toggle = UiKit.iconButton(user.enabled() ? "Заблокировать" : "Разблокировать",
                user.enabled() ? VaadinIcon.LOCK : VaadinIcon.CHECK);
        toggle.addClickListener(e -> {
            if (user.enabled()) confirm("Заблокировать " + user.username() + "?",
                    "Пользователь потеряет доступ после следующего входа.",
                    () -> update(user, new UpdateUserRequest(null, false), dialog, error));
            else update(user, new UpdateUserRequest(null, true), dialog, error);
        });
        Button save = UiKit.iconButton("Сохранить роль", VaadinIcon.CHECK);
        save.addClickListener(e -> update(user,
                new UpdateUserRequest(role.getValue().name(), null), dialog, error));
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button reset = UiKit.iconButton("Сбросить пароль", VaadinIcon.KEY);
        reset.addClickListener(e -> confirm("Сбросить пароль?",
                "Для " + user.username() + " будет создан новый пароль. Старый перестанет работать.", () -> {
            try {
                PasswordResult result = service.resetPassword(user.id(), null, actor());
                dialog.close();
                showPassword(result.generatedPassword());
            } catch (RuntimeException ex) { error.setText(UiFeedback.message(ex)); }
        }));
        boolean self = user.username().equalsIgnoreCase(actor());
        if (self) { role.setEnabled(false); save.setEnabled(false); toggle.setEnabled(false); }
        Paragraph note = new Paragraph(self
                ? "Вы можете сбросить свой пароль. Сменить собственную роль или заблокировать себя нельзя."
                : "Измените роль, состояние учётной записи или создайте новый пароль.");
        HorizontalLayout actions = new HorizontalLayout(save, toggle);
        actions.addClassName("dialog-actions");
        dialog.add(dialogBody(note, role, actions, reset, error));
        dialog.getFooter().add(new Button("Закрыть", e -> dialog.close()));
        dialog.open();
    }

    private void update(UserView user, UpdateUserRequest change, Dialog dialog, Span error) {
        try {
            service.update(user.id(), change, actor());
            dialog.close();
            refresh();
            UiFeedback.success("Пользователь обновлён");
        } catch (RuntimeException ex) { error.setText(UiFeedback.message(ex)); }
    }

    private static ComboBox<Role> roleField() {
        ComboBox<Role> field = new ComboBox<>("Роль");
        field.setItems(Role.values());
        field.setItemLabelGenerator(UiLabels::role);
        field.setWidthFull();
        return field;
    }

    private static VerticalLayout dialogBody(com.vaadin.flow.component.Component... components) {
        VerticalLayout body = new VerticalLayout(components);
        body.addClassName("dialog-body");
        body.setPadding(false);
        body.setWidthFull();
        return body;
    }

    private static Span formError() {
        Span error = new Span();
        error.addClassName("form-error");
        return error;
    }

    private static void confirm(String title, String message, Runnable action) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader(title);
        dialog.setText(message);
        dialog.setCancelable(true);
        dialog.setCancelText("Отмена");
        dialog.setConfirmText("Подтвердить");
        dialog.addConfirmListener(e -> action.run());
        dialog.open();
    }

    private static String actor() { return SecurityContextHolder.getContext().getAuthentication().getName(); }

    private static void showPassword(String value) {
        if (value == null) return;
        Dialog dialog = UiKit.dialog("Новый пароль");
        TextField field = new TextField("Сгенерированный пароль");
        field.setValue(value);
        field.setReadOnly(true);
        field.setWidthFull();
        Button copy = UiKit.iconButton("Копировать", VaadinIcon.COPY);
        copy.addClickListener(e -> field.getElement().executeJs(
                "navigator.clipboard.writeText($0)", value));
        copy.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.add(dialogBody(new Paragraph("Сохраните пароль сейчас: после закрытия окна он больше не отобразится."),
                field, copy));
        dialog.getFooter().add(new Button("Закрыть", e -> dialog.close()));
        dialog.open();
    }
}
