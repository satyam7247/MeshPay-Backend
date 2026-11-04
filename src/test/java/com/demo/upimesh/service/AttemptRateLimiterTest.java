package com.demo.upimesh.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class AttemptRateLimiterTest {
 @Test void blocksTheSixthAttemptInTheWindowAndCanClearAfterSuccess() {
  AttemptRateLimiter limiter=new AttemptRateLimiter();
  for(int i=0;i<5;i++) limiter.check("login:ip:127.0.0.1",5,900);
  ResponseStatusException limited=assertThrows(ResponseStatusException.class,()->limiter.check("login:ip:127.0.0.1",5,900));
  assertEquals(429,limited.getStatusCode().value());
  assertEquals("900",limited.getHeaders().getFirst("Retry-After"));
  limiter.clear("login:ip:127.0.0.1");
  assertDoesNotThrow(()->limiter.check("login:ip:127.0.0.1",5,900));
 }
}
