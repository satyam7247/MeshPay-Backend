package com.demo.upimesh.service;

import com.demo.upimesh.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class AccountDeletionServiceTest {
    private AccountDeletionRequestRepository requests;
    private AccountDeletionOtpChallengeRepository challenges;
    private AppUserRepository users;
    private SmsProviderService sms;
    private SessionTokenService sessions;
    private BankAccountRepository bankAccounts;
    private TrustedDeviceRepository devices;
    private NotificationPushTokenRepository notificationPushTokens;
    private SavedRecipientRepository recipients;
    private BankOtpChallengeRepository bankChallenges;
    private PasswordChallengeRepository passwordChallenges;
    private AccountRepository ledger;
    private TransactionRepository transactions;
    private BrevoEmailService email;
    private Environment environment;
    private AccountDeletionService service;
    private AppUser user;

    @BeforeEach void setUp() {
        requests=mock(AccountDeletionRequestRepository.class); challenges=mock(AccountDeletionOtpChallengeRepository.class);
        users=mock(AppUserRepository.class); sms=mock(SmsProviderService.class); sessions=mock(SessionTokenService.class);
        bankAccounts=mock(BankAccountRepository.class);
        devices=mock(TrustedDeviceRepository.class); notificationPushTokens=mock(NotificationPushTokenRepository.class); recipients=mock(SavedRecipientRepository.class);
        bankChallenges=mock(BankOtpChallengeRepository.class); passwordChallenges=mock(PasswordChallengeRepository.class);
        ledger=mock(AccountRepository.class); transactions=mock(TransactionRepository.class);
        email=mock(BrevoEmailService.class); environment=mock(Environment.class);
        service=new AccountDeletionService(requests,challenges,users,sms,sessions,bankAccounts,devices,notificationPushTokens,
                recipients,bankChallenges,passwordChallenges,ledger,transactions,email,environment,"twilio",SECRET,false);
        user=new AppUser("Test User","test@example.com","+919876543210","hash","test@meshpay");
        ReflectionTestUtils.setField(user,"id",UUID.randomUUID());
        when(sms.isConfigured()).thenReturn(true);
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(requests.findFirstByUserIdAndStatusOrderByRequestCreatedAtDesc(eq(user.getId()), eq(AccountDeletionRequest.Status.PENDING_DELETION))).thenReturn(Optional.empty());
    }

    @Test void deletionDeadlineIsExactly72HoursAfterRequest() {
        Instant requested=Instant.now();
        AccountDeletionRequest request=new AccountDeletionRequest(user.getId(),requested,false,"hash");
        assertEquals(requested.plusSeconds(72*60*60L),request.getScheduledDeletionAt());
        assertEquals(AccountDeletionRequest.Status.PENDING_DELETION,request.getStatus());
    }

    @Test void registeredMobileOtpChallengeExpiresIn60SecondsAndDoesNotReturnCode() {
        when(challenges.findOpenForUpdate(user.getId())).thenReturn(List.of());
        when(challenges.save(any())).thenAnswer(call->{AccountDeletionOtpChallenge c=call.getArgument(0);ReflectionTestUtils.setField(c,"id",UUID.randomUUID());return c;});
        var response=service.sendOtp(user,null);
        assertEquals(60,response.expiresInSeconds());
        assertEquals(60,response.resendAfterSeconds());
        assertEquals("SMS",response.deliveryMode());
        assertEquals("XXXX3210",response.maskedDestination());
        verify(sms).sendVerification("+919876543210");
    }

    @Test void emailDeletionOtpUsesRegisteredAddressAndReturnsOnlyMaskedDestination(CapturedOutput output) {
        BrevoEmailService brevo=mock(BrevoEmailService.class);
        when(brevo.isConfigured()).thenReturn(true);
        AccountDeletionService emailService=serviceWithProvider("email",brevo);
        when(challenges.findOpenForUpdate(user.getId())).thenReturn(List.of());
        AtomicReference<AccountDeletionOtpChallenge> stored=new AtomicReference<>();
        when(challenges.save(any())).thenAnswer(call->{AccountDeletionOtpChallenge c=call.getArgument(0);ReflectionTestUtils.setField(c,"id",UUID.randomUUID());stored.set(c);return c;});
        var response=emailService.sendOtp(user,null);
        assertEquals("EMAIL",response.deliveryMode());
        assertEquals("t***@example.com",response.maskedDestination());
        assertFalse(response.toString().matches(".*\\b[0-9]{6}\\b.*"));
        org.mockito.ArgumentCaptor<String> sentCode=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(brevo).sendAccountDeletionOtp(eq("test@example.com"),sentCode.capture());
        assertTrue(sentCode.getValue().matches("[0-9]{6}"));
        assertEquals(64,stored.get().getOtpHash().length());
        assertNotEquals(sentCode.getValue(),stored.get().getOtpHash());
        assertFalse(output.getAll().contains(sentCode.getValue()));
        verifyNoInteractions(sms);
    }

    @Test void deletionEmailChallengeUsesPurposeBoundHashAndRejectsBankLinkHash() {
        String code="123456";
        String bankLinkHash=BankOtpService.hmac(code,SECRET);
        assertFalse(AccountDeletionService.verifyEmailOtp(bankLinkHash,code,SECRET));
        String deletionHash=AccountDeletionService.hmac(code,SECRET);
        assertTrue(AccountDeletionService.verifyEmailOtp(deletionHash,code,SECRET));
        assertFalse(BankOtpService.verifyEmailCode(deletionHash,code,SECRET));
    }

    @Test void wrongDeletionCodeIsRejectedAndAttemptRecordedForEmailProvider() {
        BrevoEmailService brevo=mock(BrevoEmailService.class); when(brevo.isConfigured()).thenReturn(true);
        AccountDeletionService emailService=serviceWithProvider("email",brevo);
        UUID id=UUID.randomUUID(); AccountDeletionOtpChallenge challenge=new AccountDeletionOtpChallenge(user.getId(),Instant.now());
        ReflectionTestUtils.setField(challenge,"id",id); challenge.setOtpHash(AccountDeletionService.hmac("123456",SECRET));
        when(challenges.findOwnedForUpdate(id,user.getId())).thenReturn(Optional.of(challenge));
        ResponseStatusException error=assertThrows(ResponseStatusException.class,()->emailService.verifyAndRequest(user,id,"654321"));
        assertEquals(HttpStatus.BAD_REQUEST,error.getStatusCode()); assertEquals(1,challenge.getAttempts());
        verify(transactions,never()).existsBySenderVpaAndStatusNotIn(anyString(),anyList());
    }

    @Test void deletionEmailCodeExpiresAtSixtySeconds() {
        Instant created=Instant.now().minusSeconds(60);
        AccountDeletionOtpChallenge challenge=new AccountDeletionOtpChallenge(user.getId(),created);
        UUID id=UUID.randomUUID(); ReflectionTestUtils.setField(challenge,"id",id);
        challenge.setOtpHash(AccountDeletionService.hmac("123456",SECRET));
        when(challenges.findOwnedForUpdate(id,user.getId())).thenReturn(Optional.of(challenge));
        ResponseStatusException error=assertThrows(ResponseStatusException.class,()->service.verifyAndRequest(user,id,"123456"));
        assertEquals(HttpStatus.GONE,error.getStatusCode()); assertTrue(challenge.isConsumed());
        assertEquals(created.plusSeconds(60),challenge.getExpiresAt());
    }

    @Test void correctEmailOtpCreatesTheExisting72HourDeletionRequest() {
        BrevoEmailService brevo=mock(BrevoEmailService.class); when(brevo.isConfigured()).thenReturn(true);
        AccountDeletionService emailService=serviceWithProvider("email",brevo);
        UUID id=UUID.randomUUID(); AccountDeletionOtpChallenge challenge=new AccountDeletionOtpChallenge(user.getId(),Instant.now());
        ReflectionTestUtils.setField(challenge,"id",id); challenge.setOtpHash(AccountDeletionService.hmac("123456",SECRET));
        when(challenges.findOwnedForUpdate(id,user.getId())).thenReturn(Optional.of(challenge));
        when(requests.save(any())).thenAnswer(call->call.getArgument(0));
        when(transactions.existsBySenderVpaAndStatusNotIn(anyString(),anyList())).thenReturn(false);
        when(transactions.existsByReceiverVpaAndStatusNotIn(anyString(),anyList())).thenReturn(false);
        var response=emailService.verifyAndRequest(user,id,"123456");
        assertEquals(72*60*60L,response.scheduledDeletionAt().getEpochSecond()-response.requestCreatedAt().getEpochSecond());
        assertTrue(challenge.isConsumed()); assertNull(challenge.getOtpHash()); assertTrue(user.isAccountLocked());
        verify(sms,never()).verifyCode(anyString(),anyString());
    }

    @Test void fifthWrongEmailCodeConsumesDeletionChallenge() {
        BrevoEmailService brevo=mock(BrevoEmailService.class); when(brevo.isConfigured()).thenReturn(true);
        AccountDeletionService emailService=serviceWithProvider("email",brevo);
        UUID id=UUID.randomUUID(); AccountDeletionOtpChallenge challenge=new AccountDeletionOtpChallenge(user.getId(),Instant.now());
        ReflectionTestUtils.setField(challenge,"id",id); challenge.setOtpHash(AccountDeletionService.hmac("123456",SECRET));
        when(challenges.findOwnedForUpdate(id,user.getId())).thenReturn(Optional.of(challenge));
        for(int i=0;i<4;i++)assertThrows(ResponseStatusException.class,()->emailService.verifyAndRequest(user,id,"654321"));
        ResponseStatusException error=assertThrows(ResponseStatusException.class,()->emailService.verifyAndRequest(user,id,"654321"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS,error.getStatusCode()); assertEquals(5,challenge.getAttempts()); assertTrue(challenge.isConsumed());
    }

    @Test void resendingDeletionOtpInvalidatesThePreviousCodeHash() {
        BrevoEmailService brevo=mock(BrevoEmailService.class); when(brevo.isConfigured()).thenReturn(true);
        AccountDeletionService emailService=serviceWithProvider("email",brevo);
        List<AccountDeletionOtpChallenge> saved=new ArrayList<>();
        List<String> sentCodes=new ArrayList<>();
        doAnswer(call->{sentCodes.add(call.getArgument(1));return null;}).when(brevo).sendAccountDeletionOtp(anyString(),anyString());
        when(challenges.findOpenForUpdate(user.getId())).thenAnswer(call->saved.isEmpty()?List.of():List.of(saved.get(saved.size()-1)));
        when(challenges.save(any())).thenAnswer(call->{AccountDeletionOtpChallenge c=call.getArgument(0);if(ReflectionTestUtils.getField(c,"id")==null)ReflectionTestUtils.setField(c,"id",UUID.randomUUID());if(!saved.contains(c))saved.add(c);return c;});
        var first=emailService.sendOtp(user,null);
        AccountDeletionOtpChallenge old=saved.get(0); String oldHash=old.getOtpHash();
        ReflectionTestUtils.setField(old,"expiresAt",Instant.now().minusSeconds(1));
        ReflectionTestUtils.setField(old,"resendAfter",Instant.now().minusSeconds(1));
        var second=emailService.sendOtp(user,first.challengeId());
        assertNotEquals(first.challengeId(),second.challengeId()); assertTrue(old.isConsumed()); assertNull(old.getOtpHash());
        assertNotEquals(oldHash,saved.get(1).getOtpHash());
        assertFalse(AccountDeletionService.verifyEmailOtp(saved.get(1).getOtpHash(),sentCodes.get(0),SECRET));
        assertTrue(AccountDeletionService.verifyEmailOtp(saved.get(1).getOtpHash(),sentCodes.get(1),SECRET));
    }

    private AccountDeletionService serviceWithProvider(String provider,BrevoEmailService brevo){
        return new AccountDeletionService(requests,challenges,users,sms,sessions,bankAccounts,devices,notificationPushTokens,recipients,
                bankChallenges,passwordChallenges,ledger,transactions,brevo,environment,provider,SECRET,false);
    }

    @Test void accountDeletionEmailOtpFailsClosedOnProductionProfile(){
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        AccountDeletionService emailService=serviceWithProvider("email",mock(BrevoEmailService.class));
        assertThrows(IllegalStateException.class,emailService::validateOtpProviderSafety);
    }

    private static final String SECRET="test-deletion-hmac-secret-with-more-than-32-bytes";

    @Test void emailOtpGenerationAlwaysProducesSixDigits(){
        for(int i=0;i<1000;i++)assertTrue(AccountDeletionService.generateOtp().matches("[0-9]{6}"));
    }

    @Test void incorrectOtpIsRejectedAndAttemptIsRecorded() {
        UUID challengeId=UUID.randomUUID();
        AccountDeletionOtpChallenge challenge=new AccountDeletionOtpChallenge(user.getId(),Instant.now());
        ReflectionTestUtils.setField(challenge,"id",challengeId);
        when(challenges.findOwnedForUpdate(challengeId,user.getId())).thenReturn(Optional.of(challenge));
        when(sms.verifyCode(anyString(),anyString())).thenReturn(false);
        ResponseStatusException error=assertThrows(ResponseStatusException.class,
                ()->service.verifyAndRequest(user,challengeId,"123456"));
        assertEquals(HttpStatus.BAD_REQUEST,error.getStatusCode());
        assertEquals(1,challenge.getAttempts());
        verify(requests,never()).save(any());
    }

    @Test void verifiedOtpCreatesPendingRequestAndLocksPaymentAccount() {
        UUID challengeId=UUID.randomUUID();
        AccountDeletionOtpChallenge challenge=new AccountDeletionOtpChallenge(user.getId(),Instant.now());
        ReflectionTestUtils.setField(challenge,"id",challengeId);
        when(challenges.findOwnedForUpdate(challengeId,user.getId())).thenReturn(Optional.of(challenge));
        when(sms.verifyCode(anyString(),anyString())).thenReturn(true);
        when(requests.findFirstByUserIdAndStatusOrderByRequestCreatedAtDesc(user.getId(),AccountDeletionRequest.Status.PENDING_DELETION)).thenReturn(Optional.empty());
        when(requests.save(any())).thenAnswer(call->call.getArgument(0));
        when(transactions.existsBySenderVpaAndStatusNotIn(anyString(),anyList())).thenReturn(false);
        when(transactions.existsByReceiverVpaAndStatusNotIn(anyString(),anyList())).thenReturn(false);
        var response=service.verifyAndRequest(user,challengeId,"123456");
        assertEquals("PENDING_DELETION",response.status());
        assertEquals(72*60*60L,response.scheduledDeletionAt().getEpochSecond()-response.requestCreatedAt().getEpochSecond());
        assertTrue(user.isAccountLocked());
        verify(sessions).revokeAllForUser(user.getId().toString());
    }

    @Test void loginDuringGracePeriodCancelsDeletionAndRestoresAccountLockState() {
        Instant now=Instant.now();
        AccountDeletionRequest request=new AccountDeletionRequest(user.getId(),now,false,"email-key");
        user.lockAccount();
        when(requests.findByUserIdAndStatusForUpdate(user.getId(),AccountDeletionRequest.Status.PENDING_DELETION)).thenReturn(Optional.of(request));
        assertTrue(service.cancelForLogin(user.getId()));
        assertEquals(AccountDeletionRequest.Status.CANCELLED,request.getStatus());
        assertFalse(user.isAccountLocked());
    }

    @Test void scheduledDeletionAnonymizesAccountAndLeavesTransactionRepositoryUntouched() {
        AccountDeletionRequest request=new AccountDeletionRequest(user.getId(),Instant.now().minusSeconds(72*60*60L+2),false,"email-key");
        ReflectionTestUtils.setField(request,"id",UUID.randomUUID());
        when(requests.findAllByStatusAndScheduledDeletionAtLessThanEqual(eq(AccountDeletionRequest.Status.PENDING_DELETION),any(Instant.class))).thenReturn(List.of(request));
        when(requests.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        when(transactions.existsBySenderVpaAndStatusNotIn(anyString(),anyList())).thenReturn(false);
        when(transactions.existsByReceiverVpaAndStatusNotIn(anyString(),anyList())).thenReturn(false);
        when(ledger.findById(user.getUpiId())).thenReturn(Optional.empty());
        String priorEmail=user.getEmail();
        service.processDueDeletions();
        assertEquals(AccountDeletionRequest.Status.DELETED,request.getStatus());
        assertEquals("Deleted account",user.getName());
        assertNotEquals(priorEmail,user.getEmail());
        assertTrue(user.isAccountLocked());
        verify(sessions).revokeAllForUser(user.getId().toString());
        verify(notificationPushTokens).deleteAllByOwnerUserId(user.getId());
        verify(transactions,never()).delete(any());
    }
    @Test void remainingLedgerBalancePreventsDeletion() {
        UUID challengeId=UUID.randomUUID();
        AccountDeletionOtpChallenge challenge=new AccountDeletionOtpChallenge(user.getId(),Instant.now());
        ReflectionTestUtils.setField(challenge,"id",challengeId);
        when(challenges.findOwnedForUpdate(challengeId,user.getId())).thenReturn(Optional.of(challenge));
        when(sms.verifyCode(anyString(),anyString())).thenReturn(true);
        when(ledger.findById(user.getUpiId())).thenReturn(Optional.of(
                new Account(user.getUpiId(), user.getName(), new java.math.BigDecimal("0.01"))));
        ResponseStatusException error=assertThrows(ResponseStatusException.class,
                ()->service.verifyAndRequest(user,challengeId,"123456"));
        assertEquals(HttpStatus.CONFLICT,error.getStatusCode());
        verify(requests,never()).save(any());
    }
}





