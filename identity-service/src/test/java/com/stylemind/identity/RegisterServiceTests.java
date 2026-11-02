package com.stylemind.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.stylemind.identity.client.notification.NotificationEmailClient;
import com.stylemind.identity.client.notification.payload.EmailDispatch;
import com.stylemind.identity.dto.register.RegisterRequest;
import com.stylemind.identity.dto.register.RegisterResponse;
import com.stylemind.identity.model.register.RegistrationCommit;
import com.stylemind.identity.service.emailverification.internal.EmailVerificationPersistence;
import com.stylemind.identity.service.register.impl.RegisterServiceImpl;
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

        RegisterResponse response = new RegisterServiceImpl(persistence, sender).register(request);

        InOrder order = inOrder(persistence, sender);
        order.verify(persistence).createRegistration(request);
        order.verify(sender).send(committed.dispatch());
        assertThat(response).isEqualTo(committed.response());
        assertThat(request.toString()).doesNotContain("raw-password-is-not-logged");
        assertThat(committed.dispatch().toString()).doesNotContain("012345");
    }
}
