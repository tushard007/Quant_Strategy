package org.factor_investing.quant_strategy.controller;

import org.factor_investing.quant_strategy.service.WhatsAppService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/whatsapp")
public class WhatsAppController {

    @Autowired
    private WhatsAppService whatsAppService;

    @PostMapping("/send")
    public ResponseEntity<String> sendMessage(
            @RequestParam String toNumber,
            @RequestParam String message) {

        // Validate and format phone number
        String formattedNumber = formatPhoneNumber(toNumber);

        if (!isValidPhoneNumber(formattedNumber)) {
            return ResponseEntity.badRequest()
                    .body("Invalid phone number format. Use E.164 format: +919876543210");
        }

        String result = whatsAppService.sendWhatsAppMessage(formattedNumber, message);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/send-with-media")
    public ResponseEntity<String> sendMessageWithMedia(
            @RequestParam String toNumber,
            @RequestParam String message,
            @RequestParam String mediaUrl) {

        String formattedNumber = formatPhoneNumber(toNumber);

        if (!isValidPhoneNumber(formattedNumber)) {
            return ResponseEntity.badRequest()
                    .body("Invalid phone number format. Use E.164 format: +919876543210");
        }

        String result = whatsAppService.sendWhatsAppMessageWithMedia(
                formattedNumber, message, mediaUrl);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/test")
    public ResponseEntity<String> testSend() {
        // Test with your number - MUST include +
        String result = whatsAppService.sendWhatsAppMessage(
                "+919595520224",  // ← Note the + sign
                "Hello! This is a test message from Spring Boot."
        );
        return ResponseEntity.ok(result);
    }

    // Helper method to format phone number
    private String formatPhoneNumber(String number) {
        String cleaned = number.trim()
                .replace(" ", "")
                .replace("-", "")
                .replace("(", "")
                .replace(")", "");

        // Add + if missing
        if (!cleaned.startsWith("+")) {
            cleaned = "+" + cleaned;
        }

        return cleaned;
    }

    // Basic validation
    private boolean isValidPhoneNumber(String number) {
        // Must start with + and be 10-15 digits
        return number.matches("^\\+[1-9]\\d{9,14}$");
    }
}