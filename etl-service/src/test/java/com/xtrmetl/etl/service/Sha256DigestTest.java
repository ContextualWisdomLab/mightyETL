package com.xtrmetl.etl.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies deterministic lowercase SHA-256 text identities and fail-closed runtime handling.
 */
class Sha256DigestTest {

    @Test
    void producesThePublishedSha256Vector() {
        assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                Sha256Digest.digest("abc")
        );
    }

    @Test
    void rejectsMissingInputOrFactory() {
        assertThrows(NullPointerException.class, () -> Sha256Digest.digest(null));
        assertThrows(
                NullPointerException.class,
                () -> Sha256Digest.digest("abc", null)
        );
    }

    @Test
    void convertsMissingMandatoryAlgorithmIntoBrokenRuntimeSignal() {
        NoSuchAlgorithmException missingAlgorithm = new NoSuchAlgorithmException("missing");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> Sha256Digest.digest("abc", () -> {
                    throw missingAlgorithm;
                })
        );

        assertEquals("SHA-256 is required by the Java platform", exception.getMessage());
        assertInstanceOf(NoSuchAlgorithmException.class, exception.getCause());
    }

    @Test
    void utilityConstructorCannotBeCalledNormallyButRemainsCovered() throws Exception {
        Constructor<Sha256Digest> constructor = Sha256Digest.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        constructor.newInstance();
    }
}
