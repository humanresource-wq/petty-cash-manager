package com.freestone.pettycash.service;

import com.freestone.pettycash.model.PettyCashTransaction;
import com.freestone.pettycash.model.TransactionType;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.awt.Color;

@Service
public class ReportService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public byte[] generateCsvReport(List<PettyCashTransaction> list) {
        StringBuilder csv = new StringBuilder();
        // CSV Header row
        csv.append("Date,Transaction No,Type,Description,Category,Subcategory,Payer,Payee,Amount,Receipt Status\n");

        for (PettyCashTransaction t : list) {
            csv.append(t.getDate().format(DATE_FORMATTER)).append(",");
            csv.append(escapeCsvField(t.getTransactionNo())).append(",");
            csv.append(t.getType().name()).append(",");
            csv.append(escapeCsvField(t.getDescription())).append(",");
            csv.append(escapeCsvField(t.getCategory() != null ? t.getCategory().getName() : "")).append(",");
            csv.append(escapeCsvField(t.getSubcategory() != null ? t.getSubcategory().getName() : "")).append(",");
            csv.append(escapeCsvField(t.getPayer())).append(",");
            csv.append(escapeCsvField(t.getPayee() != null ? t.getPayee() : "")).append(",");
            csv.append(t.getAmount().toString()).append(",");
            csv.append(t.getReceiptStatus().name()).append("\n");
        }

        return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String escapeCsvField(String field) {
        if (field == null) {
            return "";
        }
        String value = field.replace("\"", "\"\"");
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value + "\"";
        }
        return value;
    }

    public byte[] generatePdfSummaryReport(List<PettyCashTransaction> list, LocalDate start, LocalDate end) {
        Document document = new Document(PageSize.A4, 36, 36, 54, 54);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            // Fonts configuration
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.decode("#0f172a"));
            Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.decode("#64748b"));
            Font metaLabelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.decode("#475569"));
            Font metaValueFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.decode("#0f172a"));
            Font tableHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
            Font tableBodyFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.decode("#334155"));
            Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 7, Color.decode("#94a3b8"));

            // 1. Report Title
            Paragraph title = new Paragraph("PETTY CASH SUMMARY LEDGER REPORT", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(4);
            document.add(title);

            // Date Range Subtitle
            String dateRange = "All-Time Statement Ledger";
            if (start != null && end != null) {
                dateRange = "Statement Period: " + start.format(DATE_FORMATTER) + " to " + end.format(DATE_FORMATTER);
            } else if (start != null) {
                dateRange = "Statement Period: Since " + start.format(DATE_FORMATTER);
            } else if (end != null) {
                dateRange = "Statement Period: Up to " + end.format(DATE_FORMATTER);
            }
            Paragraph subtitle = new Paragraph(dateRange, subtitleFont);
            subtitle.setAlignment(Element.ALIGN_CENTER);
            subtitle.setSpacingAfter(24);
            document.add(subtitle);

            // 2. Metrics summary boxes table (4 columns)
            BigDecimal totalSpent = BigDecimal.ZERO;
            BigDecimal totalAdded = BigDecimal.ZERO;
            int expenseCount = 0;
            int topupCount = 0;

            for (PettyCashTransaction t : list) {
                if (t.getType() == TransactionType.EXPENSE) {
                    totalSpent = totalSpent.add(t.getAmount());
                    expenseCount++;
                } else {
                    totalAdded = totalAdded.add(t.getAmount());
                    topupCount++;
                }
            }
            BigDecimal netChange = totalAdded.subtract(totalSpent);

            PdfPTable summaryTable = new PdfPTable(4);
            summaryTable.setWidthPercentage(100);
            summaryTable.setSpacingAfter(24);

            summaryTable.addCell(createSummaryCell("TOTAL REPLENISHMENTS", "₹" + totalAdded, metaLabelFont, metaValueFont, Color.decode("#f0fdf4"), Color.decode("#bbf7d0")));
            summaryTable.addCell(createSummaryCell("TOTAL SPENT", "₹" + totalSpent, metaLabelFont, metaValueFont, Color.decode("#fef2f2"), Color.decode("#fecaca")));
            summaryTable.addCell(createSummaryCell("NET MOVEMENT", "₹" + netChange, metaLabelFont, metaValueFont, Color.decode("#f0f9ff"), Color.decode("#bae6fd")));
            summaryTable.addCell(createSummaryCell("TRANSACTION COUNT", String.valueOf(list.size()), metaLabelFont, metaValueFont, Color.decode("#f8fafc"), Color.decode("#e2e8f0")));

            document.add(summaryTable);

            // 3. Transactions Table
            PdfPTable table = new PdfPTable(new float[]{1.2f, 1.3f, 2.0f, 3.5f, 1.2f, 1.2f});
            table.setWidthPercentage(100);
            table.setSpacingAfter(30);

            // Table headers
            String[] headers = {"Date", "Transaction No", "Category", "Description", "Type", "Amount"};
            for (String header : headers) {
                PdfPCell cell = new PdfPCell(new Paragraph(header, tableHeaderFont));
                cell.setBackgroundColor(Color.decode("#1e293b"));
                cell.setPadding(6);
                cell.setBorderColor(Color.decode("#334155"));
                cell.setHorizontalAlignment(header.equals("Amount") ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
                table.addCell(cell);
            }

            // Table body rows
            for (PettyCashTransaction t : list) {
                // Date
                table.addCell(createTableCell(t.getDate().format(DATE_FORMATTER), tableBodyFont, false, false));
                // Tx No
                table.addCell(createTableCell(t.getTransactionNo(), tableBodyFont, false, false));
                // Category
                String cat = t.getCategory() != null ? t.getCategory().getName() : "—";
                if (t.getSubcategory() != null) {
                    cat += " > " + t.getSubcategory().getName();
                }
                table.addCell(createTableCell(cat, tableBodyFont, false, false));
                // Description
                table.addCell(createTableCell(t.getDescription(), tableBodyFont, false, false));
                // Type
                table.addCell(createTableCell(t.getType().name(), tableBodyFont, false, false));
                // Amount
                String amt = "₹" + t.getAmount().toString();
                table.addCell(createTableCell(amt, tableBodyFont, true, false));
            }

            if (list.isEmpty()) {
                PdfPCell emptyCell = new PdfPCell(new Paragraph("No matching transactions recorded inside this date scope.", tableBodyFont));
                emptyCell.setColspan(6);
                emptyCell.setPadding(12);
                emptyCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                emptyCell.setBorderColor(Color.decode("#cbd5e1"));
                table.addCell(emptyCell);
            }

            document.add(table);

            // 4. Footer Statement
            Paragraph footer = new Paragraph("Statement report compiled automatically on " + LocalDate.now().format(DATE_FORMATTER) + " by Petty Cash Manager.\nPage 1 of 1 (Finalized)", footerFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            document.add(footer);

        } catch (Exception e) {
            throw new RuntimeException("Error occurred compiling summary PDF: " + e.getMessage(), e);
        } finally {
            document.close();
        }

        return out.toByteArray();
    }

    private PdfPCell createSummaryCell(String label, String value, Font labelFont, Font valFont, Color bg, Color border) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(bg);
        cell.setBorderColor(border);
        cell.setPadding(8);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);

        Paragraph labelPara = new Paragraph(label, labelFont);
        labelPara.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(labelPara);

        Paragraph valPara = new Paragraph(value, valFont);
        valPara.setAlignment(Element.ALIGN_CENTER);
        valPara.setSpacingBefore(4);
        cell.addElement(valPara);

        return cell;
    }

    private PdfPCell createTableCell(String text, Font font, boolean alignRight, boolean isHeader) {
        PdfPCell cell = new PdfPCell(new Paragraph(text != null ? text : "", font));
        cell.setPadding(5);
        cell.setBorderColor(Color.decode("#e2e8f0"));
        cell.setHorizontalAlignment(alignRight ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return cell;
    }
}
