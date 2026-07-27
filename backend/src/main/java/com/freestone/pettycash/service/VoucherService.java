package com.freestone.pettycash.service;

import com.freestone.pettycash.config.AppProperties;
import com.freestone.pettycash.model.PettyCashTransaction;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Service to generate single-page PDF vouchers dynamically (on-demand) using OpenPDF.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VoucherService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss");

    private final SignatureService signatureService;
    private final AppProperties appProperties;

    public byte[] generateTransactionVoucher(PettyCashTransaction tx) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // A5 landscape: 595 x 420 pt. Set 20pt margins for tight 1-page budget fit.
        Document document = new Document(PageSize.A5.rotate(), 20, 20, 15, 15);

        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.open();

            // Set Metadata
            document.addTitle("Petty Cash Voucher - " + tx.getTransactionNo());
            document.addSubject("Voucher for transaction " + tx.getTransactionNo());

            // Font configurations
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, Font.BOLD);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.BOLD);
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.NORMAL);
            Font regularFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Font.NORMAL);
            Font signFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL);

            // Title
            Paragraph title = new Paragraph("PETTY CASH VOUCHER", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(6);
            document.add(title);

            // Main Info Table
            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setSpacingBefore(4);
            table.setSpacingAfter(8);

            // Left Side: Tx details
            PdfPCell leftCell = new PdfPCell();
            leftCell.setBorder(Rectangle.NO_BORDER);
            leftCell.setPadding(2);
            leftCell.addElement(new Paragraph("Transaction No: " + tx.getTransactionNo(), regularFont));
            leftCell.addElement(new Paragraph("Voucher Number: " + (tx.getVoucherNumber() != null ? tx.getVoucherNumber() : "—"), headerFont));
            leftCell.addElement(new Paragraph("Company: " + (tx.getCompany() != null ? tx.getCompany() : "—"), regularFont));
            LocalDateTime ts = tx.getTimestamp() != null ?
                    LocalDateTime.ofInstant(tx.getTimestamp(), java.time.ZoneId.systemDefault()) :
                    tx.getDate().atStartOfDay();
            leftCell.addElement(new Paragraph("Date & Time: " + ts.format(DATE_TIME_FORMATTER), regularFont));
            leftCell.addElement(new Paragraph("Type: " + tx.getType().name(), regularFont));
            if (tx.getCategory() != null) {
                leftCell.addElement(new Paragraph("Category: " + tx.getCategory().getName(), regularFont));
            }
            if (tx.getSubcategory() != null) {
                leftCell.addElement(new Paragraph("Subcategory: " + tx.getSubcategory().getName(), regularFont));
            }
            table.addCell(leftCell);

            // Right Side: Payer/Payee details (Status field removed per requirement)
            PdfPCell rightCell = new PdfPCell();
            rightCell.setBorder(Rectangle.NO_BORDER);
            rightCell.setPadding(2);
            rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            rightCell.addElement(new Paragraph("Payer: " + tx.getPayer(), regularFont));
            if (tx.getPayee() != null && !tx.getPayee().isBlank()) {
                rightCell.addElement(new Paragraph("Payee: " + tx.getPayee(), regularFont));
            }
            table.addCell(rightCell);

            document.add(table);

            // Divider line
            Paragraph divider = new Paragraph("----------------------------------------------------------------------------------------------------------------------------------", regularFont);
            divider.setSpacingAfter(6);
            document.add(divider);

            // Details Table (Amount and Description)
            PdfPTable detailsTable = new PdfPTable(new float[]{3, 1});
            detailsTable.setWidthPercentage(100);
            detailsTable.setSpacingAfter(10);

            PdfPCell descHeader = new PdfPCell(new Phrase("Particulars / Description", headerFont));
            descHeader.setPadding(5);
            detailsTable.addCell(descHeader);

            PdfPCell amtHeader = new PdfPCell(new Phrase("Amount (INR)", headerFont));
            amtHeader.setHorizontalAlignment(Element.ALIGN_RIGHT);
            amtHeader.setPadding(5);
            detailsTable.addCell(amtHeader);

            // Row 1
            PdfPCell descCell = new PdfPCell(new Phrase(tx.getDescription(), regularFont));
            descCell.setPadding(5);
            detailsTable.addCell(descCell);

            PdfPCell amtCell = new PdfPCell(new Phrase("Rs. " + formatAmount(tx.getAmount()), boldFont));
            amtCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            amtCell.setPadding(5);
            detailsTable.addCell(amtCell);

            document.add(detailsTable);

            // Signature Blocks Table
            PdfPTable signTable = new PdfPTable(3);
            signTable.setWidthPercentage(100);
            signTable.setSpacingBefore(10);

            // Prepared By (Payer)
            byte[] preparedSignBytes = signatureService.getSignatureForUser(tx.getPayer());
            PdfPCell pCell = createSignatureCell("Prepared By", tx.getPayer(), preparedSignBytes, signFont);
            signTable.addCell(pCell);

            // Received By (Payee)
            String payeeName = (tx.getPayee() != null && !tx.getPayee().isBlank()) ? tx.getPayee() : "Cash Box";
            byte[] receivedSignBytes = (tx.getPayee() != null && !tx.getPayee().isBlank()) ?
                    signatureService.getSignatureForUser(tx.getPayee()) : null;
            PdfPCell rCell = createSignatureCell("Received By", payeeName, receivedSignBytes, signFont);
            signTable.addCell(rCell);

            // Approved By
            String approver = (appProperties != null && appProperties.getSignatures() != null &&
                    appProperties.getSignatures().getApprovedBy() != null) ?
                    appProperties.getSignatures().getApprovedBy() : "admin@freestone.com";
            byte[] approvedSignBytes = signatureService.getSignatureForUser(approver);
            PdfPCell aCell = createSignatureCell("Approved By", approver, approvedSignBytes, signFont);
            signTable.addCell(aCell);

            document.add(signTable);

            // Timestamp footer positioned at exact bottom-right corner of the page
            Phrase footerPhrase = new Phrase("Generated on: " + LocalDateTime.now().format(DATE_TIME_FORMATTER), signFont);
            com.lowagie.text.pdf.ColumnText.showTextAligned(
                    writer.getDirectContent(),
                    Element.ALIGN_RIGHT,
                    footerPhrase,
                    document.right(),
                    document.bottom() - 2,
                    0
            );

        } catch (DocumentException e) {
            throw new RuntimeException("Error writing to PDF document", e);
        } finally {
            document.close();
        }

        return out.toByteArray();
    }

    private PdfPCell createSignatureCell(String title, String name, byte[] signatureBytes, Font signFont) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);

        if (signatureBytes != null && signatureBytes.length > 0) {
            try {
                Image img = Image.getInstance(signatureBytes);
                img.scaleToFit(160, 60);
                img.setAlignment(Element.ALIGN_CENTER);
                cell.addElement(img);
            } catch (Exception e) {
                log.warn("Failed to parse signature image for {}: {}", name, e.getMessage());
                Paragraph underline = new Paragraph("________________________", signFont);
                underline.setAlignment(Element.ALIGN_CENTER);
                cell.addElement(underline);
            }
        } else {
            Paragraph underline = new Paragraph("________________________", signFont);
            underline.setAlignment(Element.ALIGN_CENTER);
            cell.addElement(underline);
        }

        Paragraph titleLabel = new Paragraph(title, signFont);
        titleLabel.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(titleLabel);

        Paragraph nameLabel = new Paragraph("(" + name + ")", signFont);
        nameLabel.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(nameLabel);

        return cell;
    }

    private String formatAmount(BigDecimal amount) {
        return amount.setScale(2, BigDecimal.ROUND_HALF_UP).toString();
    }
}
