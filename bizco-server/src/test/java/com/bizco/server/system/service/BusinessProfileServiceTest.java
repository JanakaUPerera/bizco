package com.bizco.server.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.dto.system.SystemRequests.BusinessProfileRequest;
import com.bizco.server.audit.service.AuditService;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.system.entity.BusinessProfile;
import com.bizco.server.system.repository.BusinessProfileRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class BusinessProfileServiceTest {

    private final BusinessProfileRepository repository = mock(BusinessProfileRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final BusinessProfileService service = new BusinessProfileService(repository, auditService);

    @Test
    void staleBusinessProfileUpdateReturnsConcurrentModification() {
        final BusinessProfile profile = new BusinessProfile("Bizco Auto");
        when(repository.findAll()).thenReturn(List.of(profile));

        final IdentityException exception = assertThrows(IdentityException.class, () -> service.saveProfile(
                request("Bizco Auto Updated", 99L)));

        assertEquals(ApiErrorCode.CONCURRENT_MODIFICATION, exception.getCode());
        assertEquals("Bizco Auto", profile.getBusinessName());
    }

    @Test
    void businessProfileUpdateIsAudited() {
        final BusinessProfile profile = new BusinessProfile("Bizco Auto");
        when(repository.findAll()).thenReturn(List.of(profile));
        when(repository.save(profile)).thenReturn(profile);

        service.saveProfile(request("Bizco Auto Updated", 0L));

        org.mockito.Mockito.verify(auditService).record(org.mockito.ArgumentMatchers.eq("BUSINESS_PROFILE"),
                org.mockito.ArgumentMatchers.eq("1"), org.mockito.ArgumentMatchers.eq("BUSINESS_PROFILE_UPDATED"),
                org.mockito.ArgumentMatchers.eq(null), org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.eq(null),
                org.mockito.ArgumentMatchers.eq(null));
    }

    private BusinessProfileRequest request(final String businessName, final long version) {
        return new BusinessProfileRequest(businessName, businessName, null, null, null,
                null, null, null, "LK", "LKR", "Asia/Colombo", version);
    }
}
