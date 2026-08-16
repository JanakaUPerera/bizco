package com.bizco.client.system.view;

import com.bizco.client.system.service.BusinessProfileApiClient;
import com.bizco.client.system.service.TaxConfigurationApiClient;
import com.bizco.client.ui.Icons;
import com.bizco.client.ui.UiSupport;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

/**
 * First-launch setup: business profile, then tax configuration, then a confirmation screen.
 * Shown instead of the dashboard when no business profile exists yet
 * (see {@code BizcoClientApplication.checkFirstLaunchProfile}).
 */
public class OnboardingWizardView {

    private static final String[] STEP_TITLES = {"Business Profile", "Tax Configuration", "Confirm"};

    private final BusinessProfileApiClient businessProfileApiClient;
    private final TaxConfigurationApiClient taxConfigurationApiClient;
    private final Runnable onComplete;
    private final StackPane content = new StackPane();
    private final Label[] stepLabels = new Label[STEP_TITLES.length];

    public OnboardingWizardView(final BusinessProfileApiClient businessProfileApiClient,
                                final TaxConfigurationApiClient taxConfigurationApiClient,
                                final Runnable onComplete) {
        this.businessProfileApiClient = businessProfileApiClient;
        this.taxConfigurationApiClient = taxConfigurationApiClient;
        this.onComplete = onComplete;
    }

    public Parent createView() {
        final HBox steps = new HBox(24);
        steps.getStyleClass().add("wizard-steps");
        steps.setAlignment(Pos.CENTER_LEFT);
        for (int i = 0; i < STEP_TITLES.length; i++) {
            stepLabels[i] = new Label((i + 1) + ". " + STEP_TITLES[i]);
            stepLabels[i].getStyleClass().add("wizard-step");
            steps.getChildren().add(stepLabels[i]);
        }

        final Label title = UiSupport.label("First-Time Setup", "screen-title");
        final Label intro = UiSupport.label(
                "Set up your business details before taking your first sale. You can change any of "
                        + "this later from the corresponding module.", "field-hint");
        final VBox root = new VBox(16, title, intro, steps, content);
        root.getStyleClass().add("content-surface");
        root.setPadding(new Insets(24));
        showStep(0);
        return root;
    }

    private void showStep(final int step) {
        for (int i = 0; i < stepLabels.length; i++) {
            stepLabels[i].getStyleClass().remove("wizard-step-active");
            if (i == step) {
                stepLabels[i].getStyleClass().add("wizard-step-active");
            }
        }
        final Parent stepView = switch (step) {
            case 0 -> new BusinessProfileView(businessProfileApiClient, true, () -> showStep(1)).createView(true);
            case 1 -> new TaxConfigurationView(taxConfigurationApiClient, true, () -> showStep(2)).createView();
            default -> confirmationStep();
        };
        content.getChildren().setAll(stepView);
    }

    private Parent confirmationStep() {
        final Label message = UiSupport.label(
                "Setup complete. Business profile and tax configuration are saved and can be revisited "
                        + "anytime from their own screens.", "field-hint");
        final Button finish = Icons.button("Go to Dashboard", FontAwesomeSolid.CHECK_CIRCLE);
        finish.getStyleClass().add("primary-button");
        finish.setOnAction(event -> onComplete.run());
        final VBox box = new VBox(16, message, finish);
        return box;
    }
}
