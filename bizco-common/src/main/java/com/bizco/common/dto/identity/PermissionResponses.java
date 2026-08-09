package com.bizco.common.dto.identity;

import java.util.List;

public final class PermissionResponses {

    private PermissionResponses() {
    }

    public record PermissionResponse(
            String permissionCode,
            String module,
            String action,
            String description
    ) {
    }

    public record PermissionListResponse(List<PermissionResponse> permissions) {
    }
}
