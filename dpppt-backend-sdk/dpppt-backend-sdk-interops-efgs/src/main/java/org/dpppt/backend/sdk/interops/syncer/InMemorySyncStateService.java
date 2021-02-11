package org.dpppt.backend.sdk.interops.syncer;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public class InMemorySyncStateService implements SyncStateService {

	private Map<LocalDate, Integer> nextDownloadBatchNumsMap;
	private Long lastUploadKeyBundleTag = null;

	public InMemorySyncStateService() {
		super();
		nextDownloadBatchNumsMap = new HashMap<>();
	}

	public Long getLastUploadKeyBundleTag() {
		return lastUploadKeyBundleTag;
	}

	public void setLastUploadKeyBundleTag(Long lastUploadKeyBundleTag) {
		this.lastUploadKeyBundleTag = lastUploadKeyBundleTag;
	}

	@Override
	public Integer getNextDownloadBatchNum(LocalDate dayDate) {
		return nextDownloadBatchNumsMap.get(dayDate);
	}

	@Override
	public void setNextDownloadBatchNum(LocalDate dayDate, Integer batchNum) {
		this.nextDownloadBatchNumsMap.put(dayDate, batchNum);		
	}

}
