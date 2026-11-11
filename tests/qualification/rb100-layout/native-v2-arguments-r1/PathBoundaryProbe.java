import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;

/** Read-only path metadata probe for a directory and its junction alias. */
public final class PathBoundaryProbe {
    private static final int MAX_PATH_OUTPUT = 512;

    private PathBoundaryProbe() { }

    public static void main(String[] args) throws IOException {
        if (args == null || args.length != 2)
            throw new IllegalArgumentException("PATH_PROBE_ARGUMENTS");

        Path[] paths = new Path[2];
        Path[] realPaths = new Path[2];
        String[] canonicalPaths = new String[2];
        BasicFileAttributes[] attributes = new BasicFileAttributes[2];
        boolean[] symbolicLinks = new boolean[2];

        System.out.println("argumentCount=2");
        for (int i = 0; i < 2; i++) {
            paths[i] = Paths.get(args[i]);
            if (!paths[i].isAbsolute())
                throw new IllegalArgumentException("PATH_NOT_ABSOLUTE_" + i);

            realPaths[i] = paths[i].toRealPath();
            attributes[i] = Files.readAttributes(paths[i],
                    BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            symbolicLinks[i] = Files.isSymbolicLink(paths[i]);
            canonicalPaths[i] = paths[i].toFile().getCanonicalPath();

            String prefix = "arg" + i + ".";
            line(prefix + "absolute", true);
            line(prefix + "input", ascii(paths[i].toString()));
            line(prefix + "realPath", ascii(realPaths[i].toString()));
            line(prefix + "canonicalPath", ascii(canonicalPaths[i]));
            line(prefix + "nofollow.directory", attributes[i].isDirectory());
            line(prefix + "nofollow.symbolicLink", attributes[i].isSymbolicLink());
            line(prefix + "nofollow.other", attributes[i].isOther());
            line(prefix + "nofollow.regularFile", attributes[i].isRegularFile());
            line(prefix + "files.isSymbolicLink", symbolicLinks[i]);
            line(prefix + "realEqualsCanonical",
                    realPaths[i].toString().equals(canonicalPaths[i]));
        }

        line("realPathsEqual", realPaths[0].equals(realPaths[1]));
        line("canonicalPathsEqual", canonicalPaths[0].equals(canonicalPaths[1]));
        line("nofollow.directoryEqual",
                attributes[0].isDirectory() == attributes[1].isDirectory());
        line("nofollow.symbolicLinkEqual",
                attributes[0].isSymbolicLink() == attributes[1].isSymbolicLink());
        line("nofollow.otherEqual", attributes[0].isOther() == attributes[1].isOther());
        line("files.isSymbolicLinkEqual", symbolicLinks[0] == symbolicLinks[1]);
    }

    private static void line(String key, boolean value) {
        System.out.println(key + "=" + value);
    }

    private static void line(String key, String value) {
        System.out.println(key + "=" + value);
    }

    private static String ascii(String value) {
        StringBuilder out = new StringBuilder();
        boolean truncated = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            String token;
            if (c >= 0x20 && c <= 0x7e && c != '\\' && c != '=') {
                token = String.valueOf(c);
            } else if (c == '\\') {
                token = "\\\\";
            } else {
                token = unicodeEscape(c);
            }
            int limit = MAX_PATH_OUTPUT - 3;
            if (out.length() + token.length() > limit) {
                truncated = true;
                break;
            }
            out.append(token);
        }
        if (truncated) out.append("...");
        return out.toString();
    }

    private static String unicodeEscape(char value) {
        final char[] hex = "0123456789ABCDEF".toCharArray();
        char[] escaped = new char[6];
        escaped[0] = '\\';
        escaped[1] = 'u';
        escaped[2] = hex[(value >>> 12) & 0xf];
        escaped[3] = hex[(value >>> 8) & 0xf];
        escaped[4] = hex[(value >>> 4) & 0xf];
        escaped[5] = hex[value & 0xf];
        return new String(escaped);
    }
}
