package com.demo.upimesh.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PasswordPolicyTest {
 @Test void appliesTheSameComplexityAndBcryptByteLimit() {
  assertTrue(PasswordPolicy.isValid("Meshpay9!"));
  assertFalse(PasswordPolicy.isValid("lowercase9!"));
  assertFalse(PasswordPolicy.isValid("Meshpay9!"+"\u00e9".repeat(40)));
 }
}
