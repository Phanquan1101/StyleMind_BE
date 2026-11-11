package com.stylemind.payment.validation;

import com.stylemind.payment.dto.RefundReceiptImage;
import com.stylemind.payment.errors.RefundErrorCode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class RefundReceiptValidator {
    public RefundReceiptImage validate(MultipartFile image) {
        if (image == null || image.isEmpty() || image.getSize() > 5242880) throw invalid();
        String mime = image.getContentType();
        if (!"image/jpeg".equals(mime) && !"image/png".equals(mime)) throw invalid();
        try {
            byte[] bytes = image.getBytes();
            try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw invalid();
                var reader = readers.next();
                try {
                    String format = reader.getFormatName();
                    if (!("image/png".equals(mime) ? "png".equalsIgnoreCase(format) : "jpeg".equalsIgnoreCase(format))) throw invalid();
                    reader.setInput(input, true, true);
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    if (width < 1 || height < 1 || (long) width * height > 16000000) throw invalid();
                    if (reader.read(0) == null) throw invalid();
                } finally { reader.dispose(); }
            }
            return new RefundReceiptImage(mime, bytes);
        } catch (IOException | IllegalArgumentException e) { throw invalid(); }
    }
    private com.stylemind.payment.errors.PaymentApiException invalid() {
        return RefundErrorCode.INVALID_REFUND_RECEIPT.exception();
    }
}
