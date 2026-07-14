package com.freestone.pettycash.service;

import com.freestone.pettycash.model.PettyCashTransaction;
import com.freestone.pettycash.model.TransactionType;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.awt.Color;

@Service
@Transactional(readOnly = true)
public class CustomReportService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    public static class MonthSummary {
        public BigDecimal totalSpent = BigDecimal.ZERO;
        public BigDecimal totalAdded = BigDecimal.ZERO;
    }

    public byte[] generateCsvCustomReport(List<PettyCashTransaction> list, LocalDate start, LocalDate end, String filterCompany, String filterCategory, TransactionType type, String search) {
        StringBuilder csv = new StringBuilder();
        
        // 1. Title & Metadata
        csv.append("PETTY CASH CUSTOM SUMMARY REPORT\n");
        csv.append("Generated on: ").append(LocalDateTime.now().format(DATETIME_FORMATTER)).append("\n");
        csv.append("Period: ").append(start != null ? start.toString() : "All-Time").append(" to ").append(end != null ? end.toString() : "All-Time").append("\n");
        if (filterCompany != null && !filterCompany.isBlank()) csv.append("Filtered Company: ").append(filterCompany).append("\n");
        if (filterCategory != null && !filterCategory.isBlank()) csv.append("Filtered Category: ").append(filterCategory).append("\n");
        if (type != null) csv.append("Filtered Type: ").append(type.name()).append("\n");
        if (search != null && !search.isBlank()) csv.append("Search Term: ").append(search).append("\n");
        csv.append("\n");

        // 2. Company wise Breakdown
        csv.append("--- COMPANY BREAKDOWN ---\n");
        csv.append("Company,Category,Total Expense (Spent),Total Topup (Added)\n");
        
        Map<String, Map<String, BigDecimal>> companyCategoryTotals = new TreeMap<>();
        Map<String, BigDecimal> companyTotalSpent = new TreeMap<>();
        Map<String, BigDecimal> companyTotalAdded = new TreeMap<>();

        for (PettyCashTransaction t : list) {
            String company = t.getCompany() != null ? t.getCompany() : "Unknown";
            if (t.getType() == TransactionType.EXPENSE) {
                companyCategoryTotals.computeIfAbsent(company, k -> new TreeMap<>());
                String category = t.getCategory() != null ? t.getCategory().getName() : "Uncategorized";
                companyCategoryTotals.get(company).put(category, 
                    companyCategoryTotals.get(company).getOrDefault(category, BigDecimal.ZERO).add(t.getAmount()));
                companyTotalSpent.put(company, companyTotalSpent.getOrDefault(company, BigDecimal.ZERO).add(t.getAmount()));
            } else if (t.getType() == TransactionType.TOPUP) {
                companyTotalAdded.put(company, companyTotalAdded.getOrDefault(company, BigDecimal.ZERO).add(t.getAmount()));
            }
        }

        for (String company : companyCategoryTotals.keySet()) {
            Map<String, BigDecimal> catMap = companyCategoryTotals.get(company);
            for (String category : catMap.keySet()) {
                csv.append(escapeCsvField(company)).append(",")
                   .append(escapeCsvField(category)).append(",")
                   .append(catMap.get(category).toString()).append(",")
                   .append("0.00\n");
            }
            csv.append(escapeCsvField(company)).append(",")
               .append("OVERALL TOTAL").append(",")
               .append(companyTotalSpent.getOrDefault(company, BigDecimal.ZERO).toString()).append(",")
               .append(companyTotalAdded.getOrDefault(company, BigDecimal.ZERO).toString()).append("\n");
        }
        csv.append("\n");

        // 3. Monthly Breakdown
        csv.append("--- MONTHLY STATISTICS ---\n");
        csv.append("Month,Total Spent (Expense),Total Added (Top-up)\n");

        Map<String, MonthSummary> monthlySummary = new TreeMap<>();
        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("yyyy-MM");
        for (PettyCashTransaction t : list) {
            String month = t.getDate().format(monthFormatter);
            MonthSummary summary = monthlySummary.computeIfAbsent(month, k -> new MonthSummary());
            if (t.getType() == TransactionType.EXPENSE) {
                summary.totalSpent = summary.totalSpent.add(t.getAmount());
            } else if (t.getType() == TransactionType.TOPUP) {
                summary.totalAdded = summary.totalAdded.add(t.getAmount());
            }
        }

        for (String month : monthlySummary.keySet()) {
            MonthSummary summary = monthlySummary.get(month);
            csv.append(month).append(",")
               .append(summary.totalSpent.toString()).append(",")
               .append(summary.totalAdded.toString()).append("\n");
        }
        csv.append("\n");

        // 4. Detailed Ledger Section
        csv.append("--- DETAILED TRANSACTIONS ---\n");
        csv.append("Date,Transaction No,Voucher Number,Company,Type,Description,Category,Subcategory,Payer,Payee,Amount\n");

        for (PettyCashTransaction t : list) {
            csv.append(t.getDate().toString()).append(",");
            csv.append(escapeCsvField(t.getTransactionNo())).append(",");
            csv.append(escapeCsvField(t.getVoucherNumber() != null ? t.getVoucherNumber() : "")).append(",");
            csv.append(escapeCsvField(t.getCompany() != null ? t.getCompany() : "")).append(",");
            csv.append(t.getType().name()).append(",");
            csv.append(escapeCsvField(t.getDescription())).append(",");
            csv.append(escapeCsvField(t.getCategory() != null ? t.getCategory().getName() : "")).append(",");
            csv.append(escapeCsvField(t.getSubcategory() != null ? t.getSubcategory().getName() : "")).append(",");
            csv.append(escapeCsvField(t.getPayer())).append(",");
            csv.append(escapeCsvField(t.getPayee() != null ? t.getPayee() : "")).append(",");
            csv.append(t.getAmount().toString()).append("\n");
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

    public byte[] generatePdfCustomReport(List<PettyCashTransaction> list, LocalDate start, LocalDate end, String filterCompany, String filterCategory, TransactionType type, String search) {
        Document document = new Document(PageSize.A4, 36, 36, 54, 54);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            // Font configurations
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.decode("#1e293b"));
            Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.decode("#64748b"));
            Font h2Font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.decode("#0f172a"));
            Font tableHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
            Font tableBodyFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.decode("#334155"));
            Font boldBodyFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.decode("#0f172a"));

            // 1. Title Block
            Paragraph title = new Paragraph("PETTY CASH SUMMARY STATEMENT", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(4);
            document.add(title);

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
            subtitle.setSpacingAfter(12);
            document.add(subtitle);

            // Filters Summary Box
            if (filterCompany != null || filterCategory != null || type != null || search != null) {
                StringBuilder fs = new StringBuilder("Applied Filters: ");
                boolean added = false;
                if (filterCompany != null && !filterCompany.isBlank()) {
                    fs.append("Company = ").append(filterCompany);
                    added = true;
                }
                if (filterCategory != null && !filterCategory.isBlank()) {
                    if (added) fs.append(" | ");
                    fs.append("Category = ").append(filterCategory);
                    added = true;
                }
                if (type != null) {
                    if (added) fs.append(" | ");
                    fs.append("Type = ").append(type.name());
                    added = true;
                }
                if (search != null && !search.isBlank()) {
                    if (added) fs.append(" | ");
                    fs.append("Search = \"").append(search).append("\"");
                }
                Paragraph filtersText = new Paragraph(fs.toString(), FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, Color.decode("#475569")));
                filtersText.setAlignment(Element.ALIGN_CENTER);
                filtersText.setSpacingAfter(18);
                document.add(filtersText);
            }

            // Overview Summary Metrics Boxes (regardless of range duration)
            BigDecimal totalSpent = BigDecimal.ZERO;
            BigDecimal totalAdded = BigDecimal.ZERO;
            for (PettyCashTransaction t : list) {
                if (t.getType() == TransactionType.EXPENSE) {
                    totalSpent = totalSpent.add(t.getAmount());
                } else if (t.getType() == TransactionType.TOPUP) {
                    totalAdded = totalAdded.add(t.getAmount());
                }
            }
            BigDecimal netChange = totalAdded.subtract(totalSpent);

            PdfPTable overviewTable = new PdfPTable(4);
            overviewTable.setWidthPercentage(100);
            overviewTable.setSpacingAfter(20);

            Font metaLabelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.decode("#475569"));
            Font metaValueFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.decode("#0f172a"));

            overviewTable.addCell(createSummaryCell("TOTAL REPLENISHMENTS", "₹" + totalAdded, metaLabelFont, metaValueFont, Color.decode("#f0fdf4"), Color.decode("#bbf7d0")));
            overviewTable.addCell(createSummaryCell("TOTAL SPENT", "₹" + totalSpent, metaLabelFont, metaValueFont, Color.decode("#fef2f2"), Color.decode("#fecaca")));
            overviewTable.addCell(createSummaryCell("NET MOVEMENT", "₹" + netChange, metaLabelFont, metaValueFont, Color.decode("#f0f9ff"), Color.decode("#bae6fd")));
            overviewTable.addCell(createSummaryCell("TRANSACTION COUNT", String.valueOf(list.size()), metaLabelFont, metaValueFont, Color.decode("#f8fafc"), Color.decode("#e2e8f0")));

            document.add(overviewTable);

            // 2. Section: Company wise Breakdown
            Paragraph compHeading = new Paragraph("Company Statistics Summary", h2Font);
            compHeading.setSpacingAfter(10);
            document.add(compHeading);

            Map<String, Map<String, BigDecimal>> companyCategoryTotals = new TreeMap<>();
            Map<String, BigDecimal> companyTotalSpent = new TreeMap<>();
            Map<String, BigDecimal> companyTotalAdded = new TreeMap<>();

            for (PettyCashTransaction t : list) {
                String company = t.getCompany() != null ? t.getCompany() : "Unknown";
                if (t.getType() == TransactionType.EXPENSE) {
                    companyCategoryTotals.computeIfAbsent(company, k -> new TreeMap<>());
                    String category = t.getCategory() != null ? t.getCategory().getName() : "Uncategorized";
                    companyCategoryTotals.get(company).put(category, 
                        companyCategoryTotals.get(company).getOrDefault(category, BigDecimal.ZERO).add(t.getAmount()));
                    companyTotalSpent.put(company, companyTotalSpent.getOrDefault(company, BigDecimal.ZERO).add(t.getAmount()));
                } else if (t.getType() == TransactionType.TOPUP) {
                    companyTotalAdded.put(company, companyTotalAdded.getOrDefault(company, BigDecimal.ZERO).add(t.getAmount()));
                }
            }

            if (companyCategoryTotals.isEmpty() && companyTotalAdded.isEmpty()) {
                Paragraph noCompData = new Paragraph("No company wise statistics to display.", tableBodyFont);
                noCompData.setSpacingAfter(18);
                document.add(noCompData);
            } else {
                Set<String> allCompanies = new TreeSet<>();
                allCompanies.addAll(companyCategoryTotals.keySet());
                allCompanies.addAll(companyTotalAdded.keySet());

                for (String company : allCompanies) {
                    Paragraph compSubHeading = new Paragraph(company, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.decode("#1e3a8a")));
                    compSubHeading.setSpacingAfter(6);
                    document.add(compSubHeading);

                    PdfPTable table = new PdfPTable(new float[]{3.0f, 1.5f, 1.5f});
                    table.setWidthPercentage(100);
                    table.setSpacingAfter(12);

                    // Table headers
                    String[] headers = {"Category Name", "Total Spent (Expenses)", "Total Added (Top-ups)"};
                    for (String header : headers) {
                        PdfPCell cell = new PdfPCell(new Paragraph(header, tableHeaderFont));
                        cell.setBackgroundColor(Color.decode("#3b82f6"));
                        cell.setPadding(5);
                        cell.setBorderColor(Color.decode("#93c5fd"));
                        cell.setHorizontalAlignment(header.equals("Category Name") ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
                        table.addCell(cell);
                    }

                    Map<String, BigDecimal> catMap = companyCategoryTotals.getOrDefault(company, Collections.emptyMap());
                    for (String category : catMap.keySet()) {
                        table.addCell(createTableCell(category, tableBodyFont, false, Element.ALIGN_LEFT));
                        table.addCell(createTableCell("₹" + catMap.get(category).toString(), tableBodyFont, false, Element.ALIGN_RIGHT));
                        table.addCell(createTableCell("₹0.00", tableBodyFont, false, Element.ALIGN_RIGHT));
                    }

                    // Company Total Row
                    PdfPCell totalLabelCell = new PdfPCell(new Paragraph("Overall Total for " + company, tableHeaderFont));
                    totalLabelCell.setBackgroundColor(Color.decode("#1e40af"));
                    totalLabelCell.setPadding(5);
                    totalLabelCell.setBorderColor(Color.decode("#93c5fd"));
                    table.addCell(totalLabelCell);

                    PdfPCell totalSpentCell = new PdfPCell(new Paragraph("₹" + companyTotalSpent.getOrDefault(company, BigDecimal.ZERO), tableHeaderFont));
                    totalSpentCell.setBackgroundColor(Color.decode("#1e40af"));
                    totalSpentCell.setPadding(5);
                    totalSpentCell.setBorderColor(Color.decode("#93c5fd"));
                    totalSpentCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                    table.addCell(totalSpentCell);

                    PdfPCell totalAddedCell = new PdfPCell(new Paragraph("₹" + companyTotalAdded.getOrDefault(company, BigDecimal.ZERO), tableHeaderFont));
                    totalAddedCell.setBackgroundColor(Color.decode("#1e40af"));
                    totalAddedCell.setPadding(5);
                    totalAddedCell.setBorderColor(Color.decode("#93c5fd"));
                    totalAddedCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                    table.addCell(totalAddedCell);

                    document.add(table);
                }
            }

            // 3. Section: Monthly Breakdown
            Paragraph monthHeading = new Paragraph("Monthly Statement Summary", h2Font);
            monthHeading.setSpacingBefore(12);
            monthHeading.setSpacingAfter(10);
            document.add(monthHeading);

            Map<String, MonthSummary> monthlySummary = new TreeMap<>();
            DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("yyyy-MM");
            for (PettyCashTransaction t : list) {
                String month = t.getDate().format(monthFormatter);
                MonthSummary summary = monthlySummary.computeIfAbsent(month, k -> new MonthSummary());
                if (t.getType() == TransactionType.EXPENSE) {
                    summary.totalSpent = summary.totalSpent.add(t.getAmount());
                } else if (t.getType() == TransactionType.TOPUP) {
                    summary.totalAdded = summary.totalAdded.add(t.getAmount());
                }
            }

            if (monthlySummary.isEmpty()) {
                Paragraph noMonthData = new Paragraph("No monthly statement data to display.", tableBodyFont);
                noMonthData.setSpacingAfter(18);
                document.add(noMonthData);
            } else {
                PdfPTable monthTable = new PdfPTable(new float[]{2.0f, 2.0f, 2.0f, 2.0f});
                monthTable.setWidthPercentage(100);
                monthTable.setSpacingAfter(18);

                String[] mHeaders = {"Month Period", "Total Spent (Expenses)", "Total Added (Top-ups)", "Net Balance Flow"};
                for (String header : mHeaders) {
                    PdfPCell cell = new PdfPCell(new Paragraph(header, tableHeaderFont));
                    cell.setBackgroundColor(Color.decode("#475569"));
                    cell.setPadding(5);
                    cell.setBorderColor(Color.decode("#cbd5e1"));
                    cell.setHorizontalAlignment(header.equals("Month Period") ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
                    monthTable.addCell(cell);
                }

                for (String month : monthlySummary.keySet()) {
                    MonthSummary summary = monthlySummary.get(month);
                    BigDecimal net = summary.totalAdded.subtract(summary.totalSpent);
                    
                    monthTable.addCell(createTableCell(month, tableBodyFont, false, Element.ALIGN_LEFT));
                    monthTable.addCell(createTableCell("₹" + summary.totalSpent.toString(), tableBodyFont, false, Element.ALIGN_RIGHT));
                    monthTable.addCell(createTableCell("₹" + summary.totalAdded.toString(), tableBodyFont, false, Element.ALIGN_RIGHT));
                    
                    String netText = (net.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "") + "₹" + net;
                    Color netColor = net.compareTo(BigDecimal.ZERO) >= 0 ? Color.decode("#16a34a") : Color.decode("#dc2626");
                    Font netFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, netColor);
                    monthTable.addCell(createTableCell(netText, netFont, false, Element.ALIGN_RIGHT));
                }

                document.add(monthTable);
            }

            // 4. Section: Detailed Ledger
            Paragraph ledgerHeading = new Paragraph("Detailed Transactions Statement Ledger", h2Font);
            ledgerHeading.setSpacingBefore(12);
            ledgerHeading.setSpacingAfter(10);
            document.add(ledgerHeading);

            if (list.isEmpty()) {
                Paragraph noTxData = new Paragraph("No detailed transactions matching filters.", tableBodyFont);
                document.add(noTxData);
            } else {
                PdfPTable ledgerTable = new PdfPTable(new float[]{1.2f, 1.2f, 1.8f, 1.5f, 2.5f, 1.0f, 1.2f});
                ledgerTable.setWidthPercentage(100);

                String[] lHeaders = {"Date", "Tx No", "Company", "Category", "Description", "Type", "Amount"};
                for (String header : lHeaders) {
                    PdfPCell cell = new PdfPCell(new Paragraph(header, tableHeaderFont));
                    cell.setBackgroundColor(Color.decode("#0f172a"));
                    cell.setPadding(4);
                    cell.setBorderColor(Color.decode("#334155"));
                    cell.setHorizontalAlignment(header.equals("Amount") ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
                    ledgerTable.addCell(cell);
                }

                for (PettyCashTransaction t : list) {
                    ledgerTable.addCell(createTableCell(t.getDate().toString(), tableBodyFont, false, Element.ALIGN_LEFT));
                    ledgerTable.addCell(createTableCell(t.getTransactionNo(), tableBodyFont, false, Element.ALIGN_LEFT));
                    ledgerTable.addCell(createTableCell(t.getCompany() != null ? t.getCompany() : "", tableBodyFont, false, Element.ALIGN_LEFT));
                    ledgerTable.addCell(createTableCell(t.getCategory() != null ? t.getCategory().getName() : "—", tableBodyFont, false, Element.ALIGN_LEFT));
                    ledgerTable.addCell(createTableCell(t.getDescription(), tableBodyFont, false, Element.ALIGN_LEFT));
                    ledgerTable.addCell(createTableCell(t.getType().name(), tableBodyFont, false, Element.ALIGN_LEFT));
                    ledgerTable.addCell(createTableCell("₹" + t.getAmount(), boldBodyFont, false, Element.ALIGN_RIGHT));
                }

                document.add(ledgerTable);
            }

            document.close();
        } catch (Exception e) {
            e.printStackTrace();
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

    private PdfPCell createTableCell(String text, Font font, boolean isHeader, int alignment) {
        PdfPCell cell = new PdfPCell(new Paragraph(text, font));
        cell.setPadding(4);
        cell.setBorderColor(Color.decode("#e2e8f0"));
        cell.setHorizontalAlignment(alignment);
        if (!isHeader) {
            cell.setBackgroundColor(Color.WHITE);
        }
        return cell;
    }
}
