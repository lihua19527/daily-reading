package com.dailyreading.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;
import android.util.Base64;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class ScreenshotRecognition implements AutoCloseable {
    private static final int MAX_ENCODED_LENGTH = 16 * 1024 * 1024 + 128;
    private static final int TILE_HEIGHT = 2600;
    private static final int OVERLAP = 160;
    private final TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

    JSONObject recognize(String name, String dataUrl) throws Exception {
        int comma = dataUrl.indexOf(',');
        if (!dataUrl.startsWith("data:image/") || comma < 0 || !dataUrl.substring(0, comma).endsWith(";base64")) {
            throw new IOException("请选择 PNG、JPG 或 WebP 截图");
        }
        if (dataUrl.length() > MAX_ENCODED_LENGTH) throw new IOException("单张图片请控制在 12 MB 以内");
        byte[] bytes = Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT);
        BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(bytes, 0, bytes.length, false);
        if (decoder == null) throw new IOException("图片无法打开");

        JSONArray lines = new JSONArray();
        try {
            int width = decoder.getWidth();
            int height = decoder.getHeight();
            int sample = 1;
            while (width / sample > 2400) sample *= 2;
            int tileHeight = TILE_HEIGHT * sample;
            int overlap = OVERLAP * sample;
            if ((long) height > (long) tileHeight * 20) throw new IOException("截图太长，请截成几张再识别");

            for (int top = 0; top < height; top += tileHeight - overlap) {
                int bottom = Math.min(top + tileHeight, height);
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = sample;
                Bitmap tile = decoder.decodeRegion(new Rect(0, top, width, bottom), options);
                if (tile == null) throw new IOException("图片无法打开");
                try {
                    Text text = Tasks.await(recognizer.process(InputImage.fromBitmap(tile, 0)));
                    int minY = top == 0 ? 0 : OVERLAP / 2;
                    int maxY = bottom == height ? tile.getHeight() : tile.getHeight() - OVERLAP / 2;
                    appendLines(lines, text, minY, maxY);
                } finally {
                    tile.recycle();
                }
                if (bottom == height) break;
            }
        } finally {
            decoder.recycle();
        }
        return new JSONObject().put("file", name).put("lines", lines);
    }

    private static void appendLines(JSONArray result, Text text, int minY, int maxY) throws Exception {
        List<Text.Line> lines = new ArrayList<>();
        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                Rect box = line.getBoundingBox();
                if (box != null && box.centerY() >= minY && box.centerY() < maxY) lines.add(line);
            }
        }
        lines.sort(Comparator.comparingInt(line -> line.getBoundingBox().top));
        for (Text.Line line : lines) {
            JSONArray words = new JSONArray();
            for (Text.Element element : line.getElements()) {
                Rect box = element.getBoundingBox();
                if (box != null) {
                    String word = element.getText().replaceAll("^[^A-Za-z]+|[^A-Za-z]+$", "");
                    if (!word.isEmpty()) words.put(new JSONObject().put("t", word).put("h", box.height()));
                }
            }
            result.put(new JSONObject().put("text", line.getText()).put("words", words));
        }
    }

    @Override
    public void close() {
        recognizer.close();
    }
}
