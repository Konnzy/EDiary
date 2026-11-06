package com.ediary.backend.common.importfile;

import com.ediary.backend.common.error.ApplicationException;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipInputStream;
import org.apache.commons.csv.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;

public record TabularImportFile(List<String> columns, int maxRows, String limitMessage, String emptyMessage) {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    public record RawRow(int number, List<String> values) {}

    public List<RawRow> parse(byte[] bytes, String format) {
        if (bytes.length == 0) throw invalid("Оберіть непорожній файл.");
        if (bytes.length > MAX_BYTES) throw invalid("Розмір файлу не має перевищувати 5 МБ.");
        try {
            return switch (format) {
                case "csv" -> csv(bytes);
                case "xlsx" -> xlsx(bytes);
                default -> throw invalid("Підтримуються лише CSV та XLSX.");
            };
        } catch (ApplicationException error) { throw error; }
        catch (IOException | RuntimeException error) {
            throw invalid("Не вдалося прочитати файл. Перевірте формат, кодування UTF-8 і структуру шаблону.");
        }
    }

    private List<RawRow> csv(byte[] bytes) throws IOException {
        String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString();
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        char separator = ';';
        for (char candidate : new char[]{';', ','}) {
            try (var parser = csvFormat(candidate).parse(new StringReader(text))) {
                var iterator = parser.iterator();
                if (iterator.hasNext() && iterator.next().size() == columns.size()) { separator = candidate; break; }
            }
        }
        List<RawRow> rows = new ArrayList<>();
        try (var parser = csvFormat(separator).parse(new StringReader(text))) {
            var iterator = parser.iterator();
            if (!iterator.hasNext()) throw invalid("Файл не містить заголовків.");
            List<Integer> mapping = headers(iterator.next().toList());
            int record = 1;
            while (iterator.hasNext()) {
                var item = iterator.next(); record++;
                if (item.stream().allMatch(String::isBlank)) continue;
                if (item.size() != columns.size()) throw invalid("Рядок " + record + ": кількість колонок не відповідає шаблону.");
                add(rows, record, mapping.stream().map(item::get).toList());
            }
        }
        return requireRows(rows);
    }

    private CSVFormat csvFormat(char separator) {
        return CSVFormat.RFC4180.builder().setDelimiter(separator).setIgnoreEmptyLines(false).get();
    }

    private List<RawRow> xlsx(byte[] bytes) throws IOException {
        // Bound decompressed OOXML before POI builds its object model; never extract files.
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            long total = 0; int entries = 0; byte[] buffer = new byte[8192];
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (++entries > 1000 || entry.getName().toLowerCase(Locale.ROOT).contains("vbaproject"))
                    throw invalid("Файл містить макроси або забагато складових. Скористайтеся шаблоном.");
                for (int count; (count = zip.read(buffer)) != -1;) {
                    total += count;
                    if (total > 20 * 1024 * 1024) throw invalid("Розпакований XLSX завеликий. Скористайтеся шаблоном.");
                }
            }
            if (entries == 0) throw invalid("Файл не є коректним XLSX.");
        }
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            if (book.getNumberOfSheets() != 1) throw invalid("XLSX має містити лише один аркуш.");
            var sheet = book.getSheetAt(0);
            if (sheet.getLastRowNum() > maxRows + 20) throw invalid(limitMessage);
            List<Integer> mapping = headers(cells(sheet.getRow(0), 1));
            List<RawRow> rows = new ArrayList<>();
            for (int index = 1; index <= sheet.getLastRowNum(); index++) {
                List<String> values = cells(sheet.getRow(index), index + 1);
                if (values.stream().allMatch(String::isBlank)) continue;
                add(rows, index + 1, mapping.stream().map(values::get).toList());
            }
            return requireRows(rows);
        }
    }

    private List<String> cells(Row row, int number) {
        List<String> values = new ArrayList<>();
        if (row != null && row.getLastCellNum() > columns.size()) throw invalid("Рядок " + number + ": зайві колонки.");
        var formatter = new DataFormatter(Locale.forLanguageTag("uk-UA"));
        for (int index = 0; index < columns.size(); index++) {
            Cell cell = row == null ? null : row.getCell(index);
            if (cell != null && (cell.getCellType() == CellType.FORMULA || cell.getCellType() == CellType.ERROR))
                throw invalid("Рядок " + number + ": формули та помилки Excel не підтримуються.");
            values.add(cell != null && cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)
                ? cell.getLocalDateTimeCellValue().toLocalDate().format(DateTimeFormatter.ofPattern("dd.MM.uuuu"))
                : formatter.formatCellValue(cell));
        }
        return values;
    }

    private List<Integer> headers(List<String> values) {
        var names = values.stream().map(TabularImportFile::header).toList();
        if (names.size() != columns.size() || new HashSet<>(names).size() != columns.size()
            || !new HashSet<>(names).equals(new HashSet<>(columns.stream().map(TabularImportFile::header).toList())))
            throw invalid("Заголовки мають відповідати шаблону: " + String.join(", ", columns) + ".");
        return columns.stream().map(name -> names.indexOf(header(name))).toList();
    }
    private static String header(String value) {
        return value.strip().replace('’', '\'').replace('ʼ', '\'').toLowerCase(Locale.ROOT);
    }
    private void add(List<RawRow> rows, int number, List<String> values) {
        if (rows.size() >= maxRows) throw invalid(limitMessage);
        if (values.stream().anyMatch(v -> v.length() > 500)) throw invalid("Рядок " + number + ": значення поля задовге.");
        rows.add(new RawRow(number, values.stream().map(String::strip).toList()));
    }
    private List<RawRow> requireRows(List<RawRow> rows) {
        if (rows.isEmpty()) throw invalid(emptyMessage);
        return rows;
    }
    public byte[] template(String format, String sheetName, List<List<String>> examples) {
        var rows = new ArrayList<List<String>>();
        rows.add(columns);
        rows.addAll(examples);
        try (var output = new ByteArrayOutputStream()) {
            if (format.equals("csv")) {
                var text = new StringWriter();
                try (var printer = new CSVPrinter(text, csvFormat(';'))) { printer.printRecords(rows); }
                return ("\uFEFF" + text).getBytes(StandardCharsets.UTF_8);
            }
            if (!format.equals("xlsx")) throw invalid("Підтримуються лише CSV та XLSX.");
            try (var book = new XSSFWorkbook()) {
                var sheet = book.createSheet(sheetName);
                var text = book.createCellStyle(); text.setDataFormat(book.createDataFormat().getFormat("@"));
                var heading = book.createCellStyle(); var font = book.createFont(); font.setBold(true); heading.setFont(font);
                for (int col = 0; col < columns.size(); col++) {
                    int index = col;
                    int width = rows.stream().mapToInt(row -> row.get(index).length()).max().orElse(20);
                    sheet.setDefaultColumnStyle(col, text);
                    sheet.setColumnWidth(col, Math.max(14, Math.min(32, width + 4)) * 256);
                }
                for (int row = 0; row < rows.size(); row++) {
                    var item = sheet.createRow(row);
                    for (int col = 0; col < columns.size(); col++) {
                        var cell = item.createCell(col); cell.setCellValue(rows.get(row).get(col));
                        cell.setCellStyle(row == 0 ? heading : text);
                    }
                }
                book.write(output);
            }
            return output.toByteArray();
        } catch (IOException error) { throw new IllegalStateException("Cannot build import template", error); }
    }
    private ApplicationException invalid(String message) { return new ApplicationException(HttpStatus.BAD_REQUEST, "INVALID_IMPORT_FILE", message); }
}
