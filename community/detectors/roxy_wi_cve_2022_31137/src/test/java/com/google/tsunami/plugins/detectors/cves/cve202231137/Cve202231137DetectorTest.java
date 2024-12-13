/*
 * Copyright 2024 Google LLC
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
package com.google.tsunami.plugins.detectors.cves.cve202231137;

import static com.google.common.truth.Truth.assertThat;
import static com.google.tsunami.common.data.NetworkEndpointUtils.forHostname;
import static com.google.tsunami.plugins.detectors.cves.cve202231137.Annotations.OobSleepDuration;
import static com.google.tsunami.plugins.detectors.cves.cve202231137.Cve202231137Detector.VULNERABLE_REQUEST_PATH;

import com.google.common.collect.ImmutableList;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.inject.util.Modules;
import com.google.tsunami.common.net.http.HttpClientModule;
import com.google.tsunami.common.net.http.HttpStatus;
import com.google.tsunami.common.time.testing.FakeUtcClock;
import com.google.tsunami.common.time.testing.FakeUtcClockModule;
import com.google.tsunami.plugin.payload.testing.FakePayloadGeneratorModule;
import com.google.tsunami.proto.DetectionReportList;
import com.google.tsunami.proto.NetworkService;
import com.google.tsunami.proto.TargetInfo;
import java.io.IOException;
import java.time.Instant;
import javax.inject.Inject;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link Cve202231137Detector}. */
@RunWith(JUnit4.class)
public final class Cve202231137DetectorTest {
  private final FakeUtcClock fakeUtcClock =
      FakeUtcClock.create().setNow(Instant.parse("2022-05-23T00:00:00.00Z"));
  private MockWebServer mockWebServer;
  private NetworkService service;
  private TargetInfo targetInfo;
  @Inject private Cve202231137Detector detector;

  @Bind(lazy = true)
  @OobSleepDuration
  private int sleepDuration = 1;

  @Before
  public void setUp() {
    mockWebServer = new MockWebServer();
    Guice.createInjector(
            new FakeUtcClockModule(fakeUtcClock),
            new HttpClientModule.Builder().build(),
            FakePayloadGeneratorModule.builder().setCallbackServer(mockWebServer).build(),
            Modules.override(new Cve202231137DetectorBootstrapModule())
                .with(BoundFieldModule.of(this)))
        .injectMembers(this);
    service = TestHelper.createWebService(mockWebServer);
    targetInfo = TestHelper.buildTargetInfo(forHostname(mockWebServer.getHostName()));
  }

  @After
  public void tearDown() throws IOException {
    mockWebServer.shutdown();
  }

  @Test
  public void detect_whenVulnerable_returnsVulnerability() throws InterruptedException {
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(HttpStatus.OK.code())
            .setBody("<title>Login page - Roxy-WI</title>"));
    mockWebServer.enqueue(
        new MockResponse().setResponseCode(HttpStatus.OK.code()).setBody("A Constant Response"));
    detector.detect(targetInfo, ImmutableList.of(service));
    mockWebServer.takeRequest();
    RecordedRequest secondRequest = mockWebServer.takeRequest();
    assertThat(secondRequest.getBody().toString()).contains("alert_consumer=1&ipbackend=\";");
    assertThat(secondRequest.getPath()).isEqualTo("/" + VULNERABLE_REQUEST_PATH);
  }

  @Test
  public void detect_ifNotVulnerableHtmlResponse_doesNotReportVuln() {
    mockWebServer.enqueue(
        new MockResponse().setResponseCode(HttpStatus.OK.code()).setBody("A Constant Response"));

    DetectionReportList detectionReports = detector.detect(targetInfo, ImmutableList.of(service));
    assertThat(detectionReports.getDetectionReportsList()).isEmpty();
  }
}