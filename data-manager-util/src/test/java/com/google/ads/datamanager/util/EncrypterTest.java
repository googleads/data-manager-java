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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.io.BaseEncoding;
import com.google.crypto.tink.Aead;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.KmsClient;
import com.google.crypto.tink.TinkProtoKeysetFormat;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.aead.XChaCha20Poly1305Parameters;
import com.google.crypto.tink.aead.XChaCha20Poly1305Parameters.Variant;
import com.google.crypto.tink.proto.EncryptedKeyset;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class EncrypterTest {

  private Encrypter encrypter;
  private ByteString expectedEncryptedDek;

  private static final String MOCK_KEK_URI =
      "gcp-kms://projects/MOCK_PROJECT_ID/locations/MOCK_LOCATION/keyRings/MOCK_KEY_RING_NAME/cryptoKeys/MOCK_KEY_NAME";

  @Before
  public void setUp() throws GeneralSecurityException, InvalidProtocolBufferException {
    AeadConfig.register();

    // Mocks the KmsClient and the Aead it returns for the KEK.
    KmsClient kmsClient = mock(KmsClient.class);
    Aead mockKekAead = mock(Aead.class);
    when(kmsClient.getAead(anyString())).thenReturn(mockKekAead);

    // Doesn't mock the KeysetHandle for the DEK since creating the DEK doesn't require a Google
    // Cloud Project or credentials. Also, we want to use mocking where only absolutely necessary.
    KeysetHandle dekKeysetHandle =
        KeysetHandle.generateNew(XChaCha20Poly1305Parameters.create(Variant.TINK));

    // Mocks the encryption output from the KEK.
    byte[] mockEncryptionBytes = "ENCRYPTED".getBytes(StandardCharsets.UTF_8);
    when(mockKekAead.encrypt(any(), any())).thenReturn(mockEncryptionBytes);

    this.expectedEncryptedDek =
        EncryptedKeyset.parseFrom(
                TinkProtoKeysetFormat.serializeEncryptedKeyset(
                    dekKeysetHandle, mockKekAead, new byte[0]))
            .getEncryptedKeyset();
    this.encrypter = Encrypter.create(kmsClient, MOCK_KEK_URI, dekKeysetHandle);
  }

  /** Tests that the encrypted DEK is returned as expected. */
  @Test
  public void testGetEncryptedDek() {
    ByteString encryptedDek = encrypter.getEncryptedDek();
    assertEquals("Encrypted DEK should match mock value", expectedEncryptedDek, encryptedDek);
  }

  /**
   * Verifies that the Encrypter produces distinct encryption output for each call, even if the
   * provided data is the same.
   *
   * <p>Per https://developers.google.com/tink/encrypt-data:
   *
   * <p><em>AEAD provides secrecy and authenticity and ensures that messages always have different
   * ciphertexts (encrypted outputs) even if the plaintexts (the inputs for the encryption) are the
   * same.</em>
   */
  @Test
  public void testEncryptMultipleTimes_differentResults() {
    String data = "alexf@example.com";
    // Encrypts the same data multiple times.
    List<byte[]> encryptedList =
        IntStream.range(0, 10).mapToObj(i -> encrypter.encrypt(data)).collect(Collectors.toList());
    // Verifies that encryption output differs every time by adding each encrypted value to a set.
    Set<String> encryptedValues = new HashSet<>();
    final BaseEncoding hexEncoding = BaseEncoding.base16();
    for (byte[] encryptedBytes : encryptedList) {
      String encoded = hexEncoding.encode(encryptedBytes);
      boolean isDistinct = encryptedValues.add(encoded);
      assertTrue("Encryption produced identical output in distinct invocations", isDistinct);
    }
  }
}
