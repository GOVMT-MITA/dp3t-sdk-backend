package org.dpppt.backend.sdk.ws.controller;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.dpppt.backend.sdk.model.gaen.GaenKeyInternal;

public class GaenKeyInternalResponse {
	
	private String keyData;
	private Integer rollingStartNumber;
	private Integer rollingPeriod;
	private Integer transmissionRiskLevel = 0;
	private List<String> countries;
	private int daysSinceOnsetOfSymptoms;
	private String origin;
	private String reportType;
	private Integer fake = 0;
	private String receivedAt;
	private String expiresAt;
	private String efgsBatchTag;

	public String getEfgsBatchTag() {
		return efgsBatchTag;
	}

	public void setEfgsBatchTag(String efgsBatchTag) {
		this.efgsBatchTag = efgsBatchTag;
	}

	public GaenKeyInternalResponse(GaenKeyInternal gaenKey) {
		super();
		this.setKeyData(gaenKey.getKeyData());
		this.setRollingStartNumber(gaenKey.getRollingStartNumber());
		this.setRollingPeriod(gaenKey.getRollingPeriod());
		this.setTransmissionRiskLevel(gaenKey.getTransmissionRiskLevel());
		this.setCountries(gaenKey.getCountries());
		this.setDaysSinceOnsetOfSymptoms(gaenKey.getDaysSinceOnsetOfSymptoms());
		this.setOrigin(gaenKey.getOrigin());
		this.setReportType(gaenKey.getReportType());
		this.setFake(gaenKey.getFake());
		this.setReceivedAt(DateTimeFormatter.ISO_INSTANT.format(gaenKey.getReceivedAt()));
		this.setExpiresAt(DateTimeFormatter.ISO_INSTANT.format(gaenKey.getExpiresAt()));
		this.setEfgsBatchTag(gaenKey.getEfgsBatchTag());
	}

	public String getReceivedAt() {
		return receivedAt;
	}

	public void setReceivedAt(String receivedAt) {
		this.receivedAt = receivedAt;
	}

	public String getExpiresAt() {
		return expiresAt;
	}

	public void setExpiresAt(String expiresAt) {
		this.expiresAt = expiresAt;
	}

	public String getKeyData() {
		return keyData;
	}

	public void setKeyData(String keyData) {
		this.keyData = keyData;
	}

	public Integer getRollingStartNumber() {
		return rollingStartNumber;
	}

	public void setRollingStartNumber(Integer rollingStartNumber) {
		this.rollingStartNumber = rollingStartNumber;
	}

	public Integer getRollingPeriod() {
		return rollingPeriod;
	}

	public void setRollingPeriod(Integer rollingPeriod) {
		this.rollingPeriod = rollingPeriod;
	}

	public Integer getTransmissionRiskLevel() {
		return transmissionRiskLevel;
	}

	public void setTransmissionRiskLevel(Integer transmissionRiskLevel) {
		this.transmissionRiskLevel = transmissionRiskLevel;
	}

	public String getOrigin() {
		return origin;
	}

	public void setOrigin(String origin) {
		this.origin = origin;
	}

	public String getReportType() {
		return reportType;
	}

	public void setReportType(String reportType) {
		this.reportType = reportType;
	}

	public int getDaysSinceOnsetOfSymptoms() {
		return daysSinceOnsetOfSymptoms;
	}

	public void setDaysSinceOnsetOfSymptoms(int daysSinceOnsetOfSymptoms) {
		this.daysSinceOnsetOfSymptoms = daysSinceOnsetOfSymptoms;
	}

	public GaenKeyInternalResponse() {
		super();
		countries = new ArrayList<>();
	}
	
	public List<String> getCountries() {
		return countries;
	}

	public void setCountries(List<String> countries) {
		this.countries = countries;
	}

	public Integer getFake() {
		return fake;
	}

	public void setFake(Integer fake) {
		this.fake = fake;
	}
	
	

	
}
