package com.bank.avpiteam.ui;

import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route("login")
@PageTitle("Вход | Привязка операций")
@AnonymousAllowed
@CssImport("./styles/app.css")
public class LoginView extends VerticalLayout implements BeforeEnterObserver {
    private final LoginForm form = new LoginForm();

    public LoginView() {
        addClassName("login-page");
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);
        form.setAction("login");
        form.setI18n(russianLogin());
        form.setForgotPasswordButtonVisible(false);
        form.addClassName("login-form");
        form.addAttachListener(event -> form.getElement().executeJs(
                "this.querySelector('vaadin-password-field')?.setAttribute('reveal-button-hidden', '')"));
        add(form);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        form.setError(event.getLocation().getQueryParameters().getParameters().containsKey("error"));
    }

    private static LoginI18n russianLogin() {
        LoginI18n i18n = LoginI18n.createDefault();
        i18n.getForm().setTitle("");
        i18n.getForm().setUsername("Логин");
        i18n.getForm().setPassword("Пароль");
        i18n.getForm().setSubmit("Войти");
        i18n.getForm().setForgotPassword("Забыли пароль?");
        i18n.getErrorMessage().setTitle("Не удалось войти");
        i18n.getErrorMessage().setMessage("Проверьте логин и пароль");
        i18n.getErrorMessage().setUsername("Введите логин");
        i18n.getErrorMessage().setPassword("Введите пароль");
        return i18n;
    }
}
