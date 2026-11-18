package vn.antam.clinic.service.support;

import static vn.antam.clinic.service.support.ClinicJdbc.*;
import static vn.antam.clinic.service.support.WorkflowSupport.*;

import com.google.zxing.*;
import com.google.zxing.qrcode.QRCodeWriter;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;
import javax.imageio.ImageIO;

/** Stateless formatting and validation shared by HTTP and business layers. */
public final class QrImages {

    private QrImages() {}

    public static String image(String payload) {
        try {
            var matrix = new QRCodeWriter().encode(
                payload,
                BarcodeFormat.QR_CODE,
                320,
                320,
                Map.of(EncodeHintType.CHARACTER_SET, "UTF-8", EncodeHintType.MARGIN, 2)
            );
            BufferedImage img = new BufferedImage(320, 320, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < 320; y++) for (int x = 0; x < 320; x++) img.setRGB(
                x,
                y,
                matrix.get(x, y) ? 0xff000000 : 0xffffffff
            );
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(img, "png", bytes);
            return (
                "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray())
            );
        } catch (Exception e) {
            throw new IllegalStateException("Không tạo được QR", e);
        }
    }
}
