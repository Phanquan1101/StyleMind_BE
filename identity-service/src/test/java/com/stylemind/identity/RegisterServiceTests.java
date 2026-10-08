package com.stylemind.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.stylemind.identity.dto.RegisterRequest;
import com.stylemind.identity.dto.RegisterResponse;
import com.stylemind.identity.service.EmailDispatch;
import com.stylemind.identity.service.EmailVerificationPersistence;
import com.stylemind.identity.service.NotificationEmailClient;
import com.stylemind.identity.service.RegisterService;
import com.stylemind.identity.service.RegistrationCommit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class RegisterServiceTests {

    @Test
    void sendsOnlyAfterTheTransactionalPersistenceCallReturns() {
        EmailVerificationPersistence persistence = mock(EmailVerificationPersistence.class);
        NotificationEmailClient sender = mock(NotificationEmailClient.class);
        UUID accountId = UUID.randomUUID();
        RegisterRequest request = new RegisterRequest("customer@example.com", "raw-password-is-not-logged");
        RegistrationCommit committed = new RegistrationCommit(
                new RegisterResponse(accountId, "customer@example.com", "PENDING_VERIFICATION"),
                new EmailDispatch("customer@example.com", "012345"));
        when(persistence.createRegistration(request)).thenReturn(committed);

        RegisterResponse response = new RegisterService(persistence, sender).register(request);

        InOrder order = inOrder(persistence, sender);
        order.verify(persistence).createRegistration(request);
        order.verify(sender).send(committed.dispatch());
        assertThat(response).isEqualTo(committed.response());
        assertThat(request.toString()).doesNotContain("raw-password-is-not-logged");
        assertThat(committed.dispatch().toString()).doesNotContain("012345");
    }
}
