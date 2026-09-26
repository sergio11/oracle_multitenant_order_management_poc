package com.enterpriseorders.backend.common.domain;

import com.enterpriseorders.backend.order.dto.PaymentRequest;
import jakarta.validation.constraints.Pattern;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Locks {@link PaymentMethod} to the {@code @Pattern} regex on
 * {@code PaymentRequest.paymentMethod} so the two copies of the payment
 * method literals cannot drift apart.
 *
 * <p>Approach chosen: reflection on the record component (with accessor and
 * field fallbacks) to read the regex declared in the annotation, split it on
 * {@code '|'} and compare it as a set against {@code PaymentMethod.values()}
 * names. This asserts exact set equality in both directions, so a literal
 * added/removed on either side fails the test. Reflection was preferred over
 * hardcoding {@code {"CARD","BANK_TRANSFER","WALLET","CASH"}} because it
 * also fails if the annotation moves, is renamed, or disappears.</p>
 */
class PaymentMethodConsistencyTest {

    @Test
    void paymentMethodPatternRegexMatchesEnumExactly() {
        Set<String> enumNames = Arrays.stream(PaymentMethod.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        String regexp = paymentMethodRegexp();
        Set<String> regexLiterals = Arrays.stream(regexp.split("\\|"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        assertEquals(enumNames, regexLiterals);
    }

    private String paymentMethodRegexp() {
        RecordComponent component = Arrays.stream(PaymentRequest.class.getRecordComponents())
                .filter(c -> c.getName().equals("paymentMethod"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("paymentMethod component not found"));

        Pattern pattern = component.getAnnotation(Pattern.class);
        if (pattern == null) {
            pattern = component.getAccessor().getAnnotation(Pattern.class);
        }
        if (pattern == null) {
            try {
                pattern = PaymentRequest.class.getDeclaredField("paymentMethod")
                        .getAnnotation(Pattern.class);
            } catch (NoSuchFieldException e) {
                throw new AssertionError("paymentMethod field not found", e);
            }
        }
        assertNotNull(pattern, "@Pattern annotation not found on paymentMethod");
        return pattern.regexp();
    }
}
