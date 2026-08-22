package com.bizco.client.sales.view;

import com.bizco.client.catalog.service.CatalogApiClient;
import com.bizco.client.catalog.service.VariantApiClient;
import com.bizco.client.catalog.view.VariantPickerDialog;
import com.bizco.client.customer.service.CustomerApiClient;
import com.bizco.client.sales.service.HeldSaleApiClient;
import com.bizco.client.sales.service.InvoiceApiClient;
import com.bizco.client.ui.Icons;
import com.bizco.client.ui.UiSupport;
import com.bizco.common.dto.catalog.CatalogDtos.ProductBarcodeResponse;
import com.bizco.common.dto.catalog.CatalogDtos.ProductSummaryResponse;
import com.bizco.common.dto.catalog.CatalogDtos.VariantResponse;
import com.bizco.common.dto.customer.CustomerDtos.CustomerSummaryResponse;
import com.bizco.common.dto.sales.HeldSaleDtos.HeldSaleItemRequest;
import com.bizco.common.dto.sales.HeldSaleDtos.HeldSaleSummaryResponse;
import com.bizco.common.dto.sales.HeldSaleDtos.HoldSaleRequest;
import com.bizco.common.dto.sales.InvoiceDtos.AddInvoiceLineRequest;
import com.bizco.common.dto.sales.InvoiceDtos.CreateDraftInvoiceRequest;
import com.bizco.common.dto.sales.InvoiceDtos.DiscountRequest;
import com.bizco.common.dto.sales.InvoiceDtos.InvoiceDetailResponse;
import com.bizco.common.dto.sales.InvoiceDtos.InvoiceLineResponse;
import com.bizco.common.dto.sales.InvoiceDtos.UpdateInvoiceHeaderRequest;
import com.bizco.common.dto.sales.InvoiceDtos.UpdateInvoiceLineRequest;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.converter.BigDecimalStringConverter;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

/**
 * POS cart/checkout screen (DevelopmentPlan.md Week 6/7 tasks 6.6-6.8, 7.8). A DRAFT invoice
 * backs the active cart from the first item added onward - every add/remove/quantity/discount
 * change goes through the same draft APIs the rest of Sales uses, so the totals shown here are
 * always the same authoritative, tax-calculated numbers {@code PostSaleService} will post, not a
 * client-side estimate. Holding a cart snapshots its PRODUCT lines into a held sale
 * (held_sale_items has no SERVICE/CUSTOM concept - DatabaseDesign.md &sect;12.2), so Hold is only
 * enabled while every line is a PRODUCT line.
 */
public class PosView {

    private final InvoiceApiClient invoiceApiClient;
    private final HeldSaleApiClient heldSaleApiClient;
    private final CatalogApiClient catalogApiClient;
    private final VariantApiClient variantApiClient;
    private final CustomerApiClient customerApiClient;
    private final boolean canCreate;
    private final boolean canHold;

    private final TextField productSearchField = new TextField();
    private final TableView<ProductSummaryResponse> productTable = new TableView<>();
    private final TableView<InvoiceLineResponse> cartTable = new TableView<>();
    private final TableView<HeldSaleSummaryResponse> heldTable = new TableView<>();
    private final ComboBox<String> heldStatusFilter = new ComboBox<>();
    private final Label customerLabel = new Label("Walk-in customer");
    private final Label subtotalLabel = new Label("0.00");
    private final Label discountLabel = new Label("0.00");
    private final Label vatLabel = new Label("0.00");
    private final Label totalLabel = new Label("0.00");
    private final Label stateLabel = new Label("Start a new sale by scanning or searching a product.");
    private final Button holdButton = Icons.button("Hold", FontAwesomeSolid.PAUSE_CIRCLE);
    private final Button payButton = Icons.button("Pay", FontAwesomeSolid.CASH_REGISTER);
    private final Button newSaleButton = Icons.button("New Sale", FontAwesomeSolid.FILE_INVOICE);
    private final Button removeLineButton = Icons.button("Remove Line", FontAwesomeSolid.TRASH);
    private final Button editLineButton = Icons.button("Edit Line", FontAwesomeSolid.PEN);
    private final Button headerDiscountButton = Icons.button("Discount", FontAwesomeSolid.PERCENT);
    private final Button selectCustomerButton = Icons.button("Customer", FontAwesomeSolid.USER);
    private final Button clearCustomerButton = Icons.button("Clear", FontAwesomeSolid.USER_SLASH);
    private final TabPane tabs = new TabPane();

    private InvoiceDetailResponse currentInvoice;
    private CustomerSummaryResponse selectedCustomer;

    public PosView(final InvoiceApiClient invoiceApiClient, final HeldSaleApiClient heldSaleApiClient,
                   final CatalogApiClient catalogApiClient, final VariantApiClient variantApiClient,
                   final CustomerApiClient customerApiClient, final boolean canCreate, final boolean canHold) {
        this.invoiceApiClient = invoiceApiClient;
        this.heldSaleApiClient = heldSaleApiClient;
        this.catalogApiClient = catalogApiClient;
        this.variantApiClient = variantApiClient;
        this.customerApiClient = customerApiClient;
        this.canCreate = canCreate;
        this.canHold = canHold;
    }

    public Parent createView() {
        configureProductTable();
        configureCartTable();
        configureHeldTable();

        final Tab saleTab = new Tab("Sale", saleContent());
        saleTab.setClosable(false);
        final Tab heldTab = new Tab("Held Bills", heldContent());
        heldTab.setClosable(false);
        heldTab.setOnSelectionChanged(event -> {
            if (heldTab.isSelected()) {
                loadHeldSales();
            }
        });
        tabs.getTabs().setAll(saleTab, heldTab);

        final BorderPane root = new BorderPane(tabs);
        root.getStyleClass().add("content-surface");
        refreshCartUi();
        return root;
    }

    // ---- Sale tab -----------------------------------------------------------------------------

    private HBox saleContent() {
        final HBox content = new HBox(12, productPanel(), cartPanel());
        content.setPadding(new Insets(12));
        HBox.setHgrow(content.getChildren().get(0), Priority.SOMETIMES);
        HBox.setHgrow(content.getChildren().get(1), Priority.ALWAYS);
        return content;
    }

    private VBox productPanel() {
        productSearchField.setPromptText("Scan barcode or search SKU / name, then Enter");
        productSearchField.setOnAction(event -> searchProducts());
        final Button searchButton = Icons.button("Search", FontAwesomeSolid.SEARCH);
        searchButton.setOnAction(event -> searchProducts());
        final HBox searchRow = new HBox(8, productSearchField, searchButton);
        HBox.setHgrow(productSearchField, Priority.ALWAYS);

        final Button addButton = Icons.button("Add to Cart", FontAwesomeSolid.CART_PLUS);
        addButton.setDisable(!canCreate);
        addButton.setOnAction(event -> {
            final ProductSummaryResponse selected = productTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                addProductToCart(selected);
            }
        });

        final VBox panel = new VBox(10, UiSupport.label("Products", "panel-title"), searchRow, productTable, addButton);
        VBox.setVgrow(productTable, Priority.ALWAYS);
        panel.setPadding(new Insets(4));
        panel.setPrefWidth(420);
        return panel;
    }

    private VBox cartPanel() {
        selectCustomerButton.setOnAction(event -> pickCustomer());
        clearCustomerButton.setOnAction(event -> {
            selectedCustomer = null;
            applyHeaderCustomer(null);
        });
        final HBox customerRow = new HBox(10, customerLabel, spacer(), selectCustomerButton, clearCustomerButton);
        customerRow.setAlignment(Pos.CENTER_LEFT);

        removeLineButton.setDisable(true);
        editLineButton.setDisable(true);
        removeLineButton.setOnAction(event -> removeSelectedLine());
        editLineButton.setOnAction(event -> editSelectedLine());
        cartTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, line) -> {
            removeLineButton.setDisable(line == null);
            editLineButton.setDisable(line == null);
        });
        final HBox lineActions = new HBox(8, editLineButton, removeLineButton);

        headerDiscountButton.setOnAction(event -> editHeaderDiscount());
        final HBox totals = totalsGrid();

        newSaleButton.setOnAction(event -> startNewSale());
        holdButton.setDisable(true);
        holdButton.setOnAction(event -> holdCurrentSale());
        payButton.setDisable(true);
        payButton.setOnAction(event -> openPaymentDialog());
        final HBox actions = new HBox(8, newSaleButton, headerDiscountButton, spacer(), holdButton, payButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        final VBox panel = new VBox(10, UiSupport.label("Cart", "panel-title"), customerRow, stateLabel, cartTable,
                lineActions, totals, actions);
        VBox.setVgrow(cartTable, Priority.ALWAYS);
        panel.setPadding(new Insets(4));
        return panel;
    }

    private HBox totalsGrid() {
        final GridPane grid = new GridPane();
        grid.setHgap(24);
        grid.add(totalsPair("Subtotal", subtotalLabel), 0, 0);
        grid.add(totalsPair("Discount", discountLabel), 1, 0);
        grid.add(totalsPair("VAT", vatLabel), 2, 0);
        totalLabel.getStyleClass().add("kpi-value");
        grid.add(totalsPair("Total", totalLabel), 3, 0);
        return new HBox(grid);
    }

    private VBox totalsPair(final String title, final Label valueLabel) {
        return new VBox(2, UiSupport.label(title, "form-caption"), valueLabel);
    }

    private void configureProductTable() {
        productTable.getStyleClass().add("data-table");
        productTable.getColumns().setAll(
                column("SKU", ProductSummaryResponse::sku),
                column("Name", ProductSummaryResponse::name),
                column("Barcode", p -> p.barcode() == null ? "" : p.barcode()),
                column("Price", p -> p.sellingPrice().toPlainString()));
        productTable.setRowFactory(table -> {
            final javafx.scene.control.TableRow<ProductSummaryResponse> row = new javafx.scene.control.TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !row.isEmpty()) {
                    addProductToCart(row.getItem());
                }
            });
            return row;
        });
    }

    private void configureCartTable() {
        cartTable.getStyleClass().add("data-table");
        cartTable.getColumns().setAll(
                column("#", line -> String.valueOf(line.lineNumber())),
                column("Description", InvoiceLineResponse::descriptionSnapshot),
                column("Qty", line -> line.quantity().stripTrailingZeros().toPlainString()),
                column("Unit Price", line -> line.unitPrice().toPlainString()),
                column("Discount", this::discountLabelText),
                column("Line Total", line -> line.lineTotalInclVat().toPlainString()));
    }

    private String discountLabelText(final InvoiceLineResponse line) {
        if ("NONE".equals(line.discountType()) || line.discountValue() == null
                || line.discountValue().compareTo(BigDecimal.ZERO) == 0) {
            return "-";
        }
        return "PERCENTAGE".equals(line.discountType()) ? line.discountValue().toPlainString() + "%"
                : line.discountValue().toPlainString();
    }

    /** Tries an exact barcode match first (task 19.1 - may resolve straight to a specific variant),
     *  falling back to the plain name/SKU search that populates {@code productTable}. A barcode
     *  miss (404, the common case when the cashier is typing a name) is expected and silently
     *  falls through - a real error (e.g. session expiry) still surfaces via the fallback search's
     *  own {@code UiSupport.onFx} handling. */
    private void searchProducts() {
        final String query = productSearchField.getText();
        if (query == null || query.isBlank()) {
            return;
        }
        catalogApiClient.barcode(query).handle((response, error) -> response)
                .thenAccept(response -> javafx.application.Platform.runLater(() -> {
                    if (response != null) {
                        productSearchField.clear();
                        addProductToCart(response.product(), response.resolvedVariantId(), response.variantSpecific());
                    } else {
                        runNameSearch(query);
                    }
                }));
    }

    private void runNameSearch(final String query) {
        UiSupport.onFx(catalogApiClient.products(query, null, null, true, 0, 50), result -> {
            productTable.setItems(FXCollections.observableArrayList(result.data()));
            if (result.data().size() == 1) {
                addProductToCart(result.data().get(0));
                productSearchField.clear();
            }
        }, "Products could not be searched.");
    }

    /** Picked from the product-search table - not yet resolved to a specific variant. */
    private void addProductToCart(final ProductSummaryResponse product) {
        addProductToCart(product, null, false);
    }

    /** Phase 6 Week 19 (task 19.1/19.2): {@code knownVariantId} is trusted as-is only when
     *  {@code variantSpecific} is true (an exact variant-barcode match) - every other path (a
     *  product-level barcode match, or a plain search-table pick) still needs to check whether the
     *  product has more than one active variant and prompt via {@link VariantPickerDialog} before
     *  committing to one. */
    private void addProductToCart(final ProductSummaryResponse product, final UUID knownVariantId,
                                  final boolean variantSpecific) {
        if (variantSpecific) {
            addProductLine(product, knownVariantId);
            return;
        }
        UiSupport.onFx(variantApiClient.variants(product.productId()), variants -> {
            final VariantResponse picked = VariantPickerDialog.show(variants, product.name());
            if (picked == null && variants.stream().filter(VariantResponse::active).count() > 1) {
                return; // user cancelled the picker
            }
            final UUID variantId = picked != null ? picked.productVariantId() : knownVariantId;
            if (variantId == null) {
                UiSupport.alert("Product has no active variant to sell.");
                return;
            }
            addProductLine(product, variantId);
        }, "Product variants could not be loaded.");
    }

    /** Scanning the same product+variant again bumps its existing line's quantity, matching real
     *  POS behavior. */
    private void addProductLine(final ProductSummaryResponse product, final UUID productVariantId) {
        if (currentInvoice == null) {
            createDraftThen(() -> addProductLine(product, productVariantId));
            return;
        }
        final InvoiceLineResponse existing = currentInvoice.lines().stream()
                .filter(line -> "PRODUCT".equals(line.lineType()) && product.productId().equals(line.productId())
                        && java.util.Objects.equals(productVariantId, line.productVariantId()))
                .findFirst().orElse(null);
        if (existing != null) {
            UiSupport.onFx(invoiceApiClient.updateLine(currentInvoice.invoiceId(), existing.invoiceLineId(),
                    new UpdateInvoiceLineRequest(existing.quantity().add(BigDecimal.ONE), null, null, currentInvoice.version())),
                    this::setCurrentInvoice, "Cart could not be updated.");
            return;
        }
        UiSupport.onFx(invoiceApiClient.addLine(currentInvoice.invoiceId(), new AddInvoiceLineRequest("PRODUCT",
                product.productId(), productVariantId, null, null, BigDecimal.ONE, null, null, DiscountRequest.NONE)),
                this::setCurrentInvoice, "Product could not be added to the cart.");
    }

    private void createDraftThen(final Runnable next) {
        final UUID customerId = selectedCustomer == null ? null : selectedCustomer.customerId();
        UiSupport.onFx(invoiceApiClient.createDraft(new CreateDraftInvoiceRequest(java.time.LocalDate.now(), null,
                "SALES", customerId, null)), created -> {
            currentInvoice = new InvoiceDetailResponse(created.invoiceId(), created.invoiceNumber(),
                    created.invoiceDate(), null, "SALES", created.status(), created.customerId(), null,
                    BigDecimal.ZERO, "NONE", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, created.totalAmount(), null, null, null, created.version(), java.util.List.of());
            next.run();
        }, "A new sale could not be started.");
    }

    private void removeSelectedLine() {
        final InvoiceLineResponse selected = cartTable.getSelectionModel().getSelectedItem();
        if (selected == null || currentInvoice == null) {
            return;
        }
        UiSupport.onFx(invoiceApiClient.deleteLine(currentInvoice.invoiceId(), selected.invoiceLineId()),
                this::setCurrentInvoice, "Line could not be removed.");
    }

    private void editSelectedLine() {
        final InvoiceLineResponse selected = cartTable.getSelectionModel().getSelectedItem();
        if (selected == null || currentInvoice == null) {
            return;
        }
        final LineEditResult result = showLineEditDialog(selected);
        if (result == null) {
            return;
        }
        UiSupport.onFx(invoiceApiClient.updateLine(currentInvoice.invoiceId(), selected.invoiceLineId(),
                new UpdateInvoiceLineRequest(result.quantity(), null,
                        new DiscountRequest(result.discountType(), result.discountValue()), currentInvoice.version())),
                this::setCurrentInvoice, "Line could not be updated.");
    }

    private void editHeaderDiscount() {
        if (currentInvoice == null) {
            return;
        }
        final DiscountEditResult result = showDiscountDialog("Invoice Discount", currentInvoice.discountType(),
                currentInvoice.discountValue());
        if (result == null) {
            return;
        }
        UiSupport.onFx(invoiceApiClient.updateHeader(currentInvoice.invoiceId(), new UpdateInvoiceHeaderRequest(
                        currentInvoice.invoiceDate(), currentInvoice.dueDate(), currentInvoice.invoiceType(),
                        currentInvoice.customerId(), new DiscountRequest(result.type(), result.value()),
                        currentInvoice.notes(), currentInvoice.version())),
                this::setCurrentInvoice, "Invoice discount could not be applied.");
    }

    private void pickCustomer() {
        final CustomerSummaryResponse picked = CustomerPickerDialog.show(customerApiClient);
        if (picked == null) {
            return;
        }
        selectedCustomer = picked;
        applyHeaderCustomer(picked.customerId());
    }

    private void applyHeaderCustomer(final UUID customerId) {
        if (currentInvoice == null) {
            customerLabel.setText(selectedCustomer == null ? "Walk-in customer" : selectedCustomer.name());
            return;
        }
        UiSupport.onFx(invoiceApiClient.updateHeader(currentInvoice.invoiceId(), new UpdateInvoiceHeaderRequest(
                        currentInvoice.invoiceDate(), currentInvoice.dueDate(), currentInvoice.invoiceType(), customerId,
                        new DiscountRequest(currentInvoice.discountType(), currentInvoice.discountValue()),
                        currentInvoice.notes(), currentInvoice.version())),
                this::setCurrentInvoice, "Customer could not be applied to this sale.");
    }

    private void startNewSale() {
        currentInvoice = null;
        selectedCustomer = null;
        productTable.getItems().clear();
        productSearchField.clear();
        refreshCartUi();
    }

    private void holdCurrentSale() {
        if (currentInvoice == null || currentInvoice.lines().isEmpty()) {
            return;
        }
        final boolean allProducts = currentInvoice.lines().stream().allMatch(line -> "PRODUCT".equals(line.lineType()));
        if (!allProducts) {
            UiSupport.alert("Only product lines can be held; remove any service/custom lines first.");
            return;
        }
        final var items = currentInvoice.lines().stream()
                .map(line -> new HeldSaleItemRequest(line.productId(), line.productVariantId(), line.quantity(),
                        line.unitPrice(), new DiscountRequest(line.discountType(), line.discountValue())))
                .toList();
        UiSupport.onFx(heldSaleApiClient.hold(new HoldSaleRequest(currentInvoice.customerId(), items, null)),
                held -> {
                    UiSupport.alert("Bill held as " + held.heldNumber() + ".");
                    startNewSale();
                }, "Sale could not be held.");
    }

    private void openPaymentDialog() {
        if (currentInvoice == null || currentInvoice.lines().isEmpty()) {
            return;
        }
        new PaymentDialog(invoiceApiClient, currentInvoice, posted -> {
            UiSupport.alert("Invoice " + posted.invoiceNumber() + " posted (" + posted.paymentStatus() + ").");
            startNewSale();
        }).show();
    }

    private void setCurrentInvoice(final InvoiceDetailResponse invoice) {
        currentInvoice = invoice;
        refreshCartUi();
    }

    private void refreshCartUi() {
        if (currentInvoice == null) {
            cartTable.setItems(FXCollections.observableArrayList());
            customerLabel.setText(selectedCustomer == null ? "Walk-in customer" : selectedCustomer.name());
            subtotalLabel.setText("0.00");
            discountLabel.setText("0.00");
            vatLabel.setText("0.00");
            totalLabel.setText("0.00");
            stateLabel.setText("Start a new sale by scanning or searching a product.");
            holdButton.setDisable(true);
            payButton.setDisable(true);
            return;
        }
        cartTable.setItems(FXCollections.observableArrayList(currentInvoice.lines()));
        customerLabel.setText(selectedCustomer == null ? "Walk-in customer" : selectedCustomer.name());
        subtotalLabel.setText(currentInvoice.subtotal().toPlainString());
        discountLabel.setText(currentInvoice.discountAmount().toPlainString());
        vatLabel.setText(currentInvoice.vatAmount().toPlainString());
        totalLabel.setText(currentInvoice.totalAmount().toPlainString());
        final boolean hasLines = !currentInvoice.lines().isEmpty();
        stateLabel.setText(hasLines ? currentInvoice.lines().size() + " line(s) in cart" : "Cart is empty.");
        holdButton.setDisable(!hasLines || !canHold);
        payButton.setDisable(!hasLines || !canCreate);
    }

    // ---- Held Bills tab -------------------------------------------------------------------------

    private VBox heldContent() {
        heldStatusFilter.getItems().setAll("", "HELD", "RESUMED", "CANCELLED", "EXPIRED", "CONVERTED");
        heldStatusFilter.setValue("");
        heldStatusFilter.setOnAction(event -> loadHeldSales());
        final Button refreshButton = Icons.button("Refresh", FontAwesomeSolid.SYNC);
        refreshButton.setOnAction(event -> loadHeldSales());
        final HBox header = new HBox(10, UiSupport.label("Held Bills", "screen-title"), spacer(),
                heldStatusFilter, refreshButton);
        header.getStyleClass().add("screen-header");

        final Button resumeButton = Icons.button("Resume", FontAwesomeSolid.PLAY_CIRCLE);
        final Button cancelButton = Icons.button("Cancel Hold", FontAwesomeSolid.BAN);
        resumeButton.setDisable(true);
        cancelButton.setDisable(true);
        heldTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, held) -> {
            final boolean active = held != null && ("HELD".equals(held.status()) || "RESUMED".equals(held.status()));
            resumeButton.setDisable(!active || !canHold);
            cancelButton.setDisable(!active || !canHold);
        });
        resumeButton.setOnAction(event -> resumeSelectedHeldSale());
        cancelButton.setOnAction(event -> cancelSelectedHeldSale());
        final HBox actions = new HBox(8, resumeButton, cancelButton);

        final VBox content = new VBox(10, header, heldTable, actions);
        content.setPadding(new Insets(12));
        VBox.setVgrow(heldTable, Priority.ALWAYS);
        return content;
    }

    private void configureHeldTable() {
        heldTable.getStyleClass().add("data-table");
        heldTable.getColumns().setAll(
                column("Number", HeldSaleSummaryResponse::heldNumber),
                column("Status", HeldSaleSummaryResponse::status),
                column("Items", held -> String.valueOf(held.itemCount())),
                column("Est. Total", held -> held.estimatedTotal().toPlainString()),
                column("Held At", held -> String.valueOf(held.heldAt())));
    }

    private void loadHeldSales() {
        UiSupport.onFx(heldSaleApiClient.search(heldStatusFilter.getValue()),
                result -> heldTable.setItems(FXCollections.observableArrayList(result.data())),
                "Held bills could not be loaded.");
    }

    private void resumeSelectedHeldSale() {
        final HeldSaleSummaryResponse selected = heldTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        final CompletableFuture<InvoiceDetailResponse> resumed = heldSaleApiClient.resume(selected.heldSaleId())
                .thenCompose(detail -> heldSaleApiClient.convert(selected.heldSaleId()));
        UiSupport.onFx(resumed, invoice -> {
            setCurrentInvoice(invoice);
            loadCustomerIfPresent(invoice.customerId());
            tabs.getSelectionModel().select(0);
        }, "Held bill could not be resumed.");
    }

    private void loadCustomerIfPresent(final UUID customerId) {
        if (customerId == null) {
            selectedCustomer = null;
            refreshCartUi();
            return;
        }
        UiSupport.onFx(customerApiClient.get(customerId), customer -> {
            selectedCustomer = new CustomerSummaryResponse(customer.customerId(), customer.customerCode(),
                    customer.name(), customer.phone(), customer.category(), customer.status(), customer.creditLimit(),
                    customer.anonymized(), customer.version());
            refreshCartUi();
        }, "Customer could not be loaded.");
    }

    private void cancelSelectedHeldSale() {
        final HeldSaleSummaryResponse selected = heldTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        UiSupport.onFx(heldSaleApiClient.cancel(selected.heldSaleId()), cancelled -> loadHeldSales(),
                "Held bill could not be cancelled.");
    }

    // ---- small dialogs --------------------------------------------------------------------------

    private record LineEditResult(BigDecimal quantity, String discountType, BigDecimal discountValue) {
    }

    private record DiscountEditResult(String type, BigDecimal value) {
    }

    private LineEditResult showLineEditDialog(final InvoiceLineResponse line) {
        final Dialog<LineEditResult> dialog = new Dialog<>();
        dialog.setTitle("Edit Line");
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        final TextField qtyField = numberField(line.quantity().stripTrailingZeros().toPlainString());
        final ComboBox<String> discountType = new ComboBox<>(FXCollections.observableArrayList("NONE", "PERCENTAGE", "FIXED"));
        discountType.setValue(line.discountType());
        final TextField discountValue = numberField(line.discountValue() == null ? "0" : line.discountValue().toPlainString());
        final GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.addRow(0, new Label("Quantity"), qtyField);
        grid.addRow(1, new Label("Discount Type"), discountType);
        grid.addRow(2, new Label("Discount Value"), discountValue);
        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(button -> button == ButtonType.OK
                ? new LineEditResult(parse(qtyField.getText(), line.quantity()), discountType.getValue(),
                        parse(discountValue.getText(), BigDecimal.ZERO))
                : null);
        return dialog.showAndWait().orElse(null);
    }

    private DiscountEditResult showDiscountDialog(final String title, final String currentType, final BigDecimal currentValue) {
        final Dialog<DiscountEditResult> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        final ComboBox<String> discountType = new ComboBox<>(FXCollections.observableArrayList("NONE", "PERCENTAGE", "FIXED"));
        discountType.setValue(currentType == null ? "NONE" : currentType);
        final TextField discountValue = numberField(currentValue == null ? "0" : currentValue.toPlainString());
        final GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.addRow(0, new Label("Type"), discountType);
        grid.addRow(1, new Label("Value"), discountValue);
        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(button -> button == ButtonType.OK
                ? new DiscountEditResult(discountType.getValue(), parse(discountValue.getText(), BigDecimal.ZERO))
                : null);
        return dialog.showAndWait().orElse(null);
    }

    private TextField numberField(final String initial) {
        final TextField field = new TextField(initial);
        field.setTextFormatter(new TextFormatter<>(new BigDecimalStringConverter()));
        return field;
    }

    private BigDecimal parse(final String text, final BigDecimal fallback) {
        try {
            return text == null || text.isBlank() ? fallback : new BigDecimal(text.trim());
        } catch (final NumberFormatException exception) {
            return fallback;
        }
    }

    private <T> TableColumn<T, String> column(final String title, final java.util.function.Function<T, String> value) {
        final TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        column.setPrefWidth(110);
        return column;
    }

    private HBox spacer() {
        final HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }
}
