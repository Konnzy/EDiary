package com.ediary.backend.user.profile.service;

import com.ediary.backend.common.error.ApplicationException;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

final class AvatarImage {
    static final String CONTENT_TYPE = "image/jpeg";
    private static final long MAX_INPUT_BYTES = 5L * 1024 * 1024;
    private static final long MAX_PIXELS = 20_000_000;
    private static final int SIDE = 512;
    private static final int MAX_OUTPUT_BYTES = 500 * 1024;

    private AvatarImage() {}

    static byte[] normalize(MultipartFile file) {
        if (file == null || file.isEmpty()) throw invalidImage();
        if (file.getSize() > MAX_INPUT_BYTES) {
            throw new ApplicationException(HttpStatus.CONTENT_TOO_LARGE, "AVATAR_TOO_LARGE",
                "Розмір зображення не має перевищувати 5 МБ");
        }

        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(file.getBytes()))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalidImage();
            var reader = readers.next();
            try {
                String type = switch (reader.getFormatName()) {
                    case "JPEG" -> CONTENT_TYPE;
                    case "png" -> "image/png";
                    default -> null;
                };
                if (type == null || !type.equals(file.getContentType())) throw invalidImage();
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0) throw invalidImage();
                if ((long) width * height > MAX_PIXELS) {
                    throw new ApplicationException(HttpStatus.BAD_REQUEST, "AVATAR_DIMENSIONS_TOO_LARGE",
                        "Роздільна здатність фото не має перевищувати 20 мегапікселів");
                }
                int side = Math.min(width, height);
                var parameters = reader.getDefaultReadParam();
                parameters.setSourceRegion(new Rectangle((width - side) / 2, (height - side) / 2, side, side));
                int sampling = Math.max(1, side / (2 * SIDE));
                parameters.setSourceSubsampling(sampling, sampling, 0, 0);
                return encode(reader.read(0, parameters));
            } finally {
                reader.dispose();
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw invalidImage();
        }
    }

    private static byte[] encode(BufferedImage source) throws IOException {
        var resized = new BufferedImage(SIDE, SIDE, BufferedImage.TYPE_INT_RGB);
        var graphics = resized.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, SIDE, SIDE);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(source, 0, 0, SIDE, SIDE, null);
        } finally {
            graphics.dispose();
        }

        var writer = ImageIO.getImageWritersByFormatName("JPEG").next();
        try {
            var parameters = writer.getDefaultWriteParam();
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            for (float quality : new float[] {0.92f, 0.75f, 0.6f}) {
                parameters.setCompressionQuality(quality);
                var bytes = new ByteArrayOutputStream();
                try (var output = new MemoryCacheImageOutputStream(bytes)) {
                    writer.setOutput(output);
                    writer.write(null, new IIOImage(resized, null, null), parameters);
                }
                if (bytes.size() <= MAX_OUTPUT_BYTES) return bytes.toByteArray();
            }
        } finally {
            writer.dispose();
        }
        throw new ApplicationException(HttpStatus.BAD_REQUEST, "AVATAR_OUTPUT_TOO_LARGE",
            "Не вдалося стиснути фото до 500 КБ");
    }

    private static ApplicationException invalidImage() {
        return new ApplicationException(HttpStatus.BAD_REQUEST, "INVALID_AVATAR",
            "Завантажте коректний JPG або PNG розміром до 5 МБ");
    }
}
