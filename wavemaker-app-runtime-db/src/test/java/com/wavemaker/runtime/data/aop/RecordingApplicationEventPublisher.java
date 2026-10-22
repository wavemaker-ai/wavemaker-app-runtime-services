/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.aop;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;

/**
 * A plain {@link ApplicationEventPublisher} test double that just records every published event - there is no
 * Spring {@code ApplicationContext} anywhere in this test harness, so this stands in for one wherever a handler
 * needs an {@code @Autowired ApplicationEventPublisher} (set via {@code TestReflectionUtils.setField}, since the
 * field has no setter).
 */
public class RecordingApplicationEventPublisher implements ApplicationEventPublisher {

    private final List<Object> publishedEvents = new ArrayList<>();

    @Override
    public void publishEvent(final Object event) {
        publishedEvents.add(event);
    }

    public List<Object> getPublishedEvents() {
        return publishedEvents;
    }
}
