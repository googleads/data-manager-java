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

package com.google.ads.datamanager.samples;

import com.beust.jcommander.Parameter;
import com.google.ads.datamanager.samples.common.BaseParamsConfig;
import com.google.ads.datamanager.util.Encrypter;
import com.google.ads.datamanager.util.UserDataFormatter;
import com.google.ads.datamanager.v1.AudienceMember;
import com.google.ads.datamanager.v1.Consent;
import com.google.ads.datamanager.v1.ConsentStatus;
import com.google.ads.datamanager.v1.Destination;
import com.google.ads.datamanager.v1.Encoding;
import com.google.ads.datamanager.v1.EncryptionInfo;
import com.google.ads.datamanager.v1.GcpWrappedKeyInfo;
import com.google.ads.datamanager.v1.GcpWrappedKeyInfo.KeyType;
import com.google.ads.datamanager.v1.IngestAudienceMembersRequest;
import com.google.ads.datamanager.v1.IngestAudienceMembersResponse;
import com.google.ads.datamanager.v1.IngestionServiceClient;
import com.google.ads.datamanager.v1.Product;
import com.google.ads.datamanager.v1.ProductAccount;
import com.google.ads.datamanager.v1.TermsOfService;
import com.google.ads.datamanager.v1.TermsOfServiceStatus;
import com.google.ads.datamanager.v1.UserData;
import com.google.ads.datamanager.v1.UserIdentifier;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Sends an {@link IngestAudienceMembersRequest} with encrypted user data.
 *
 * <p>User data is read from a data file. See the {@code audience_members_1.csv} file in the {@code
 * resources/sampledata} directory for a sample file.
 */
public class IngestAudienceMembersWithEncryption {
  private static final Logger LOGGER =
      Logger.getLogger(IngestAudienceMembersWithEncryption.class.getName());

  private static final class ParamsConfig
      extends BaseParamsConfig<IngestAudienceMembersWithEncryption.ParamsConfig> {

    @Parameter(
        names = "--operatingAccountProduct",
        required = true,
        description = "Product type of the operating account")
    Product operatingAccountProduct;

    @Parameter(
        names = "--operatingAccountId",
        required = true,
        description = "ID of the operating account")
    String operatingAccountId;

    @Parameter(
        names = "--loginAccountProduct",
        required = false,
        description = "Product type of the login account")
    Product loginAccountProduct;

    @Parameter(
        names = "--loginAccountId",
        required = false,
        description = "ID of the login account")
    String loginAccountId;

    @Parameter(
        names = "--linkedAccountProduct",
        required = false,
        description = "Product type of the linked account")
    Product linkedAccountProduct;

    @Parameter(
        names = "--linkedAccountId",
        required = false,
        description = "ID of the linked account")
    String linkedAccountId;

    @Parameter(names = "--audienceId", required = true, description = "ID of the audience")
    String audienceId;

    @Parameter(
        names = "--csvFile",
        required = true,
        description = "Comma-separated file containing user data to ingest")
    String csvFile;

    @Parameter(names = "--keyUri", required = true, description = "URI of the Google Cloud KMS key")
    String keyUri;

    @Parameter(
        names = "--wipProvider",
        required = true,
        description =
            "Workload Identity Pool provider name. Must follow the pattern: "
                + " projects/PROJECT_ID/locations/global/workloadIdentityPools/"
                + "WIP_ID/providers/PROVIDER_ID")
    String wipProvider;
  }

  public static void main(String[] args) throws IOException, GeneralSecurityException {
    IngestAudienceMembersWithEncryption.ParamsConfig paramsConfig =
        new IngestAudienceMembersWithEncryption.ParamsConfig().parseOrExit(args);
    if ((paramsConfig.loginAccountId == null) != (paramsConfig.loginAccountProduct == null)) {
      throw new IllegalArgumentException(
          "Must specify either both or neither of login account ID and login account product");
    }
    if ((paramsConfig.linkedAccountId == null) != (paramsConfig.linkedAccountProduct == null)) {
      throw new IllegalArgumentException(
          "Must specify either both or neither of linked account ID and linked account product");
    }
    new IngestAudienceMembersWithEncryption().runExample(paramsConfig);
  }

  /**
   * Runs the example.
   *
   * @param params the parameters for the example
   */
  private void runExample(ParamsConfig params) throws IOException, GeneralSecurityException {
    // Reads member data from the data file.
    List<Member> memberList = readMemberDataFile(params.csvFile);

    // Gets an instance of the UserDataFormatter for normalizing and formatting the data.
    UserDataFormatter userDataFormatter = UserDataFormatter.create();
    // Gets an instance of the encryption utility.
    Encrypter encrypter = Encrypter.createForGcpKms(params.keyUri, null);
    // Builds the audience_members collection for the request.

    List<AudienceMember> audienceMembers = new ArrayList<>();
    for (Member member : memberList) {
      UserData.Builder userDataBuilder = UserData.newBuilder();

      // Adds a UserIdentifier for each valid email address for the member.
      for (String email : member.emailAddresses) {
        String normalizedEmail;
        try {
          normalizedEmail = userDataFormatter.formatEmailAddress(email);
        } catch (IllegalArgumentException iae) {
          // Skips invalid input.
          continue;
        }
        // Hashes the normalized email address.
        byte[] emailHash = userDataFormatter.hashString(normalizedEmail);
        // Encrypts the email hash.
        byte[] encryptedEmailHash = encryptHash(emailHash, encrypter, userDataFormatter);
        // Encodes the encrypted email hash using hex encoding.
        String encodedEncryptedEmail = userDataFormatter.hexEncode(encryptedEmailHash);
        // Sets the email address identifier to the encoded encrypted email hash.
        userDataBuilder.addUserIdentifiers(
            UserIdentifier.newBuilder().setEmailAddress(encodedEncryptedEmail));
      }

      // Adds a UserIdentifier for each valid phone number for the member.
      for (String phoneNumber : member.phoneNumbers) {
        String normalizedPhoneNumber;
        try {
          normalizedPhoneNumber = userDataFormatter.formatPhoneNumber(phoneNumber);
        } catch (IllegalArgumentException iae) {
          // Skips invalid input.
          continue;
        }
        // Hashes the normalized phone number.
        byte[] phoneNumberHash = userDataFormatter.hashString(normalizedPhoneNumber);
        // Encrypts the email hash.
        byte[] encryptedPhoneNumber = encryptHash(phoneNumberHash, encrypter, userDataFormatter);
        // Encodes the encrypted phone number hash using hex encoding.
        String encodedEncryptedPhoneNumber = userDataFormatter.hexEncode(encryptedPhoneNumber);
        // Sets the phone number identifier to the encoded encrypted phone number.
        userDataBuilder.addUserIdentifiers(
            UserIdentifier.newBuilder().setPhoneNumber(encodedEncryptedPhoneNumber));
      }

      if (userDataBuilder.getUserIdentifiersCount() > 0) {
        audienceMembers.add(AudienceMember.newBuilder().setUserData(userDataBuilder).build());
      }
    }

    // Builds the Destination for the request.
    Destination.Builder destinationBuilder =
        Destination.newBuilder()
            .setOperatingAccount(
                ProductAccount.newBuilder()
                    .setProduct(params.operatingAccountProduct)
                    .setAccountId(params.operatingAccountId))
            .setProductDestinationId(params.audienceId);
    if (params.loginAccountProduct != null && params.loginAccountId != null) {
      destinationBuilder.setLoginAccount(
          ProductAccount.newBuilder()
              .setProduct(params.loginAccountProduct)
              .setAccountId(params.loginAccountId));
    }
    if (params.linkedAccountProduct != null && params.linkedAccountId != null) {
      destinationBuilder.setLinkedAccount(
          ProductAccount.newBuilder()
              .setProduct(params.linkedAccountProduct)
              .setAccountId(params.linkedAccountId));
    }

    // Configures the EncryptionInfo for the request.
    EncryptionInfo encryptionInfo =
        EncryptionInfo.newBuilder()
            .setGcpWrappedKeyInfo(
                GcpWrappedKeyInfo.newBuilder()
                    .setKekUri(params.keyUri)
                    .setWipProvider(params.wipProvider)
                    .setKeyType(KeyType.XCHACHA20_POLY1305)
                    // Sets the encrypted_dek field to the Base64-encoded encrypted DEK.
                    .setEncryptedDek(
                        userDataFormatter.base64Encode(encrypter.getEncryptedDek().toByteArray())))
            .build();

    IngestAudienceMembersRequest request =
        IngestAudienceMembersRequest.newBuilder()
            .addDestinations(destinationBuilder)
            .addAllAudienceMembers(audienceMembers)
            .setConsent(
                Consent.newBuilder()
                    .setAdPersonalization(ConsentStatus.CONSENT_GRANTED)
                    .setAdUserData(ConsentStatus.CONSENT_GRANTED))
            // Sets validate_only to true to validate but not apply the changes in the request.
            .setValidateOnly(true)
            .setTermsOfService(
                TermsOfService.newBuilder()
                    .setCustomerMatchTermsOfServiceStatus(TermsOfServiceStatus.ACCEPTED))
            // Sets encryption info on the request.
            .setEncryptionInfo(encryptionInfo)
            // Sets encoding to hex encoding since that was used to encode the encrypted values.
            .setEncoding(Encoding.HEX)
            .build();

    try (IngestionServiceClient ingestionServiceClient = IngestionServiceClient.create()) {
      IngestAudienceMembersResponse response =
          ingestionServiceClient.ingestAudienceMembers(request);
      LOGGER.info(String.format("Response:%n%s", response));
    }
  }

  /**
   * Encrypts the provided {@code hashBytes}.
   *
   * @param hashBytes the hash bytes to encrypt
   * @param encrypter the {@link Encrypter} instance
   * @return the encrypted value as a byte array
   */
  private byte[] encryptHash(byte[] hashBytes, Encrypter encrypter, UserDataFormatter formatter) {
    // Encodes the hash using Base64 encoding.
    String encodedHash = formatter.base64Encode(hashBytes);
    // Encrypts the Base64-encoded hash.
    return encrypter.encrypt(encodedHash);
  }

  /** Data object for a single row of input data. */
  private static class Member {
    private final List<String> emailAddresses = new ArrayList<>();
    private final List<String> phoneNumbers = new ArrayList<>();
  }

  /**
   * Reads the data file and parses each line into a {@link
   * IngestAudienceMembersWithEncryption.Member} object.
   *
   * @param dataFile the CSV data file
   * @return a list of Member objects
   */
  private List<Member> readMemberDataFile(String dataFile) throws IOException {
    List<Member> members = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(new FileReader(dataFile))) {
      String line;
      int lineNumber = 0;
      while ((line = reader.readLine()) != null) {
        lineNumber++;
        if (line.startsWith("#")) {
          // Skips comment lines.
          continue;
        }
        // Expected format:
        // email_1,email_2,email_3,phone_1,phone_2,phone_3
        String[] columns = line.split(",");
        if (columns[0].equals("email_1")) {
          // Skips header row.
          continue;
        }
        Member member = new Member();
        for (int col = 0; col < columns.length; col++) {
          if (columns[col] == null || columns[col].trim().isEmpty()) {
            // Skips blank value for the row and column.
            continue;
          }
          // Parses the row, ignoring anything after column index 5.
          if (col < 3) {
            member.emailAddresses.add(columns[col]);
          } else if (col < 6) {
            member.phoneNumbers.add(columns[col]);
          } else {
            LOGGER.warning("Ignoring column index " + col + " in line #" + lineNumber);
          }
        }
        if (member.emailAddresses.isEmpty() && member.phoneNumbers.isEmpty()) {
          // Skips the row since it contains no user data.
          LOGGER.warning(String.format("Ignoring line %d. No data.", lineNumber));
        } else {
          // Adds the parsed user data to the list.
          members.add(member);
        }
      }
    }

    return members;
  }
}
