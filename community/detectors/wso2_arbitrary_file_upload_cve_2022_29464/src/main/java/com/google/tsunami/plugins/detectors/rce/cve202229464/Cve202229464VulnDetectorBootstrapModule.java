/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.tsunami.plugins.detectors.rce.cve202229464;

import com.google.tsunami.plugin.PluginBootstrapModule;

/** An CVE-2022-29464 Guice module that bootstraps the {@link Cve202229464VulnDetector}. */
public final class Cve202229464VulnDetectorBootstrapModule extends PluginBootstrapModule {

  @Override
  protected void configurePlugin() {
    registerPlugin(Cve202229464VulnDetector.class);
  }
}
