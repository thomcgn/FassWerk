package org.thomcgn.backend.qr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thomcgn.backend.common.exception.BadRequestException;

import java.io.ByteArrayOutputStream;

@Service
@RequiredArgsConstructor
public class QrCodeService {

    private static final int DEFAULT_WIDTH = 320;
    private static final int DEFAULT_HEIGHT = 320;

    public byte[] generateQrPng(String content) {
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, DEFAULT_WIDTH, DEFAULT_HEIGHT);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", outputStream);
            return outputStream.toByteArray();
        } catch (Exception exception) {
            throw new BadRequestException("QR code generation failed");
        }
    }
}

