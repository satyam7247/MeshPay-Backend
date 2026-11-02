package com.demo.upimesh.service;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
class MobileNumberNormalizerTest {
 @Test void acceptsTenDigitNumber(){assertEquals("9876543210",MobileNumberNormalizer.normalize("9876543210"));}
 @Test void removesIndiaPrefixAndSeparators(){assertEquals("9876543210",MobileNumberNormalizer.normalize("+91 98765-43210"));}
 @Test void removesLocalTrunkPrefix(){assertEquals("9876543210",MobileNumberNormalizer.normalize("09876543210"));}
 @Test void rejectsTooShortNumber(){assertThrows(ResponseStatusException.class,()->MobileNumberNormalizer.normalize("12345"));}
 @Test void rejectsNullNumber(){assertThrows(ResponseStatusException.class,()->MobileNumberNormalizer.normalize(null));}
}