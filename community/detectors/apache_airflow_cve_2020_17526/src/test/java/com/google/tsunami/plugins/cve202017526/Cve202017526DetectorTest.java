/*
 * Copyright 2020 Google LLC
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
package com.google.tsunami.plugins.cve202017526;

import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.tsunami.common.data.NetworkEndpointUtils.forHostnameAndPort;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;
import com.google.inject.Guice;
import com.google.protobuf.util.Timestamps;
import com.google.tsunami.common.net.http.HttpClientModule;
import com.google.tsunami.common.net.http.HttpStatus;
import com.google.tsunami.common.time.testing.FakeUtcClock;
import com.google.tsunami.common.time.testing.FakeUtcClockModule;
import com.google.tsunami.plugin.payload.testing.FakePayloadGeneratorModule;
import com.google.tsunami.plugin.payload.testing.PayloadTestHelper;
import com.google.tsunami.proto.DetectionReport;
import com.google.tsunami.proto.DetectionReportList;
import com.google.tsunami.proto.DetectionStatus;
import com.google.tsunami.proto.NetworkService;
import com.google.tsunami.proto.Severity;
import com.google.tsunami.proto.TargetInfo;
import com.google.tsunami.proto.Vulnerability;
import com.google.tsunami.proto.VulnerabilityId;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import javax.inject.Inject;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for the {@link Cve202017526Detector}.
 */
@RunWith(JUnit4.class)
public final class Cve202017526DetectorTest {
    private final MockWebServer mockTargetService = new MockWebServer();
    private final MockWebServer mockCallbackServer = new MockWebServer();
    private final FakeUtcClock fakeUtcClock =
            FakeUtcClock.create().setNow(Instant.parse("2020-01-01T00:00:00.00Z"));
    private final SecureRandom testSecureRandom =
            new SecureRandom() {
                @Override
                public void nextBytes(byte[] bytes) {
                    Arrays.fill(bytes, (byte) 0xFF);
                }
            };

    @Inject
    private Cve202017526Detector detector;

    @Before
    public void setUp() throws IOException {
        mockCallbackServer.start();

        Guice.createInjector(
                        new FakeUtcClockModule(fakeUtcClock),
                        new HttpClientModule.Builder().build(),
                        FakePayloadGeneratorModule.builder()
                                .setCallbackServer(mockCallbackServer)
                                .setSecureRng(testSecureRandom)
                                .build(),
                        new Cve202017526DetectorModule())
                .injectMembers(this);
    }

    @After
    public void tearDown() throws Exception {
        mockTargetService.shutdown();
        mockCallbackServer.shutdown();
    }

    @Test
    public void detect_withCallbackServer_onVulnerableTarget_returnsVulnerability()
            throws IOException {
        startMockWebServer();
        mockCallbackServer.enqueue(PayloadTestHelper.generateMockSuccessfulCallbackResponse());
        NetworkService targetNetworkService =
                NetworkService.newBuilder()
                        .setNetworkEndpoint(
                                forHostnameAndPort(mockTargetService.getHostName(), mockTargetService.getPort()))
                        .addSupportedHttpMethods("POST")
                        .build();
        TargetInfo targetInfo =
                TargetInfo.newBuilder()
                        .addNetworkEndpoints(targetNetworkService.getNetworkEndpoint())
                        .build();

        DetectionReportList detectionReports =
                detector.detect(targetInfo, ImmutableList.of(targetNetworkService));

        Truth.assertThat(mockCallbackServer.getRequestCount()).isEqualTo(1);
        assertThat(detectionReports.getDetectionReportsList())
                .comparingExpectedFieldsOnly()
                .containsExactly(
                        DetectionReport.newBuilder()
                                .setTargetInfo(targetInfo)
                                .setNetworkService(targetNetworkService)
                                .setDetectionTimestamp(
                                        Timestamps.fromMillis(Instant.now(fakeUtcClock).toEpochMilli()))
                                .setDetectionStatus(DetectionStatus.VULNERABILITY_VERIFIED)
                                .setVulnerability(
                                        Vulnerability.newBuilder()
                                                .setMainId(
                                                        VulnerabilityId.newBuilder().setPublisher("TSUNAMI_COMMUNITY")
                                                                .setValue("CVE-2020-17526"))
                                                .setSeverity(Severity.CRITICAL)
                                                .setTitle("CVE-2020-17526 Authentication bypass lead to Arbitrary Code Execution in Apache Airflow")
                                                .setDescription(
                                                        "An attacker can use a default DAG to execute arbitrary code on"
                                                                + " the server hosting the apache airflow application.")
                                                .setRecommendation(
                                                        "update to higher versions or Change default value for [webserver] secret_key config"))
                                .build());
    }

    @Test
    public void detect_withCallbackServer_butNoCallback_returnsEmpty() throws IOException {
        startMockWebServer();
        NetworkService targetNetworkService =
                NetworkService.newBuilder()
                        .setNetworkEndpoint(
                                forHostnameAndPort(mockTargetService.getHostName(), mockTargetService.getPort()))
                        .addSupportedHttpMethods("POST")
                        .build();
        TargetInfo targetInfo =
                TargetInfo.newBuilder()
                        .addNetworkEndpoints(targetNetworkService.getNetworkEndpoint())
                        .build();

        DetectionReportList detectionReports =
                detector.detect(targetInfo, ImmutableList.of(targetNetworkService));

        assertThat(detectionReports.getDetectionReportsList()).isEmpty();
    }

    @Test
    public void detect_withoutCallbackServer_returnsEmpty() throws IOException {
        NetworkService targetNetworkService =
                NetworkService.newBuilder()
                        .setNetworkEndpoint(
                                forHostnameAndPort(mockTargetService.getHostName(), mockTargetService.getPort()))
                        .addSupportedHttpMethods("POST")
                        .build();
        TargetInfo targetInfo =
                TargetInfo.newBuilder()
                        .addNetworkEndpoints(targetNetworkService.getNetworkEndpoint())
                        .build();
        Guice.createInjector(
                        new FakeUtcClockModule(fakeUtcClock),
                        new HttpClientModule.Builder().build(),
                        FakePayloadGeneratorModule.builder().build(),
                        new Cve202017526DetectorModule())
                .injectMembers(this);

        DetectionReportList detectionReports =
                detector.detect(targetInfo, ImmutableList.of(targetNetworkService));

        assertThat(detectionReports.getDetectionReportsList()).isEmpty();
    }

    private void startMockWebServer()
            throws IOException {
        final Dispatcher dispatcher = new Dispatcher() {
            final MockResponse unauthorizedResponse = new MockResponse().setResponseCode(401).setBody("You are not authenticated. "
                    + "Please see https://www.mlflow.org/docs/latest/auth/index.html#authenticating-to-mlflow "
                    + "on how to authenticate");

            @Override
            public MockResponse dispatch(RecordedRequest request) {
                switch (request.getPath()) {
                    case "/admin/":
                        return new MockResponse().setResponseCode(200).addHeader("Set-Cookie: session=aaaaaa")
                                .setBody("<title>Airflow - DAGs</title> \n var CSRF = \"bbbbbb\"");
                    case "/admin/airflow/paused?is_paused=true&dag_id=example_trigger_target_dag":
                        if (Objects.requireNonNull(request.getHeaders().get("X-CSRFToken")).equals("bbbbbb")
                                && Objects.requireNonNull(request.getHeaders().get("Cookie")).equals("session=aaaaaa")) {
                            return new MockResponse().setResponseCode(200);
                        }
                    case "/admin/airflow/trigger?dag_id=example_trigger_target_dag&origin=%2Fadmin%2Fairflow%2Ftree%3Fdag_id%3Dexample_trigger_target_dag":
                        if (Objects.requireNonNull(request.getHeaders().get("X-CSRFToken")).equals("bbbbbb")
                                && Objects.requireNonNull(request.getHeaders().get("Cookie")).equals("session=aaaaaa")
                                && request.getBody().toString().contains("dag_id=example_trigger_target_dag&origin=")
                        ) {
                            return new MockResponse().setResponseCode(200);
                        }
                }
                return null;
            }
        };
        mockTargetService.setDispatcher(dispatcher);
        mockTargetService.start();
        mockTargetService.url("/");
    }
}