package org.factor_investing.quant_strategy.service;

import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Arrays;

@Service
public class WhatsAppService {

    @Value("${twilio.whatsapp.from}")
    private String fromWhatsAppNumber;

    public String sendWhatsAppMessage(String toNumber, String messageBody) {
        try {
            // Clean and format the number
            String cleanNumber = toNumber.trim()
                    .replace("whatsapp:", "")
                    .replace("whatsapp=", "")
                    .replace(" ", "")
                    .replace("-", "");

            // Add + if missing
            if (!cleanNumber.startsWith("+")) {
                cleanNumber = "+" + cleanNumber;
            }

            // Create the WhatsApp number format
            String formattedTo = "whatsapp:" + cleanNumber;

            System.out.println("Sending WhatsApp message:");
            System.out.println("To: " + formattedTo);
            System.out.println("From: " + fromWhatsAppNumber);
            System.out.println("Message: " + messageBody);

            Message message = Message.creator(
                    new PhoneNumber(formattedTo),
                    new PhoneNumber(fromWhatsAppNumber),
                    messageBody
            ).create();

            System.out.println("✓ Message sent successfully!");
            System.out.println("SID: " + message.getSid());
            System.out.println("Status: " + message.getStatus());

            return "Message sent successfully! SID: " + message.getSid();

        } catch (Exception e) {
            System.err.println("✗ Error sending message: " + e.getMessage());
            e.printStackTrace();
            return "Failed to send message: " + e.getMessage();
        }
    }

    public String sendWhatsAppMessageWithMedia(String toNumber, String messageBody, String mediaUrl) {
        try {
            String cleanNumber = toNumber.trim()
                    .replace("whatsapp:", "")
                    .replace("whatsapp=", "")
                    .replace(" ", "")
                    .replace("-", "");

            if (!cleanNumber.startsWith("+")) {
                cleanNumber = "+" + cleanNumber;
            }

            String formattedTo = "whatsapp:" + cleanNumber;

            Message message = Message.creator(
                            new PhoneNumber(formattedTo),
                            new PhoneNumber(fromWhatsAppNumber),
                            messageBody
                    )
                    .setMediaUrl(Arrays.asList(URI.create(mediaUrl)))
                    .create();

            return "Message with media sent! SID: " + message.getSid();

        } catch (Exception e) {
            e.printStackTrace();
            return "Failed to send message: " + e.getMessage();
        }
    }
}