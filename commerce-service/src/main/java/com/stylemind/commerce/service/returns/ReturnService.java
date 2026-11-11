package com.stylemind.commerce.service.returns;

import com.stylemind.commerce.dto.returns.ReturnCommand;
import com.stylemind.commerce.dto.returns.ReturnCommand.Image;
import com.stylemind.commerce.dto.returns.ReturnResponse;
import com.stylemind.commerce.dto.returns.ReturnResponse.AdminPage;
import com.stylemind.commerce.dto.returns.ReturnResponse.OrderReturns;
import java.util.List;
import java.util.UUID;

public interface ReturnService {

    OrderReturns forOrder(UUID owner, UUID orderId);

    ReturnResponse create(UUID owner, UUID orderId, ReturnCommand command, List<Image> images);

    ReturnResponse addEvidence(UUID owner, UUID id, List<Image> images);

    Image evidence(UUID actor, boolean admin, UUID id, UUID imageId);

    AdminPage adminPage(int page, int size, long offset);

    ReturnResponse decide(UUID admin, UUID id, ReturnCommand.Decision decision);
}
