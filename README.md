# Data Manager API utility library and samples for Java

[![Maven Central](https://img.shields.io/maven-central/v/com.google.api-ads/data-manager-util.svg)](https://central.sonatype.com/artifact/com.google.api-ads/data-manager-util)

Utility library and code samples for working with the
[Data Manager API](https://developers.google.com/data-manager/api) and Java.

## Requirements

- Java 8+

## Setup instructions

The `com.google.api-ads:data-manager-util` utility library is published to
[Maven Central](https://central.sonatype.com/artifact/com.google.api-ads/data-manager-util).

For complete instructions on setting up API access and installing the client and
utility libraries in your Maven or Gradle project, see the
[Set up API access](https://developers.google.com/data-manager/api/devguides/quickstart/set-up-access)
and
[Install a client library](https://developers.google.com/data-manager/api/devguides/quickstart/install-library#java)
guides.

## Repository structure

- [`data-manager-util`](data-manager-util): Source code and tests for the
  `com.google.api-ads:data-manager-util` Maven artifact. Use the utilities in
  the library to help with common tasks like formatting, hashing, encrypting,
  and encoding data for Data Manager API requests.

- [`data-manager-samples`](data-manager-samples): Code samples demonstrating how
  to construct and send requests to the Data Manager API using the
  [`com.google.api-ads:data-manager`](https://central.sonatype.com/artifact/com.google.api-ads/data-manager)
  client library and the `com.google.api-ads:data-manager-util` utility library.
  Check out the
  [samples](data-manager-samples/src/main/java/com/google/ads/datamanager/samples/)
  directory for runnable examples.

## Run samples

To run a sample, invoke the sample using the Gradle `run` task from the command
line. The first argument should be the simple class name of the sample, such as
`IngestEvents`.

You can pass arguments to a sample in one of two ways:

### 1. Explicitly, on the command line

```shell
./gradlew run --args="IngestEvents
  --operatingAccountType <operating_account_type>
  --operatingAccountId <operating_account_id>
  --conversionActionId <conversion_action_id>
  --jsonFile '</path/to/your/file>'"
```

Quote any argument that contains a space.

### 2. Using an arguments file

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

## Authors

- [Josh Radcliff](https://github.com/jradcliff)
- [Lindsey Volta](https://github.com/lindsey-volta)
