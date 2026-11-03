package com.stylemind.wardrobecommunity.openapi;

import com.stylemind.wardrobecommunity.dto.chat.SendChatImagesRequest;
import com.stylemind.wardrobecommunity.dto.chat.SendChatTextRequest;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Encoding;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.parameters.RequestBody;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Springdoc can pick only one overloaded handler; preserve both real request media types explicitly. */
@Configuration(proxyBeanMethods=false)
public class ChatImageOpenApiConfiguration {
    @Bean OpenApiCustomizer chatMessageMediaTypes() {
        return api -> {
            var path=api.getPaths().get("/api/v1/chats/{chatId}/messages");
            if(path==null||path.getPost()==null) return;
            var content=new Content();
            for(var entry:java.util.Map.of("application/json",SendChatTextRequest.class,"multipart/form-data",SendChatImagesRequest.class).entrySet()){
                var resolved=ModelConverters.getInstance().resolveAsResolvedSchema(new AnnotatedType(entry.getValue()).resolveAsRef(true));
                resolved.referencedSchemas.forEach(api.getComponents()::addSchemas);
                var media=new MediaType().schema(resolved.schema);
                if(entry.getKey().equals("multipart/form-data")) media.addEncoding("message",new Encoding().contentType("application/json"));
                content.addMediaType(entry.getKey(),media);
            }
            path.getPost().setRequestBody(new RequestBody().required(true).content(content)
                    .description("JSON for text; multipart message JSON plus 1–5 images for private JPEG/PNG/WebP uploads."));
        };
    }
}
