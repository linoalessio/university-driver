package de.lino.thma.persistence.export;

import de.lino.thma.persistence.export.excel.ExamTranscriptExcelExporter;
import de.lino.thma.persistence.export.pdf.ExamTranscriptExporter;

import java.util.List;

/**
 * One labeled group of rows, shared by {@link ExamTranscriptExporter} (PDF) and
 * {@link ExamTranscriptExcelExporter} (Excel) so both formats can be built from the
 * same already-grouped, already-formatted input: printed as a heading followed by its
 * own data rows, e.g. one semester's exams.
 *
 * @param title the section's heading, e.g. a semester's name
 * @param rows the section's rows, one cell value per column header, in the same order
 */
public record TranscriptSection(String title, List<List<String>> rows) {
}
