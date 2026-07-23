package com.vs.vsaiagent.orchestration;

import java.util.List;

public record CapabilityCatalog(List<CapabilityDescriptor> capabilities) {

    public CapabilityCatalog {
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
    }

    public CapabilityDescriptor find(String id) {
        return capabilities.stream().filter(item -> item.id().equals(id)).findFirst().orElse(null);
    }
}
