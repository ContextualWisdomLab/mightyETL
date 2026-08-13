package com.xtrmetl.etl.documentation;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the workflow matcher recognizes the Windows Maven Wrapper command form.
 */
class MavenWrapperWindowsInvocationMatcherTest {

    @Test
    void recognizesWindowsWrapperInvocation() throws ReflectiveOperationException {
        Method matcher = MavenWrapperIntegrityTest.class.getDeclaredMethod(
                "containsWrapperInvocation",
                String.class
        );
        matcher.setAccessible(true);

        boolean matched = (boolean) matcher.invoke(null, "run: .\\\\mvnw.cmd -B test");

        assertTrue(matched, "Windows Maven Wrapper workflow command must be recognized");
    }
}
