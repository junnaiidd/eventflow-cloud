package com.example.eventflow.service;

import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.example.eventflow.repository.RegistrationRepository;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

/** Creates ticket IDs and QR codes. The QR code simply encodes the ticket ID; all validation is server-side. */
@Service
public class TicketService {

    /** 32 symbols without the look-alikes I, O, 0 and 1, so IDs can also be typed by hand. */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final Pattern FORMAT = Pattern.compile("^EF-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}$");

    private final SecureRandom random = new SecureRandom();
    private final RegistrationRepository registrations;

    public TicketService(RegistrationRepository registrations) {
        this.registrations = registrations;
    }

    /** Random, unguessable ID such as EF-7K2M-Q9XA-4PDW (60 bits of randomness), unique in the database. */
    public String generateUniqueTicketId() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String id = "EF-" + group() + "-" + group() + "-" + group();
            if (!registrations.existsByTicketId(id)) {
                return id;
            }
        }
        throw new IllegalStateException("Could not generate a unique ticket id");
    }

    private String group() {
        StringBuilder sb = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /** Trims and upper-cases scanned/typed input. Never returns null. */
    public static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    }

    /** True if the text has the shape of a ticket ID. A malformed code is rejected before touching the database. */
    public static boolean isWellFormed(String ticketId) {
        return ticketId != null && FORMAT.matcher(ticketId).matches();
    }

    /** PNG image of a QR code for the given text. */
    public byte[] qrPng(String content, int sizePx) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx,
                    Map.of(EncodeHintType.MARGIN, 1, EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Could not generate QR code", e);
        }
    }
}
