package com.stylemind.wardrobecommunity.validation.chat;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import com.stylemind.wardrobecommunity.model.chat.ChatImage;
import com.stylemind.wardrobecommunity.model.chat.ChatImageCommand;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import jakarta.servlet.ServletException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Semaphore;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ChatImageRequestValidator {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    public static final long MAX_PIXELS = 40_000_000;
    private final ObjectReader reader;
    private final ChatSessionRequestValidator sessions;
    private final ChatMessageRequestValidator text;
    private final Semaphore decode = new Semaphore(1);

    public ChatImageRequestValidator(ObjectMapper mapper, ChatSessionRequestValidator sessions, ChatMessageRequestValidator text) {
        this.reader = mapper.readerFor(JsonNode.class).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        this.sessions = sessions;
        this.text = text;
    }
    public ChatImageCommand validate(HttpServletRequest request) {
        if (request.getQueryString() != null) { throw invalid(); }
        try {
            var parts = request.getParts();
            if (parts.stream().anyMatch(part -> !Set.of("message", "images").contains(part.getName()))) { throw invalid(); }
            var metadata = parts.stream().filter(part -> part.getName().equals("message")).toList();
            var images = parts.stream().filter(part -> part.getName().equals("images")).toList();
            if (metadata.size() != 1 || images.isEmpty() || images.size() > 5) { throw invalid(); }
            // Multipart metadata is bounded separately from uploaded file data.
            if (metadata.getFirst().getSize() > 65536 || metadata.getFirst().getSize() < 1
                    || metadata.getFirst().getContentType() == null
                    || !MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(metadata.getFirst().getContentType()))) { throw invalid(); }
            JsonNode node;
            try (var input = metadata.getFirst().getInputStream()) { node = reader.readTree(input.readNBytes(65537)); }
            if (node == null || !node.isObject() || !node.path("client_message_id").isTextual()
                    || node.size() < 1 || node.size() > 2) { throw invalid(); }
            var names = node.fieldNames();
            while (names.hasNext()) { if (!Set.of("client_message_id", "content").contains(names.next())) { throw invalid(); } }
            var key = sessions.uuid(node.path("client_message_id").asText());
            String caption = null;
            if (node.hasNonNull("content")) {
                if (!node.path("content").isTextual()) { throw invalid(); }
                caption = text.send(("{\"client_message_id\":\"" + key + "\",\"content\":"
                        + node.get("content").toString() + "}").getBytes(java.nio.charset.StandardCharsets.UTF_8)).content();
            }
            var validated = new ArrayList<ChatImage>();
            for (Part file : images) { validated.add(image(file.getSize(), file.getContentType(), file::getInputStream)); }
            return new ChatImageCommand(key, caption, validated);
        } catch (IOException | ServletException | IllegalArgumentException failed) { throw invalid(); }
    }
    public ChatImage image(MultipartFile file) {
        if (file == null) { throw invalid(); }
        return image(file.getSize(), file.getContentType(), file::getInputStream);
    }
    private interface Input { InputStream open() throws IOException; }
    private ChatImage image(long size, String declared, Input source) {
        if (size < 1 || size > MAX_BYTES) { throw invalid(); }
        if (!decode.tryAcquire()) {
            throw new ApiOperationException(HttpStatus.SERVICE_UNAVAILABLE, "CHAT_IMAGE_PROCESSOR_BUSY",
                    "Image processing is busy; retry later");
        }
        try (var input = source.open()) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length < 1 || bytes.length > MAX_BYTES) { throw invalid(); }
            try (var imageInput = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                if (imageInput == null) { throw invalid(); }
                var readers = ImageIO.getImageReaders(imageInput);
                if (!readers.hasNext()) { throw invalid(); }
                var imageReader = readers.next();
                try {
                    imageReader.setInput(imageInput, true, true);
                    String mime = switch (imageReader.getFormatName().toLowerCase(Locale.ROOT)) {
                        case "jpeg", "jpg" -> "image/jpeg";
                        case "png" -> "image/png";
                        case "webp" -> "image/webp";
                        default -> throw invalid();
                    };
                    int width = imageReader.getWidth(0), height = imageReader.getHeight(0);
                    if (width < 1 || height < 1 || (long) width * height > MAX_PIXELS) { throw invalid(); }
                    if (declared != null && declared.toLowerCase(Locale.ROOT).startsWith("image/")
                            && !declared.equalsIgnoreCase(mime) && !(mime.equals("image/jpeg") && declared.equalsIgnoreCase("image/jpg"))) {
                        throw invalid();
                    }
                    // Verify decoding with a bounded sample; the original bytes are stored unchanged.
                    var param = imageReader.getDefaultReadParam();
                    int sampleX = Math.max(1, Math.ceilDiv(width, 1024));
                    int sampleY = Math.max(1, Math.ceilDiv(height, 1024));
                    param.setSourceSubsampling(sampleX, sampleY, 0, 0);
                    var decoded = imageReader.read(0, param);
                    if (decoded == null || decoded.getWidth() != Math.ceilDiv(width, sampleX)
                            || decoded.getHeight() != Math.ceilDiv(height, sampleY)) { throw invalid(); }
                    decoded.flush();
                    return new ChatImage(bytes, mime, sha256(bytes));
                } finally { imageReader.dispose(); }
            }
        } catch (ApiOperationException rejected) { throw rejected; }
        catch (IOException | RuntimeException failed) { throw invalid(); }
        finally { decode.release(); }
    }
    public static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 is required"); }
    }
    private static ApiOperationException invalid() {
        return new ApiOperationException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Image request is missing or malformed");
    }
}
