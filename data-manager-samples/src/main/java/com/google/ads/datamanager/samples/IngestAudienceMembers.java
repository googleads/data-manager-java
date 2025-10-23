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
import com.google.ads.datamanager.util.UserDataFormatter.Encoding;
import com.google.ads.datamanager.v1.AudienceMember;
import com.google.ads.datamanager.v1.Consent;
import com.google.ads.datamanager.v1.ConsentStatus;
import com.google.ads.datamanager.v1.Destination;
import com.google.ads.datamanager.v1.EncryptionInfo;
import com.google.ads.datamanager.v1.GcpWrappedKeyInfo;
import com.google.ads.datamanager.v1.GcpWrappedKeyInfo.KeyType;
import com.google.ads.datamanager.v1.IngestAudienceMembersRequest;
import com.google.ads.datamanager.v1.IngestAudienceMembersResponse;
import com.google.ads.datamanager.v1.IngestionServiceClient;
import com.google.ads.datamanager.v1.ProductAccount;
import com.google.ads.datamanager.v1.ProductAccount.AccountType;
import com.google.ads.datamanager.v1.TermsOfService;
import com.google.ads.datamanager.v1.TermsOfServiceStatus;
import com.google.ads.datamanager.v1.UserData;
import com.google.ads.datamanager.v1.UserIdentifier;
import com.google.common.collect.Lists;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sends an {@link IngestAudienceMembersRequest} with the option to use encryption.
 *
 * <p>User data is read from a data file. See the {@code audience_members_1.csv} file in the {@code
 * resources/sampledata} directory for a sample file.
 */
public class IngestAudienceMembers {
  private static final Logger LOGGER = Logger.getLogger(IngestAudienceMembers.class.getName());

  /** The maximum number of audience members allowed per request. */
  private static final int MAX_MEMBERS_PER_REQUEST = 10_000;

  private static final class ParamsConfig extends BaseParamsConfig<ParamsConfig> {

    @Parameter(
        names = "--operatingAccountType",
        required = true,
        description = "Account type of the operating account")
    AccountType operatingAccountType;

    @Parameter(
        names = "--operatingAccountId",
        required = true,
        description = "ID of the operating account")
    String operatingAccountId;

    @Parameter(
        names = "--loginAccountType",
        required = false,
        description = "Account type of the login account")
    AccountType loginAccountType;

    @Parameter(
        names = "--loginAccountId",
        required = false,
        description = "ID of the login account")
    String loginAccountId;

    @Parameter(
        names = "--linkedAccountType",
        required = false,
        description = "Account type of the linked account")
    AccountType linkedAccountType;

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

    @Parameter(
        names = "--keyUri",
        required = false,
        description =
            "URI of the Google Cloud KMS key for encrypting data. If this parameter is set, you"
                + " must also set the --wipProvider parameter.")
    String keyUri;

    @Parameter(
        names = "--wipProvider",
        required = false,
        description =
            "Workload Identity Pool provider name for encrypting data. If this parameter is set,"
                + " you must also set the --keyUri parameter. The argument for this parameter must"
                + " follow the pattern:"
                + " projects/PROJECT_ID/locations/global/workloadIdentityPools/WIP_ID/providers/PROVIDER_ID")
    String wipProvider;

    @Parameter(
        names = "--validateOnly",
        required = false,
        arity = 1,
        description = "Whether to enable validateOnly on the request")
    boolean validateOnly = true;
  }

  public static void main(String[] args) throws IOException, GeneralSecurityException {
    ParamsConfig paramsConfig = new ParamsConfig().parseOrExit(args);
    if ((paramsConfig.loginAccountId == null) != (paramsConfig.loginAccountType == null)) {
      throw new IllegalArgumentException(
          "Must specify either both or neither of login account ID and login account type");
    }
    if ((paramsConfig.linkedAccountId == null) != (paramsConfig.linkedAccountType == null)) {
      throw new IllegalArgumentException(
          "Must specify either both or neither of linked account ID and linked account type");
    }
    if ((paramsConfig.keyUri == null) != (paramsConfig.wipProvider == null)) {
      throw new IllegalArgumentException(
          "Must specify either both or neither of key URI and WIP provider");
    }
    new IngestAudienceMembers().runExample(paramsConfig);
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

    // Determines if encryption parameters are set.
    boolean useEncryption = (params.keyUri != null && params.wipProvider != null);
    Encrypter encrypter = null;
    if (useEncryption) {
      // Gets an instance of the encryption utility.
      encrypter = Encrypter.createForGcpKms(params.keyUri, null);
    }
    // Builds the audience_members collection for the request.
    List<AudienceMember> audienceMembers = new ArrayList<>();
    for (Member member : memberList) {
      UserData.Builder userDataBuilder = UserData.newBuilder();

      // Adds a UserIdentifier for each valid email address for the member.
      for (String email : member.emailAddresses) {
        String processedEmail;
        try {
          processedEmail =
              useEncryption
                  ? userDataFormatter.processEmailAddress(email, Encoding.HEX, encrypter)
                  : userDataFormatter.processEmailAddress(email, Encoding.HEX);
        } catch (IllegalArgumentException iae) {
          // Skips invalid input.
          continue;
        }
        // Sets the email address identifier to the encoded and possibly encrypted email hash.
        userDataBuilder.addUserIdentifiers(
            UserIdentifier.newBuilder().setEmailAddress(processedEmail));
      }

      // Adds a UserIdentifier for each valid phone number for the member.
      for (String phoneNumber : member.phoneNumbers) {
        String processedPhoneNumber;
        try {
          processedPhoneNumber =
              useEncryption
                  ? userDataFormatter.processPhoneNumber(phoneNumber, Encoding.HEX, encrypter)
                  : userDataFormatter.processPhoneNumber(phoneNumber, Encoding.HEX);
        } catch (IllegalArgumentException iae) {
          // Skips invalid input.
          continue;
        }
        // Sets the phone number identifier to the encoded and possibly encrypted phone number hash.
        userDataBuilder.addUserIdentifiers(
            UserIdentifier.newBuilder().setPhoneNumber(processedPhoneNumber));
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
                    .setAccountType(params.operatingAccountType)
                    .setAccountId(params.operatingAccountId))
            .setProductDestinationId(params.audienceId);
    if (params.loginAccountType != null && params.loginAccountId != null) {
      destinationBuilder.setLoginAccount(
          ProductAccount.newBuilder()
              .setAccountType(params.loginAccountType)
              .setAccountId(params.loginAccountId));
    }
    if (params.linkedAccountType != null && params.linkedAccountId != null) {
      destinationBuilder.setLinkedAccount(
          ProductAccount.newBuilder()
              .setAccountType(params.linkedAccountType)
              .setAccountId(params.linkedAccountId));
    }

    // Configures the EncryptionInfo for the request if encryption parameters provided.
    EncryptionInfo encryptionInfo = null;
    if (useEncryption) {
      encryptionInfo =
          EncryptionInfo.newBuilder()
              .setGcpWrappedKeyInfo(
                  GcpWrappedKeyInfo.newBuilder()
                      .setKekUri(params.keyUri)
                      .setWipProvider(params.wipProvider)
                      .setKeyType(KeyType.XCHACHA20_POLY1305)
                      // Sets the encrypted_dek field to the Base64-encoded encrypted DEK.
                      .setEncryptedDek(
                          userDataFormatter.base64Encode(
                              encrypter.getEncryptedDek().toByteArray())))
              .build();
    }

    try (IngestionServiceClient ingestionServiceClient = IngestionServiceClient.create()) {
      int requestCount = 0;
      // Batches requests to send up to the maximum number of audience members per request.
      for (List<AudienceMember> audienceMembersBatch :
          Lists.partition(audienceMembers, MAX_MEMBERS_PER_REQUEST)) {
        requestCount++;
        // Builds the request.
        IngestAudienceMembersRequest.Builder requestBuilder =
            IngestAudienceMembersRequest.newBuilder()
                .addDestinations(destinationBuilder)
                // Adds members from the current batch.
                .addAllAudienceMembers(audienceMembersBatch)
                .setConsent(
                    Consent.newBuilder()
                        .setAdPersonalization(ConsentStatus.CONSENT_GRANTED)
                        .setAdUserData(ConsentStatus.CONSENT_GRANTED))
                // Sets validate_only. If true, then the Data Manager API only validates the request
                // but doesn't apply changes.
                .setValidateOnly(params.validateOnly)
                // Sets encoding to match the encoding used.
                .setEncoding(com.google.ads.datamanager.v1.Encoding.HEX)
                .setTermsOfService(
                    TermsOfService.newBuilder()
                        .setCustomerMatchTermsOfServiceStatus(TermsOfServiceStatus.ACCEPTED));

        if (useEncryption) {
          // Sets encryption info on the request.
          requestBuilder.setEncryptionInfo(encryptionInfo);
        }

        IngestAudienceMembersRequest request = requestBuilder.build();
        IngestAudienceMembersResponse response =
            ingestionServiceClient.ingestAudienceMembers(request);
        if (LOGGER.isLoggable(Level.INFO)) {
          LOGGER.info(String.format("Response for request #%d:%n%s", requestCount, response));
        }
      }
      LOGGER.info("# of requests sent: " + requestCount);
    }
  }

  /** Data object for a single row of input data. */
  private static class Member {
    private final List<String> emailAddresses = new ArrayList<>();
    private final List<String> phoneNumbers = new ArrayList<>();
  }

  /**
   * Reads the data file and parses each line into a {@link IngestAudienceMembers.Member} object.
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
          // Parses the row, ignoring anything beyond column index 5.
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
