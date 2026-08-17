package com.bizco.client.sales.view;

import com.bizco.client.customer.service.CustomerApiClient;
import com.bizco.client.ui.Icons;
import com.bizco.client.ui.UiSupport;
import com.bizco.common.dto.customer.CustomerDtos.CustomerSummaryResponse;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

/** A small modal search-and-pick dialog, reused wherever POS checkout needs to attach a customer. */
final class CustomerPickerDialog {

    private CustomerPickerDialog() {
    }

    static CustomerSummaryResponse show(final CustomerApiClient customerApiClient) {
        final Dialog<CustomerSummaryResponse> dialog = new Dialog<>();
        dialog.setTitle("Select Customer");
        dialog.setResizable(true);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);

        final TextField searchField = new TextField();
        searchField.setPromptText("Search code, name, phone");
        final TableView<CustomerSummaryResponse> table = new TableView<>();
        table.getStyleClass().add("data-table");
        table.getColumns().setAll(
                column("Code", CustomerSummaryResponse::customerCode),
                column("Name", CustomerSummaryResponse::name),
                column("Phone", CustomerSummaryResponse::phone),
                column("Category", CustomerSummaryResponse::category));
        table.setPrefHeight(320);
        table.setPrefWidth(520);

        final Runnable search = () -> UiSupport.onFx(
                customerApiClient.search(searchField.getText(), null, "ACTIVE", 0, 30),
                result -> table.setItems(FXCollections.observableArrayList(result.data())),
                "Customers could not be searched.");
        searchField.setOnAction(event -> search.run());
        final Button searchButton = Icons.button("Search", FontAwesomeSolid.SEARCH);
        searchButton.setOnAction(event -> search.run());
        final HBox searchRow = new HBox(8, searchField, searchButton);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        table.setRowFactory(view -> {
            final javafx.scene.control.TableRow<CustomerSummaryResponse> row = new javafx.scene.control.TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    dialog.setResult(row.getItem());
                    dialog.close();
                }
            });
            return row;
        });

        final VBox content = new VBox(10, searchRow, table);
        content.setPadding(new Insets(8));
        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(button -> button == ButtonType.OK
                ? table.getSelectionModel().getSelectedItem() : null);
        search.run();
        return dialog.showAndWait().orElse(null);
    }

    private static TableColumn<CustomerSummaryResponse, String> column(final String title,
                                                                        final java.util.function.Function<CustomerSummaryResponse, String> value) {
        final TableColumn<CustomerSummaryResponse, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        column.setPrefWidth(120);
        return column;
    }
}
