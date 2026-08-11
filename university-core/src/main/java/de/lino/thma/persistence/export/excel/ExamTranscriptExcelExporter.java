package de.lino.thma.persistence.export.excel;

import de.lino.database.json.file.FileProvider;
import de.lino.thma.persistence.export.pdf.ExamTranscriptExporter;
import de.lino.thma.persistence.export.TranscriptLegendEntry;
import de.lino.thma.persistence.export.TranscriptSection;
import de.lino.thma.utility.Constraints;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Exports a grouped transcript (see {@link TranscriptSection}) to an Excel workbook via
 * Apache POI, styled to match {@link ExcelExporter}: a shaded, bold header row, thin
 * borders around every cell, and light banding on alternating data rows, plus one
 * light-gray, bold, merged section row per group (e.g. a semester's name) and a blank
 * row between sections. An optional legend (e.g. a grading-scale key) goes on its own
 * sheet. The Excel counterpart to {@link ExamTranscriptExporter}'s PDF, sharing the
 * same input shape so a caller can offer both formats from the same already-built data.
 */
public final class ExamTranscriptExcelExporter {

    /**
     * Font size, in points, of header, section and data cells.
     */
    private static final short CELL_FONT_SIZE = 11;

    /**
     * RGB color of every cell border.
     */
    private static final byte[] BORDER_COLOR = {(byte) 140, (byte) 140, (byte) 140};

    /**
     * RGB background fill of the header row and each section row.
     */
    private static final byte[] HEADER_FILL = {(byte) 230, (byte) 230, (byte) 230};

    /**
     * RGB background fill of alternating ("banded") data rows.
     */
    private static final byte[] BAND_FILL = {(byte) 247, (byte) 247, (byte) 247};

    /**
     * Not instantiable; all functionality is exposed through
     * {@link #export(String, List, List, String, List, Path)}.
     */
    private ExamTranscriptExcelExporter() {
    }

    /**
     * Writes {@code sections} to an Excel workbook at {@code output}, one column per
     * entry in {@code columnHeaders}, on a sheet named after {@code documentTitle}
     * (truncated and sanitized to fit Excel's sheet-name rules). {@code legendEntries}
     * (if not empty) is written to its own sheet named after {@code legendTitle}, its
     * label column auto-sized to the widest entry.
     *
     * @param documentTitle the transcript sheet's own name
     * @param columnHeaders the column headers, in display order
     * @param sections the grouped rows to write, in the order they should appear
     * @param legendTitle the legend sheet's name; ignored if {@code legendEntries} is empty
     * @param legendEntries the legend's entries, or empty to omit the legend sheet
     * @param output the file path the workbook is written to; overwritten if it already exists
     * @throws IOException if the workbook cannot be written to {@code output}
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code columnHeaders} is empty
     */
    public static void export(
            final String documentTitle,
            final List<String> columnHeaders,
            final List<TranscriptSection> sections,
            final String legendTitle,
            final List<TranscriptLegendEntry> legendEntries,
            final Path output
    ) throws IOException {

        Objects.requireNonNull(documentTitle, "@ExamTranscriptExcelExporter.export: documentTitle must not be null");
        Objects.requireNonNull(columnHeaders, "@ExamTranscriptExcelExporter.export: columnHeaders must not be null");
        Objects.requireNonNull(sections, "@ExamTranscriptExcelExporter.export: sections must not be null");
        Objects.requireNonNull(legendEntries, "@ExamTranscriptExcelExporter.export: legendEntries must not be null");
        Objects.requireNonNull(output, "@ExamTranscriptExcelExporter.export: output must not be null");

        if (columnHeaders.isEmpty()) throw new IllegalArgumentException("@ExamTranscriptExcelExporter.export: columnHeaders must not be empty");

        final Path outputPath = Constraints.EXPORT_PATH.resolve(output.getFileName());

        if (Files.exists(outputPath)) Files.delete(outputPath);
        if (!Files.exists(Constraints.EXPORT_PATH)) FileProvider.getInstance().createDirectory(Constraints.EXPORT_PATH);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {

            writeTranscriptSheet(workbook, documentTitle, columnHeaders, sections);

            if (!legendEntries.isEmpty()) {
                writeLegendSheet(workbook, legendTitle, legendEntries);
            }

            try (OutputStream out = Files.newOutputStream(outputPath)) {
                workbook.write(out);
            }

        }

    }

    /**
     * Writes the main sheet: the bold, shaded header row, then one light-gray, bold,
     * merged section row per group followed by that group's own banded data rows and a
     * blank spacer row before the next group.
     *
     * @param workbook the workbook to add the sheet to
     * @param title the sheet's own name
     * @param headers the column headers, in display order
     * @param sections the grouped rows to write, in the order they should appear
     */
    private static void writeTranscriptSheet(
            final XSSFWorkbook workbook, final String title, final List<String> headers, final List<TranscriptSection> sections
    ) {

        final Sheet sheet = workbook.createSheet(sheetName(title));

        final XSSFCellStyle headerStyle = createRowStyle(workbook, true, HEADER_FILL);
        final XSSFCellStyle sectionStyle = createRowStyle(workbook, true, HEADER_FILL);
        final XSSFCellStyle cellStyle = createRowStyle(workbook, false, null);
        final XSSFCellStyle bandedStyle = createRowStyle(workbook, false, BAND_FILL);

        int rowIndex = 0;

        writeRow(sheet.createRow(rowIndex++), headers, headerStyle);

        for (final TranscriptSection section : sections) {

            final Row sectionRow = sheet.createRow(rowIndex);

            for (int i = 0; i < headers.size(); i++) {
                sectionRow.createCell(i).setCellStyle(sectionStyle);
            }
            sectionRow.getCell(0).setCellValue(section.title());

            if (headers.size() > 1) {
                sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 0, headers.size() - 1));
            }

            rowIndex++;

            int dataIndex = 0;
            for (final List<String> row : section.rows()) {
                writeRow(sheet.createRow(rowIndex++), row, dataIndex++ % 2 == 1 ? bandedStyle : cellStyle);
            }

            rowIndex++; // blank row separating this section from the next

        }

        for (int i = 0; i < headers.size(); i++) {
            sheet.autoSizeColumn(i);
        }

        sheet.createFreezePane(0, 1);

    }

    /**
     * Writes the legend sheet: {@code entries}' labels in a bold left column, their
     * descriptions in a plain right column.
     *
     * @param workbook the workbook to add the sheet to
     * @param title the sheet's own name
     * @param entries the legend's entries, in order
     */
    private static void writeLegendSheet(final XSSFWorkbook workbook, final String title, final List<TranscriptLegendEntry> entries) {

        final Sheet sheet = workbook.createSheet(sheetName(title));

        final Font labelFont = workbook.createFont();
        labelFont.setBold(true);
        final CellStyle labelStyle = workbook.createCellStyle();
        labelStyle.setFont(labelFont);

        for (int i = 0; i < entries.size(); i++) {

            final TranscriptLegendEntry entry = entries.get(i);
            final Row row = sheet.createRow(i);

            final Cell labelCell = row.createCell(0);
            labelCell.setCellValue(entry.label());
            labelCell.setCellStyle(labelStyle);

            row.createCell(1).setCellValue(entry.description());

        }

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);

    }

    /**
     * Writes one row of cell values with the given style applied to every cell.
     *
     * @param row the sheet row to fill
     * @param values the cell values, in column order
     * @param style the style to apply to every cell in the row
     */
    private static void writeRow(final Row row, final List<String> values, final XSSFCellStyle style) {

        for (int i = 0; i < values.size(); i++) {

            final Cell cell = row.createCell(i);
            cell.setCellValue(values.get(i));
            cell.setCellStyle(style);

        }

    }

    /**
     * Builds a bordered, vertically-centered style for a header, section or data row,
     * with an optional background fill.
     *
     * @param workbook the workbook to create the style and font in
     * @param bold whether the row's text should be bold, i.e. whether it is a header or section row
     * @param fill the background fill color, as RGB bytes, or {@code null} for no fill
     * @return the row's cell style
     */
    private static XSSFCellStyle createRowStyle(final XSSFWorkbook workbook, final boolean bold, final byte[] fill) {

        final Font font = workbook.createFont();
        font.setBold(bold);
        font.setFontHeightInPoints(CELL_FONT_SIZE);

        final XSSFCellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setVerticalAlignment(VerticalAlignment.CENTER);

        if (fill != null) {
            style.setFillForegroundColor(new XSSFColor(fill, null));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }

        final XSSFColor borderColor = new XSSFColor(BORDER_COLOR, null);

        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setTopBorderColor(borderColor);
        style.setBottomBorderColor(borderColor);
        style.setLeftBorderColor(borderColor);
        style.setRightBorderColor(borderColor);

        return style;

    }

    /**
     * Sanitizes {@code title} into a valid Excel sheet name: strips the characters
     * Excel forbids in one ({@code \ / ? * [ ]}), and truncates to its 31-character
     * limit.
     *
     * @param title the title to sanitize
     * @return the sanitized sheet name, or {@code "Sheet1"} if nothing valid remains
     */
    private static String sheetName(final String title) {

        final String sanitized = title.replaceAll("[\\\\/?*\\[\\]:]", " ").trim();
        final String truncated = sanitized.length() > 31 ? sanitized.substring(0, 31).trim() : sanitized;

        return truncated.isEmpty() ? "Sheet1" : truncated;

    }

}
