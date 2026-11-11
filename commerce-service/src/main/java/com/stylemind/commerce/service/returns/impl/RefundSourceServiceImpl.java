package com.stylemind.commerce.service.returns.impl;

import com.stylemind.commerce.dto.returns.RefundSource;
import com.stylemind.commerce.dto.returns.RefundSource.Result;
import com.stylemind.commerce.dto.returns.RefundSource.Type;
import com.stylemind.commerce.dto.returns.ReturnResponse.Status;
import com.stylemind.commerce.errors.ApiOperationException;
import com.stylemind.commerce.repository.returns.RefundSourceRepository;
import com.stylemind.commerce.repository.returns.ReturnRepository;
import com.stylemind.commerce.service.returns.RefundSourceService;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefundSourceServiceImpl implements RefundSourceService {

    private final RefundSourceRepository repository;
    private final ReturnRepository returns;

    @Override
    @Transactional(readOnly = true)
    public RefundSource get(Type type, UUID id) {
        return repository.find(type, id, false).orElseThrow(this::notFound).source();
    }

    @Override
    @Transactional
    public void complete(Result incoming) {
        Result result = new Result(incoming.refundId(), incoming.sourceType(), incoming.sourceId(), incoming.orderId(),
                incoming.amountVnd(), incoming.refundedAt().withOffsetSameInstant(ZoneOffset.UTC)
                        .truncatedTo(ChronoUnit.MICROS));
        var stored = repository.find(result.sourceType(), result.sourceId(), true).orElseThrow(this::notFound);
        RefundSource source = stored.source();
        if (!source.orderId().equals(result.orderId()) || source.amountVnd() != result.amountVnd()) {
            throw conflict();
        }
        if (stored.refunded()) {
            if (repository.findResult(result.refundId()).filter(result::equals).isEmpty()) {
                throw conflict();
            }
            return;
        }
        if (!repository.recordResult(result)) {
            throw conflict();
        }
        repository.markComplete(result.sourceType(), result.sourceId());
        if (result.sourceType() == Type.RETURN) {
            returns.appendHistory(result.sourceId(), Status.REFUNDED, "REFUND_COMPLETED", null, null,
                    result.refundedAt());
        }
    }

    private ApiOperationException notFound() {
        return new ApiOperationException(HttpStatus.NOT_FOUND, "REFUND_SOURCE_NOT_FOUND",
                "No verified refund source was found");
    }

    private ApiOperationException conflict() {
        return new ApiOperationException(HttpStatus.CONFLICT, "REFUND_RESULT_CONFLICT",
                "Refund completion does not match its source or recorded refund");
    }
}
