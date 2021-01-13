/*
 * Created by Malta Information Technology Agency
 * https://mita.gov.mt
 * Copyright (c) 2021. All rights reserved.
 */

CREATE TABLE t_exposed(
 pk_exposed_id SERIAL,
 `key` TEXT NOT NULL, 
 received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 onset DATE NOT NULL,
 app_source VARCHAR(50) NOT NULL
);

-- Add keys for table t_exposed

ALTER TABLE t_exposed ADD CONSTRAINT PK_t_exposed PRIMARY KEY (pk_exposed_id);

ALTER TABLE t_exposed ADD CONSTRAINT `key` UNIQUE (`key`(767));
