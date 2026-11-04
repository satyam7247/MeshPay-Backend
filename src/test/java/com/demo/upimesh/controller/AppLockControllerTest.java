package com.demo.upimesh.controller;

import com.demo.upimesh.model.AppUser;
import com.demo.upimesh.service.AppLockService;
import com.demo.upimesh.service.BrevoEmailService;
import com.demo.upimesh.service.ClientIpResolver;
import com.demo.upimesh.service.PasswordService;
import com.demo.upimesh.service.SessionTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.lang.reflect.Field;
import java.util.UUID;

class AppLockControllerTest {
    @Test void enableRequiresAccountPasswordThenEnablesAndNotifies() throws Exception {
        AppLockService locks=mock(AppLockService.class);
        PasswordService passwords=mock(PasswordService.class);
        SessionTokenService sessions=mock(SessionTokenService.class);
        BrevoEmailService email=mock(BrevoEmailService.class);
        AppLockController controller=new AppLockController(locks,passwords,new ClientIpResolver(""),sessions,email);
        AppUser user=new AppUser("Test","lock@example.test","+919876543210","bcrypt-hash","lock@meshpay");
        assignId(user);
        var authentication=new UsernamePasswordAuthenticationToken(user,null);
        HttpServletRequest request=mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("192.0.2.10");
        when(locks.enable(user,"numeric","123456")).thenReturn(new AppLockService.Status(true,"numeric"));

        var result=controller.enable(new AppLockController.EnableRequest("account-pass","numeric","123456"),
                authentication, "Bearer session-token", request);

        assertTrue(result.enabled());
        verify(passwords).verifyAccountPasswordForAppLock(user,"account-pass","192.0.2.10");
        verify(email).sendAppLockChanged(user.getEmail(),false);
        verify(sessions).setAppLockVerified("session-token",user.getId().toString(),false);
    }

    @Test void invalidAccountPasswordStopsBeforeChangingAppLock() throws Exception {
        AppLockService locks=mock(AppLockService.class);
        PasswordService passwords=mock(PasswordService.class);
        SessionTokenService sessions=mock(SessionTokenService.class);
        BrevoEmailService email=mock(BrevoEmailService.class);
        AppLockController controller=new AppLockController(locks,passwords,new ClientIpResolver(""),sessions,email);
        AppUser user=new AppUser("Test","lock@example.test","+919876543210","bcrypt-hash","lock@meshpay");
        assignId(user);
        var authentication=new UsernamePasswordAuthenticationToken(user,null);
        HttpServletRequest request=mock(HttpServletRequest.class);
        doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED))
                .when(passwords).verifyAccountPasswordForAppLock(any(),anyString(),anyString());

        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->controller.enable(
                new AppLockController.EnableRequest("wrong","numeric","123456"),authentication,"Bearer token",request));
        verifyNoInteractions(locks,sessions,email);
    }

    private static void assignId(AppUser user) throws Exception {
        Field id=AppUser.class.getDeclaredField("id");id.setAccessible(true);id.set(user,UUID.randomUUID());
    }
}
