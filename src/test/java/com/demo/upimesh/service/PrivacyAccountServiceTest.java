package com.demo.upimesh.service;

import com.demo.upimesh.model.AppUser;
import com.demo.upimesh.model.AppUserRepository;
import com.demo.upimesh.model.Transaction;
import com.demo.upimesh.model.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PrivacyAccountServiceTest {
    private AppUserRepository users;
    private TransactionRepository transactions;
    private PasswordEncoder passwords;
    private com.demo.upimesh.model.AccountDeletionRequestRepository deletionRequests;
    private AttemptRateLimiter attempts;
    private PrivacyAccountService service;
    private AppUser user;

    @BeforeEach
    void setUp() {
        users = mock(AppUserRepository.class);
        transactions = mock(TransactionRepository.class);
        passwords = mock(PasswordEncoder.class);
        deletionRequests = mock(com.demo.upimesh.model.AccountDeletionRequestRepository.class);
        OfflinePaymentPolicy policy = new OfflinePaymentPolicy(users, transactions, deletionRequests);
        attempts = new AttemptRateLimiter();
        service = new PrivacyAccountService(users, transactions, passwords, policy, deletionRequests, attempts);
        user = new AppUser("Test User", "test@example.com", "+919876543210", "hashed-password", "test@meshpay");
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(deletionRequests.findFirstByUserIdAndStatusOrderByRequestCreatedAtDesc(eq(user.getId()), any())).thenReturn(Optional.empty());
        when(deletionRequests.findByUserIdAndStatusForUpdate(eq(user.getId()), any())).thenReturn(Optional.empty());
        when(transactions.sumSettledAmountSince(eq(user.getUpiId()), eq(Transaction.Status.SETTLED), any(Instant.class)))
                .thenReturn(BigDecimal.ZERO);
    }

    @Test
    void persistsConfiguredLimitAndPrivacyFlagsOnTheAccountEntity() {
        when(passwords.matches("correct-password", "hashed-password")).thenReturn(true);
        var response = service.update(user, new PrivacyAccountService.UpdateRequest(
                new BigDecimal("750.50"), true, true, "correct-password"));
        assertEquals(new BigDecimal("750.50"), response.offlinePaymentLimit());
        assertTrue(response.hideBalance());
        assertTrue(response.hideTransactionDetails());
        verify(users).findById(user.getId());
    }

    @Test
    void emergencyLockBlocksPaymentAndUnlockRequiresPasswordVerification() {
        assertTrue(service.lock(user, true).accountLocked());
        ResponseStatusException locked = assertThrows(ResponseStatusException.class,
                () -> service.requireUnlocked(user.getId()));
        assertEquals(423, locked.getStatusCode().value());

        when(passwords.matches("correct-password", "hashed-password")).thenReturn(true);
        assertFalse(service.unlock(user, "correct-password").accountLocked());
        service.requireUnlocked(user.getId());
    }

    @Test
    void hiddenTransactionDetailsAreRedactedBeforeLeavingTheServer() {
        user.updatePrivacySettings(BigDecimal.ZERO, false, true);
        Transaction tx = new Transaction();
        tx.setId(5L);tx.setPacketHash("packet-hash");tx.setSenderVpa(user.getUpiId());
        tx.setReceiverVpa("receiver@meshpay");tx.setAmount(new BigDecimal("45.00"));
        tx.setSignedAt(Instant.now());tx.setSettledAt(Instant.now());tx.setBridgeNodeId("bridge");
        tx.setStatus(Transaction.Status.SETTLED);
        when(transactions.findTop20ByStatusAndSenderVpaOrStatusAndReceiverVpaOrderByIdDesc(
                Transaction.Status.SETTLED, user.getUpiId(), Transaction.Status.SETTLED, user.getUpiId()))
                .thenReturn(List.of(tx));

        var result = service.transactions(user).get(0);
        assertTrue(result.detailsHidden());
        assertNull(result.senderVpa());
        assertNull(result.receiverVpa());
        assertNull(result.amount());
        assertNull(result.bridgeNodeId());
    }

    @Test void raisingOfflineLimitRequiresPasswordReauthentication() {
        assertThrows(ResponseStatusException.class, () -> service.update(user,
                new PrivacyAccountService.UpdateRequest(new BigDecimal("100.00"), false, false)));
        when(passwords.matches("correct-password", "hashed-password")).thenReturn(true);
        assertDoesNotThrow(() -> service.update(user,
                new PrivacyAccountService.UpdateRequest(new BigDecimal("100.00"), false, false, "correct-password")));
    }

    @Test void limitIncreasePasswordChecksAreLimitedByUserAndClientIp() {
        for(int i=0;i<5;i++) {
            String clientIp="user-ip-"+i;
            ResponseStatusException wrong=assertThrows(ResponseStatusException.class,()->service.update(user,
                    new PrivacyAccountService.UpdateRequest(new BigDecimal("100.00"),false,false,"wrong"),clientIp));
            assertEquals(401,wrong.getStatusCode().value());
        }
        ResponseStatusException userLimited=assertThrows(ResponseStatusException.class,()->service.update(user,
                new PrivacyAccountService.UpdateRequest(new BigDecimal("100.00"),false,false,"wrong"),"another-ip"));
        assertEquals(429,userLimited.getStatusCode().value());
        assertEquals("900",userLimited.getHeaders().getFirst("Retry-After"));

        for(int i=0;i<5;i++) {
            AppUser distinctUser=new AppUser("Test User","test"+i+"@example.com","+91987654321"+i,
                    "hashed-password","test"+i+"@meshpay");
            UUID distinctId=UUID.randomUUID();
            ReflectionTestUtils.setField(distinctUser,"id",distinctId);
            when(users.findById(distinctId)).thenReturn(Optional.of(distinctUser));
            ResponseStatusException wrong=assertThrows(ResponseStatusException.class,()->service.update(distinctUser,
                    new PrivacyAccountService.UpdateRequest(new BigDecimal("100.00"),false,false,"wrong"),"shared-ip"));
            assertEquals(401,wrong.getStatusCode().value());
        }
        AppUser lastUser=new AppUser("Last User","last@example.com","+919876543219","hashed-password","last@meshpay");
        UUID lastId=UUID.randomUUID();
        ReflectionTestUtils.setField(lastUser,"id",lastId);
        when(users.findById(lastId)).thenReturn(Optional.of(lastUser));
        ResponseStatusException ipLimited=assertThrows(ResponseStatusException.class,()->service.update(lastUser,
                new PrivacyAccountService.UpdateRequest(new BigDecimal("100.00"),false,false,"wrong"),"shared-ip"));
        assertEquals(429,ipLimited.getStatusCode().value());
    }

    @Test void unlockPasswordChecksAreLimitedByUserAndClientIp() {
        for(int i=0;i<5;i++) {
            String clientIp="unlock-ip-"+i;
            ResponseStatusException wrong=assertThrows(ResponseStatusException.class,()->service.unlock(user,"wrong",clientIp));
            assertEquals(401,wrong.getStatusCode().value());
        }
        ResponseStatusException userLimited=assertThrows(ResponseStatusException.class,()->service.unlock(user,"wrong","another-unlock-ip"));
        assertEquals(429,userLimited.getStatusCode().value());

        for(int i=0;i<5;i++) {
            AppUser distinctUser=new AppUser("Unlock User","unlock"+i+"@example.com","+91876543210"+i,
                    "hashed-password","unlock"+i+"@meshpay");
            UUID distinctId=UUID.randomUUID();
            ReflectionTestUtils.setField(distinctUser,"id",distinctId);
            when(users.findByIdForUpdate(distinctId)).thenReturn(Optional.of(distinctUser));
            ResponseStatusException wrong=assertThrows(ResponseStatusException.class,()->service.unlock(distinctUser,"wrong","shared-unlock-ip"));
            assertEquals(401,wrong.getStatusCode().value());
        }
        AppUser lastUser=new AppUser("Last Unlock","last-unlock@example.com","+918765432109","hashed-password","last-unlock@meshpay");
        UUID lastId=UUID.randomUUID();
        ReflectionTestUtils.setField(lastUser,"id",lastId);
        when(users.findByIdForUpdate(lastId)).thenReturn(Optional.of(lastUser));
        ResponseStatusException ipLimited=assertThrows(ResponseStatusException.class,()->service.unlock(lastUser,"wrong","shared-unlock-ip"));
        assertEquals(429,ipLimited.getStatusCode().value());
    }
}




