package com.stylemind.stylist.service.session;

import com.stylemind.stylist.errors.ErrorCode;
import com.stylemind.stylist.errors.StylistApiException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Opaque, fixed-size keyset cursor bound to both the JWT owner and session path. */
@Component
public class SessionHistoryCursorCodec {

    private static final int VERSION = 1;
    private static final int ENCODED_BYTES = 61;

    public record Position(UUID customerId, UUID sessionId, Instant sentAt, UUID messageId) {
    }

    public String encode(UUID customerId, UUID sessionId, Instant sentAt, UUID messageId) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(ENCODED_BYTES);
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeByte(VERSION);
                writeUuid(output, customerId);
                writeUuid(output, sessionId);
                output.writeLong(sentAt.getEpochSecond());
                output.writeInt(sentAt.getNano());
                writeUuid(output, messageId);
            }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot encode session history cursor", exception);
        }
    }

    public Position decode(String cursor, UUID expectedCustomerId, UUID expectedSessionId) {
        try {
            if (cursor == null || cursor.length() > 128) throw new IOException("Invalid cursor length");
            byte[] bytes = Base64.getUrlDecoder().decode(cursor);
            if (bytes.length != ENCODED_BYTES) throw new IOException("Invalid cursor size");
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
                if (input.readUnsignedByte() != VERSION) throw new IOException("Unsupported cursor version");
                UUID customerId = readUuid(input);
                UUID sessionId = readUuid(input);
                Instant sentAt = Instant.ofEpochSecond(input.readLong(), input.readInt());
                UUID messageId = readUuid(input);
                if (input.available() != 0 || !expectedCustomerId.equals(customerId)
                        || !expectedSessionId.equals(sessionId)) throw new IOException("Cursor scope mismatch");
                return new Position(customerId, sessionId, sentAt, messageId);
            }
        } catch (IllegalArgumentException | IOException | java.time.DateTimeException exception) {
            throw new StylistApiException(ErrorCode.INVALID_CURSOR);
        }
    }

    private static void writeUuid(DataOutputStream output, UUID value) throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }
}
