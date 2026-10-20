// Copyright 2026 Google LLC
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
import com.google.ads.datamanager.v1.AccountName;
import com.google.ads.datamanager.v1.ContactIdInfo;
import com.google.ads.datamanager.v1.CreateUserListRequest;
import com.google.ads.datamanager.v1.DataSourceType;
import com.google.ads.datamanager.v1.IngestedUserListInfo;
import com.google.ads.datamanager.v1.IngestedUserListInfo.UploadKeyType;
import com.google.ads.datamanager.v1.MobileIdInfo;
import com.google.ads.datamanager.v1.MobileIdInfo.KeySpace;
import com.google.ads.datamanager.v1.ProductAccount.AccountType;
import com.google.ads.datamanager.v1.UserIdInfo;
import com.google.ads.datamanager.v1.UserList;
import com.google.ads.datamanager.v1.UserListServiceClient;
import com.google.ads.datamanager.v1.UserListServiceSettings;
import com.google.api.gax.rpc.FixedHeaderProvider;
import com.google.common.base.Strings;
import com.google.protobuf.util.Durations;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/** Sample of creating a {@link UserList} in an operating account. */
public class CreateUserList {
  private static final Logger LOGGER = Logger.getLogger(CreateUserList.class.getName());

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
        names = "--uploadKeyType",
        required = false,
        description =
            "Upload key type of the user list (CONTACT_ID, USER_ID, or MOBILE_ID). Defaults to"
                + " CONTACT_ID.")
    UploadKeyType uploadKeyType = UploadKeyType.CONTACT_ID;

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
        names = "--keySpace",
        required = false,
        description =
            "Key space of mobile IDs (ANDROID or IOS). Required when --uploadKeyType is"
                + " MOBILE_ID.")
    KeySpace keySpace;

    @Parameter(
        names = "--appId",
        required = false,
        description =
            "String that uniquely identifies a mobile application from which the data was"
                + " collected (for example, 'com.labpixies.flood' for Android or '476943146' for"
                + " iOS). Required when --uploadKeyType is MOBILE_ID.")
    String appId;

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
    if (UploadKeyType.MOBILE_ID.equals(paramsConfig.uploadKeyType)) {
      if (paramsConfig.keySpace == null || Strings.isNullOrEmpty(paramsConfig.appId)) {
        throw new IllegalArgumentException(
            "Must specify both keySpace and appId when uploadKeyType is MOBILE_ID");
      }
    } else if (paramsConfig.keySpace != null || !Strings.isNullOrEmpty(paramsConfig.appId)) {
      throw new IllegalArgumentException(
          "keySpace and appId are only supported when uploadKeyType is MOBILE_ID");
    }
    new CreateUserList().runExample(paramsConfig);
  }

  // [START create-user-list]
  /**
   * Runs the example.
   *
   * @param params the parameters for the example
   * @throws IOException if the API request fails
   */
  private void runExample(ParamsConfig params) throws IOException {
    // Optional: Sets headers to specify the login-account and linked-account if your credentials
    // aren't for a user in the operating account (for example, when authenticating through a Google
    // Ads manager account or Data Partner account).
    Map<String, String> headers = new HashMap<>();
    if (params.loginAccountId != null) {
      headers.put(
          "login-account",
          // Uses the AccountName utility to construct the login-account resource name.
          AccountName.format(params.loginAccountType.name(), params.loginAccountId));
    }
    if (params.linkedAccountId != null) {
      headers.put(
          "linked-account",
          // Uses the AccountName utility to construct the linked-account resource name.
          AccountName.format(params.linkedAccountType.name(), params.linkedAccountId));
    }

    // Configures the Customer Match user list.
    // For more details on constructing a UserList, see:
    // https://developers.google.com/data-manager/api/devguides/audiences/google-ads/customer-match/create-audience
    IngestedUserListInfo.Builder ingestedUserListInfoBuilder =
        IngestedUserListInfo.newBuilder()
            // Sets to a single UploadKeyType.
            .addUploadKeyTypes(params.uploadKeyType);

    switch (params.uploadKeyType) {
      case CONTACT_ID:
        // Use CONTACT_ID for hashed email addresses, phone numbers, or physical addresses.
        ingestedUserListInfoBuilder.setContactIdInfo(
            ContactIdInfo.newBuilder()
                .setDataSourceType(DataSourceType.DATA_SOURCE_TYPE_FIRST_PARTY));
        break;
      case USER_ID:
        // Use USER_ID for first-party user IDs assigned to logged-in users.
        // Note: Display & Video 360 does not support USER_ID lists.
        ingestedUserListInfoBuilder.setUserIdInfo(
            UserIdInfo.newBuilder()
                .setDataSourceType(DataSourceType.DATA_SOURCE_TYPE_FIRST_PARTY));
        break;
      case MOBILE_ID:
        // Use MOBILE_ID for mobile advertising IDs.
        ingestedUserListInfoBuilder.setMobileIdInfo(
            MobileIdInfo.newBuilder()
                .setDataSourceType(DataSourceType.DATA_SOURCE_TYPE_FIRST_PARTY)
                .setKeySpace(params.keySpace)
                .setAppId(params.appId));
        break;
      default:
        throw new IllegalArgumentException(
            "Unsupported upload key type: " + params.uploadKeyType);
    }

    UserList userList =
        UserList.newBuilder()
            // Required. The display name must be unique among all lists owned by the account.
            .setDisplayName(
                String.format(
                    "Customer Match (%s) - %s",
                    params.uploadKeyType.name(),
                    LocalDateTime.now(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))))
            .setDescription(
                String.format("Customer Match audience for %s", params.uploadKeyType.name()))
            // Optional. The duration an audience member remains in the list after being added. Must
            // correspond to whole days (defaults to maximum if not set).
            .setMembershipDuration(Durations.fromDays(30))
            // Required for a Customer Match audience.
            .setIngestedUserListInfo(ingestedUserListInfoBuilder)
            .build();

    // Configures the settings for UserListService to include headers.
    UserListServiceSettings userListServiceSettings =
        UserListServiceSettings.newBuilder()
            .setHeaderProvider(FixedHeaderProvider.create(headers))
            .build();

    // Creates a client for UserListService using the settings to include headers if provided.
    // Uses a try-with-resources block so the client shuts down properly.
    try (UserListServiceClient userListServiceClient =
        UserListServiceClient.create(userListServiceSettings)) {
      // Builds the request targeting the parent operating account.
      CreateUserListRequest request =
          CreateUserListRequest.newBuilder()
              .setParent(
                  AccountName.format(params.operatingAccountType.name(), params.operatingAccountId))
              .setUserList(userList)
              // Sets validate_only. If true, then the Data Manager API only validates the request
              // but doesn't apply changes.
              .setValidateOnly(params.validateOnly)
              .build();

      // Sends the request.
      UserList response = userListServiceClient.createUserList(request);

      // Logs the created user list. When validate_only is false, response.getId() contains the
      // created user list ID, which you pass as the product_destination_id in subsequent ingest or
      // remove requests.
      LOGGER.info(() -> String.format("Response:%n%s", response));
    }
  }
  // [END create-user-list]
}
