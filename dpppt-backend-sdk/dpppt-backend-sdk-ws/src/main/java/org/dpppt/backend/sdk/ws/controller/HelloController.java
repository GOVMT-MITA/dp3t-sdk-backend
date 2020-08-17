/*
 * Copyright (c) 2020 Malta Information Technology Agency <https://mita.gov.mt>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package org.dpppt.backend.sdk.ws.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import ch.ubique.openapi.docannotations.Documentation;

@Controller
@RequestMapping("/")
public class HelloController {

	@CrossOrigin(origins = { "*" })
	@GetMapping(value = "")
	@Documentation(description = "Hello return", responses = {"200=>server live"})
	public @ResponseBody ResponseEntity<String> hello() {
		return ResponseEntity.ok().header("X-HELLO", "dp3t").body("Hello from DP3T WS");
	}

}
