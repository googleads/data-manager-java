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
import com.google.ads.datamanager.util.UserDataFormatter;
import com.google.ads.datamanager.v1.AudienceMember;
import com.google.ads.datamanager.v1.Consent;
import com.google.ads.datamanager.v1.ConsentStatus;
import com.google.ads.datamanager.v1.Destination;
import com.google.ads.datamanager.v1.Encoding;
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
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Sends an {@link IngestAudienceMembersRequest} without using encryption.
 *
 * <p>User data is read from a data file. See the {@code audience_members_1.csv} file in the {@code
 * resources/sampledata} directory for a sample file.
 */
public class IngestAudienceMembers {
  private static final Logger LOGGER = Logger.getLogger(IngestAudienceMembers.class.getName());

  private static final class ParamsConfig extends BaseParamsConfig<ParamsConfig> {

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
  }

  public static void main(String[] args) throws IOException {
    ParamsConfig paramsConfig = new ParamsConfig().parseOrExit(args);
    if ((paramsConfig.loginAccountId == null) != (paramsConfig.loginAccountProduct == null)) {
      throw new IllegalArgumentException(
          "Must specify either both or neither of login account ID and login account product");
    }
    if ((paramsConfig.linkedAccountId == null) != (paramsConfig.linkedAccountProduct == null)) {
      throw new IllegalArgumentException(
          "Must specify either both or neither of linked account ID and linked account product");
    }
    new IngestAudienceMembers().runExample(paramsConfig);
  }

  /**
   * Runs the example. This sample assumes that the login and operating account are the same.
   *
   * @param params the parameters for the example
   */
  private void runExample(ParamsConfig params) throws IOException {
    // Reads member data from the data file.
    List<Member> memberList = readMemberDataFile(params.csvFile);

    // Gets an instance of the UserDataFormatter for normalizing and formatting the data.
    UserDataFormatter userDataFormatter = UserDataFormatter.create();

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
        // Hex encodes the hash.
        String encodedEmailHash = userDataFormatter.hexEncode(emailHash);
        // Sets the email address identifier to the encoded email hash.
        userDataBuilder.addUserIdentifiers(
            UserIdentifier.newBuilder().setEmailAddress(encodedEmailHash));
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
        // Hex encodes the hash.
        String encodedPhoneNumberHash = userDataFormatter.hexEncode(phoneNumberHash);
        // Sets the phone number identifier to the encoded phone number hash.
        userDataBuilder.addUserIdentifiers(
            UserIdentifier.newBuilder().setPhoneNumber(encodedPhoneNumberHash));
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

    // Builds the request.
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
            // Sets encoding to match the encoding used.
            .setEncoding(Encoding.HEX)
            .setTermsOfService(
                TermsOfService.newBuilder()
                    .setCustomerMatchTermsOfServiceStatus(TermsOfServiceStatus.ACCEPTED))
            .build();

    try (IngestionServiceClient ingestionServiceClient = IngestionServiceClient.create()) {
      IngestAudienceMembersResponse response =
          ingestionServiceClient.ingestAudienceMembers(request);
      LOGGER.info(() -> String.format("Response:%n%s", response));
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
