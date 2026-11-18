package vn.antam.clinic.service.support.document;

import com.fasterxml.jackson.databind.*;
import java.awt.Color;
import java.io.*;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import vn.antam.clinic.service.support.ClinicJdbc;

@Component
public class DocumentRenderer {

    private static final String ROOT = "templates/documents/v1/";
    private final ObjectMapper json = new ObjectMapper();

    public JsonNode template(String type) {
        try {
            var manifest = json.readTree(
                new ClassPathResource(ROOT + "manifest.json").getInputStream()
            );
            for (var item : manifest.path("templates"))
                if (item.path("type").asText().equals(type)) return json.readTree(
                    new ClassPathResource(ROOT + item.path("file").asText()).getInputStream()
                );
            throw ClinicJdbc.bad("Loại tài liệu không hợp lệ.");
        } catch (IOException e) {
            throw new IllegalStateException("Không đọc được mẫu BE", e);
        }
    }

    public byte[] render(JsonNode template, Map<String, Object> snapshot) {
        return vn.antam.clinic.config.BulkheadConfig.pdf(() -> renderLimited(template, snapshot));
    }

    private byte[] renderLimited(JsonNode template, Map<String, Object> snapshot) {
        try (PDDocument document = new PDDocument()) {
            if (
                !template.path("page").path("size").asText().equals("A4") ||
                !template.path("page").path("orientation").asText().equals("PORTRAIT")
            ) throw new IllegalStateException("Mẫu giấy phải là A4 dọc.");
            PDFont font = PDType0Font.load(
                document,
                new ClassPathResource(ROOT + "assets/NotoSans-Regular.ttf").getInputStream()
            );
            var writer = new Writer(document, font, template, snapshot);
            writer.page();
            for (var f : template.path("metadata")) writer.field(f, snapshot);
            writer.heading("Thông tin người bệnh");
            for (var f : template.path("patientFields")) writer.field(f, snapshot);
            writer.heading("Thông tin phiếu");
            for (var f : template.path("fields")) writer.field(f, snapshot);
            var table = template.path("table");
            if (!table.isNull() && !table.isMissingNode()) {
                Object raw = snapshot.get(table.path("rowsKey").asText());
                if (raw instanceof List<?> rows && !rows.isEmpty()) {
                    writer.tableHeader(table);
                    for (Object value : rows) {
                        if (!(value instanceof Map<?, ?> row)) continue;
                        List<String> cells = new ArrayList<>();
                        for (var column : table.path("columns"))
                            cells.add(Objects.toString(row.get(column.path("key").asText()), ""));
                        writer.tableRow(table, cells);
                    }
                }
            }
            for (var section : template.path("sections")) {
                String value = Objects.toString(snapshot.get(section.path("key").asText()), "");
                if (!value.isBlank()) {
                    writer.heading(section.path("label").asText());
                    writer.paragraph(value, 10);
                }
            }
            writer.ensure(95);
            writer.y -= 16;
            StringJoiner signatures = new StringJoiner("                         ");
            for (var signature : template.path("signatures")) signatures.add(signature.asText());
            writer.paragraph(signatures.toString(), 10);
            writer.paragraph("(Ký, ghi rõ họ tên)", 9);
            writer.y -= 36;
            writer.paragraph(template.path("footer").asText(), 8);
            writer.close();
            int count = document.getNumberOfPages();
            for (int i = 0; i < count; i++) {
                try (
                    var stream = new PDPageContentStream(
                        document,
                        document.getPage(i),
                        PDPageContentStream.AppendMode.APPEND,
                        true,
                        true
                    )
                ) {
                    stream.beginText();
                    stream.setFont(font, 8);
                    stream.newLineAtOffset(43, 25);
                    stream.showText(
                        "Minh An · " +
                            Objects.toString(snapshot.get("document.code"), "") +
                            " · Mẫu " +
                            template.path("templateVersion").asText() +
                            " · Trang " +
                            (i + 1) +
                            " / " +
                            count
                    );
                    stream.endText();
                }
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Không tạo được PDF", e);
        }
    }

    private static class Writer {

        final PDDocument doc;
        final PDFont font;
        final JsonNode template;
        final Map<String, Object> data;
        PDPageContentStream stream;
        float y;
        final float left, width, top;

        Writer(PDDocument doc, PDFont font, JsonNode template, Map<String, Object> data) {
            this.doc = doc;
            this.font = font;
            this.template = template;
            this.data = data;
            left = ((float) template.path("page").path("marginMm").asDouble(15) * 72) / 25.4f;
            if (left < 20 || left > 100) throw new IllegalStateException(
                "Lề mẫu giấy không hợp lệ."
            );
            width = PDRectangle.A4.getWidth() - 2 * left;
            top = PDRectangle.A4.getHeight() - left - 4;
        }

        void close() throws IOException {
            if (stream != null) stream.close();
            stream = null;
        }

        void page() throws IOException {
            close();
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            stream = new PDPageContentStream(doc, page);
            y = top;
            String logo = template.path("letterhead").path("logo").asText().replace(".svg", ".png");
            try (var source = new ClassPathResource(ROOT + logo).getInputStream()) {
                var logoImage = PDImageXObject.createFromByteArray(
                    doc,
                    source.readAllBytes(),
                    "Minh An"
                );
                stream.drawImage(
                    logoImage,
                    left,
                    top - 37,
                    (37f * logoImage.getWidth()) / logoImage.getHeight(),
                    37
                );
            }
            Object qr = data.get("document.businessQrImage");
            if (qr instanceof String img && !img.isBlank()) {
                byte[] bytes = Base64.getDecoder().decode(img.substring(img.indexOf(',') + 1));
                float size = ((float) template.path("qr").path("sizeMm").asDouble(24) * 72) / 25.4f;
                stream.drawImage(
                    PDImageXObject.createFromByteArray(doc, bytes, "QR nghiệp vụ"),
                    left + width - size - 6,
                    top - size,
                    size,
                    size
                );
            }
            y = top - 54;
            paragraph(Objects.toString(data.get("clinic.name"), "Minh An"), 9);
            paragraph(Objects.toString(data.get("clinic.address"), ""), 8);
            paragraph(
                "Điện thoại: " +
                    Objects.toString(data.get("clinic.phone"), "") +
                    " · " +
                    Objects.toString(data.get("clinic.email"), ""),
                8
            );
            y -= 8;
            stream.setStrokingColor(new Color(15, 110, 98));
            stream.moveTo(left, y);
            stream.lineTo(left + width, y);
            stream.stroke();
            y -= 23;
            paragraph(template.path("title").asText(), 16);
            if (Boolean.TRUE.equals(data.get("draft"))) paragraph(
                template.path("draftWatermark").asText() + " — CHƯA CHỐT / CHƯA CÔNG BỐ",
                12
            );
        }

        void ensure(float height) throws IOException {
            if (y - height < 58) page();
        }

        void heading(String value) throws IOException {
            ensure(36);
            y -= 8;
            stream.setNonStrokingColor(new Color(15, 110, 98));
            line(value, 11, left, y);
            stream.setNonStrokingColor(Color.BLACK);
            y -= 17;
        }

        void field(JsonNode field, Map<String, Object> source) throws IOException {
            Object value = source.get(field.path("key").asText());
            if (value != null && !value.toString().isBlank()) paragraph(
                field.path("label").asText() + ": " + value,
                9
            );
        }

        void line(String value, float size, float x, float top) throws IOException {
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(x, top);
            stream.showText(clean(value));
            stream.endText();
        }

        String clean(String value) {
            StringBuilder out = new StringBuilder();
            value.codePoints().forEach(c -> {
                if (Character.isISOControl(c)) {
                    out.append(' ');
                    return;
                }
                String s = new String(Character.toChars(c));
                try {
                    font.encode(s);
                    out.append(s);
                } catch (Exception e) {
                    out.append('?');
                }
            });
            return out.toString();
        }

        List<String> wrap(String value, float max, float size) throws IOException {
            List<String> result = new ArrayList<>();
            for (String raw : value.split("\\R", -1)) {
                String current = "";
                for (String word : clean(raw).trim().split("\\s+")) {
                    String candidate = current.isEmpty() ? word : current + " " + word;
                    if ((font.getStringWidth(candidate) / 1000) * size <= max) {
                        current = candidate;
                        continue;
                    }
                    if (!current.isEmpty()) {
                        result.add(current);
                        current = "";
                    }
                    for (int cp : word.codePoints().toArray()) {
                        candidate = current + new String(Character.toChars(cp));
                        if (
                            !current.isEmpty() &&
                            (font.getStringWidth(candidate) / 1000) * size > max
                        ) {
                            result.add(current);
                            current = new String(Character.toChars(cp));
                        } else current = candidate;
                    }
                }
                result.add(current);
            }
            return result;
        }

        void paragraph(String value, float size) throws IOException {
            for (String row : wrap(value, width, size)) {
                ensure(size + 7);
                line(row, size, left, y);
                y -= size + 5;
            }
        }

        void tableHeader(JsonNode table) throws IOException {
            List<String> headings = new ArrayList<>();
            for (var c : table.path("columns")) headings.add(c.path("label").asText());
            drawRow(table, headings, true);
        }

        void tableRow(JsonNode table, List<String> values) throws IOException {
            int n = values.size();
            float col = width / n;
            List<List<String>> wrapped = new ArrayList<>();
            for (String value : values) wrapped.add(wrap(value, col - 10, 8));
            int count = wrapped.stream().mapToInt(List::size).max().orElse(1);
            int offset = 0;
            while (offset < count) {
                if (y < 90) {
                    page();
                    tableHeader(table);
                }
                int chunk = Math.min(count - offset, Math.max(1, (int) ((y - 65) / 12) - 1));
                List<String> segment = new ArrayList<>();
                for (var cell : wrapped)
                    segment.add(
                        String.join(
                            "\n",
                            cell.subList(
                                Math.min(offset, cell.size()),
                                Math.min(offset + chunk, cell.size())
                            )
                        )
                    );
                drawRow(table, segment, false);
                offset += chunk;
            }
        }

        void drawRow(JsonNode table, List<String> values, boolean header) throws IOException {
            int n = values.size();
            float col = width / n;
            List<List<String>> rows = new ArrayList<>();
            for (String v : values) rows.add(wrap(v, col - 10, 8));
            float height = rows.stream().mapToInt(List::size).max().orElse(1) * 12 + 10;
            ensure(height);
            stream.setStrokingColor(new Color(180, 202, 190));
            for (int i = 0; i < n; i++) {
                stream.addRect(left + i * col, y - height, col, height);
                stream.stroke();
                float top = y - 12;
                for (String v : rows.get(i)) {
                    line(v, 8, left + i * col + 5, top);
                    top -= 12;
                }
            }
            y -= height;
        }
    }
}
