package com.bank.avpiteam.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import java.util.List;

/** Небольшая общая обёртка для таблиц с постраничным показом загруженных записей. */
final class PagedGrid<T> extends VerticalLayout {
    private final Grid<T> grid;
    private final Span position = new Span();
    private final Button previous = new Button("Назад", e -> changePage(-1));
    private final Button next = new Button("Далее", e -> changePage(1));
    private final ComboBox<Integer> pageSize = new ComboBox<>("Строк на странице");
    private List<T> rows = List.of();
    private int page;

    PagedGrid(Grid<T> grid) {
        this.grid = grid;
        addClassName("paged-grid");
        setPadding(false);
        setSpacing(false);
        setWidthFull();
        grid.setWidthFull();
        grid.setAllRowsVisible(true);
        pageSize.setItems(10, 25, 50);
        pageSize.setValue(10);
        pageSize.addValueChangeListener(e -> {
            page = 0;
            renderPage();
        });
        HorizontalLayout navigation = new HorizontalLayout(position, previous, next);
        navigation.addClassName("pagination-navigation");
        navigation.setAlignItems(Alignment.CENTER);
        HorizontalLayout controls = new HorizontalLayout(pageSize, navigation);
        controls.addClassName("pagination-controls");
        controls.setWidthFull();
        controls.setAlignItems(Alignment.END);
        add(grid, controls);
        renderPage();
    }

    void setRows(List<T> rows) {
        this.rows = List.copyOf(rows);
        page = Math.min(page, maxPage());
        renderPage();
    }

    private void changePage(int delta) {
        page = Math.max(0, Math.min(maxPage(), page + delta));
        renderPage();
    }

    private int maxPage() {
        return rows.isEmpty() ? 0 : (rows.size() - 1) / pageSize.getValue();
    }

    private void renderPage() {
        int size = pageSize.getValue();
        int from = Math.min(page * size, rows.size());
        int to = Math.min(from + size, rows.size());
        grid.setItems(rows.subList(from, to));
        position.setText(rows.isEmpty() ? "Нет записей"
                : "Показано " + (from + 1) + "–" + to + " из " + rows.size());
        previous.setEnabled(page > 0);
        next.setEnabled(page < maxPage());
    }
}
