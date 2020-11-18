package org.dpppt.backend.sdk.interops.utils;

import java.io.IOException;
import java.io.InputStreamReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

public class ContentTypeCorrectionInterceptor implements ClientHttpRequestInterceptor {

  private static final Logger LOGGER = LoggerFactory.getLogger(ContentTypeCorrectionInterceptor.class);

	@Override
	public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
			throws IOException {
		ClientHttpResponse response = execution.execute(request, body);
		final ClientHttpResponse responseWrapper = new BufferingClientHttpResponseWrapper(response);
		if (responseWrapper.getHeaders().containsKey("Content-Type")) {
			String contentType = response.getHeaders().getContentType().toString();
			if (contentType.replace(' ', Character.MIN_VALUE).equals("application/protobuf;version=1.0")) {
				responseWrapper.getHeaders().set("Content-Type", "application/x-protobuf");					
			}					
		}
      return responseWrapper;			
  }

}
