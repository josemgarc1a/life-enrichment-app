package com.lifeenrichment.dto.response;

import lombok.Builder;
import lombok.Getter;

/** Response payload returned after a successful resident profile photo upload. */
@Getter @Builder
public class PhotoUploadResponse {
    private String photoUrl;
}
