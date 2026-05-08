package com.allwage.clockin.service;

import org.springframework.lang.NonNull;

/**
 * Interface for sending WhatsApp messages.
 *
 * The actual WhatsApp Business API integration is already done elsewhere.
 * This interface is provided so you can send messages - just call it.
 *
 * For this assessment, use the provided stub implementation which logs
 * messages instead of sending them.
 */
public interface WhatsAppClient {

    /**
     * Send a WhatsApp message to a phone number.
     *
     * @param phoneNumber The recipient's phone number (international format)
     * @param message     The message to send
     * @return true if the message was sent successfully
     */
    boolean sendMessage(@NonNull String phoneNumber, @NonNull String message);
}
