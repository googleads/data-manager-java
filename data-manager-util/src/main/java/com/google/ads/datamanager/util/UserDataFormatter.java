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

import com.google.common.base.Preconditions;
import com.google.common.io.BaseEncoding;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Utility for normalizing and formatting user data.
 *
 * <p>Methods throw {@link IllegalArgumentException} when passed invalid input. Since arguments to
 * these methods contain user data, exception messages <em>don't</em> include the argument values.
 *
 * <p>Instances of this class are <em>not</em> thread-safe.
 */
@NotThreadSafe
public class UserDataFormatter {

  /** SHA-256 digest. Instances of MessageDigest are not thread safe. */
  private final MessageDigest sha256Digest;

  /** Encoder for hex. */
  private final BaseEncoding hexEncoder = BaseEncoding.base16();

  /** Encoder for Base64. */
  private final BaseEncoding base64Encoder = BaseEncoding.base64();

  /** Locale for case conversions. Uses the root locale for consistency. */
  private static final Locale LOCALE = Locale.ROOT;

  /** Pattern that matches any whitespace character. */
  private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s");

  /** Pattern that matches the period (.) character. */
  private static final Pattern PERIOD_PATTERN = Pattern.compile("\\.");

  /** Pattern that matches all non-digits. */
  private static final Pattern NON_DIGIT_PATTERN = Pattern.compile("\\D");

  /** Pattern that matches all prefixes for a given name. */
  private static final Pattern GIVEN_NAME_PREFIX_PATTERN =
      Pattern.compile("(?:mr|mrs|ms|dr)\\.(?:\\s|$)");

  /** Pattern that matches all suffixes for a last name. */
  private static final Pattern LAST_NAME_SUFFIX_PATTERN =
      Pattern.compile(
          "(?:,\\s*|\\s+)(?:jr\\.|sr\\.|2nd|3rd|ii|iii|iv|v|vi|cpa|dc|dds|vm|jd|md|phd)\\s?$");

  /** Pattern that matches all uppercase characters. */
  private static final Pattern ALL_UPPERCASE_CHARS_PATTERN = Pattern.compile("^[A-Z]+$");

  private UserDataFormatter(MessageDigest sha256Digest) {
    this.sha256Digest = sha256Digest;
  }

  /** Returns a new instance. */
  public static UserDataFormatter create() {
    MessageDigest sha256Digest;
    try {
      sha256Digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("Failed to obtain a SHA-256 message digest", e);
    }
    return new UserDataFormatter(sha256Digest);
  }

  /**
   * Returns the provided email address, normalized and formatted.
   *
   * @param emailAddress the email address to format
   * @throws IllegalArgumentException if {@code emailAddress} is invalid. Examples of an invalid
   *     value include a {@code null}, blank, or empty string, an email address without a domain, or
   *     an email address that contains spaces.
   */
  public String formatEmailAddress(String emailAddress) {
    // Checks for null using checkArgument instead of checkNotNull so the caller only needs to
    // handle IllegalArgumentException.
    Preconditions.checkArgument(emailAddress != null, "Null email address");
    // Removes leading and trailing whitespace.
    emailAddress = emailAddress.trim();
    Preconditions.checkArgument(!emailAddress.isEmpty(), "Empty or blank email address");
    Preconditions.checkArgument(
        !WHITESPACE_PATTERN.matcher(emailAddress).matches(),
        "Email contains intermediate whitespace");
    String[] emailParts =
        emailAddress
            // Converts to lowercase.
            .toLowerCase(LOCALE)
            // Splits into the username and domain components of the email address.
            .split("@", 2);
    Preconditions.checkArgument(emailParts.length == 2, "Email is not of the form user@domain");
    String username = emailParts[0];
    String domain = emailParts[1];
    Preconditions.checkArgument(!username.isEmpty(), "Email address without the domain is empty");
    Preconditions.checkArgument(!domain.isEmpty(), "Domain of email address is empty");

    if ("gmail.com".equals(domain) || "googlemail.com".equals(domain)) {
      // Handles variations of Gmail addresses. See:
      // https://gmail.googleblog.com/2008/03/2-hidden-ways-to-get-more-from-your.html
      // "Create variations of your email address" at:
      // https://support.google.com/a/users/answer/9282734

      // Removes all periods (.).
      username = PERIOD_PATTERN.matcher(username).replaceAll("");
    }

    // Throws an exception if any of the above results in no characters in the username.
    Preconditions.checkArgument(
        !username.isEmpty(), "Email address without the domain name is empty after normalization");

    return String.format("%s@%s", username, domain);
  }

  /**
   * Returns the provided phone number, normalized and formatted.
   *
   * @param phoneNumber the phone number to format
   * @throws IllegalArgumentException if {@code phoneNumber} is invalid. Examples of an invalid
   *     value include a {@code null}, blank, or empty string, or a phone number that contains no
   *     digits.
   */
  public String formatPhoneNumber(String phoneNumber) {
    // Checks for null using checkArgument instead of checkNotNull so the caller only needs to
    // handle IllegalArgumentException.
    Preconditions.checkArgument(phoneNumber != null, "Null phone number");
    phoneNumber = phoneNumber.trim();
    Preconditions.checkArgument(!phoneNumber.isEmpty(), "Empty or blank phone number");

    // Removes all non-digits.
    String digitsOnly = NON_DIGIT_PATTERN.matcher(phoneNumber).replaceAll("");

    // Returns null if no digits were in the phoneNumber.
    Preconditions.checkArgument(!digitsOnly.isEmpty(), "Phone number contains no digits");

    // Prepends a '+' symbol.
    return String.format("+%s", digitsOnly);
  }

  /**
   * Returns the provided given name, normalized and formatted.
   *
   * @param givenName the given name to format
   * @throws IllegalArgumentException if {@code givenName} is invalid. Examples of an invalid value
   *     include a {@code null}, blank, or empty string, or a name that consists only of a prefix
   *     such as "Mrs.".
   */
  public String formatGivenName(String givenName) {
    Preconditions.checkArgument(givenName != null, "Null given name");
    givenName = givenName.trim();
    Preconditions.checkArgument(!givenName.isEmpty(), "Empty or blank given name");
    givenName = givenName.toLowerCase(LOCALE);
    String withoutPrefix = GIVEN_NAME_PREFIX_PATTERN.matcher(givenName).replaceAll("").trim();
    Preconditions.checkArgument(!withoutPrefix.isEmpty(), "Given name consists solely of a prefix");
    return withoutPrefix;
  }

  /**
   * Returns the provided last name, normalized and formatted.
   *
   * @param lastName the last name to format
   * @throws IllegalArgumentException if {@code lastName} is invalid. Examples of an invalid value
   *     include a {@code null}, blank, or empty string, or a name that consists only of a suffix
   *     such as "Jr.".
   */
  public String formatLastName(String lastName) {
    Preconditions.checkArgument(lastName != null, "Null last name");
    lastName = lastName.trim();
    Preconditions.checkArgument(!lastName.isEmpty(), "Empty or blank last name");
    lastName = lastName.toLowerCase(LOCALE);

    // Uses a loop to handle the case where there are multiple suffixes, such as ", Jr., DDS".
    Matcher matcher;
    while ((matcher = LAST_NAME_SUFFIX_PATTERN.matcher(lastName)).find()) {
      lastName = matcher.replaceAll("");
    }
    Preconditions.checkArgument(!lastName.isEmpty(), "Last name consists solely of a suffix");
    return lastName;
  }

  /**
   * Returns the provided region code, normalized and formatted.
   *
   * @param regionCode the region code to format
   * @throws IllegalArgumentException if {@code regionCode} is invalid. Examples of an invalid value
   *     include a {@code null}, blank, or empty string, or a code that's not exactly 2 characters,
   *     or a code with non-alpha characters.
   */
  public String formatRegionCode(String regionCode) {
    Preconditions.checkArgument(regionCode != null, "Null region code");
    regionCode = regionCode.trim().toUpperCase(LOCALE);
    Preconditions.checkArgument(!regionCode.isEmpty(), "Empty or blank region code");
    // Verifies it's a 2-character code since the API requires an ISO 3166-1 alpha-2 code
    // (https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2).
    Preconditions.checkArgument(
        regionCode.length() == 2,
        "Region code length is %s, but length must be 2",
        regionCode.length());
    Preconditions.checkArgument(
        ALL_UPPERCASE_CHARS_PATTERN.matcher(regionCode).matches(),
        "Region code contains characters other than A-Z");
    return regionCode;
  }

  /**
   * Returns the provided postal code, normalized and formatted.
   *
   * @param postalCode the postal code to format
   * @throws IllegalArgumentException if {@code postalCode} is invalid. Examples of an invalid value
   *     include a {@code null}, blank, or empty string.
   */
  public String formatPostalCode(String postalCode) {
    Preconditions.checkArgument(postalCode != null, "Null postal code");
    postalCode = postalCode.trim();
    Preconditions.checkArgument(!postalCode.isEmpty(), "Empty or blank postal code");
    return postalCode;
  }

  /**
   * Returns the SHA-256 hash of the provided string.
   *
   * @param s the string to hash
   * @throws IllegalArgumentException if the string is null, blank, or empty
   */
  public byte[] hashString(String s) {
    Preconditions.checkArgument(s != null, "Null string");
    // Rejects an empty or blank string. The digest() method accepts a byte array based on an empty
    // or blank string, but it's extremely unlikely that an empty string is valid in the context of
    // formatting user data.
    Preconditions.checkArgument(!s.trim().isEmpty(), "Empty or blank string");
    return sha256Digest.digest(s.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Returns the provided byte array, encoded using hex (base 16) encoding.
   *
   * @param bytes the byte array to encode
   * @throws IllegalArgumentException if the byte array is null or empty
   */
  public String hexEncode(byte[] bytes) {
    Preconditions.checkArgument(bytes != null, "Null byte array");
    Preconditions.checkArgument(bytes.length > 0, "Empty byte array");
    return hexEncoder.encode(bytes);
  }

  /**
   * Returns the provided byte array, encoded using Base64 encoding.
   *
   * @param bytes the byte array to encode
   * @throws IllegalArgumentException if the byte array is null or empty
   */
  public String base64Encode(byte[] bytes) {
    Preconditions.checkArgument(bytes != null, "Null byte array");
    Preconditions.checkArgument(bytes.length > 0, "Empty byte array");
    return base64Encoder.encode(bytes);
  }
}
