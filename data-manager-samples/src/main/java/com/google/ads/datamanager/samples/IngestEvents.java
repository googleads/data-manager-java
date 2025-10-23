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
import com.google.ads.datamanager.util.UserDataFormatter.Encoding;
import com.google.ads.datamanager.v1.AdIdentifiers;
import com.google.ads.datamanager.v1.Consent;
import com.google.ads.datamanager.v1.ConsentStatus;
import com.google.ads.datamanager.v1.Destination;
import com.google.ads.datamanager.v1.Event;
import com.google.ads.datamanager.v1.EventSource;
import com.google.ads.datamanager.v1.IngestEventsRequest;
import com.google.ads.datamanager.v1.IngestEventsResponse;
import com.google.ads.datamanager.v1.IngestionServiceClient;
import com.google.ads.datamanager.v1.ProductAccount;
import com.google.ads.datamanager.v1.ProductAccount.AccountType;
import com.google.ads.datamanager.v1.UserData;
import com.google.ads.datamanager.v1.UserIdentifier;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.reflect.TypeToken;
import com.google.gson.GsonBuilder;
import com.google.protobuf.util.Timestamps;
import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Sends an {@link IngestEventsRequest} without using encryption.
 *
 * <p>Event data is read from a data file. See the {@code events_1.json} file in the {@code
 * resources/sampledata} directory for a sample file.
 */
public class IngestEvents {
  private static final Logger LOGGER = Logger.getLogger(IngestEvents.class.getName());

  /** The maximum number of events allowed per request. */
  private static final int MAX_EVENTS_PER_REQUEST = 2_000;

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

    @Parameter(
        names = "--conversionActionId",
        required = true,
        description = "ID of the conversion action")
    String conversionActionId;

    @Parameter(
        names = "--jsonFile",
        required = true,
        description = "JSON file containing user data to ingest")
    String jsonFile;

    @Parameter(
        names = "--validateOnly",
        required = false,
        arity = 1,
        description = "Whether to enable validateOnly on the request")
    boolean validateOnly = true;
  }

  public static void main(String[] args) throws IOException {
    ParamsConfig paramsConfig = new ParamsConfig().parseOrExit(args);
    if ((paramsConfig.loginAccountId == null) != (paramsConfig.loginAccountType == null)) {
      throw new IllegalArgumentException(
          "Must specify either both or neither of login account ID and login account type");
    }
    if ((paramsConfig.linkedAccountId == null) != (paramsConfig.linkedAccountType == null)) {
      throw new IllegalArgumentException(
          "Must specify either both or neither of linked account ID and linked account type");
    }
    new IngestEvents().runExample(paramsConfig);
  }

  /**
   * Runs the example. This sample assumes that the login and operating account are the same.
   *
   * @param params the parameters for the example
   */
  private void runExample(ParamsConfig params) throws IOException {
    // Reads event data from the JSON file.
    List<EventRecord> eventRecords = readEventData(params.jsonFile);

    // Gets an instance of the UserDataFormatter for normalizing and formatting the data.
    UserDataFormatter userDataFormatter = UserDataFormatter.create();

    // Builds the events collection for the request.
    List<Event> events = new ArrayList<>();
    for (EventRecord eventRecord : eventRecords) {
      Event.Builder eventBuilder = Event.newBuilder();
      try {
        eventBuilder.setEventTimestamp(Timestamps.parse(eventRecord.timestamp));
      } catch (ParseException pe) {
        LOGGER.warning(
            () ->
                String.format("Skipping event with invalid timestamp: %s", eventRecord.timestamp));
        continue;
      }

      if (Strings.isNullOrEmpty(eventRecord.transactionId)) {
        LOGGER.warning("Skipping event with no transaction ID");
        continue;
      }
      eventBuilder.setTransactionId(eventRecord.transactionId);
      if (!Strings.isNullOrEmpty(eventRecord.eventSource)) {
        try {
          eventBuilder.setEventSource(EventSource.valueOf(eventRecord.eventSource));
        } catch (IllegalArgumentException iae) {
          LOGGER.warning("Skipping event with invalid event source: " + eventRecord.eventSource);
          continue;
        }
      }

      if (!Strings.isNullOrEmpty(eventRecord.gclid)) {
        eventBuilder.setAdIdentifiers(AdIdentifiers.newBuilder().setGclid(eventRecord.gclid));
      }

      if (!Strings.isNullOrEmpty(eventRecord.currency)) {
        eventBuilder.setCurrency(eventRecord.currency);
      }

      if (eventRecord.value != null) {
        eventBuilder.setConversionValue(eventRecord.value);
      }

      UserData.Builder userDataBuilder = UserData.newBuilder();

      // Adds a UserIdentifier for each valid email address for the eventRecord.
      if (eventRecord.emails != null) {
        for (String email : eventRecord.emails) {
          String preparedEmail;
          try {
            preparedEmail = userDataFormatter.processEmailAddress(email, Encoding.HEX);
          } catch (IllegalArgumentException iae) {
            // Skips invalid input.
            continue;
          }
          // Sets the email address identifier to the encoded email hash.
          userDataBuilder.addUserIdentifiers(
              UserIdentifier.newBuilder().setEmailAddress(preparedEmail));
        }
      }

      // Adds a UserIdentifier for each valid phone number for the eventRecord.
      if (eventRecord.phoneNumbers != null) {
        for (String phoneNumber : eventRecord.phoneNumbers) {
          String preparedPhoneNumber;
          try {
            preparedPhoneNumber = userDataFormatter.processPhoneNumber(phoneNumber, Encoding.HEX);
          } catch (IllegalArgumentException iae) {
            // Skips invalid input.
            continue;
          }
          // Sets the phone number identifier to the encoded phone number hash.
          userDataBuilder.addUserIdentifiers(
              UserIdentifier.newBuilder().setPhoneNumber(preparedPhoneNumber));
        }
      }

      if (userDataBuilder.getUserIdentifiersCount() > 0) {
        eventBuilder.setUserData(userDataBuilder);
      }
      events.add(eventBuilder.build());
    }

    // Builds the Destination for the request.
    Destination.Builder destinationBuilder =
        Destination.newBuilder()
            .setOperatingAccount(
                ProductAccount.newBuilder()
                    .setAccountType(params.operatingAccountType)
                    .setAccountId(params.operatingAccountId))
            .setProductDestinationId(params.conversionActionId);
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

    try (IngestionServiceClient ingestionServiceClient = IngestionServiceClient.create()) {
      int requestCount = 0;
      // Batches requests to send up to the maximum number of events per request.
      for (List<Event> eventsBatch : Lists.partition(events, MAX_EVENTS_PER_REQUEST)) {
        requestCount++;
        // Builds the request.
        IngestEventsRequest request =
            IngestEventsRequest.newBuilder()
                .addDestinations(destinationBuilder)
                // Adds events from the current batch.
                .addAllEvents(eventsBatch)
                .setConsent(
                    Consent.newBuilder()
                        .setAdPersonalization(ConsentStatus.CONSENT_GRANTED)
                        .setAdUserData(ConsentStatus.CONSENT_GRANTED))
                // Sets validate_only. If true, then the Data Manager API only validates the request
                // but doesn't apply changes.
                .setValidateOnly(params.validateOnly)
                // Sets encoding to match the encoding used.
                .setEncoding(com.google.ads.datamanager.v1.Encoding.HEX)
                .build();

        LOGGER.info(() -> String.format("Request:%n%s", request));
        IngestEventsResponse response = ingestionServiceClient.ingestEvents(request);
        LOGGER.info(String.format("Response for request #:%n%s", requestCount, response));
      }

      LOGGER.info("# of requests sent: " + requestCount);
    }
  }

  /** Data object for a single row of input data. */
  @SuppressWarnings("unused")
  private static class EventRecord {
    private List<String> emails;
    private List<String> phoneNumbers;
    private String timestamp;
    private String transactionId;
    private String eventSource;
    private Double value;
    private String currency;
    private String gclid;
  }

  /** Reads the data file and parses each line into a {@link EventRecord} object. */
  private List<EventRecord> readEventData(String jsonFile) throws IOException {
    try (BufferedReader jsonReader =
        Files.newBufferedReader(Paths.get(jsonFile), StandardCharsets.UTF_8)) {
      // Define the type for Gson to deserialize into (List of EventRecord objects)
      Type recordListType = new TypeToken<ArrayList<EventRecord>>() {}.getType();

      // Parse the JSON string from the file into a List of EventRecord objects
      return new GsonBuilder().create().fromJson(jsonReader, recordListType);
    }
  }
}
