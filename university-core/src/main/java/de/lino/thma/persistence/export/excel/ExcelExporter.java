package de.lino.thma.persistence.export.excel;

import de.lino.database.json.file.FileProvider;
import de.lino.thma.persistence.export.DataExporter;
import de.lino.thma.persistence.export.pdf.PdfExporter;
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
import java.util.function.Function;

/**
 * Exports an arbitrary data set to an Excel worksheet via Apache POI, styled to match
 * {@link PdfExporter}: a shaded, bold header row, thin borders around every cell, and
 * light banding on alternating data rows.
 *
 * <p>Rows can be any type {@code T}; the caller supplies the column headers and a
 * function turning a row into one cell value per header. This keeps the exporter
 * independent of any specific entity, e.g. {@code Semester} or {@code Module}.
 */
public final class ExcelExporter implements DataExporter {

    /**
     * Font size, in points, of the document title cell.
     */
    private static final short TITLE_FONT_SIZE = 14;

    /**
     * Font size, in points, of the header row and data cells.
     */
    private static final short CELL_FONT_SIZE = 11;

    /**
     * RGB color of every cell border.
     */
    private static final byte[] BORDER_COLOR = {(byte) 140, (byte) 140, (byte) 140};

    /**
     * RGB background fill of the header row.
     */
    private static final byte[] HEADER_FILL = {(byte) 230, (byte) 230, (byte) 230};

    /**
     * RGB background fill of alternating ("banded") data rows.
     */
    private static final byte[] BAND_FILL = {(byte) 247, (byte) 247, (byte) 247};

    /**
     * Writes {@code rows} to an Excel worksheet at {@code output}, with one column per
     * entry in {@code headers} and one sheet row per element of {@code rows}. Every
     * cell is bordered, the header row is bold with a shaded background, and data rows
     * are banded on alternating lines, matching the look of {@link PdfExporter}.
     * Columns are auto-sized to their content and the header row is frozen so it stays
     * visible while scrolling.
     *
     * @param <T> the type of the exported rows
     * @param rows the data set to export, in the order it should appear
     * @param headers the column headers, in display order
     * @param rowMapper turns a row into one cell value per header, in the same order as {@code headers}
     * @param title the document title, printed above the table and used as the sheet name
     * @param output the file path the workbook is written to; overwritten if it already exists
     * @throws IOException if the workbook cannot be written to {@code output}
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code headers} is empty
     */
    @Override
    public <T> void export(final List<T> rows,
                            final List<String> headers,
                            final Function<T, List<String>> rowMapper,
                            final String title,
                            final Path output) throws IOException {

        Objects.requireNonNull(rows, "@ExcelExporter.export: rows must not be null");
        Objects.requireNonNull(headers, "@ExcelExporter.export: headers must not be null");
        Objects.requireNonNull(rowMapper, "@ExcelExporter.export: rowMapper must not be null");
        Objects.requireNonNull(title, "@ExcelExporter.export: title must not be null");
        Objects.requireNonNull(output, "@ExcelExporter.export: output must not be null");

        if (headers.isEmpty()) throw new IllegalArgumentException("@ExcelExporter.export: headers must not be empty");

        final Path outputPath = Constraints.EXPORT_PATH.resolve(output.getFileName());

        if (Files.exists(outputPath)) Files.delete(outputPath);
        if (!Files.exists(Constraints.EXPORT_PATH)) FileProvider.getInstance().createDirectory(Constraints.EXPORT_PATH);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {

            final Sheet sheet = workbook.createSheet(title);

            final XSSFCellStyle titleStyle = createTitleStyle(workbook);
            final XSSFCellStyle headerStyle = createRowStyle(workbook, true, HEADER_FILL);
            final XSSFCellStyle cellStyle = createRowStyle(workbook, false, null);
            final XSSFCellStyle bandedStyle = createRowStyle(workbook, false, BAND_FILL);

            final Row titleRow = sheet.createRow(0);
            final Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(title);
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, headers.size() - 1));

            final int headerRowIndex = 2;
            writeRow(sheet.createRow(headerRowIndex), headers, headerStyle);

            int rowIndex = headerRowIndex + 1;
            int dataIndex = 0;

            for (final T row : rows) {
                writeRow(sheet.createRow(rowIndex++), rowMapper.apply(row), dataIndex++ % 2 == 1 ? bandedStyle : cellStyle);
            }

            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            sheet.createFreezePane(0, headerRowIndex + 1);

            try (OutputStream out = Files.newOutputStream(outputPath)) {
                workbook.write(out);
            }

        }

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
     * Builds the bold, unbordered style used for the title cell.
     *
     * @param workbook the workbook to create the style and font in
     * @return the title cell style
     */
    private static XSSFCellStyle createTitleStyle(final XSSFWorkbook workbook) {

        final Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints(TITLE_FONT_SIZE);

        final XSSFCellStyle style = workbook.createCellStyle();
        style.setFont(font);

        return style;

    }

    /**
     * Builds a bordered, vertically-centered style for a header or data row, with an
     * optional background fill.
     *
     * @param workbook the workbook to create the style and font in
     * @param bold whether the row's text should be bold, i.e. whether it is the header row
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

}
