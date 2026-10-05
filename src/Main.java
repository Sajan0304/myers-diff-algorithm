import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class Main {
    static final byte KEEP = 0;
    static final byte DELETE = 1;
    static final byte INSERT = 2;

    static class Line {
        byte[] data;
        int start;
        int end;

        Line(byte[] data, int start, int end) {
            this.data = data;
            this.start = start;
            this.end = end;
        }

        int length() {
            return end - start;
        }
    }

    static class Edit {
        byte type;
        Line line;

        Edit(byte type, Line line) {
            this.type = type;
            this.line = line;
        }
    }

    static class CharEdit {
        byte type;
        int value;

        CharEdit(byte type, int value) {
            this.type = type;
            this.value = value;
        }
    }

    static byte[] readFile(String path) throws IOException {
        return Files.readAllBytes(Path.of(path));
    }

    static List<Line> splitLines(byte[] data) {
        List<Line> lines = new ArrayList<>();
        int start = 0;

        for (int i = 0; i < data.length; i++) {
            if (data[i] == '\n') {
                lines.add(new Line(data, start, i));
                start = i + 1;
            }
        }

        if (start < data.length) {
            lines.add(new Line(data, start, data.length));
        }

        return lines;
    }

    static boolean sameLine(Line a, Line b) {
        int lenA = a.length();
        int lenB = b.length();

        if (lenA != lenB) {
            return false;
        }

        for (int i = 0; i < lenA; i++) {
            if (a.data[a.start + i] != b.data[b.start + i]) {
                return false;
            }
        }

        return true;
    }

    static int[] findCommonPart(List<Line> oldLines, List<Line> newLines) {
        int oldSize = oldLines.size();
        int newSize = newLines.size();

        int prefix = 0;

        while (prefix < oldSize && prefix < newSize &&
                sameLine(oldLines.get(prefix), newLines.get(prefix))) {
            prefix++;
        }

        int suffix = 0;

        while (suffix < oldSize - prefix &&
                suffix < newSize - prefix &&
                sameLine(
                        oldLines.get(oldSize - 1 - suffix),
                        newLines.get(newSize - 1 - suffix))) {
            suffix++;
        }

        return new int[]{prefix, suffix};
    }

    static List<Edit> lineDiff(List<Line> oldLines, List<Line> newLines) {
        int oldSize = oldLines.size();
        int newSize = newLines.size();

        int[] common = findCommonPart(oldLines, newLines);
        int prefix = common[0];
        int suffix = common[1];

        List<Edit> result = new ArrayList<>(oldSize + newSize);

        for (int i = 0; i < prefix; i++) {
            result.add(new Edit(KEEP, oldLines.get(i)));
        }

        int oldStart = prefix;
        int oldEnd = oldSize - suffix;
        int newStart = prefix;
        int newEnd = newSize - suffix;

        int oldCount = oldEnd - oldStart;
        int newCount = newEnd - newStart;

        if (oldCount == 0) {
            for (int i = newStart; i < newEnd; i++) {
                result.add(new Edit(INSERT, newLines.get(i)));
            }
        } else if (newCount == 0) {
            for (int i = oldStart; i < oldEnd; i++) {
                result.add(new Edit(DELETE, oldLines.get(i)));
            }
        } else {
            result.addAll(solveMiddle(oldLines,newLines,oldStart,oldEnd,newStart,newEnd));
        }

        for (int i = oldSize - suffix; i < oldSize; i++) {
            result.add(new Edit(KEEP, oldLines.get(i)));
        }

        return result;
    }

    static List<Edit> solveMiddle(List<Line> oldLines,List<Line> newLines,int oldStart,int oldEnd,int newStart,int newEnd) {
        int n = oldEnd - oldStart;
        int m = newEnd - newStart;
        int max = n + m;
        int offset = max;

        int[] v = new int[2 * max + 1];
        List<int[]> history = new ArrayList<>();

        int finalD = 0;
        boolean found = false;

        for (int d = 0; d <= max && !found; d++) {
            for (int k = -d; k <= d; k += 2) {
                int x;

                if (k == -d || (k != d && v[offset + k - 1] < v[offset + k + 1])) {
                    x = v[offset + k + 1];

                } else {
                    x = v[offset + k - 1] + 1;
                }

                int y = x - k;
                while (x < n && y < m &&
                        sameLine(oldLines.get(oldStart + x),newLines.get(newStart + y))) {
                    x++;
                    y++;
                }

                v[offset + k] = x;
                if (x >= n && y >= m) {
                    finalD = d;
                    found = true;
                    break;
                }
            }

            history.add(v.clone());
        }

        return backtrack(oldLines,newLines,oldStart,newStart,history,finalD,offset,n,m);
    }

    static List<Edit> backtrack(List<Line> oldLines,List<Line> newLines,int oldStart,int newStart,List<int[]> history,int finalD,int offset,int n,int m) {
        List<Edit> result = new ArrayList<>(n + m);
        int x = n;
        int y = m;

        for (int d = finalD; d > 0; d--) {
            int[] previous = history.get(d - 1);
            int k = x - y;
            int previousK;

            if (k == -d || (k != d && previous[offset + k - 1] < previous[offset + k + 1])) {
                previousK = k + 1;
            } else {
                previousK = k - 1;
            }

            int previousX = previous[offset + previousK];
            int previousY = previousX - previousK;

            while (x > previousX && y > previousY) {
                result.add(new Edit(KEEP, oldLines.get(oldStart + x - 1)));
                x--;
                y--;
            }

            if (x == previousX) {
                result.add(new Edit(INSERT,newLines.get(newStart + y - 1)));
                y--;
            } else {
                result.add( new Edit(DELETE,oldLines.get(oldStart + x - 1)));
                x--;
            }
        }

        while (x > 0 && y > 0) {
            result.add(new Edit(KEEP,oldLines.get(oldStart + x - 1)));
            x--;
            y--;
        }

        Collections.reverse(result);
        return result;
    }

    static List<Edit> orderChanges(List<Edit> edits) {
        List<Edit> result = new ArrayList<>(edits.size());
        int i = 0;

        while (i < edits.size()) {
            if (edits.get(i).type == KEEP) {
                result.add(edits.get(i));
                i++;
                continue;
            }

            int start = i;

            while (i < edits.size() && edits.get(i).type != KEEP) {
                i++;
            }

            int end = i;

            for (int j = start; j < end; j++) {
                if (edits.get(j).type == DELETE) {
                    result.add(edits.get(j));
                }
            }

            for (int j = start; j < end; j++) {
                if (edits.get(j).type == INSERT) {
                    result.add(edits.get(j));
                }
            }
        }

        return result;
    }

    static void writeLine(BufferedOutputStream out, byte prefix, Line line)
            throws IOException {
        out.write(prefix);
        out.write(line.data, line.start, line.length());
        out.write('\n');
    }

    static void printLines(List<Edit> edits) throws IOException {
        BufferedOutputStream out =
                new BufferedOutputStream(System.out, 64 * 1024);

        for (Edit edit : edits) {
            if (edit.type == KEEP) {
                writeLine(out, (byte) ' ', edit.line);
            } else if (edit.type == DELETE) {
                writeLine(out, (byte) '-', edit.line);
            } else {
                writeLine(out, (byte) '+', edit.line);
            }
        }

        out.flush();
    }

    static List<CharEdit> characterDiff(int[] oldText, int[] newText) {
        int n = oldText.length;
        int m = newText.length;
        int max = n + m;
        int offset = max;

        int[] v = new int[2 * max + 1];
        List<int[]> history = new ArrayList<>();

        for (int d = 0; d <= max; d++) {
            for (int k = -d; k <= d; k += 2) {
                int x;

                if (k == -d || (k != d && v[offset + k - 1] < v[offset + k + 1])) {
                    x = v[offset + k + 1];
                } else {
                    x = v[offset + k - 1] + 1;
                }
                int y = x - k;
                while (x < n && y < m && oldText[x] == newText[y]) {
                    x++;
                    y++;
                }
                v[offset + k] = x;
                if (x >= n && y >= m) {
                    history.add(v.clone());
                    return backtrackCharacters(oldText, newText,history,d, offset);
                }
            }

            history.add(v.clone());
        }

        return new ArrayList<>();
    }

    static List<CharEdit> backtrackCharacters(int[] oldText,int[] newText, List<int[]> history, int finalD,int offset) {
        List<CharEdit> result = new ArrayList<>();
        int x = oldText.length;
        int y = newText.length;

        for (int d = finalD; d > 0; d--) {
            int[] previous = history.get(d - 1);
            int k = x - y;
            int previousK;

            if (k == -d ||(k != d && previous[offset + k - 1] < previous[offset + k + 1])) {
                previousK = k + 1;
            } else {
                previousK = k - 1;
            }

            int previousX = previous[offset + previousK];
            int previousY = previousX - previousK;

            while (x > previousX && y > previousY) {
                result.add(new CharEdit(KEEP,oldText[x - 1]));
                x--;
                y--;
            }

            if (x == previousX) {
                result.add(new CharEdit(INSERT,newText[y - 1]));
                y--;
            } else {
                result.add(new CharEdit(DELETE,oldText[x - 1]));
                x--;
            }
        }

        while (x > 0 && y > 0) {
            result.add( new CharEdit(KEEP,oldText[x - 1]) );
            x--;
            y--;
        }

        Collections.reverse(result);
        return result;
    }

    static String changedRanges(List<CharEdit> edits) {
        List<int[]> oldRanges = new ArrayList<>();
        List<int[]> newRanges = new ArrayList<>();

        int oldIndex = 0;
        int newIndex = 0;
        int oldStart = -1;
        int newStart = -1;

        for (CharEdit edit : edits) {
            if (edit.type == KEEP) {
                if (oldStart != -1) {
                    oldRanges.add(new int[]{oldStart, oldIndex});
                    oldStart = -1;
                }

                if (newStart != -1) {
                    newRanges.add(new int[]{newStart, newIndex});
                    newStart = -1;
                }

                oldIndex++;
                newIndex++;
            } else if (edit.type == DELETE) {
                if (oldStart == -1) {
                    oldStart = oldIndex;
                }
                oldIndex++;
            } else {
                if (newStart == -1) {
                    newStart = newIndex;
                }
                newIndex++;
            }
        }

        if (oldStart != -1) {
            oldRanges.add(new int[]{oldStart, oldIndex});
        }

        if (newStart != -1) {
            newRanges.add(new int[]{newStart, newIndex});
        }

        return formatRanges(oldRanges) + " | " + formatRanges(newRanges);
    }

    static String formatRanges(List<int[]> ranges) {
        if (ranges.isEmpty()) {
            return ".";
        }

        StringBuilder result = new StringBuilder();

        for (int i = 0; i < ranges.size(); i++) {
            if (i > 0) {
                result.append(",");
            }

            result.append(ranges.get(i)[0])
                    .append("-")
                    .append(ranges.get(i)[1]);
        }

        return result.toString();
    }

    static String lineText(Line line) {
        return new String(line.data,line.start,line.length(),StandardCharsets.UTF_8);
    }

    static String highlightFor(Line oldLine, Line newLine) {
        String oldText = lineText(oldLine);
        String newText = lineText(newLine);

        int[] oldChars = oldText.codePoints().toArray();
        int[] newChars = newText.codePoints().toArray();

        List<CharEdit> edits = characterDiff(oldChars, newChars);
        return changedRanges(edits);
    }

    static void printHighlights(List<Edit> edits) throws IOException {
        BufferedOutputStream out =new BufferedOutputStream(System.out, 64 * 1024);
        int i = 0;
        while (i < edits.size()) {
            if (edits.get(i).type == KEEP) {
                writeLine(out, (byte) ' ', edits.get(i).line);
                i++;
                continue;
            }
            int start = i;
            while (i < edits.size() && edits.get(i).type != KEEP) {
                i++;
            }
            int end = i;
            List<Line> deleted = new ArrayList<>();
            List<Line> inserted = new ArrayList<>();
            for (int j = start; j < end; j++) {
                Edit edit = edits.get(j);
                if (edit.type == DELETE) {
                    deleted.add(edit.line);
                } else {
                    inserted.add(edit.line);
                }
            }

            int pairs = Math.min(deleted.size(), inserted.size());
            for (int j = 0; j < deleted.size(); j++) {
                writeLine(out, (byte) '-', deleted.get(j));
            }
            for (int j = 0; j < inserted.size(); j++) {
                writeLine(out, (byte) '+', inserted.get(j));

                if (j < pairs) {
                    String ranges =highlightFor(deleted.get(j),inserted.get(j));
                    out.write('?');
                    out.write(' ');
                    out.write(ranges.getBytes(StandardCharsets.UTF_8));
                    out.write('\n');
                }
            }
        }

        out.flush();
    }

    public static void main(String[] args) {
        if (args.length != 3 || !(args[0].equals("lines") || args[0].equals("highlight"))) {
            System.err.println("usage: Main lines|highlight A_PATH B_PATH");
            System.exit(2);
        }

        String command = args[0];
        String oldPath = args[1];
        String newPath = args[2];

        try {
            byte[] oldBytes = readFile(oldPath);
            byte[] newBytes = readFile(newPath);

            List<Line> oldLines = splitLines(oldBytes);
            List<Line> newLines = splitLines(newBytes);

            List<Edit> edits = lineDiff(oldLines, newLines);

            edits = orderChanges(edits);

            if (command.equals("lines")) {
                printLines(edits);
            } else {
                printHighlights(edits);
            }
        } catch (IOException e) {
            System.err.println("Could not read input file");
            System.exit(2);
        }
    }
}

