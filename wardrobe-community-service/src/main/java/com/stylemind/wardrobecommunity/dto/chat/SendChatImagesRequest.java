package com.stylemind.wardrobecommunity.dto.chat;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public record SendChatImagesRequest(
        @Schema(requiredMode=Schema.RequiredMode.REQUIRED) SendChatImageMetadata message,
        @ArraySchema(minItems=1, maxItems=5, schema=@Schema(type="string",format="binary")) List<MultipartFile> images) { }
