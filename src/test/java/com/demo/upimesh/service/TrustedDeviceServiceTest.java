package com.demo.upimesh.service;

import com.demo.upimesh.model.AppSessionRepository;
import com.demo.upimesh.model.AppUserRepository;
import com.demo.upimesh.model.AppUser;
import com.demo.upimesh.model.TrustedDevice;
import com.demo.upimesh.model.TrustedDeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TrustedDeviceServiceTest {
    private TrustedDeviceRepository devices;
    private AppSessionRepository sessions;
    private AppUserRepository users;
    private TrustedDeviceService service;
    private AppUser user;

    @BeforeEach
    void setUp() {
        devices=mock(TrustedDeviceRepository.class);
        sessions=mock(AppSessionRepository.class);
        users=mock(AppUserRepository.class);
        service=new TrustedDeviceService(devices,sessions,users);
        user=new AppUser("Test User","test@example.com","+919876543210","hash","test@meshpay");
        ReflectionTestUtils.setField(user,"id",UUID.randomUUID());
    }

    @Test
    void passwordVerifiedRegistrationCreatesAStableInstallIdentity() {
        String id=UUID.randomUUID().toString();
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(devices.findByOwnerUserIdAndDeviceIdForUpdate(user.getId(),id)).thenReturn(Optional.empty());
        when(devices.saveAndFlush(any(TrustedDevice.class))).thenAnswer(invocation -> invocation.getArgument(0));
        assertEquals(id,service.registerPasswordVerifiedDevice(user,id,"Chrome on Windows","web"));
        verify(devices).saveAndFlush(argThat(device->device.getDeviceId().equals(id)
                && device.getVerificationMethod().equals("PASSWORD_LOGIN")));
    }

    @Test
    void revocationInvalidatesEverySessionBoundToTheDevice() {
        String id=UUID.randomUUID().toString();
        TrustedDevice device=new TrustedDevice(user.getId(),id,"Android","android");
        when(devices.findByOwnerUserIdAndDeviceIdForUpdate(user.getId(),id)).thenReturn(Optional.of(device));
        service.revoke(user,id);
        verify(sessions).deleteAllByDeviceIdAndUserId(id,user.getId().toString());
        assertNotNull(device.getRevokedAt());
        when(devices.findByOwnerUserIdAndDeviceIdAndRevokedAtIsNull(user.getId(),id)).thenReturn(Optional.empty());
        assertFalse(service.isActive(user.getId().toString(),id));
    }
}
