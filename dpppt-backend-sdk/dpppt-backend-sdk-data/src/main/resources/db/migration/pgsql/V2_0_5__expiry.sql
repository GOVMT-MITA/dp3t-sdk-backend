/*
 * Created by Ubique Innovation AG
 * https://www.ubique.ch
 * Copyright (c) 2020. All rights reserved.
 */

alter table t_gaen_exposed add column expires_at Timestamp with time zone DEFAULT now() NOT NULL;

alter table t_debug_gaen_exposed add column expires_at Timestamp with time zone DEFAULT now() NOT NULL;
