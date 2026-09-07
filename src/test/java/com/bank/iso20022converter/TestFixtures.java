package com.bank.iso20022converter;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public final class TestFixtures {

    private TestFixtures() {
    }

    public static String pain001Sample() {
        return read("/samples/xml/pain001-sample.xml");
    }

    public static String pacs002RejectionSample() {
        return read("/samples/xml/pacs002-rejection-sample.xml");
    }

    private static String read(String classpathResource) {
        try (InputStream in = TestFixtures.class.getResourceAsStream(classpathResource)) {
            if (in == null) {
                throw new IllegalStateException("Missing test resource: " + classpathResource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
