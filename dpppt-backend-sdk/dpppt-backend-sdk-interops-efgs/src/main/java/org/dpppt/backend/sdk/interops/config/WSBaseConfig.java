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
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.net.ssl.SSLContext;
import javax.sql.DataSource;

import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContexts;
import org.dpppt.backend.sdk.data.gaen.GAENDataService;
import org.dpppt.backend.sdk.data.gaen.JDBCGAENDataServiceImpl;
import org.dpppt.backend.sdk.interops.batchsigning.SignatureGenerator;
import org.dpppt.backend.sdk.interops.controller.NotifyMeController;
import org.dpppt.backend.sdk.interops.syncer.EfgsSyncer;
import org.dpppt.backend.sdk.interops.syncer.InMemorySyncStateService;
import org.dpppt.backend.sdk.interops.syncer.SyncStateService;
import org.dpppt.backend.sdk.interops.utils.MappingJackson2HttpMessageConverter;
import org.dpppt.backend.sdk.interops.utils.ProtobufHttpMessageConverter;
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
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
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

	@Value("${ws.interops.efgs.signature.alias:}")
	public String signCertAlias;

	@Value("${ws.interops.efgs.tls.keystore:}")
	private String tlsKeystore;

	@Value("${ws.interops.efgs.tls.password:}")
	public String tlsKeystorePass;

	@Value("${ws.interops.efgs.tls.alias:}")
	public String tlsCertAlias;

	@Value("${ws.interops.efgs.tls.truststore:}")
	private String tlsTruststore;

	@Value("${ws.interops.efgs.maxage:2}")
	int efgsMaxAgeDays;
	
	@Value("${ws.interops.efgs.download.notbefore:1970-01-01}")
	String efgsDownloadNotBefore;

	@Value("${ws.interops.efgs.download.maxkeys:10000}")
	long efgsMaxDownloadKeys;

	@Value("${ws.interops.efgs.upload.maxkeys:10000}")
	long efgsMaxUploadKeys;

	@Value("${ws.interops.efgs.upload.minkeys:150}")
	int efgsMinUploadKeys;

	@Value("${ws.interops.efgs.fakekeysenabled:true}")
	boolean fakekeysenabled;

	@Value("${ws.app.gaen.key_size:16}")
	int gaenKeySizeBytes;

	@Value("${ws.interops.efgs.callback.id:}")
	String efgsCallbackId;

	@Value("${ws.interops.efgs.callback.url:}")
	String efgsCallbackUrl;

	@Value("${ws.interops.efgs.protobuf.version:1.0}")
	String efgsProtobufVersion;

	@Value("${ws.interops.efgs.json.version:1.0}")
	String efgsJsonVersion;
	
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
	public SyncStateService syncStateService() {
		return new InMemorySyncStateService();
	}
	
	@Bean
	public EfgsSyncer efgsSyncer(GAENDataService gaenDataService, RestTemplate restTemplate, SignatureGenerator signatureGenerator, SyncStateService syncStateService) throws Exception {
		EfgsSyncer syncer = new EfgsSyncer(efgsBaseUrl, 
				retentionDays, 
				efgsMaxAgeDays, 
				efgsMaxDownloadKeys,
				LocalDate.parse(efgsDownloadNotBefore),
				efgsMaxUploadKeys, 
				fakekeysenabled,
				efgsMinUploadKeys,
				gaenKeySizeBytes,
				originCountry, 
				otherCountries,
				Duration.ofMillis(releaseBucketDuration), 
				gaenDataService, 
				restTemplate, 
				signatureGenerator,
				syncStateService,
				efgsCallbackId,
				efgsCallbackUrl);
		syncer.init();
		return syncer;
	}

	@Autowired
	@Lazy
	EfgsSyncer efgsSyncer;
	
	@Autowired
	ExecutorService executorService;

    
	@Override
	public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
		taskRegistrar.addFixedRateTask(new IntervalTask(() -> {
			executorService.submit(efgsSyncer.startSync());
		}, releaseBucketDuration));

	}

	private static final int CONNECT_TIMEOUT = 20000;
	private static final int SOCKET_TIMEOUT = 20000;

	@Bean
	ProtobufHttpMessageConverter protobufHttpMessageConverter() {
		ProtobufHttpMessageConverter hmc = new ProtobufHttpMessageConverter(efgsProtobufVersion, efgsJsonVersion);
		return hmc;
    }
	
	@Bean
	public RestTemplate restTemplate(RestTemplateBuilder builder, ProtobufHttpMessageConverter hmc) throws Exception {

		Supplier<ClientHttpRequestFactory> s1 = () -> new HttpComponentsClientHttpRequestFactory(
				createHttpClient());
		
		RestTemplate rt = builder
				.requestFactory(s1)
				.messageConverters(hmc, new ByteArrayHttpMessageConverter(), new MappingJackson2HttpMessageConverter())
				.build();
		
		List<ClientHttpRequestInterceptor> interceptors = rt.getInterceptors();
		//interceptors.add(new LoggingRequestInterceptor());
		rt.setInterceptors(interceptors);
		return rt;

	}
	
	private void loadTrustAnchors(KeyStore keyStore) throws Exception {
	    PKIXParameters params = new PKIXParameters(keyStore);

	    Set<TrustAnchor> trustAnchors = params.getTrustAnchors();
	    List<Certificate> certificates = trustAnchors.stream()
	      .map(TrustAnchor::getTrustedCert)
	      .collect(Collectors.toList());

	    certificates.forEach(c -> {
	    	System.out.println(c.toString());
	    });
	}
	
	private HttpClient createHttpClient() {
		char[] password = tlsKeystorePass.toCharArray();
        final SSLContext sslContext;
        try {
            sslContext = SSLContexts.custom()
                .loadKeyMaterial(keyStore(tlsKeystore, password), password, (aliases, socket) -> tlsCertAlias)
                .build();
            
        } catch (Exception e) {
            throw new IllegalStateException("Error loading key or trust material", e);
        }
        final SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(
            sslContext,
            new String[] { "TLSv1.2", "TLSv1.3" },
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

	private KeyStore keyStore(String file, char[] password) throws Exception {
		KeyStore keyStore = file.endsWith(".jks") ? KeyStore.getInstance("JKS") : KeyStore.getInstance("PKCS12");
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
		X509Certificate certificate = (X509Certificate) keystore.getCertificate(signCertAlias);
		PrivateKey privateKey = (PrivateKey) keystore.getKey(signCertAlias, password);
		return new SignatureGenerator(certificate, privateKey);		
	}
	
	@Bean
	public ExecutorService executorService() {
		return Executors.newSingleThreadExecutor();
	};

	@Bean
	public NotifyMeController notifyMeController(EfgsSyncer efgsSyncer, ExecutorService executorService) {
		return new NotifyMeController(efgsSyncer, executorService);
	}
}
