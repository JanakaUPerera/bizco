package com.bizco.server.identity.controller;

import com.bizco.common.dto.scheduling.AppointmentDtos.TechnicianListResponse;
import com.bizco.common.dto.scheduling.AppointmentDtos.TechnicianResponse;
import com.bizco.server.identity.entity.StaffProfile;
import com.bizco.server.identity.repository.StaffProfileRepository;
import com.bizco.server.identity.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** ApiContracts.md &sect;28.1: schedulable staff, independent of RBAC role. */
@RestController
@RequestMapping("/api/v1/staff/technicians")
public class StaffTechnicianController {

    private final StaffProfileRepository staffProfileRepository;
    private final UserRepository userRepository;

    public StaffTechnicianController(final StaffProfileRepository staffProfileRepository,
                                      final UserRepository userRepository) {
        this.staffProfileRepository = staffProfileRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('appointment.read')")
    TechnicianListResponse list(@RequestParam(required = false) final Boolean active,
                                @RequestParam(required = false) final String q) {
        final String query = q == null || q.isBlank() ? null : q.trim();
        return new TechnicianListResponse(staffProfileRepository.searchTechnicians(active, query).stream()
                .map(this::toResponse).toList());
    }

    private TechnicianResponse toResponse(final StaffProfile profile) {
        return userRepository.findById(profile.getUserId())
                .map(user -> new TechnicianResponse(user.getId(), user.getDisplayName(), user.isActive()))
                .orElse(new TechnicianResponse(profile.getUserId(), profile.getDisplayName(), false));
    }
}
