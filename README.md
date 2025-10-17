# Data Manager API utility library and samples for Java

Utility library and code samples for working with the
[Data Manager API](https://developers.google.com/data-manager/api) and Java.

## Setup instructions

https://developers.google.com/data-manager/api/get-started/set-up-access#java

## Repository structure

- [`data-manager-util`](data-manager-util): Source code for the utility library.

  Follow the setup instructions to declare a dependency on the current version
  of `com.google.api-ads:data-manager-util` in your project. Use the utilities
  in the library to help with common tasks like formatting, hashing, encrypting,
  and encoding data for Data Manager API requests.

- [`data-manager-samples`](data-manager-samples): Code samples for working with
  the Data Manager API and the utility library.

  The `data-manager-samples` project demonstrates how to set up a project that
  depends on the Data Manager API client library and the `data-manager-util`
  library. Check out the
  [samples](data-manager-samples/src/main/java/com/google/ads/datamanager/samples/)
  directory for code samples that construct and send requests to the Data Manager
  API.

## Run samples

To run a sample, invoke the sample using the Gradle `run` task from the command
line. The first argument should be the simple class name of the sample, such as
`IngestEvents`.

You can pass arguments to a sample in one of two ways:

### 1.  Explicitly, on the command line

```shell
./gradlew run --args="IngestEvents
  --operatingAccountType <operating_account_type>
  --operatingAccountId <operating_account_id>
  --conversionActionId <conversion_action_id>
  --jsonFile '</path/to/your/file>'"
```

Quote any argument that contains a space.

### 2.  Using an arguments file

You can also save arguments in a file. Don't quote argument values in your
arguments file, even if the value contains a space.

```
--operatingAccountType <operating_account_type>
--operatingAccountId <operating_account_id>
--conversionActionId <conversion_action_id>
--jsonFile </path/to/your/file>
```

The first example used one line per argument pair. You can also put each
argument on a separate line if you'd prefer that format.

```
--operatingAccountType
<operating_account_type>
--operatingAccountId
<operating_account_id>
--conversionActionId
<conversion_action_id>
--jsonFile
</path/to/your/file>
```

Then, run the sample by passing:

1. The simple class name of the sample.
2. The file path prefixed with the `@` character.

```shell
./gradlew run --args="IngestEvents @/path/to/your/argsfile"
```

## Issue tracker

- https://github.com/googleads/data-manager-java/issues

## Contributing

Contributions welcome! See the [Contributing Guide](CONTRIBUTING.md).
