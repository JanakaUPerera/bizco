package com.bizco.client.system.view;

import com.bizco.client.system.service.BusinessProfileApiClient;
import com.bizco.client.ui.UiSupport;
import com.bizco.common.dto.system.SystemRequests.BusinessProfileRequest;
import com.bizco.common.dto.system.SystemResponses.BusinessProfileResponse;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

public class BusinessProfileView {

    private final BusinessProfileApiClient apiClient;
    private final boolean canWrite;
    private final Runnable savedCallback;
    private final TextField businessNameField = new TextField();
    private final TextField legalNameField = new TextField();
    private final TextField vatField = new TextField();
    private final TextField phoneField = new TextField();
    private final TextField emailField = new TextField();
    private final TextField address1Field = new TextField();
    private final TextField address2Field = new TextField();
    private final TextField cityField = new TextField();
    private final TextField countryField = new TextField("LK");
    private final TextField currencyField = new TextField("LKR");
    private final TextField timezoneField = new TextField("Asia/Colombo");
    private final Button saveButton = new Button("Save Profile");
    private long version;

    public BusinessProfileView(final BusinessProfileApiClient apiClient, final boolean canWrite, final Runnable savedCallback) {
        this.apiClient = apiClient;
        this.canWrite = canWrite;
        this.savedCallback = savedCallback;
    }

    public Parent createView(final boolean firstLaunch) {
        saveButton.getStyleClass().add("primary-button");
        saveButton.setDisable(!canWrite);
        saveButton.setOnAction(event -> save());

        final GridPane grid = new GridPane();
        grid.getStyleClass().add("form-grid");
        grid.setHgap(12);
        grid.setVgap(12);
        addRow(grid, 0, "Business Name", businessNameField);
        addRow(grid, 1, "Legal Name", legalNameField);
        addRow(grid, 2, "VAT Registration", vatField);
        addRow(grid, 3, "Phone", phoneField);
        addRow(grid, 4, "Email", emailField);
        addRow(grid, 5, "Address Line 1", address1Field);
        addRow(grid, 6, "Address Line 2", address2Field);
        addRow(grid, 7, "City", cityField);
        addRow(grid, 8, "Country", countryField);
        addRow(grid, 9, "Currency", currencyField);
        addRow(grid, 10, "Timezone", timezoneField);

        final Label title = UiSupport.label(firstLaunch ? "First Launch Setup" : "Business Profile", "screen-title");
        final VBox root = new VBox(16, title, grid, saveButton);
        root.getStyleClass().add("content-surface");
        root.setPadding(new Insets(24));
        load();
        return root;
    }

    private void load() {
        UiSupport.onFx(apiClient.getProfile(), profile -> profile.ifPresent(this::populate),
                "Business profile could not be loaded.");
    }

    private void populate(final BusinessProfileResponse profile) {
        businessNameField.setText(profile.businessName());
        legalNameField.setText(profile.legalName());
        vatField.setText(profile.vatRegistrationNumber());
        phoneField.setText(profile.phone());
        emailField.setText(profile.email());
        address1Field.setText(profile.addressLine1());
        address2Field.setText(profile.addressLine2());
        cityField.setText(profile.city());
        countryField.setText(profile.countryCode());
        currencyField.setText(profile.currencyCode());
        timezoneField.setText(profile.timezone());
        version = profile.version();
    }

    private void save() {
        if (businessNameField.getText().isBlank()) {
            UiSupport.alert("Business name is required.");
            return;
        }
        final BusinessProfileRequest request = new BusinessProfileRequest(
                businessNameField.getText(), legalNameField.getText(), vatField.getText(), phoneField.getText(),
                emailField.getText(), address1Field.getText(), address2Field.getText(), cityField.getText(),
                countryField.getText(), currencyField.getText(), timezoneField.getText(), version);
        UiSupport.onFx(apiClient.saveProfile(request), response -> savedCallback.run(),
                "Business profile could not be saved.");
    }

    private void addRow(final GridPane grid, final int row, final String label, final javafx.scene.Node field) {
        grid.add(new Label(label), 0, row);
        grid.add(field, 1, row);
    }
}
