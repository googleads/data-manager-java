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

package com.google.ads.datamanager.samples.common;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.internal.DefaultConsole;
import com.google.common.annotations.VisibleForTesting;
import java.io.PrintStream;

/**
 * Base class for all JCommander parameter classes.
 *
 * @param <P> the type of the subclass
 */
@Parameters(
    // Allows users to separate a parameter from its value using ' ' or '='.
    separators = " =")
public abstract class BaseParamsConfig<P extends BaseParamsConfig<P>> {
  @Parameter(names = "--help", help = true)
  protected boolean help = false;

  public P parseOrExit(String[] args) {
    return parseOrExit(args, Runtime.getRuntime(), System.err);
  }

  @VisibleForTesting
  @SuppressWarnings("unchecked")
  protected final P parseOrExit(String[] args, Runtime runtime, PrintStream printStream) {
    JCommander.Builder jcBuilder =
        JCommander.newBuilder().addObject(this).console(new DefaultConsole(printStream));
    if (this.getClass().getEnclosingClass() != null) {
      // Sets the program name for the usage string if this class follows the convention of making
      // the params config class an inner class of the code sample.
      jcBuilder.programName(this.getClass().getEnclosingClass().getSimpleName());
    }
    JCommander jc = jcBuilder.build();
    boolean argsValid;
    try {
      jc.parse(args);
      argsValid = true;
    } catch (ParameterException pe) {
      argsValid = false;
      pe.getJCommander().getConsole().println(pe.getMessage());
    }

    if (help || !argsValid) {
      jc.usage();
      runtime.exit(1);
    }
    return (P) this;
  }
}
