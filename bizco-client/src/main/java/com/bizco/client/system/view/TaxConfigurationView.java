package com.bizco.client.system.view;

import com.bizco.client.system.service.TaxConfigurationApiClient;
import com.bizco.client.ui.Icons;
import com.bizco.client.ui.UiSupport;
import com.bizco.common.dto.system.SystemRequests.TaxConfigurationRequest;
import com.bizco.common.dto.system.SystemResponses.TaxConfigurationResponse;
import java.math.BigDecimal;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

public class TaxConfigurationView {

    private final TaxConfigurationApiClient apiClient;
    private final boolean canWrite;
    private final CheckBox vatEnabledBox = new CheckBox("VAT enabled");
    private final TextField vatRateField = new TextField("18.0000");
    private final Label statusLabel = UiSupport.label("", "field-hint");
    private final Button saveButton = Icons.button("Save Tax Configuration", FontAwesomeSolid.SAVE);
    private final Runnable savedCallback;
    private long version;

    public TaxConfigurationView(final TaxConfigurationApiClient apiClient, final boolean canWrite) {
        this(apiClient, canWrite, () -> { });
    }

    public TaxConfigurationView(final TaxConfigurationApiClient apiClient, final boolean canWrite,
                                final Runnable savedCallback) {
        this.apiClient = apiClient;
        this.canWrite = canWrite;
        this.savedCallback = savedCallback;
    }

    public Parent createView() {
        vatEnabledBox.setDisable(!canWrite);
        vatRateField.setDisable(!canWrite);
        saveButton.getStyleClass().add("primary-button");
        saveButton.setDisable(!canWrite);
        saveButton.setOnAction(event -> save());

        final GridPane grid = new GridPane();
        grid.getStyleClass().add("form-grid");
        grid.setHgap(12);
        grid.setVgap(12);
        grid.add(vatEnabledBox, 0, 0, 2, 1);
        grid.add(new Label("VAT Rate (%)"), 0, 1);
        grid.add(vatRateField, 1, 1);

        final Label title = UiSupport.label("Tax Configuration", "screen-title");
        final Label note = UiSupport.label(
                "VAT is calculated line-by-line at posting time using this rate. Changes only affect invoices "
                        + "posted after saving.", "field-hint");
        final VBox root = new VBox(16, title, note, grid, saveButton, statusLabel);
        root.getStyleClass().add("content-surface");
        root.setPadding(new Insets(24));
        load();
        return root;
    }

    private void load() {
        UiSupport.onFx(apiClient.getConfiguration(), this::populate, "Tax configuration could not be loaded.");
    }

    private void populate(final TaxConfigurationResponse response) {
        vatEnabledBox.setSelected(response.vatEnabled());
        vatRateField.setText(response.vatRate().toPlainString());
        version = response.version();
        statusLabel.setText("");
    }

    private void save() {
        final BigDecimal vatRate;
        try {
            vatRate = new BigDecimal(vatRateField.getText().trim());
        } catch (final NumberFormatException exception) {
            UiSupport.alert("VAT rate must be a number.");
            return;
        }
        if (vatRate.compareTo(BigDecimal.ZERO) < 0 || vatRate.compareTo(new BigDecimal("100")) > 0) {
            UiSupport.alert("VAT rate must be between 0 and 100.");
            return;
        }
        final TaxConfigurationRequest request = new TaxConfigurationRequest(vatEnabledBox.isSelected(), vatRate, version);
        UiSupport.onFx(apiClient.saveConfiguration(request), response -> {
            populate(response);
            statusLabel.setText("Saved.");
            savedCallback.run();
        }, "Tax configuration could not be saved.");
    }
}
