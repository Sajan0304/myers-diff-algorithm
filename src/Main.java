import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class Main {
    // Sequences being compared, the result marks, and Myers' V arrays (forward / backward).
    static int[] A, B, vf, vb;
    static boolean[] del, ins;

    // Line diff. A line that never occurs in the other file can't be matched, so it is
    // deleted/inserted directly and Myers runs only on the rest (still minimal, much faster).
    static boolean[][] lineDiff(int[] a, int[] b, int idCount) {
        boolean[] inA = new boolean[idCount], 
        inB = new boolean[idCount];
        for (int x : a){
            inA[x] = true;
        } 
        for (int x : b){ 
            inB[x] = true;
        }
        int[] ka = keep(a, inB), 
        kb = keep(b, inA);   // indices of lines worth diffing

        int[] sa = new int[ka.length], 
        sb = new int[kb.length];

        for (int i = 0; i < ka.length; i++) {
            sa[i] = a[ka[i]];
        }
        for (int j = 0; j < kb.length; j++){ 
            sb[j] = b[kb[j]];
        }
        boolean[][] r = diff(sa, sb);
        boolean[] dl = new boolean[a.length], 
        in = new boolean[b.length];

        Arrays.fill(dl, true);
        Arrays.fill(in, true);

        for (int i = 0; i < ka.length; i++){ 
            dl[ka[i]] = r[0][i];
        }
        for (int j = 0; j < kb.length; j++) {
            in[kb[j]] = r[1][j];
        }
        return new boolean[][]{dl, in};
    }

    static int[] keep(int[] s, boolean[] inOther) {
        int c = 0;
        for (int x : s){ 
            if (inOther[x]){
                c++;
            } 
        }
        int[] idx = new int[c];
        c = 0;
        for (int i = 0; i < s.length; i++) {
            if (inOther[s[i]]) {
                idx[c++] = i;
            }
        }
        return idx;
    }

    // Minimal diff of a -> b. Returns {del, ins}: del[i] = a[i] deleted, ins[j] = b[j] inserted.
    static boolean[][] diff(int[] a, int[] b) {
        A = a; B = b;
        del = new boolean[a.length];
        ins = new boolean[b.length];
        vf = new int[a.length + b.length + 5];
        vb = new int[a.length + b.length + 5];
        solve(0, a.length, 0, b.length);
        return new boolean[][]{del, ins};
    }

    // Linear-space Myers: strip common prefix/suffix, split at a point on a shortest path, recurse.
    static void solve(int x0, int x1, int y0, int y1) {
        while (x0 < x1 && y0 < y1 && A[x0] == B[y0]) { 
            x0++; y0++; 
        }
        while (x0 < x1 && y0 < y1 && A[x1 - 1] == B[y1 - 1]) { 
            x1--; y1--; 
        }
        if (x0 == x1 || y0 == y1) {
            for (int i = x0; i < x1; i++) {
                del[i] = true;
            }
            for (int j = y0; j < y1; j++) {
                ins[j] = true;
            }
            return;
        }
        int[] mid = middle(x0, x1, y0, y1);
        solve(x0, mid[0], y0, mid[1]);
        solve(mid[0], x1, mid[1], y1);
    }

    // Middle snake: run Myers forward from the start and backward from the end at the same time.
    // vf[k] = furthest x on diagonal k = x - y going forward; vb[c] = the same going backward
    // (measured from the end). When they meet, that point lies on a shortest edit path.
    static int[] middle(int x0, int x1, int y0, int y1) {
        int n = x1 - x0, m = y1 - y0, delta = n - m, off = (n + m + 1) / 2 + 1;
        boolean odd = (delta & 1) != 0;
        vf[off + 1] = 0;
        vb[off + 1] = 0;
        for (int d = 0; ; d++) {
            for (int k = -d; k <= d; k += 2) {
                int x = (k == -d || (k != d && vf[off + k - 1] < vf[off + k + 1]))
                        ? vf[off + k + 1]          // step down (insert)
                        : vf[off + k - 1] + 1;     // step right (delete)
                int y = x - k;
                while (x < n && y < m && A[x0 + x] == B[y0 + y]) { x++; y++; }  // follow snake
                vf[off + k] = x;
                int c = delta - k;
                if (odd && c >= 1 - d && c <= d - 1 && x + vb[off + c] >= n)
                    return new int[]{x0 + x, y0 + y};
            }
            for (int c = -d; c <= d; c += 2) {
                int x = (c == -d || (c != d && vb[off + c - 1] < vb[off + c + 1]))
                        ? vb[off + c + 1]
                        : vb[off + c - 1] + 1;
                int y = x - c;
                while (x < n && y < m && A[x1 - 1 - x] == B[y1 - 1 - y]) { x++; y++; }
                vb[off + c] = x;
                int k = delta - c;
                if (!odd && k >= -d && k <= d && x + vf[off + k] >= n)
                    return new int[]{x1 - x, y1 - y};
            }
        }
    }

    // A file split into lines: line i is data[st[i] .. en[i]).
    static class Lines {
        byte[] data; int[] st, en, id; int n;

        Lines(byte[] d, Map<String, Integer> ids) {
            data = d;
            int cnt = 0;
            for (byte b : d) if (b == '\n') cnt++;
            if (d.length > 0 && d[d.length - 1] != '\n') cnt++;
            st = new int[cnt]; en = new int[cnt]; id = new int[cnt];
            int s = 0;
            for (int i = 0; i <= d.length; i++) {
                if (i == d.length ? s < i : d[i] == '\n') {
                    st[n] = s; en[n] = i;
                    // equal byte content -> equal id, so the diff compares ints instead of bytes
                    id[n] = ids.computeIfAbsent(new String(d, s, i - s, StandardCharsets.ISO_8859_1), x -> ids.size());
                    n++;
                    s = i + 1;
                }
            }
        }

        void write(OutputStream out, char prefix, int i) throws IOException {
            out.write(prefix);
            out.write(data, st[i], en[i] - st[i]);
            out.write('\n');
        }

        int[] codePoints(int i) {
            return new String(data, st[i], en[i] - st[i], StandardCharsets.UTF_8).codePoints().toArray();
        }
    }

    // "3-5,9-12" for the runs of true in marks, or "." if none.
    static String ranges(boolean[] marks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < marks.length; i++) {
            if (!marks[i]) continue;
            int s = i;
            while (i < marks.length && marks[i]) i++;
            if (sb.length() > 0) sb.append(',');
            sb.append(s).append('-').append(i);
        }
        return sb.length() == 0 ? "." : sb.toString();
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 3 || !(args[0].equals("lines") || args[0].equals("highlight"))) {
            System.err.println("usage: Main lines|highlight A_PATH B_PATH");
            System.exit(2);
        }
        byte[] da, db;
        try {
            da = Files.readAllBytes(Path.of(args[1]));
            db = Files.readAllBytes(Path.of(args[2]));
        } catch (IOException | RuntimeException e) {
            System.err.println("Could not read input file");
            System.exit(2);
            return;
        }
        boolean highlight = args[0].equals("highlight");
        Map<String, Integer> ids = new HashMap<>();
        Lines a = new Lines(da, ids), b = new Lines(db, ids);
        boolean[][] r = lineDiff(a.id, b.id, ids.size());
        ids = null;
        boolean[] dl = r[0], in = r[1];

        OutputStream out = new BufferedOutputStream(new FileOutputStream(FileDescriptor.out), 1 << 16);
        int i = 0, j = 0;
        while (i < a.n || j < b.n) {
            if (i < a.n && j < b.n && !dl[i] && !in[j]) {   // keep line
                a.write(out, ' ', i++);
                j++;
                continue;
            }
            int i0 = i, j0 = j;                              // change block: all '-' then all '+'
            while (i < a.n && dl[i]) i++;
            while (j < b.n && in[j]) j++;
            for (int p = i0; p < i; p++) a.write(out, '-', p);
            for (int q = j0; q < j; q++) {
                b.write(out, '+', q);
                int p = i0 + (q - j0);                        // paired '-' line
                if (highlight && p < i) {
                    boolean[][] c = diff(a.codePoints(p), b.codePoints(q));
                    out.write(("? " + ranges(c[0]) + " | " + ranges(c[1]) + "\n").getBytes(StandardCharsets.UTF_8));
                }
            }
        }
        out.flush();
    }
}