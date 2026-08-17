package com.bizco.server.sales.api;

import com.bizco.common.api.ApiHeaders;
import com.bizco.common.dto.finance.PaymentDtos.CustomerPaymentResponse;
import com.bizco.common.dto.finance.PaymentDtos.CustomerPaymentSearchResponse;
import com.bizco.common.dto.finance.PaymentDtos.RecordCustomerPaymentRequest;
import com.bizco.server.idempotency.service.IdempotencyService.IdempotentResult;
import com.bizco.server.sales.application.PaymentAllocationService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** ApiContracts.md &sect;17.2/17.3: one payment split across several of a customer's invoices. */
@RestController
@RequestMapping("/api/v1")
public class CustomerPaymentController {

    private final PaymentAllocationService service;

    public CustomerPaymentController(final PaymentAllocationService service) {
        this.service = service;
    }

    @PostMapping("/customer-payments")
    @PreAuthorize("hasAuthority('invoice.payment.create')")
    ResponseEntity<CustomerPaymentResponse> record(@RequestHeader(ApiHeaders.IDEMPOTENCY_KEY) final UUID idempotencyKey,
                                                    @RequestBody final RecordCustomerPaymentRequest request,
                                                    final Authentication authentication) {
        final IdempotentResult<CustomerPaymentResponse> result = service.recordForCustomer(idempotencyKey, request,
                authentication);
        return ResponseEntity.status(result.replayed() ? 200 : 201)
                .header(ApiHeaders.IDEMPOTENT_REPLAY, String.valueOf(result.replayed()))
                .body(result.response());
    }

    @GetMapping("/customers/{customerId}/payments")
    @PreAuthorize("hasAuthority('customer.read')")
    CustomerPaymentSearchResponse customerPayments(@PathVariable final UUID customerId) {
        return service.customerPayments(customerId);
    }
}
