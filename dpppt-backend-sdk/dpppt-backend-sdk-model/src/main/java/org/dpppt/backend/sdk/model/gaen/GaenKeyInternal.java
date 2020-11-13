package org.dpppt.backend.sdk.model.gaen;

import java.util.ArrayList;
import java.util.List;

public class GaenKeyInternal {
	
	private String keyData;
	private Integer rollingStartNumber;
	private Integer rollingPeriod;
	private Integer transmissionRiskLevel = 0;
	private List<String> countries;
	private long daysSinceOnsetOfSymptoms;
	private String origin;
	private String reportType;
	private Integer fake = 0;
	
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

	public long getDaysSinceOnsetOfSymptoms() {
		return daysSinceOnsetOfSymptoms;
	}

	public void setDaysSinceOnsetOfSymptoms(long daysSinceOnsetOfSymptoms) {
		this.daysSinceOnsetOfSymptoms = daysSinceOnsetOfSymptoms;
	}

	public GaenKeyInternal() {
		super();
		countries = new ArrayList<>();
	}
	
	public GaenKeyInternal(GaenKey gaenKey) {
		super();
		this.setKeyData(gaenKey.getKeyData());
		this.setRollingStartNumber(gaenKey.getRollingStartNumber());
		this.setRollingPeriod(gaenKey.getRollingPeriod());
		this.setTransmissionRiskLevel(gaenKey.getTransmissionRiskLevel());		
	}
	
	public GaenKey asGaenKey() {
		GaenKey res = new GaenKey();
		res.setKeyData(this.getKeyData());
		res.setRollingStartNumber(this.getRollingStartNumber());
		res.setRollingPeriod(this.getRollingPeriod());
		res.setTransmissionRiskLevel(this.getTransmissionRiskLevel());
		res.setFake(this.getFake());
		return res;		
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
