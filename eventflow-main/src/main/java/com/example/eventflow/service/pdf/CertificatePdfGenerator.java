package com.example.eventflow.service.pdf;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.example.eventflow.model.Certificate;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfWriter;

/**
 * Draws the participation certificate (A4 landscape) from the details stored on the Certificate row, so a
 * regenerated PDF is identical to the original even if the event is renamed later.
 */
@Component
public class CertificatePdfGenerator {

    private static final Color BRAND = new Color(79, 70, 229);
    private static final Color DARK = new Color(30, 41, 59);
    private static final Color MUTED = new Color(100, 116, 139);
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    public byte[] generate(Certificate c) {
        try {
            var page = PageSize.A4.rotate();
            Document doc = new Document(page, 70, 70, 70, 60);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            doc.open();

            PdfContentByte canvas = writer.getDirectContentUnder();
            canvas.setColorStroke(BRAND);
            canvas.setLineWidth(3f);
            canvas.rectangle(28, 28, page.getWidth() - 56, page.getHeight() - 56);
            canvas.stroke();
            canvas.setLineWidth(0.8f);
            canvas.rectangle(38, 38, page.getWidth() - 76, page.getHeight() - 76);
            canvas.stroke();

            doc.add(centered("EVENTFLOW", FontFactory.HELVETICA_BOLD, 12, BRAND, 6));
            doc.add(centered("Certificate of Participation", FontFactory.TIMES_BOLD, 34, DARK, 22));
            doc.add(centered("This is to certify that", FontFactory.HELVETICA, 13, MUTED, 10));
            doc.add(centered(c.getParticipantName(), FontFactory.TIMES_BOLDITALIC, 32, BRAND, 10));
            doc.add(centered("has successfully attended", FontFactory.HELVETICA, 13, MUTED, 10));
            doc.add(centered(c.getEventName(), FontFactory.HELVETICA_BOLD, 21, DARK, 8));
            doc.add(centered("held on " + c.getEventDate().format(LONG_DATE), FontFactory.HELVETICA, 13, MUTED, 26));

            String issuer = c.getOrganizerName() == null || c.getOrganizerName().isBlank() ? "EventFlow" : c.getOrganizerName();
            doc.add(centered("Organised by " + issuer, FontFactory.HELVETICA_BOLD, 12, DARK, 22));

            LocalDate issued = c.getIssuedAt().atZone(ZoneId.of("Asia/Kolkata")).toLocalDate();
            doc.add(centered("Certificate ID: " + c.getCertificateCode() + "   |   Issued on " + issued.format(LONG_DATE),
                    FontFactory.HELVETICA, 9, MUTED, 0));

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Could not generate certificate PDF", e);
        }
    }

    private static Paragraph centered(String text, String fontName, float size, Color color, float spacingAfter) {
        Paragraph p = new Paragraph(text, FontFactory.getFont(fontName, size, color));
        p.setAlignment(Element.ALIGN_CENTER);
        p.setSpacingAfter(spacingAfter);
        return p;
    }
}
