package com.demo.upimesh.service;

import com.demo.upimesh.model.AppSession;
import com.demo.upimesh.model.AppSessionRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AppLockIdleTimeoutTest {
    @Test void verifiedSessionExpiresAfterIdleWindowAndActivityRenewsIt() throws Exception {
        AppSessionRepository repository=mock(AppSessionRepository.class);
        SessionTokenService service=new SessionTokenService(repository,mock(TrustedDeviceService.class));
        Field timeout=SessionTokenService.class.getDeclaredField("appLockIdleTimeoutSeconds");
        timeout.setAccessible(true);timeout.setLong(service,300);
        AppSession session=new AppSession("hash","user",Instant.now().plusSeconds(3600));
        session.setAppLockVerified(true);
        when(repository.findById(anyString())).thenReturn(Optional.of(session));

        assertTrue(service.isAppLockVerifiedAndTouch("token", "/api/transactions", "GET"));
        assertTrue(session.getAppLockVerifiedAt().isAfter(Instant.now().minusSeconds(1)));
        Field verifiedAt=AppSession.class.getDeclaredField("appLockVerifiedAt");
        verifiedAt.setAccessible(true);verifiedAt.set(session,Instant.now().minusSeconds(301));

        assertFalse(service.isAppLockVerifiedAndTouch("token", "/api/transactions", "GET"));
        assertFalse(session.isAppLockVerified());
    }

    @Test void pollingDoesNotExtendIdleWindowAndActivityWritesAreThrottled() throws Exception {
        AppSessionRepository repository=mock(AppSessionRepository.class);
        SessionTokenService service=new SessionTokenService(repository,mock(TrustedDeviceService.class));
        Field timeout=SessionTokenService.class.getDeclaredField("appLockIdleTimeoutSeconds");
        timeout.setAccessible(true);timeout.setLong(service,300);
        AppSession session=new AppSession("hash","user",Instant.now().plusSeconds(3600));
        session.setAppLockVerified(true);
        Instant verifiedAt=session.getAppLockVerifiedAt();
        when(repository.findById(anyString())).thenReturn(Optional.of(session));

        assertTrue(service.isAppLockVerifiedAndTouch("token", "/api/privacy-account", "GET"));
        assertEquals(verifiedAt, session.getAppLockVerifiedAt());
        assertTrue(service.isAppLockVerifiedAndTouch("token", "/api/notifications/unread-count", "GET"));
        assertEquals(verifiedAt, session.getAppLockVerifiedAt());
        assertTrue(service.isAppLockVerifiedAndTouch("token", "/api/transactions", "GET"));
        assertEquals(verifiedAt, session.getAppLockVerifiedAt(), "activity inside 30 seconds must not write a new touch");
    }

    @Test void onlyTheActualGetPollingRoutesAreExemptFromActivityTouches() throws Exception {
        AppSessionRepository repository=mock(AppSessionRepository.class);
        SessionTokenService service=new SessionTokenService(repository,mock(TrustedDeviceService.class));
        Field timeout=SessionTokenService.class.getDeclaredField("appLockIdleTimeoutSeconds");
        timeout.setAccessible(true);timeout.setLong(service,300);
        AppSession session=new AppSession("hash","user",Instant.now().plusSeconds(3600));
        session.setAppLockVerified(true);
        Field verifiedAt=AppSession.class.getDeclaredField("appLockVerifiedAt");
        verifiedAt.setAccessible(true);
        Instant old=Instant.now().minusSeconds(31);
        verifiedAt.set(session,old);
        when(repository.findById(anyString())).thenReturn(Optional.of(session));

        assertTrue(service.isAppLockVerifiedAndTouch("token", "/api/privacy-account", "POST"));
        assertTrue(session.getAppLockVerifiedAt().isAfter(old), "non-GET requests must count as activity");
    }
}
