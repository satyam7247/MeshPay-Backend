package com.demo.upimesh.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.demo.upimesh.model.AppUser;
import com.demo.upimesh.model.AppUserRepository;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class AppLockServiceTest {
    private AppUserRepository users;
    private BCryptPasswordEncoder encoder;
    private AppLockService service;
    private AppUser user;

    @BeforeEach void setUp() throws Exception {
        users=mock(AppUserRepository.class);
        encoder=new BCryptPasswordEncoder(4);
        service=new AppLockService(users,encoder);
        user=new AppUser("Test User","lock@example.test","+919876543210","account-hash","lock@meshpay");
        Field id=AppUser.class.getDeclaredField("id");id.setAccessible(true);id.set(user,UUID.randomUUID());
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
    }

    @Test void enablesOnlyValidPasswordTypeAndStoresOnlyBcryptHash() {
        var status=service.enable(user,"numeric","123456");
        assertTrue(status.enabled());
        assertEquals("numeric",status.type());
        assertNotEquals("123456",user.getAppLockPasswordHash());
        assertTrue(encoder.matches("123456",user.getAppLockPasswordHash()));
        assertThrows(ResponseStatusException.class,()->service.enable(user,"numeric","12x456"));
        assertThrows(ResponseStatusException.class,()->service.enable(user,"alphabetic","ABC123"));
    }

    @Test void verificationAcceptsCorrectPasswordAndRejectsIncorrectPassword() {
        user.enableAppLock("alphabetic",encoder.encode("AbCdEf"));
        assertThrows(ResponseStatusException.class,()->service.verify(user,"AbCdEg"));
        assertEquals(1,user.getAppLockFailedAttempts());
        assertTrue(service.verify(user,"AbCdEf").enabled());
        assertEquals(0,user.getAppLockFailedAttempts());
    }

    @Test void fifthFailedUnlockPersistsFifteenMinuteLock() {
        user.enableAppLock("numeric",encoder.encode("123456"));
        for(int attempt=1;attempt<=4;attempt++) {
            ResponseStatusException error=assertThrows(ResponseStatusException.class,()->service.verify(user,"000000"));
            assertEquals(HttpStatus.BAD_REQUEST,error.getStatusCode());
        }
        ResponseStatusException limited=assertThrows(ResponseStatusException.class,()->service.verify(user,"000000"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS,limited.getStatusCode());
        assertNotNull(user.getAppLockLockedUntil());
        assertTrue(user.getAppLockLockedUntil().isAfter(java.time.Instant.now().plusSeconds(890)));
        verify(users,times(5)).findByIdForUpdate(user.getId());
    }

    @Test void disablingRequiresTheCurrentScreenLockPassword() {
        user.enableAppLock("numeric",encoder.encode("123456"));
        assertThrows(ResponseStatusException.class,()->service.disable(user,"000000"));
        assertTrue(user.isAppLockEnabled());
        assertFalse(service.disable(user,"123456").enabled());
        assertNull(user.getAppLockPasswordHash());
    }
}
