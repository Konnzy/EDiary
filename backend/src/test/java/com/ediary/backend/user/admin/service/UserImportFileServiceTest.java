package com.ediary.backend.user.admin.service;

import com.ediary.backend.common.error.ApplicationException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.apache.commons.csv.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class UserImportFileServiceTest {
    private final UserImportFileService files = new UserImportFileService(Clock.fixed(Instant.parse("2026-11-05T10:00:00Z"),ZoneOffset.UTC));
    @Test void bothTemplatesRoundTripAndPreserveUkrainianTextAndPhone() {
        for (String format : List.of("csv","xlsx")) {
            var rows = files.parse(files.template(format),format);
            assertThat(rows).hasSize(1);
            assertThat(rows.getFirst().number()).isEqualTo(2);
            assertThat(rows.getFirst().values()).contains("Петренко","+380671112233","15.04.2011");
        }
    }
    @Test void csvSupportsCommasQuotesReorderedHeadersBomAndBlankRows() throws Exception {
        var headers = new ArrayList<>(UserImportFileService.COLUMNS); Collections.reverse(headers);
        var data = new ArrayList<>(files.parse(files.template("csv"),"csv").getFirst().values()); Collections.reverse(data);
        var text = new java.io.StringWriter();
        try (var csv = new CSVPrinter(text,CSVFormat.RFC4180)) { csv.printRecord(headers); csv.println(); csv.printRecord(data); }
        var rows = files.parse(("\uFEFF"+text).getBytes(StandardCharsets.UTF_8),"csv");
        assertThat(rows.getFirst().number()).isEqualTo(3);
        assertThat(rows.getFirst().values().get(0)).isEqualTo("Учень");
    }
    @Test void rejectsMalformedFilesAndTooManyRecords() {
        assertThatThrownBy(()->files.parse(new byte[0],"csv")).isInstanceOf(ApplicationException.class);
        assertThatThrownBy(()->files.parse(new byte[UserImportFileService.MAX_BYTES+1],"csv")).hasMessageContaining("5 МБ");
        assertThatThrownBy(()->files.parse("wrong;headers".getBytes(StandardCharsets.UTF_8),"csv")).hasMessageContaining("Заголовки");
        assertThatThrownBy(()->files.parse(new byte[]{(byte)0xff},"csv")).hasMessageContaining("UTF-8");
        assertThatThrownBy(()->files.parse("not a workbook".getBytes(StandardCharsets.UTF_8),"xlsx")).isInstanceOf(ApplicationException.class);
        String template=new String(files.template("csv"),StandardCharsets.UTF_8);
        String[] lines=template.split("\\R");
        assertThatThrownBy(()->files.parse((lines[0]+"\n"+(lines[1]+"\n").repeat(201)).getBytes(StandardCharsets.UTF_8),"csv")).hasMessageContaining("200");
    }
    @Test void rejectsFormulasAndExtraSheets() throws Exception {
        for (String scenario:List.of("formula","extra")) {
            byte[] data;
            try(var book=new XSSFWorkbook(new java.io.ByteArrayInputStream(files.template("xlsx")));var out=new ByteArrayOutputStream()) {
                if(scenario.equals("formula"))book.getSheetAt(0).getRow(1).getCell(1).setCellFormula("1+1");
                if(scenario.equals("extra"))book.createSheet("Hidden");
                book.write(out);data=out.toByteArray();
            }
            assertThatThrownBy(()->files.parse(data,"xlsx")).isInstanceOf(ApplicationException.class);
        }
    }
    @Test void acceptsAnyNameForSingleSheetAndTemplateOnlyUsesBoldHeaders() throws Exception {
        byte[] data;
        try(var book=new XSSFWorkbook(new java.io.ByteArrayInputStream(files.template("xlsx")));var out=new ByteArrayOutputStream()) {
            var sheet=book.getSheetAt(0);
            assertThat(sheet.getPaneInformation()).isNull();
            assertThat(sheet.getCTWorksheet().isSetAutoFilter()).isFalse();
            for(var row:sheet)for(var cell:row) {
                assertThat(cell.getCellStyle().getFillPattern()).isEqualTo(org.apache.poi.ss.usermodel.FillPatternType.NO_FILL);
                assertThat(book.getFontAt(cell.getCellStyle().getFontIndex()).getBold()).isEqualTo(row.getRowNum()==0);
                assertThat(cell.getCellStyle().getBorderBottom()).isEqualTo(org.apache.poi.ss.usermodel.BorderStyle.NONE);
            }
            book.setSheetName(0,"Дані для імпорту");
            book.write(out);data=out.toByteArray();
        }
        assertThat(files.parse(data,"xlsx").getFirst().values().get(2)).isEqualTo("Петренко");
    }
}
