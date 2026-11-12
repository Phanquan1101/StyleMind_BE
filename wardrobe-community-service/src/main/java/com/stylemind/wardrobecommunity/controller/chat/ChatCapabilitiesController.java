package com.stylemind.wardrobecommunity.controller.chat;

import com.stylemind.wardrobecommunity.storage.chat.ChatImageStorage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Chat Sessions")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class ChatCapabilitiesController {
    private final ChatImageStorage storage;

    /** Lets the web app hide the image picker when private image storage is not configured on this deployment. */
    @GetMapping(path = "/api/v1/me/chats/capabilities", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Chat features available on this deployment")
    public ResponseEntity<Capabilities> capabilities() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Capabilities(storage.isAvailable()));
    }

    public record Capabilities(@com.fasterxml.jackson.annotation.JsonProperty("images_enabled") boolean imagesEnabled) { }
}
