package com.example.eventflow.service.pdf;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

/** Builds the downloadable PDF version of an e-ticket (details + QR code). */
@Component
public class TicketPdfGenerator {

    public record TicketData(String participantName, String eventName, LocalDate eventDate, LocalTime eventTime,
            String venue, String ticketId, String status, byte[] qrPng) {
    }

    private static final Color BRAND = new Color(79, 70, 229);
    private static final Color MUTED = new Color(100, 116, 139);

    public byte[] generate(TicketData d) {
        try {
            Document doc = new Document(PageSize.A5, 36, 36, 36, 36);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfWriter.getInstance(doc, out);
            doc.open();

            Paragraph brand = new Paragraph("EventFlow", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, BRAND));
            brand.setSpacingAfter(2);
            doc.add(brand);
            Paragraph sub = new Paragraph("Event ticket", FontFactory.getFont(FontFactory.HELVETICA, 10, MUTED));
            sub.setSpacingAfter(16);
            doc.add(sub);

            Paragraph title = new Paragraph(d.eventName(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 17, Color.BLACK));
            title.setSpacingAfter(14);
            doc.add(title);

            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setSpacingAfter(14);
            addRow(table, "PARTICIPANT", d.participantName());
            addRow(table, "STATUS", d.status());
            addRow(table, "DATE", d.eventDate() == null ? "-" : d.eventDate().format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)));
            addRow(table, "TIME", d.eventTime() == null ? "-" : d.eventTime().format(DateTimeFormatter.ofPattern("HH:mm")));
            addRow(table, "VENUE", d.venue());
            addRow(table, "TICKET ID", d.ticketId());
            doc.add(table);

            Image qr = Image.getInstance(d.qrPng());
            qr.scaleToFit(170, 170);
            qr.setAlignment(Element.ALIGN_CENTER);
            doc.add(qr);

            Paragraph hint = new Paragraph("Show this QR code at the entrance. Each ticket can be used once.",
                    FontFactory.getFont(FontFactory.HELVETICA, 9, MUTED));
            hint.setAlignment(Element.ALIGN_CENTER);
            hint.setSpacingBefore(8);
            doc.add(hint);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Could not generate ticket PDF", e);
        }
    }

    private static void addRow(PdfPTable table, String label, String value) {
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA, 8, MUTED);
        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.BLACK);
        PdfPCell l = new PdfPCell(new Paragraph(label, labelFont));
        l.setBorder(Rectangle.NO_BORDER);
        l.setPaddingBottom(6);
        PdfPCell v = new PdfPCell(new Paragraph(value == null ? "-" : value, valueFont));
        v.setBorder(Rectangle.NO_BORDER);
        v.setPaddingBottom(6);
        table.addCell(l);
        table.addCell(v);
    }
}
