package com.ediary.backend.user.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ediary.backend.common.error.ApplicationException;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class AvatarImageTest {
    @Test
    void rejectsMissingEmptyOversizedAndUnreadableUploads() {
        assertThatThrownBy(() -> AvatarImage.normalize(null)).isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> AvatarImage.normalize(new MockMultipartFile("file",new byte[0])))
            .isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> AvatarImage.normalize(new MockMultipartFile("file","large.jpg","image/jpeg",new byte[5*1024*1024+1])))
            .isInstanceOf(ApplicationException.class).hasMessageContaining("5 МБ");
        assertThatThrownBy(() -> AvatarImage.normalize(new MockMultipartFile("file","bad.jpg","image/jpeg",new byte[]{1,2,3})))
            .isInstanceOf(ApplicationException.class);
    }

    @Test
    void validatesTheActualImageFormatRatherThanTrustingTheFilename() throws Exception {
        var image=new BufferedImage(24,40,BufferedImage.TYPE_INT_RGB);
        var png=new ByteArrayOutputStream();ImageIO.write(image,"png",png);
        assertThatThrownBy(() -> AvatarImage.normalize(new MockMultipartFile("file","fake.jpg","image/jpeg",png.toByteArray())))
            .isInstanceOf(ApplicationException.class);
        var gif=new ByteArrayOutputStream();ImageIO.write(image,"gif",gif);
        assertThatThrownBy(() -> AvatarImage.normalize(new MockMultipartFile("file","image.gif","image/gif",gif.toByteArray())))
            .isInstanceOf(ApplicationException.class);
        var jpeg=new ByteArrayOutputStream();ImageIO.write(image,"JPEG",jpeg);
        var normalized=ImageIO.read(new ByteArrayInputStream(AvatarImage.normalize(
            new MockMultipartFile("file","portrait.jpg","image/jpeg",jpeg.toByteArray()))));
        assertThat(normalized.getWidth()).isEqualTo(512);
        assertThat(normalized.getHeight()).isEqualTo(512);
    }
    @Test
    void normalizes4kToACompactSquare() throws Exception {
        var source = new BufferedImage(3840, 2160, BufferedImage.TYPE_INT_RGB);
        var graphics = source.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(840, 0, 2160, 2160);
        graphics.dispose();
        var png = new ByteArrayOutputStream();
        ImageIO.write(source, "png", png);

        byte[] bytes = AvatarImage.normalize(new MockMultipartFile("file", "4k.png", "image/png", png.toByteArray()));

        assertThat(bytes.length).isLessThanOrEqualTo(500 * 1024);
        assertThat(bytes).startsWith((byte) 0xff, (byte) 0xd8);
        var image = ImageIO.read(new ByteArrayInputStream(bytes));
        assertThat(image.getWidth()).isEqualTo(512);
        assertThat(image.getHeight()).isEqualTo(512);
        assertThat(new Color(image.getRGB(0, 256)).getRed()).isGreaterThan(240);
    }

    @Test
    void rejectsExcessiveDimensionsFromTheHeader() throws Exception {
        var png = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", png);
        byte[] bytes = png.toByteArray();
        var header = ByteBuffer.wrap(bytes);
        header.putInt(16, 5000).putInt(20, 5000);
        var crc = new CRC32();
        crc.update(bytes, 12, 17);
        header.putInt(29, (int) crc.getValue());

        assertThatThrownBy(() -> AvatarImage.normalize(new MockMultipartFile("file", "large.png", "image/png", bytes)))
            .isInstanceOf(ApplicationException.class)
            .hasMessageContaining("20 мегапікселів");
    }
}
