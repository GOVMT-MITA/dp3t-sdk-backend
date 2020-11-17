package org.dpppt.backend.sdk.interops.controller;

import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.dpppt.backend.sdk.interops.syncer.EfgsSyncer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class NotifyMeController {

	private final EfgsSyncer efgsSyncer;
	private final ExecutorService executor;

	public NotifyMeController(EfgsSyncer efgsSyncer) {
		super();
		this.efgsSyncer = efgsSyncer;
		executor = Executors.newSingleThreadExecutor();
	}
	
	@GetMapping(value = "/notify_me", 
			produces="application/json")
	public @ResponseBody ResponseEntity<Void> getCode(
			@RequestParam(required = true) String date,
			@RequestParam(required = true) String batchTag) {
		
		Future<Long> numRecv = executor.submit(efgsSyncer.startDownload(LocalDate.parse(date), batchTag));
		
		return ResponseEntity.ok().build();
		
	}	
	
}
