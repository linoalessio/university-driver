package de.lino.thma.persistence.export.pdf;

import de.lino.database.json.file.FileProvider;
import de.lino.thma.persistence.export.DataExporter;
import de.lino.thma.utility.Constraints;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Exports an arbitrary data set to a PDF table via Apache PDFBox, styled after a
 * standard Microsoft Word table: a shaded, bold header row, thin borders around every
 * cell, and light banding on alternating data rows.
 *
 * <p>Rows can be any type {@code T}; the caller supplies the column headers and a
 * function turning a row into one cell value per header. This keeps the exporter
 * independent of any specific entity, e.g. {@code Semester} or {@code Module}.
 */
public final class PdfExporter implements DataExporter {

    /**
     * Page margin, on all four sides, in PDF points.
     */
    private static final float MARGIN = 50f;

    /**
     * Font size, in points, of the document title.
     */
    private static final float TITLE_FONT_SIZE = 18f;

    /**
     * Vertical gap, in points, between the title and the table's header row.
     */
    private static final float TITLE_GAP = 20f;

    /**
     * Font size, in points, of header and data cells.
     */
    private static final float CELL_FONT_SIZE = 10.5f;

    /**
     * Height, in points, of every table row.
     */
    private static final float ROW_HEIGHT = 22f;

    /**
     * Horizontal padding, in points, between a cell's border and its text.
     */
    private static final float CELL_PADDING_X = 6f;

    /**
     * Width, in points, of every cell border.
     */
    private static final float BORDER_WIDTH = 0.75f;

    /**
     * Color of every cell border.
     */
    private static final Color BORDER_COLOR = new Color(140, 140, 140);

    /**
     * Background color of the header row.
     */
    private static final Color HEADER_FILL = new Color(230, 230, 230);

    /**
     * Background color of alternating ("banded") data rows.
     */
    private static final Color BAND_FILL = new Color(247, 247, 247);

    /**
     * Writes {@code rows} to a PDF table at {@code output}, with one column per
     * entry in {@code headers} and one table row per element of {@code rows}. Columns
     * share the page width evenly; cell text is truncated with an ellipsis if it does
     * not fit. Every cell is bordered, the header row is bold with a shaded
     * background, and data rows are banded on alternating lines, matching the look of
     * a default Microsoft Word table. A new page is started automatically, repeating
     * the header, once the current page runs out of space.
     *
     * @param <T> the type of the exported rows
     * @param rows the data set to export, in the order it should appear
     * @param headers the column headers, in display order
     * @param rowMapper turns a row into one cell value per header, in the same order as {@code headers}
     * @param title the document title, printed above the table
     * @param output the file path the PDF is written to; overwritten if it already exists
     * @throws IOException if the PDF cannot be written to {@code output}
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code headers} is empty
     */
    @Override
    public <T> void export(final List<T> rows,
                            final List<String> headers,
                            final Function<T, List<String>> rowMapper,
                            final String title,
                            final Path output) throws IOException {

        Objects.requireNonNull(rows, "@PdfExporter.export: rows must not be null");
        Objects.requireNonNull(headers, "@PdfExporter.export: headers must not be null");
        Objects.requireNonNull(rowMapper, "@PdfExporter.export: rowMapper must not be null");
        Objects.requireNonNull(title, "@PdfExporter.export: title must not be null");
        Objects.requireNonNull(output, "@PdfExporter.export: output must not be null");

        final Path outputPath = Constraints.EXPORT_PATH.resolve(output.getFileName());

        if (headers.isEmpty()) throw new IllegalArgumentException("@PdfExporter.export: headers must not be empty");

        if (Files.exists(outputPath)) Files.delete(outputPath);
        if (!Files.exists(Constraints.EXPORT_PATH)) FileProvider.getInstance().createDirectory(Constraints.EXPORT_PATH);

        final PDFont titleFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        final PDFont headerFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        final PDFont cellFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

        try (PDDocument document = new PDDocument()) {

            final float columnWidth = (PDRectangle.A4.getWidth() - MARGIN * 2) / headers.size();

            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDPageContentStream stream = new PDPageContentStream(document, page);

            float y = page.getMediaBox().getHeight() - MARGIN;
            writeText(stream, titleFont, TITLE_FONT_SIZE, MARGIN, y, title);
            y -= TITLE_GAP;

            y = writeGridRow(stream, headerFont, MARGIN, y, columnWidth, headers, HEADER_FILL);

            int rowIndex = 0;
            for (final T row : rows) {

                if (y - ROW_HEIGHT < MARGIN) {

                    stream.close();
                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    stream = new PDPageContentStream(document, page);
                    y = page.getMediaBox().getHeight() - MARGIN;
                    y = writeGridRow(stream, headerFont, MARGIN, y, columnWidth, headers, HEADER_FILL);

                }

                final Color band = rowIndex % 2 == 1 ? BAND_FILL : null;
                y = writeGridRow(stream, cellFont, MARGIN, y, columnWidth, rowMapper.apply(row), band);
                rowIndex++;

            }

            stream.close();
            document.save(outputPath.toFile());

        }

    }

    /**
     * Writes one bordered table row: an optional background fill spanning the whole
     * row, a thin border around every cell, and each cell's text truncated to fit and
     * vertically centered within {@link #ROW_HEIGHT}.
     *
     * @param stream the content stream to write to
     * @param font the font to render the cells with
     * @param x0 the horizontal offset of the row's first cell
     * @param y the vertical offset of the row's top edge
     * @param columnWidth the width of each cell
     * @param cells the cell values, in column order
     * @param fillColor the row's background color, or {@code null} for no fill
     * @return the vertical offset of the row's bottom edge, i.e. where the next row starts
     * @throws IOException if the row cannot be written to {@code stream}
     */
    private static float writeGridRow(final PDPageContentStream stream,
                                       final PDFont font,
                                       final float x0,
                                       final float y,
                                       final float columnWidth,
                                       final List<String> cells,
                                       final Color fillColor) throws IOException {

        final float rowBottom = y - ROW_HEIGHT;

        if (fillColor != null) {
            stream.setNonStrokingColor(fillColor);
            stream.addRect(x0, rowBottom, columnWidth * cells.size(), ROW_HEIGHT);
            stream.fill();
        }

        stream.setNonStrokingColor(Color.BLACK);
        stream.setStrokingColor(BORDER_COLOR);
        stream.setLineWidth(BORDER_WIDTH);

        for (int i = 0; i < cells.size(); i++) {

            final float x = x0 + columnWidth * i;

            stream.addRect(x, rowBottom, columnWidth, ROW_HEIGHT);
            stream.stroke();

            final String cell = truncateToWidth(font, CELL_FONT_SIZE, cells.get(i), columnWidth - CELL_PADDING_X * 2);
            final float baseline = rowBottom + (ROW_HEIGHT - CELL_FONT_SIZE) / 2 + CELL_FONT_SIZE * 0.2f;

            writeText(stream, font, CELL_FONT_SIZE, x + CELL_PADDING_X, baseline, cell);

        }

        return rowBottom;

    }

    /**
     * Writes a single line of text at the given baseline position.
     *
     * @param stream the content stream to write to
     * @param font the font to render the text with
     * @param fontSize the font size to render the text with
     * @param x the horizontal offset to write the text at
     * @param y the vertical offset (baseline) to write the text at
     * @param text the text to write
     * @throws IOException if the text cannot be written to {@code stream}
     */
    private static void writeText(final PDPageContentStream stream,
                                   final PDFont font,
                                   final float fontSize,
                                   final float x,
                                   final float y,
                                   final String text) throws IOException {

        stream.beginText();
        stream.setFont(font, fontSize);
        stream.newLineAtOffset(x, y);
        stream.showText(text);
        stream.endText();

    }

    /**
     * Shortens {@code text} with a trailing ellipsis so it fits within {@code maxWidth}
     * when rendered with {@code font} at {@code fontSize}, leaving it unchanged if it
     * already fits.
     *
     * @param font the font the text will be rendered with
     * @param fontSize the font size the text will be rendered with
     * @param text the text to fit
     * @param maxWidth the width to fit the text into
     * @return {@code text}, truncated with {@code "..."} if necessary
     * @throws IOException if {@code font}'s glyph widths cannot be read
     */
    private static String truncateToWidth(final PDFont font, final float fontSize, final String text, final float maxWidth) throws IOException {

        if (font.getStringWidth(text) / 1000 * fontSize <= maxWidth) {
            return text;
        }

        final String ellipsis = "...";
        String truncated = text;

        while (!truncated.isEmpty() && font.getStringWidth(truncated + ellipsis) / 1000 * fontSize > maxWidth) {
            truncated = truncated.substring(0, truncated.length() - 1);
        }

        return truncated + ellipsis;

    }

}
