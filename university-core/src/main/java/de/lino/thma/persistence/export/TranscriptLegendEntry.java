package de.lino.thma.persistence.export;

import de.lino.thma.persistence.export.excel.ExamTranscriptExcelExporter;
import de.lino.thma.persistence.export.pdf.ExamTranscriptExporter;

/**
 * One entry of a closing legend, shared by {@link ExamTranscriptExporter} (PDF) and
 * {@link ExamTranscriptExcelExporter} (Excel), e.g. a grading-scale key:
 * {@code label} in a left column, {@code description} in a right column, aligned
 * consistently across every entry.
 *
 * @param label the entry's left-column text, e.g. a grade range
 * @param description the entry's right-column text, e.g. what the grade range means
 */
public record TranscriptLegendEntry(String label, String description) {
}
