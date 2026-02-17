package com.mixpanel.android.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class Base64CoderTest {

    @Test
    public void testEncodeStringBasic() {
        assertEquals("SGVsbG8=", Base64Coder.encodeString("Hello"));
    }

    @Test
    public void testEncodeStringEmpty() {
        assertEquals("", Base64Coder.encodeString(""));
    }

    @Test
    public void testEncodeDecodeRoundTrip() {
        String original = "Hello, Mixpanel!";
        String encoded = Base64Coder.encodeString(original);
        String decoded = Base64Coder.decodeString(encoded);
        assertEquals(original, decoded);
    }

    @Test
    public void testEncodeDecodeRoundTripBinary() {
        byte[] original = new byte[]{0, 1, 2, 127, -128, -1};
        char[] encoded = Base64Coder.encode(original);
        byte[] decoded = Base64Coder.decode(encoded);
        assertArrayEquals(original, decoded);
    }

    @Test
    public void testEncodeKnownValues() {
        // Standard Base64 test vectors
        assertEquals("", Base64Coder.encodeString(""));
        assertEquals("Zg==", Base64Coder.encodeString("f"));
        assertEquals("Zm8=", Base64Coder.encodeString("fo"));
        assertEquals("Zm9v", Base64Coder.encodeString("foo"));
        assertEquals("Zm9vYg==", Base64Coder.encodeString("foob"));
        assertEquals("Zm9vYmE=", Base64Coder.encodeString("fooba"));
        assertEquals("Zm9vYmFy", Base64Coder.encodeString("foobar"));
    }

    @Test
    public void testDecodeKnownValues() {
        assertEquals("f", Base64Coder.decodeString("Zg=="));
        assertEquals("fo", Base64Coder.decodeString("Zm8="));
        assertEquals("foo", Base64Coder.decodeString("Zm9v"));
        assertEquals("foobar", Base64Coder.decodeString("Zm9vYmFy"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDecodeInvalidLength() {
        Base64Coder.decode("abc"); // not a multiple of 4
    }

    @Test
    public void testEncodeWithPartialLength() {
        byte[] data = new byte[]{65, 66, 67, 68, 69};
        char[] encoded = Base64Coder.encode(data, 3); // only encode first 3 bytes "ABC"
        assertEquals("QUJD", new String(encoded));
    }

    @Test
    public void testLargePayload() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("Mixpanel event data ");
        }
        String original = sb.toString();
        String encoded = Base64Coder.encodeString(original);
        String decoded = Base64Coder.decodeString(encoded);
        assertEquals(original, decoded);
    }

    @Test
    public void testSpecialCharacters() {
        String original = "{\"event\":\"test\",\"properties\":{\"key\":\"value with spaces & symbols!@#$%\"}}";
        String encoded = Base64Coder.encodeString(original);
        String decoded = Base64Coder.decodeString(encoded);
        assertEquals(original, decoded);
    }

    @Test
    public void testUnicodeCharacters() {
        String original = "Unicode: \u00e9\u00e8\u00ea \u00fc\u00f6\u00e4";
        String encoded = Base64Coder.encodeString(original);
        String decoded = Base64Coder.decodeString(encoded);
        assertEquals(original, decoded);
    }

    @Test
    public void testSingleByte() {
        byte[] data = new byte[]{42};
        char[] encoded = Base64Coder.encode(data);
        byte[] decoded = Base64Coder.decode(encoded);
        assertArrayEquals(data, decoded);
    }

    @Test
    public void testTwoBytes() {
        byte[] data = new byte[]{42, 43};
        char[] encoded = Base64Coder.encode(data);
        byte[] decoded = Base64Coder.decode(encoded);
        assertArrayEquals(data, decoded);
    }

    @Test
    public void testAllByteValues() {
        byte[] allBytes = new byte[256];
        for (int i = 0; i < 256; i++) {
            allBytes[i] = (byte) i;
        }
        char[] encoded = Base64Coder.encode(allBytes);
        byte[] decoded = Base64Coder.decode(encoded);
        assertArrayEquals(allBytes, decoded);
    }
}
