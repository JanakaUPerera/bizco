package com.bizco.server.customer.api;

import com.bizco.common.dto.customer.CustomerDtos.CustomerCreateRequest;
import com.bizco.common.dto.customer.CustomerDtos.CustomerCreditSummaryResponse;
import com.bizco.common.dto.customer.CustomerDtos.CustomerDetailResponse;
import com.bizco.common.dto.customer.CustomerDtos.CustomerSearchResponse;
import com.bizco.common.dto.customer.CustomerDtos.CustomerSummaryResponse;
import com.bizco.common.dto.customer.CustomerDtos.CustomerUpdateRequest;
import com.bizco.server.customer.application.CustomerService;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(final CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('customer.read')")
    CustomerSearchResponse search(@RequestParam(required = false) final String q,
                                  @RequestParam(required = false) final String category,
                                  @RequestParam(required = false) final String status,
                                  @RequestParam(defaultValue = "0") final int page,
                                  @RequestParam(defaultValue = "20") final int size) {
        final Page<CustomerSummaryResponse> result = customerService.search(q, category, status, page, size);
        return new CustomerSearchResponse(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('customer.create')")
    ResponseEntity<CustomerDetailResponse> create(@RequestBody final CustomerCreateRequest request,
                                                  final Authentication authentication) {
        final CustomerDetailResponse created = customerService.create(request, authentication);
        return ResponseEntity.created(URI.create("/api/v1/customers/" + created.customerId())).body(created);
    }

    @GetMapping("/{customerId}")
    @PreAuthorize("hasAuthority('customer.read')")
    CustomerDetailResponse get(@PathVariable final UUID customerId, final Authentication authentication) {
        return customerService.get(customerId, hasPermission(authentication, "customer.view_pii"), authentication);
    }

    @PutMapping("/{customerId}")
    @PreAuthorize("hasAuthority('customer.update')")
    CustomerDetailResponse update(@PathVariable final UUID customerId, @RequestBody final CustomerUpdateRequest request,
                                  final Authentication authentication) {
        return customerService.update(customerId, request, authentication);
    }

    @PostMapping("/{customerId}/block")
    @PreAuthorize("hasAuthority('customer.update')")
    CustomerDetailResponse block(@PathVariable final UUID customerId, final Authentication authentication) {
        return customerService.block(customerId, authentication);
    }

    @PostMapping("/{customerId}/activate")
    @PreAuthorize("hasAuthority('customer.update')")
    CustomerDetailResponse activate(@PathVariable final UUID customerId, final Authentication authentication) {
        return customerService.activate(customerId, authentication);
    }

    @GetMapping("/{customerId}/credit-summary")
    @PreAuthorize("hasAuthority('customer.credit.read')")
    CustomerCreditSummaryResponse creditSummary(@PathVariable final UUID customerId) {
        return customerService.creditSummary(customerId);
    }

    @PostMapping("/{customerId}/anonymize")
    @PreAuthorize("hasAuthority('customer.anonymize')")
    CustomerDetailResponse anonymize(@PathVariable final UUID customerId, final Authentication authentication) {
        return customerService.anonymize(customerId, authentication);
    }

    private boolean hasPermission(final Authentication authentication, final String permission) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> permission.equals(authority.getAuthority()));
    }
}

