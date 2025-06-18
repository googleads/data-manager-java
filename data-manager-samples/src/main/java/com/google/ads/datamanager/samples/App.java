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

import com.google.common.base.Preconditions;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.stream.IntStream;

/**
 * Top-level runner for code samples. Used as the entry point for the {@code run} task from Gradle's
 * {@code java-application} plugin.
 */
public class App {
  public static void main(String[] args)
      throws ClassNotFoundException,
          NoSuchMethodException,
          InvocationTargetException,
          IllegalAccessException {
    // Verifies that at least one argument (the class name) was supplied.
    Preconditions.checkArgument(args.length > 0, "Missing required sample class name arg");
    String sampleName = args[0];
    System.out.printf("Running %s example%n", sampleName);
    // Uses reflection to get the sample class and its `main` method.
    Class<?> sampleClass = Class.forName("com.google.ads.datamanager.samples." + sampleName);
    Method mainMethod = sampleClass.getMethod("main", String[].class);
    // Passes the remaining arguments to the `main` method of the sample class.
    String[] remainingArgs =
        IntStream.range(1, args.length).mapToObj(i -> args[i]).toArray(size -> new String[size]);
    mainMethod.invoke(null, (Object) remainingArgs);
  }
}
