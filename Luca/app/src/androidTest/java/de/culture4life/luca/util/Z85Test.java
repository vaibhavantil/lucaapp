package de.culture4life.luca.util;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class Z85Test {

    private static final String BASE64_ENCODED_DATA = "AgAAgKt0X2dGsPr48Gs80B49tT8Zlk3SRLrTmiboPwTFAXJkEJuNV3/CMsQSc9fOrX4qBC2oYAMK+Zy+Gv1/10C/8/U5W8ZDoXoGrsRkHF/rM44eZ0fK9qjKDnx9+lgz";
    private static final String Z85_ENCODED_DATA = "0SSjJT8}YYmZh::[m$ek9Zc[}8j11Bm7Q<ocG)U%-q$0&5sXUXF5i$V5{8WIT:R-5eVr4V3I*ja8Vtfqk!({-iA%G<P)u{C-af&{(OBLxxgHbrSlbP$EFN54";

    @Test
    public void encodeAndDecode_validInput_correctOutput() {
        byte[] input = "AOU\nÄÖÜ".getBytes(StandardCharsets.UTF_8);
        String encoded = Z85.encode(input);
        byte[] decoded = Z85.decode(encoded);
        assertArrayEquals(input, decoded);
    }

    @Test
    public void encode_validInput_correctOutput() {
        byte[] data = SerializationUtil.deserializeFromBase64(BASE64_ENCODED_DATA).blockingGet();
        String actual = Z85.encode(data);
        System.out.println(actual);
        assertEquals(Z85_ENCODED_DATA, actual);
    }

    @Test
    public void decode_validInput_correctOutput() {
        byte[] expected = SerializationUtil.deserializeFromBase64(BASE64_ENCODED_DATA).blockingGet();
        byte[] actual = Z85.decode(Z85_ENCODED_DATA);
        assertArrayEquals(expected, actual);
    }

}