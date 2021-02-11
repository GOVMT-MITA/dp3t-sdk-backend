package org.dpppt.backend.sdk.interops.controller;

import java.time.LocalDate;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.dpppt.backend.sdk.interops.syncer.EfgsSyncer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
	private static final Logger logger = LoggerFactory.getLogger(NotifyMeController.class);

	public NotifyMeController(EfgsSyncer efgsSyncer, ExecutorService executor) {
		super();
		this.efgsSyncer = efgsSyncer;
		this.executor = executor;
	}
	
	@GetMapping(value = "/notify_me")
	public @ResponseBody ResponseEntity<Void> getCode(
			@RequestParam(required = true) String date,
			@RequestParam(required = true) String batchTag) {
		
		logger.info("Received callback notification for " + date + ", batch tag " + batchTag);
		
		Future<Long> numRecv = executor.submit(efgsSyncer.startDownload(LocalDate.parse(date), batchTag));
		
		logger.info("Callback handled");
		
		return ResponseEntity.ok().build();
		
	}	
	
}
