package com.bank.avpiteam.ui;

import com.bank.avpiteam.settings.NlpAdminGateway;
import com.bank.avpiteam.settings.NlpSettings;
import com.bank.avpiteam.settings.NlpSettingsErrors;
import com.bank.avpiteam.settings.NlpSettingsService;
import com.bank.avpiteam.settings.NlpTestResponse;
import com.bank.avpiteam.settings.NlpTestService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.security.core.context.SecurityContextHolder;

@Route(value = "admin/nlp", layout = MainLayout.class)
@PageTitle("Настройки NLP | Привязка операций")
@RolesAllowed("ADMIN")
public class NlpSettingsView extends VerticalLayout {
    private final NlpSettingsService settingsService;
    private final NlpTestService testService;
    private final NumberField threshold = new NumberField("Порог от 0 до 1");
    private final TextArea triggers = new TextArea("Слова-маркеры (по одному в строке)");
    private final TextArea negations = new TextArea("Слова-отрицания (по одному в строке)");
    private final TextField comment = new TextField("Комментарий к изменению");
    private final TextArea testText = new TextArea("Текст для проверки");
    private final Paragraph versionInfo = new Paragraph();
    private final Paragraph testResult = new Paragraph();
    private final Button exampleButton = UiKit.iconButton("Подставить пример", VaadinIcon.FILE_TEXT);
    private final Grid<NlpSettings> history = new Grid<>(NlpSettings.class, false);
    private final PagedGrid<NlpSettings> pagedHistory = new PagedGrid<>(history);
    private int version;

    public NlpSettingsView(NlpSettingsService settingsService, NlpTestService testService) {
        this.settingsService = settingsService;
        this.testService = testService;
        addClassName("content-page");
        add(new H1("Настройки NLP"));
        threshold.setMin(0);
        threshold.setMax(1);
        threshold.setStep(0.01);
        threshold.setWidth("260px");
        triggers.setWidthFull();
        triggers.setHeight("190px");
        negations.setWidthFull();
        negations.setHeight("190px");
        comment.setWidthFull();
        testText.setWidthFull();
        testText.setMaxLength(2000);
        testText.setMinHeight("125px");
        testResult.setText("Здесь появится результат проверки.");
        testResult.addClassName("test-result");
        exampleButton.setEnabled(false);
        Button runTest = UiKit.iconButton("Проверить черновик", VaadinIcon.PLAY);
        runTest.addClickListener(e -> testDraft());
        runTest.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        HorizontalLayout wordFields = new HorizontalLayout(triggers, negations);
        wordFields.addClassName("word-fields");
        wordFields.setWidthFull();
        VerticalLayout editor = card(new H2("Текущие настройки"), versionInfo,
                new Paragraph("Изменения применятся только после сохранения."),
                threshold, wordFields, comment, actions());
        VerticalLayout tester = card(new H2("Проверка текста"),
                new Paragraph("Проверка использует значения из полей выше и не сохраняет их."),
                testText, new HorizontalLayout(exampleButton, runTest), testResult);
        configureHistory();
        add(editor, tester, card(new H2("История изменений"), pagedHistory));
        refresh();
        loadExamples();
    }

    private HorizontalLayout actions() {
        Button save = UiKit.iconButton("Сохранить настройки", VaadinIcon.CHECK);
        save.addClickListener(e -> save());
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button reset = UiKit.iconButton("Сбросить", VaadinIcon.REFRESH);
        reset.addClickListener(e -> confirm("Сбросить настройки?", () -> {
            settingsService.reset(actor()); refresh(); UiFeedback.success("Настройки сброшены");
        }));
        HorizontalLayout actions = new HorizontalLayout(save, reset);
        actions.addClassName("form-actions");
        return actions;
    }

    private void save() {
        try {
            settingsService.update(NlpFormData.settings(version, threshold.getValue(),
                    triggers.getValue(), negations.getValue(), comment.getValue()), actor());
            refresh();
            UiFeedback.success("Настройки сохранены");
        } catch (NlpSettingsErrors.Conflict ex) {
            refresh();
            UiFeedback.error(UiFeedback.message(ex));
        } catch (RuntimeException ex) { UiFeedback.error(UiFeedback.message(ex)); }
    }

    private void testDraft() {
        try {
            NlpTestResponse result = testService.test(NlpFormData.test(testText.getValue(),
                    threshold.getValue(), triggers.getValue(), negations.getValue()));
            testResult.setText((result.thirdParty() ? "Третье лицо обнаружено" : "Третье лицо не обнаружено")
                    + " · " + UiLabels.decision(result.decision())
                    + " · оценка " + Math.round(result.score() * 100) + "%"
                    + (result.extractedName() == null ? "" : " · имя: " + result.extractedName()));
            testResult.removeClassName("result-empty");
        } catch (RuntimeException ex) { UiFeedback.error(UiFeedback.message(ex)); }
    }

    private void loadExamples() {
        try {
            NlpAdminGateway.Examples examples = testService.examples();
            examples.positive().stream().findFirst().ifPresent(example -> {
                exampleButton.setEnabled(true);
                exampleButton.addClickListener(e -> testText.setValue(example));
            });
        } catch (RuntimeException ex) {
            exampleButton.setTooltipText("Примеры сейчас недоступны");
        }
    }

    private void configureHistory() {
        history.addClassName("history-grid");
        history.addColumn(NlpSettings::version).setHeader("Версия").setWidth("90px").setFlexGrow(0);
        history.addColumn(NlpSettings::threshold).setHeader("Порог").setWidth("90px").setFlexGrow(0);
        history.addColumn(s -> UiLabels.actor(s.changedBy())).setHeader("Кто изменил").setWidth("160px").setFlexGrow(0);
        history.addColumn(s -> UiLabels.dateTime(s.changedAt())).setHeader("Когда").setWidth("175px").setFlexGrow(0);
        history.addComponentColumn(s -> {
            Span value = new Span(UiLabels.historyComment(s.comment()));
            value.addClassName("history-comment");
            return value;
        }).setHeader("Комментарий").setWidth("320px").setFlexGrow(1);
        history.addComponentColumn(s -> {
            if (s.version() == version) return new Span();
            Button restore = UiKit.iconButton("Восстановить", VaadinIcon.REFRESH);
            restore.addClickListener(e -> confirm(
                "Восстановить версию " + s.version() + "?", () -> {
                    settingsService.rollback(s.version(), actor()); refresh();
                    UiFeedback.success("Версия восстановлена");
                }));
            return restore;
        }).setHeader("Действие").setWidth("220px").setFlexGrow(0);
    }

    private void refresh() {
        NlpSettings current = settingsService.current();
        version = current.version();
        threshold.setValue(current.threshold());
        triggers.setValue(NlpFormData.lines(current.triggerWords()));
        negations.setValue(NlpFormData.lines(current.negationWords()));
        comment.clear();
        versionInfo.setText("Версия " + version + " · изменил " + UiLabels.actor(current.changedBy())
                + " · " + UiLabels.dateTime(current.changedAt()));
        pagedHistory.setRows(settingsService.history());
    }

    private void confirm(String title, Runnable action) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader(title);
        dialog.setCancelable(true);
        dialog.setCancelText("Отмена");
        dialog.setConfirmText("Подтвердить");
        dialog.addConfirmListener(e -> {
            try { action.run(); }
            catch (RuntimeException ex) { UiFeedback.error(UiFeedback.message(ex)); }
        });
        dialog.open();
    }

    private static VerticalLayout card(com.vaadin.flow.component.Component... components) {
        VerticalLayout card = new VerticalLayout(components);
        card.addClassName("surface-card");
        return card;
    }

    private static String actor() { return SecurityContextHolder.getContext().getAuthentication().getName(); }
}
