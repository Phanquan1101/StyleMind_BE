package com.stylemind.commerce.service.returns;

import com.stylemind.commerce.dto.returns.RefundSource;
import java.util.UUID;

public interface RefundSourceService {

    RefundSource get(RefundSource.Type type, UUID id);

    void complete(RefundSource.Result result);
}
