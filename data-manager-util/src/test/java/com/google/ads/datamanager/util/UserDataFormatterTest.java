// Copyright 2025 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.ads.datamanager.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.google.common.io.BaseEncoding;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import org.junit.Test;

public class UserDataFormatterTest {
  private final UserDataFormatter formatter = UserDataFormatter.create();
  private final BaseEncoding hexEncoder = BaseEncoding.base16();

  @Test
  public void testFormatEmailAddress_validInputs() {
    assertEquals(
        "Case should be normalized in name",
        "quinny@example.com",
        formatter.formatEmailAddress("QuinnY@example.com"));
    assertEquals(
        "Case should be normalized in domain",
        "quinny@example.com",
        formatter.formatEmailAddress("QuinnY@EXAMPLE.com"));
  }

  @Test
  public void testFormatEmailAddress_invalidInput_throwsException() {
    assertThrows(
        "Null email address",
        IllegalArgumentException.class,
        () -> formatter.formatEmailAddress(null));
    assertThrows(
        "Email without user portion",
        IllegalArgumentException.class,
        () -> formatter.formatEmailAddress("@example.com"));
    assertThrows(
        "Email without domain",
        IllegalArgumentException.class,
        () -> formatter.formatEmailAddress("quinn"));
  }

  @Test
  public void testFormatEmailAddress_gmailVariations() {
    assertEquals(
        "jeffersonloveshiking@gmail.com",
        formatter.formatEmailAddress("jefferson.Loves.hiking@gmail.com"));
    assertEquals(
        "jeffersonloveshiking@gmail.com",
        formatter.formatEmailAddress("j.e.f..ferson.Loves.hiking@gmail.com"));
    assertEquals(
        "jeffersonloveshiking@googlemail.com",
        formatter.formatEmailAddress("jefferson.Loves.hiking@googlemail.com"));
    assertEquals(
        "jeffersonloveshiking@googlemail.com",
        formatter.formatEmailAddress("j.e.f..ferson.Loves.hiking@googlemail.com"));
  }

  @Test
  public void testFormatPhoneNumber_validInputs() {
    String[][] validInputsOutputs = {
      {"1 800 555 0100", "+18005550100"},
      {"18005550100", "+18005550100"},
      {"+1 800-555-0100", "+18005550100"},
      {"441134960987", "+441134960987"},
      {"+441134960987", "+441134960987"},
      {"+44-113-496-0987", "+441134960987"},
    };
    for (String[] inputOutput : validInputsOutputs) {
      assertEquals(
          "Output incorrect for: " + inputOutput[0],
          inputOutput[1],
          formatter.formatPhoneNumber(inputOutput[0]));
    }
  }

  @Test
  public void testFormatPhoneNumber_invalidInput_throwsException() {
    assertThrows(
        "Null phone number",
        IllegalArgumentException.class,
        () -> formatter.formatPhoneNumber(null));
    assertThrows(
        "Non-digit phone number",
        IllegalArgumentException.class,
        () -> formatter.formatPhoneNumber("+abc-DEF"));
    assertThrows(
        "Phone number with only +",
        IllegalArgumentException.class,
        () -> formatter.formatPhoneNumber("++++"));
  }

  @Test
  public void testFormatGivenName_validInputs() {
    assertEquals("alex", formatter.formatGivenName(" Alex   "));
    assertEquals("Contains 'mr.' as a prefix", "alex", formatter.formatGivenName(" Mr. Alex   "));
    assertEquals("Contains 'mrs.' as a prefix", "alex", formatter.formatGivenName(" Mrs. Alex   "));
    assertEquals("Contains 'dr.' as a prefix", "alex", formatter.formatGivenName(" Dr. Alex   "));
    assertEquals("Contains 'dr.' as a suffix", "alex", formatter.formatGivenName(" Alex Dr."));
    assertEquals(
        "Contains 'mr' but not as a prefix", "mralex", formatter.formatGivenName(" Mralex   "));
  }

  @Test
  public void testFormatGivenName_invalidInput_throwsException() {
    assertThrows(
        "Null given name", IllegalArgumentException.class, () -> formatter.formatGivenName(null));
    assertThrows(
        "Empty name", IllegalArgumentException.class, () -> formatter.formatGivenName(" "));
    assertThrows(
        "Given name that only contains a prefix",
        IllegalArgumentException.class,
        () -> formatter.formatGivenName(" Mr. "));
  }

  @Test
  public void testFormatLastName_validInputs() {
    assertEquals("quinn", formatter.formatLastName(" Quinn   "));
    assertEquals("Hyphenated name", "quinn-alex", formatter.formatLastName("Quinn-Alex"));
    assertEquals(
        "Contains 'jr.' as a suffix with preceding comma",
        "quinn",
        formatter.formatLastName(" Quinn, Jr.   "));
    assertEquals(
        "Contains 'jr.' as a suffix with preceding comma",
        "quinn",
        formatter.formatLastName(" Quinn,Jr.   "));
    assertEquals("Contains 'sr.' as a suffix", "quinn", formatter.formatLastName(" Quinn Sr.  "));
    assertEquals("Contains multiple suffixes", "quinn", formatter.formatLastName("quinn, jr. dds"));
    assertEquals(
        "Contains multiple suffixes", "quinn", formatter.formatLastName("quinn, jr., dds"));
    assertEquals(
        "Ends with suffix characters that aren't a suffix",
        "boardds",
        formatter.formatLastName("Boardds"));
    assertEquals(
        "Contains suffix character 'cpa' in the middle",
        "lacparm",
        formatter.formatLastName("lacparm"));
  }

  @Test
  public void testFormatLastName_invalidInput_throwsException() {
    assertThrows(
        "Null given name", IllegalArgumentException.class, () -> formatter.formatLastName(null));
    assertThrows("Empty name", IllegalArgumentException.class, () -> formatter.formatLastName(" "));
    assertThrows(
        "Last name that only contains a suffix",
        IllegalArgumentException.class,
        () -> formatter.formatLastName(", Jr. "));
    assertThrows(
        "Last name that only contains multiple suffixes",
        IllegalArgumentException.class,
        () -> formatter.formatLastName(",Jr.,DDS "));
  }

  @Test
  public void testFormatRegionCode_validInputs() {
    assertEquals("US", formatter.formatRegionCode("us"));
    assertEquals("US", formatter.formatRegionCode("us  "));
    assertEquals("US", formatter.formatRegionCode("  us  "));
  }

  @Test
  public void testFormatRegionCode_invalidInputs() {
    assertThrows(
        "Null region code", IllegalArgumentException.class, () -> formatter.formatRegionCode(null));
    assertThrows(
        "Empty region code", IllegalArgumentException.class, () -> formatter.formatRegionCode(""));
    assertThrows(
        "Blank region code",
        IllegalArgumentException.class,
        () -> formatter.formatRegionCode("  "));
    assertThrows(
        "Region code with < 2 chars",
        IllegalArgumentException.class,
        () -> formatter.formatRegionCode("u"));
    assertThrows(
        "Region code with > 2 chars",
        IllegalArgumentException.class,
        () -> formatter.formatRegionCode(" usa "));
    assertThrows(
        "Region code with intermediate spaces",
        IllegalArgumentException.class,
        () -> formatter.formatRegionCode(" u s "));
    assertThrows(
        "Region code with non-alpha chars",
        IllegalArgumentException.class,
        () -> formatter.formatRegionCode(" u2 "));
  }

  @Test
  public void testFormatPostalCode_validInputs() {
    assertEquals("94045", formatter.formatPostalCode("94045"));
    assertEquals("94045", formatter.formatPostalCode(" 94045  "));
    assertEquals("1229-076", formatter.formatPostalCode("1229-076"));
    assertEquals("1229-076", formatter.formatPostalCode("  1229-076  "));
  }

  @Test
  public void testFormatPostalCode_invalidInputs() {
    assertThrows(
        "Null postal code", IllegalArgumentException.class, () -> formatter.formatPostalCode(null));
    assertThrows(
        "Empty postal code", IllegalArgumentException.class, () -> formatter.formatPostalCode(""));
    assertThrows(
        "Blank postal code",
        IllegalArgumentException.class,
        () -> formatter.formatPostalCode("  "));
  }

  @Test
  public void testHashString_validInputs() {
    Function<String, String> hashAndEncode = s -> hexEncoder.encode(formatter.hashString(s));
    assertEquals(
        "509E933019BB285A134A9334B8BB679DFF79D0CE023D529AF4BD744D47B4FD8A",
        hashAndEncode.apply("alexz@example.com"));
    assertEquals(
        "FB4F73A6EC5FDB7077D564CDD22C3554B43CE49168550C3B12C547B78C517B30",
        hashAndEncode.apply("+18005550100"));
  }

  @Test
  public void testHashString_invalidInput_throwsException() {
    assertThrows("Null string", IllegalArgumentException.class, () -> formatter.hashString(null));
    assertThrows("Empty string", IllegalArgumentException.class, () -> formatter.hashString(""));
    assertThrows("Blank string", IllegalArgumentException.class, () -> formatter.hashString(" "));
    assertThrows("Blank string", IllegalArgumentException.class, () -> formatter.hashString("  "));
  }

  @Test
  public void testHexEncode_validInputs() {
    assertEquals("61634B313233", formatter.hexEncode("acK123".getBytes(StandardCharsets.UTF_8)));
    assertEquals("3939395F58595A", formatter.hexEncode("999_XYZ".getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  public void testHexEncode_invalidInput_throwsException() {
    assertThrows(
        "Null byte array", IllegalArgumentException.class, () -> formatter.hexEncode(null));
    assertThrows(
        "Empty byte array", IllegalArgumentException.class, () -> formatter.hexEncode(new byte[0]));
  }

  @Test
  public void testBase64Encode_invalidInput_throwsException() {
    assertThrows(
        "Null byte array", IllegalArgumentException.class, () -> formatter.base64Encode(null));
    assertThrows(
        "Empty byte array",
        IllegalArgumentException.class,
        () -> formatter.base64Encode(new byte[0]));
  }

  @Test
  public void testBase64Encode_validInputs() {
    assertEquals("YWNLMTIz", formatter.base64Encode("acK123".getBytes(StandardCharsets.UTF_8)));
    assertEquals(
        "OTk5X1hZWg==", formatter.base64Encode("999_XYZ".getBytes(StandardCharsets.UTF_8)));
  }
}
