package com.allwage.clockin.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

/**
 * Stub implementation of WhatsAppClient for the assessment.
 *
 * This logs messages instead of actually sending them via WhatsApp.
 * Treat this as if messages are being sent - your code should call
 * this service when confirmations need to be sent.
 */
@Service
public class WhatsAppClientStub implements WhatsAppClient {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppClientStub.class);

    @Override
    public boolean sendMessage(@NonNull String phoneNumber, @NonNull String message) {
        log.info("📱 WhatsApp message to {}: {}", phoneNumber, message);
        return true;
    }
}
