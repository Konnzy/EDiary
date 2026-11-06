package com.ediary.backend.user.admin.service;

import com.ediary.backend.common.importfile.TabularImportFile;
import com.ediary.backend.user.admin.dto.UserImportDtos.RawRow;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserImportFileService {
    public static final int MAX_BYTES = TabularImportFile.MAX_BYTES;
    public static final List<String> COLUMNS = List.of("Роль", "Логін", "Прізвище", "Ім’я", "По батькові",
        "Електронна пошта", "Телефон", "Дата народження", "Клас", "Навчальний рік", "Предмет", "Логін учня");
    private static final TabularImportFile FILE = new TabularImportFile(COLUMNS, 200,
        "Максимум 200 користувачів за один імпорт.", "Додайте хоча б одного користувача після заголовків.");
    private final Clock clock;

    public List<RawRow> parse(byte[] bytes, String format) {
        return FILE.parse(bytes, format).stream().map(row -> new RawRow(row.number(), row.values())).toList();
    }

    public byte[] template(String format) {
        var today = LocalDate.now(clock);
        int start = today.getMonthValue() >= 9 ? today.getYear() : today.getYear() - 1;
        return FILE.template(format, "Користувачі", List.of(
            List.of("Учень", "student.example", "Петренко", "Іван", "Олександрович",
                "student@example.org", "", "15.04." + (today.getYear() - 15), "9-А", start + "/" + (start + 1), "", ""),
            List.of("Вчитель", "teacher.example", "Коваленко", "Олена", "Іванівна",
                "teacher@example.org", "+380671112234", "20.03.1985", "", "", "Математика", ""),
            List.of("Батьки", "parent.example", "Петренко", "Олександр", "Іванович",
                "parent@example.org", "+380671112235", "10.05.1980", "", "", "", "student.example")
        ));
    }
}
