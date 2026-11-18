package vn.antam.clinic.service.support.chatbot;

import java.util.*;

public final class TextChunker {

    private TextChunker() {}

    /** Paragraph boundaries first; bounded 350-word windows with 50-word overlap for long paragraphs. */
    public static List<String> split(String title, String content) {
        List<String> chunks = new ArrayList<>();
        StringBuilder pending = new StringBuilder();
        for (String paragraph : content.strip().split("\\n\\s*\\n|(?m)(?=^#{1,6} )")) {
            String[] words = paragraph.strip().split("\\s+");
            if (paragraph.isBlank()) continue;
            if (words.length > 350) {
                flush(chunks, title, pending);
                for (int from = 0; from < words.length; from += 300) {
                    int to = Math.min(from + 350, words.length);
                    chunks.add(
                        title + "\n" + String.join(" ", Arrays.copyOfRange(words, from, to))
                    );
                    if (to == words.length) break;
                }
            } else {
                if (pending.length() + paragraph.length() > 1800) flush(chunks, title, pending);
                if (!pending.isEmpty()) pending.append("\n\n");
                pending.append(paragraph);
            }
        }
        flush(chunks, title, pending);
        List<String> bounded = new ArrayList<>();
        for (String chunk : chunks) {
            if (chunk.length() <= 3500) {
                bounded.add(chunk);
                continue;
            }
            for (int start = 0; start < chunk.length(); start += 2700) {
                int end = Math.min(start + 3000, chunk.length());
                if (end < chunk.length() && Character.isHighSurrogate(chunk.charAt(end - 1))) end--;
                if (start > 0 && Character.isLowSurrogate(chunk.charAt(start))) start++;
                bounded.add(chunk.substring(start, end));
                if (end == chunk.length()) break;
            }
        }
        return List.copyOf(bounded);
    }

    private static void flush(List<String> out, String title, StringBuilder b) {
        if (!b.isEmpty()) {
            out.add(title + "\n" + b);
            b.setLength(0);
        }
    }
}
