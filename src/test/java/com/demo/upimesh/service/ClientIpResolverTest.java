package com.demo.upimesh.service;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClientIpResolverTest {
 @Test void ignoresForwardedHeadersFromUntrustedPeers() {
  ClientIpResolver resolver=new ClientIpResolver("10.0.0.4");
  HttpServletRequest request=mock(HttpServletRequest.class); when(request.getRemoteAddr()).thenReturn("203.0.113.5");
  when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.9");
  assertEquals("203.0.113.5",resolver.resolve(request));
 }
 @Test void usesForwardedAddressOnlyBehindConfiguredProxy() {
  ClientIpResolver resolver=new ClientIpResolver("10.0.0.4");
  HttpServletRequest request=mock(HttpServletRequest.class); when(request.getRemoteAddr()).thenReturn("10.0.0.4");
  when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.9");
  assertEquals("198.51.100.9",resolver.resolve(request));
 }
}
