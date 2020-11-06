package org.dpppt.backend.sdk.model.gaen;

import java.util.ArrayList;
import java.util.List;

public class GaenKeyWithRegions extends GaenKey {
	
	private List<String> regions;

	public List<String> getRegions() {
		return regions;
	}

	public void setRegions(List<String> regions) {
		this.regions = regions;
	}

	public GaenKeyWithRegions() {
		super();
		regions = new ArrayList<>();
	}

	public GaenKeyWithRegions(String keyData, Integer rollingStartNumber, Integer rollingPeriod, List<String> regions) {
		super(keyData, rollingStartNumber, rollingPeriod);
		this.regions = regions;
	}
	

	
}
