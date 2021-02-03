package org.dpppt.backend.sdk.interops.syncer;

import java.time.LocalDate;

public interface SyncStateService {

	String getLastDownloadedBatchTag(LocalDate dayDate);

	void setLastDownloadedBatchTag(LocalDate dayDate, String batchTag);

	Long getLastUploadKeyBundleTag();
	
	void setLastUploadKeyBundleTag(Long lastUploadKeyBundleTag);

}
