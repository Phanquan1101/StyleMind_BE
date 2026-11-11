package com.stylemind.commerce.validation.returns;

import com.stylemind.commerce.dto.returns.ReturnCommand.Image;
import com.stylemind.commerce.errors.ApiOperationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ReturnEvidenceValidator {

    private static final int MAX_IMAGE_BYTES = 1024 * 1024;
    private static final int MAX_IMAGES = 5;
    private static final long MAX_IMAGE_PIXELS = 16_000_000;
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xff, (byte) 0xd8, (byte) 0xff};

    public List<Image> validate(List<MultipartFile> files) {
        if (files == null || files.isEmpty() || files.size() > MAX_IMAGES) {
            throw invalidEvidence();
        }
        return files.stream().map(this::read).toList();
    }

    private Image read(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > MAX_IMAGE_BYTES) {
            throw invalidEvidence();
        }
        String mime = file.getContentType();
        byte[] signature;
        if (MediaType.IMAGE_PNG_VALUE.equals(mime)) {
            signature = PNG_SIGNATURE;
        } else if (MediaType.IMAGE_JPEG_VALUE.equals(mime)) {
            signature = JPEG_SIGNATURE;
        } else {
            throw invalidEvidence();
        }
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length < signature.length || bytes.length > MAX_IMAGE_BYTES
                    || !Arrays.equals(bytes, 0, signature.length, signature, 0, signature.length)) {
                throw invalidEvidence();
            }
            validateImage(bytes);
            return new Image(mime, bytes);
        } catch (IOException exception) {
            throw invalidEvidence();
        }
    }

    private void validateImage(byte[] bytes) throws IOException {
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw invalidEvidence();
            }
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long) width * height > MAX_IMAGE_PIXELS) {
                    throw invalidEvidence();
                }
                reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    private ApiOperationException invalidEvidence() {
        return new ApiOperationException(HttpStatus.BAD_REQUEST, "INVALID_RETURN_EVIDENCE",
                "Upload one to five JPEG or PNG images, each at most 1 MiB and 16 megapixels");
    }
}
