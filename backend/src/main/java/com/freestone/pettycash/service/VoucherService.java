package com.freestone.pettycash.service;

import com.freestone.pettycash.model.PettyCashTransaction;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Service to generate PDF vouchers dynamically (on-demand) using OpenPDF.
 */
@Service
public class VoucherService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss");

    public byte[] generateTransactionVoucher(PettyCashTransaction tx) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A5.rotate()); // A5 in landscape is perfect for cash receipt vouchers

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            // Set Metadata
            document.addTitle("Petty Cash Voucher - " + tx.getTransactionNo());
            document.addSubject("Voucher for transaction " + tx.getTransactionNo());

            // Font configurations
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Font.BOLD);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.BOLD);
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.NORMAL);
            Font regularFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Font.NORMAL);
            Font signFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Font.NORMAL);

            // Title
            Paragraph title = new Paragraph("PETTY CASH VOUCHER", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(15);
            document.add(title);

            // Main Info Table
            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setSpacingBefore(10);
            table.setSpacingAfter(20);

            // Left Side: Tx details
            PdfPCell leftCell = new PdfPCell();
            leftCell.setBorder(Rectangle.NO_BORDER);
            leftCell.addElement(new Paragraph("Voucher No: " + tx.getTransactionNo(), headerFont));
            leftCell.addElement(new Paragraph("Date: " + tx.getDate().format(DATE_FORMATTER), regularFont));
            leftCell.addElement(new Paragraph("Type: " + tx.getType().name(), regularFont));
            if (tx.getCategory() != null) {
                leftCell.addElement(new Paragraph("Category: " + tx.getCategory().getName(), regularFont));
            }
            if (tx.getSubcategory() != null) {
                leftCell.addElement(new Paragraph("Subcategory: " + tx.getSubcategory().getName(), regularFont));
            }
            table.addCell(leftCell);

            // Right Side: Payer/Payee details
            PdfPCell rightCell = new PdfPCell();
            rightCell.setBorder(Rectangle.NO_BORDER);
            rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            rightCell.addElement(new Paragraph("Payer: " + tx.getPayer(), regularFont));
            if (tx.getPayee() != null && !tx.getPayee().isBlank()) {
                rightCell.addElement(new Paragraph("Payee: " + tx.getPayee(), regularFont));
            }
            rightCell.addElement(new Paragraph("Status: " + tx.getReceiptStatus().name(), regularFont));
            table.addCell(rightCell);

            document.add(table);

            // Divider line
            Paragraph divider = new Paragraph("----------------------------------------------------------------------------------------------------------------------------------", regularFont);
            divider.setSpacingAfter(10);
            document.add(divider);

            // Details Table (Amount and Description)
            PdfPTable detailsTable = new PdfPTable(new float[]{3, 1});
            detailsTable.setWidthPercentage(100);
            detailsTable.setSpacingAfter(20);

            PdfPCell descHeader = new PdfPCell(new Phrase("Particulars / Description", headerFont));
            descHeader.setPadding(8);
            detailsTable.addCell(descHeader);

            PdfPCell amtHeader = new PdfPCell(new Phrase("Amount (INR)", headerFont));
            amtHeader.setHorizontalAlignment(Element.ALIGN_RIGHT);
            amtHeader.setPadding(8);
            detailsTable.addCell(amtHeader);

            // Row 1
            PdfPCell descCell = new PdfPCell(new Phrase(tx.getDescription(), regularFont));
            descCell.setPadding(8);
            detailsTable.addCell(descCell);

            PdfPCell amtCell = new PdfPCell(new Phrase("Rs. " + formatAmount(tx.getAmount()), boldFont));
            amtCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            amtCell.setPadding(8);
            detailsTable.addCell(amtCell);

            document.add(detailsTable);

            // Signature Blocks Table
            PdfPTable signTable = new PdfPTable(3);
            signTable.setWidthPercentage(100);
            signTable.setSpacingBefore(30);

            PdfPCell pCell = new PdfPCell(new Phrase("________________________\nPrepared By\n(" + tx.getPayer() + ")", signFont));
            pCell.setBorder(Rectangle.NO_BORDER);
            pCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            signTable.addCell(pCell);

            String payeeName = (tx.getPayee() != null && !tx.getPayee().isBlank()) ? tx.getPayee() : "Cash Box";
            PdfPCell rCell = new PdfPCell(new Phrase("________________________\nReceived By\n(" + payeeName + ")", signFont));
            rCell.setBorder(Rectangle.NO_BORDER);
            rCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            signTable.addCell(rCell);

            PdfPCell aCell = new PdfPCell(new Phrase("________________________\nApproved By\n(Authorized Sign)", signFont));
            aCell.setBorder(Rectangle.NO_BORDER);
            aCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            signTable.addCell(aCell);

            document.add(signTable);

            // Timestamp footer
            Paragraph footer = new Paragraph("\nGenerated on: " + LocalDateTime.now().format(DATE_TIME_FORMATTER), signFont);
            footer.setAlignment(Element.ALIGN_RIGHT);
            document.add(footer);

        } catch (DocumentException e) {
            throw new RuntimeException("Error writing to PDF document", e);
        } finally {
            document.close();
        }

        return out.toByteArray();
    }

    private String formatAmount(BigDecimal amount) {
        return amount.setScale(2, BigDecimal.ROUND_HALF_UP).toString();
    }
}
