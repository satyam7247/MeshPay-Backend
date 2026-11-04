package com.demo.upimesh.model;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AccountDeletionRequestRepositoryTest {
    @Autowired private AccountDeletionRequestRepository requests;

    @Test void dueQueryReturnsOnlyPendingRequestsWhoseBackendDeadlineHasPassed() {
        Instant now=Instant.now();
        AccountDeletionRequest due=requests.save(new AccountDeletionRequest(UUID.randomUUID(),now.minusSeconds(72*60*60L+1),false,"old"));
        requests.save(new AccountDeletionRequest(UUID.randomUUID(),now,false,"future"));
        var found=requests.findDueForUpdate(AccountDeletionRequest.Status.PENDING_DELETION,now);
        assertEquals(1,found.size());
        assertEquals(due.getId(),found.get(0).getId());
    }
}

