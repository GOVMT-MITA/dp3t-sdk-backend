/*
 * Copyright (c) 2020 Ubique Innovation AG <https://www.ubique.ch>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package org.dpppt.backend.sdk.interops.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import javax.net.ssl.SSLContext;
import javax.sql.DataSource;

import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContexts;
import org.dpppt.backend.sdk.data.gaen.GAENDataService;
import org.dpppt.backend.sdk.data.gaen.JDBCGAENDataServiceImpl;
import org.dpppt.backend.sdk.interops.batchsigning.SignatureGenerator;
import org.dpppt.backend.sdk.interops.syncer.EfgsSyncer;
import org.dpppt.backend.sdk.interops.utils.LoggingRequestInterceptor;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.protobuf.ProtobufHttpMessageConverter;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.IntervalTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableScheduling
public abstract class WSBaseConfig implements SchedulingConfigurer, WebMvcConfigurer {

	@Value("${ws.retentiondays: 14}")
	int retentionDays;

	@Value("${ws.exposedlist.releaseBucketDuration: 7200000}")
	long releaseBucketDuration;

	@Value("${ws.app.gaen.timeskew:PT2h}")
	Duration timeSkew;

	@Value("${ws.origin.country}")
	String originCountry;

	@Value("${ws.international.countries:}")
	List<String> otherCountries;

	@Value("${ws.interops.efgs.baseurl:}")
	String efgsBaseUrl;

	@Value("${ws.interops.efgs.signature.keystore:}")
	private String signKeystore;

	@Value("${ws.interops.efgs.signature.password:}")
	public String signKeystorePass;

	@Value("${ws.interops.efgs.tls.keystore:}")
	private String tlsKeystore;

	@Value("${ws.interops.efgs.tls.password:}")
	public String tlsKeystorePass;

	@Value("${ws.interops.efgs.tls.truststore:}")
	private String tlsTruststore;
	
	@Value("${ws.interops.efgs.maxage: 2}")
	int efgsMaxAgeDays;
	
	@Value("${ws.interops.efgs.maxage: 8000}")
	long efgsMaxDownloadKeys;

	protected final Logger logger = LoggerFactory.getLogger(getClass());

	public abstract DataSource dataSource();

	public abstract Flyway flyway();

	public abstract String getDbType();

	@Bean
	public GAENDataService gaenDataService() {
		return new JDBCGAENDataServiceImpl(getDbType(), dataSource(), Duration.ofMillis(releaseBucketDuration),
				timeSkew, originCountry, otherCountries);
	}

	@Bean
	public EfgsSyncer efgsSyncer(GAENDataService gaenDataService, RestTemplate restTemplate, SignatureGenerator signatureGenerator) throws Exception {
		return new EfgsSyncer(efgsBaseUrl, retentionDays, efgsMaxAgeDays, efgsMaxDownloadKeys, Duration.ofMillis(releaseBucketDuration), gaenDataService, restTemplate, signatureGenerator);
	}

	@Autowired
	@Lazy
	EfgsSyncer efgsSyncer;

	@Override
	public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
		taskRegistrar.addFixedRateTask(new IntervalTask(() -> {
			efgsSyncer.sync();
		}, 20000));
	}

	private static final int CONNECT_TIMEOUT = 20000;
	private static final int SOCKET_TIMEOUT = 20000;

	@Bean
	ProtobufHttpMessageConverter protobufHttpMessageConverter() {
		ProtobufHttpMessageConverter hmc = new ProtobufHttpMessageConverter();
		return hmc;
    }
	
	@Bean
	public RestTemplate restTemplate(RestTemplateBuilder builder, ProtobufHttpMessageConverter hmc) throws Exception {
		/*char[] password = tlsKeystorePass.toCharArray();

		SSLContext sslContext = SSLContextBuilder.create()
				.setProtocol("TLS")
				.loadKeyMaterial(keyStore(tlsKeystore, password), password)
				.loadTrustMaterial(null, new TrustSelfSignedStrategy()).build();*/

		Supplier<ClientHttpRequestFactory> s1 = () -> new HttpComponentsClientHttpRequestFactory(
				createHttpClient());
		
		RestTemplate rt = builder
				.requestFactory(s1)
				.messageConverters(hmc)
				.build();
		
		List<ClientHttpRequestInterceptor> interceptors = rt.getInterceptors();
		interceptors.add(new ClientHttpRequestInterceptor() {

			@Override
			public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
					throws IOException {
				ClientHttpResponse response = execution.execute(request, body);				
		        response.getHeaders().set("Content-Type", "application/x-protobuf");
		        return response;			}
			
		});
		interceptors.add(new LoggingRequestInterceptor());
		rt.setInterceptors(interceptors);
		return rt;

	}
	
	private HttpClient createHttpClient() {
		char[] password = tlsKeystorePass.toCharArray();
        final SSLContext sslContext;
        try {
            sslContext = SSLContexts.custom()
                .loadKeyMaterial(keyStore(tlsKeystore, password), password, (aliases, socket) -> "covidalert")
                .loadTrustMaterial(null, (x509Certificates, s) -> false)
                .build();
        } catch (Exception e) {
            throw new IllegalStateException("Error loading key or trust material", e);
        }
        final SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(
            sslContext,
            new String[] { "TLSv1.3" },
            null,
            SSLConnectionSocketFactory.getDefaultHostnameVerifier());
        final Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
            .register("http", PlainConnectionSocketFactory.getSocketFactory())
            .register("https", sslSocketFactory)
            .build();
        final PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(registry);
        connectionManager.setMaxTotal(30);
        connectionManager.setDefaultMaxPerRoute(20);
        return HttpClients.custom()
        		.disableCookieManagement()
        		.setSSLSocketFactory(sslSocketFactory)
        		.setConnectionManager(connectionManager)
        		.setDefaultRequestConfig(RequestConfig.custom()
        				.setConnectTimeout(CONNECT_TIMEOUT)
    					.setSocketTimeout(SOCKET_TIMEOUT)
    					.build())
        		.setUserAgent("dp3t-interop")
        		.build();
    }

	private CloseableHttpClient httpClient(SSLContext sslContext) {
		PoolingHttpClientConnectionManager manager = new PoolingHttpClientConnectionManager();
		manager.setDefaultMaxPerRoute(20);
		manager.setMaxTotal(30);

		// Create HttpClientBuilder using system properties including proxy settings
		HttpClientBuilder builder = HttpClients.custom()
				.setSSLContext(sslContext)
				.useSystemProperties()
				.setUserAgent("dp3t-interop");
		
		builder.setConnectionManager(manager)
			.disableCookieManagement()
			.setDefaultRequestConfig(
				RequestConfig.custom()
					.setConnectTimeout(CONNECT_TIMEOUT)
					.setSocketTimeout(SOCKET_TIMEOUT)
					.build());
		
		return builder.build();
	}

	private KeyStore keyStore(String file, char[] password) throws Exception {
		KeyStore keyStore = KeyStore.getInstance("PKCS12");
		InputStream in = null;
		if (file.startsWith("classpath:/")) {
			in = new ClassPathResource(file.substring(11)).getInputStream();
		} else if (file.startsWith("file:/")) {
			in = new FileInputStream(file.substring(6));
		}

		keyStore.load(in, password);
		return keyStore;
	}
	
	@Bean
	public SignatureGenerator signatureGenerator() throws Exception {
		char[] password = signKeystorePass.toCharArray();
		KeyStore keystore = keyStore(signKeystore, password);
		X509Certificate certificate = (X509Certificate) keystore.getCertificate("covidalert");
		PrivateKey privateKey = (PrivateKey) keystore.getKey("covidalert", password);
		return new SignatureGenerator(certificate, privateKey);
		
	}
}