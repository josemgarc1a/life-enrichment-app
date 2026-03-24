package com.lifeenrichment.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter @Setter @Builder
public class LinkFamilyMemberRequest {

    @NotNull
    private UUID userId;

    private String relationshipLabel;
}
