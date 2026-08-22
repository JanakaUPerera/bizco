package com.bizco.client.catalog.view;

import com.bizco.common.dto.catalog.CatalogDtos.VariantResponse;
import java.util.List;
import java.util.function.Function;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

/** Phase 6 Week 19 (task 19.2): a small modal pick-one dialog for POS, opened whenever a resolved
 *  product turns out to have more than one active variant. Follows {@code CustomerPickerDialog}'s
 *  modal-table-pick pattern, minus the search box - a product's variant list is always small
 *  enough to just show (§56.1: variants are per-product, not a global catalog to search).
 *
 *  <p>Takes an already-fetched variant list rather than fetching its own - the caller (POS) always
 *  already has it via {@code VariantApiClient.variants}'s async {@code onFx} callback, and calling
 *  this dialog's blocking {@code showAndWait()} from inside that callback (itself already running
 *  on the FX thread via {@code Platform.runLater}) is the same safe nested-event-loop pattern
 *  every other modal picker in this codebase already relies on - there is no blocking/synchronous
 *  future-await helper in {@code UiSupport} to fetch with instead. */
public final class VariantPickerDialog {

    private VariantPickerDialog() {
    }

    /** Returns {@code null} if the user cancels, or immediately (no dialog shown) if there is
     *  nothing to pick or exactly one active variant to pick - the common still-single-variant
     *  case falls straight through unprompted. */
    public static VariantResponse show(final List<VariantResponse> allVariants, final String productName) {
        final List<VariantResponse> variants = allVariants.stream().filter(VariantResponse::active).toList();
        if (variants.isEmpty()) {
            return null;
        }
        if (variants.size() == 1) {
            return variants.get(0);
        }
        final Dialog<VariantResponse> dialog = new Dialog<>();
        dialog.setTitle("Select Variant" + (productName == null ? "" : " - " + productName));
        dialog.setResizable(true);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);

        final TableView<VariantResponse> table = new TableView<>();
        table.getStyleClass().add("data-table");
        table.getColumns().setAll(
                column("Label", v -> v.variantLabel() == null ? "" : v.variantLabel()),
                column("SKU", VariantResponse::sku),
                column("Barcode", v -> v.barcode() == null ? "" : v.barcode()),
                column("Price", v -> v.sellingPrice() == null ? "" : v.sellingPrice().toPlainString()));
        table.setItems(FXCollections.observableArrayList(variants));
        table.getSelectionModel().selectFirst();
        table.setPrefHeight(280);
        table.setPrefWidth(480);
        table.setRowFactory(view -> {
            final TableRow<VariantResponse> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    dialog.setResult(row.getItem());
                    dialog.close();
                }
            });
            return row;
        });

        final VBox content = new VBox(10, new Label("This product has more than one variant - pick one:"), table);
        content.setPadding(new Insets(8));
        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(button -> button == ButtonType.OK
                ? table.getSelectionModel().getSelectedItem() : null);
        return dialog.showAndWait().orElse(null);
    }

    private static TableColumn<VariantResponse, String> column(final String title,
                                                                final Function<VariantResponse, String> value) {
        final TableColumn<VariantResponse, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        column.setPrefWidth(110);
        return column;
    }
}
