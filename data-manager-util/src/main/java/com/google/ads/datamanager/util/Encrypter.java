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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.crypto.tink.Aead;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.KmsClient;
import com.google.crypto.tink.RegistryConfiguration;
import com.google.crypto.tink.TinkProtoKeysetFormat;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.aead.XChaCha20Poly1305Parameters;
import com.google.crypto.tink.aead.XChaCha20Poly1305Parameters.Variant;
import com.google.crypto.tink.integration.gcpkms.GcpKmsClient;
import com.google.crypto.tink.proto.EncryptedKeyset;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.security.GeneralSecurityException;

/**
 * Encryption utility that simplifies the process of encryption using a data encryption key (DEK)
 * encrypted with a key encryption key (KEK) from a key management service.
 */
public class Encrypter {
  /** Authenticated Encryption with Associated Data (AEAD) for the data encryption key (DEK). */
  private final Aead dekAead;

  /** The DEK, encrypted using the KEK. */
  private final ByteString encryptedDek;

  private static final byte[] EMPTY_ASSOCIATED_DATA = new byte[0];

  /**
   * Private constructor for a new instance.
   *
   * @param dekAead the Aead for the DEK
   * @param encryptedDek the encrypted DEK
   * @throws NullPointerException if any argument is null
   */
  private Encrypter(Aead dekAead, ByteString encryptedDek) {
    Preconditions.checkNotNull(dekAead, "Null DEK Aead");
    Preconditions.checkNotNull(encryptedDek, "Null encrypted DEK");
    this.dekAead = dekAead;
    this.encryptedDek = encryptedDek;
  }

  /**
   * Package-private factory method for creating a new instance.
   *
   * <p>Constructs the arguments needed for the constructor so the constructor implementation
   * remains as simple as possible.
   *
   * @param kmsClient the {@link KmsClient} for retrieving the KEK
   * @param kekUri the URI of the KEK
   * @param dekKeysetHandle the KeysetHandle for the DEK
   * @return a new instance
   * @throws GeneralSecurityException if any of the initialization steps fail
   * @throws NullPointerException if any argument is null
   */
  @VisibleForTesting
  static Encrypter create(KmsClient kmsClient, String kekUri, KeysetHandle dekKeysetHandle)
      throws GeneralSecurityException {
    Preconditions.checkNotNull(kmsClient, "Null kmsClient");
    Preconditions.checkNotNull(dekKeysetHandle, "Null dekKeysetHandle");
    Preconditions.checkNotNull(kekUri, "Null kekUri");

    // Creates an Aead for the DEK.
    AeadConfig.register();
    Aead dekAead = dekKeysetHandle.getPrimitive(RegistryConfiguration.get(), Aead.class);

    // Gets the remote AEAD for the KEK from the KMS using the provided URI.
    Aead kekAead;
    try {
      kekAead = kmsClient.getAead(kekUri);
    } catch (GeneralSecurityException gse) {
      throw new GeneralSecurityException("Failed to get the key at URI: " + kekUri, gse);
    }

    ByteString encryptedDek;
    try {
      encryptedDek =
          EncryptedKeyset.parseFrom(
                  TinkProtoKeysetFormat.serializeEncryptedKeyset(
                      dekKeysetHandle, kekAead, EMPTY_ASSOCIATED_DATA))
              .getEncryptedKeyset();
    } catch (InvalidProtocolBufferException e) {
      throw new GeneralSecurityException("Failed to encrypt the DEK", e);
    }
    return new Encrypter(dekAead, encryptedDek);
  }

  /**
   * Creates a new instance using credentials from the specified path to access the KEK from Google
   * Cloud KMS.
   *
   * @param kekUri the URI of the Google Cloud KMS key that the instance should use as a key
   *     encryption key (KEK)
   * @param credentialsPath path to the credentials file. If {@code null}, uses Application Default
   *     Credentials.
   * @return a new instance
   */
  public static Encrypter createForGcpKms(String kekUri, String credentialsPath)
      throws GeneralSecurityException {
    AeadConfig.register();
    KeysetHandle dekKeysetHandle =
        KeysetHandle.generateNew(XChaCha20Poly1305Parameters.create(Variant.TINK));
    return Encrypter.create(
        new GcpKmsClient().withCredentials(credentialsPath), kekUri, dekKeysetHandle);
  }

  /** Returns the DEK, encrypted using the KEK. */
  public final ByteString getEncryptedDek() {
    return this.encryptedDek;
  }

  /** Encrypts the provided data. */
  public byte[] encrypt(String data) {
    byte[] dataBytes = data.getBytes(UTF_8);
    try {
      return dekAead.encrypt(dataBytes, EMPTY_ASSOCIATED_DATA);
    } catch (GeneralSecurityException gse) {
      throw new IllegalArgumentException("Failed to encrypt data", gse);
    }
  }
}
