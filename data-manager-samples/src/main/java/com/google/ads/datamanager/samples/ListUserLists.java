// Copyright 2026 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law of or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.ads.datamanager.samples;

import com.beust.jcommander.Parameter;
import com.google.ads.datamanager.samples.common.BaseParamsConfig;
import com.google.ads.datamanager.v1.AccountName;
import com.google.ads.datamanager.v1.ListUserListsRequest;
import com.google.ads.datamanager.v1.ProductAccount.AccountType;
import com.google.ads.datamanager.v1.UserList;
import com.google.ads.datamanager.v1.UserListServiceClient;
import com.google.ads.datamanager.v1.UserListServiceSettings;
import com.google.api.gax.rpc.FixedHeaderProvider;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf.util.JsonFormat.Printer;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Lists the user lists for a given account. */
public class ListUserLists {

  private static class ParamsConfig extends BaseParamsConfig<ParamsConfig> {

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
    new ListUserLists().runExample(paramsConfig);
  }

  // [START list-user-lists]
  /**
   * Runs the example that lists all user lists in the operating account.
   *
   * @param params command line parameters to specify the required operating account and the
   *     optional login and linked accounts
   * @throws IOException if the API request fails
   */
  private void runExample(ParamsConfig params) throws IOException {
    // Creates a map of headers for the UserListService client.
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

    // Configures the settings to include headers.
    UserListServiceSettings userListServiceSettings =
        UserListServiceSettings.newBuilder()
            .setHeaderProvider(FixedHeaderProvider.create(headers))
            .build();

    // Constructs a UserListServiceClient using the settings to include headers if provided.
    // Uses a try-with-resources block so the client shuts down properly.
    try (UserListServiceClient userListServiceClient =
        UserListServiceClient.create(userListServiceSettings)) {
      ListUserListsRequest request =
          ListUserListsRequest.newBuilder()
              .setParent(
                  AccountName.format(params.operatingAccountType.name(), params.operatingAccountId))
              .build();

      // Creates a JSON printer for printing the UserList resources in the response.
      Printer printer = JsonFormat.printer();
      int userListIndex = 0;
      // Uses the "iterateAll()" convenience method to automatically handle pagination.
      for (UserList userList : userListServiceClient.listUserLists(request).iterateAll()) {
        System.out.printf("==== UserList[%d]:%n", userListIndex++);
        System.out.print(printer.print(userList));
        System.out.printf("%n%n");
      }
      System.out.printf("Total count of user list resources: %d%n", userListIndex);
    }
  }
  // [END list-user-lists]
}
