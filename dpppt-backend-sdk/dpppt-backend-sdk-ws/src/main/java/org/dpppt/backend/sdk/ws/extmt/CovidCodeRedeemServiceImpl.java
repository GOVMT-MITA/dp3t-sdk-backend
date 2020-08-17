package org.dpppt.backend.sdk.ws.extmt;

import java.nio.charset.Charset;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

public class CovidCodeRedeemServiceImpl implements CovidCodeRedeemService {

	private static final Log LOG = LogFactory.getLog(CovidCodeRedeemServiceImpl.class);
	
	private String redeemServiceUrl;
	private String redeemServiceUsername;
	private String redeemServicePassword;
	private boolean enabled;
	
	public boolean isEnabled() {
		return enabled;
	}

	@Value("${authz.redeem.service.enabled}")
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getRedeemServiceUsername() {
		return redeemServiceUsername;
	}

	@Value("${authz.redeem.service.username}")
	public void setRedeemServiceUsername(String redeemServiceUsername) {
		this.redeemServiceUsername = redeemServiceUsername;
	}

	public String getRedeemServicePassword() {
		return redeemServicePassword;
	}

	@Value("${authz.redeem.service.password}")
	public void setRedeemServicePassword(String redeemServicePassword) {
		this.redeemServicePassword = redeemServicePassword;
	}

	public String getRedeemServiceUrl() {
		return redeemServiceUrl;
	}

	@Value("${authz.redeem.service.url}")
	public void setRedeemServiceUrl(String redeemServiceUrl) {
		this.redeemServiceUrl = redeemServiceUrl;
	}

	@SuppressWarnings("serial")
	private HttpHeaders createHeaders(String username, String password){
		   return new HttpHeaders() {{
		         String auth = username + ":" + password;
		         byte[] encodedAuth = Base64.encodeBase64( 
		            auth.getBytes(Charset.forName("UTF8")) );
		         String authHeader = "Basic " + new String( encodedAuth );
		         set( "Authorization", authHeader );
		      }};
		}
	
	public boolean redeem(String covidCode) {
		
		if (!isEnabled()) return true;
		
		RestTemplate rest = new RestTemplate();
		try {
			ResponseEntity<Void> res = rest.exchange(getRedeemServiceUrl() + "/v1/codes/redeemed/" + covidCode, 
					HttpMethod.DELETE, 
					new HttpEntity<Void>(createHeaders(getRedeemServiceUsername(), getRedeemServicePassword())), Void.class);
			
			if (!res.getStatusCode().is2xxSuccessful() && !res.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
				LOG.error("CovidCode Redeem Service returned " + res.getStatusCodeValue());
				return false;
			}
			return true;
			
		} catch (Exception e) {
			LOG.error(e);
			return false;
		}
	}
	
}
