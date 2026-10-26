package kardash.project.android;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;
import kardash.project.transfer.TransferClient;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Read-only, temporary URI grants for explicit clipboard and share actions. */
public final class SharedFilesProvider extends ContentProvider {
    static ArrayList<Uri> prepare(Context context, List<TransferClient.Source> sources) throws IOException {
        Path root = context.getCacheDir().toPath().resolve("shared");
        Files.createDirectories(root);
        Path directory = Files.createDirectory(root.resolve(UUID.randomUUID().toString()));
        ArrayList<Uri> result = new ArrayList<>();
        try {
            for (var source : TransferClient.sources(sources)) {
                Files.copy(source.path(), directory.resolve(source.name()));
                result.add(new Uri.Builder().scheme("content").authority(context.getPackageName() + ".shared")
                        .appendPath(directory.getFileName().toString()).appendPath(source.name()).build());
            }
            return result;
        } catch (IOException | RuntimeException e) {
            try (var children = Files.list(directory)) {
                for (Path child : children.collect(java.util.stream.Collectors.toList())) Files.deleteIfExists(child);
            }
            Files.deleteIfExists(directory);
            throw e;
        }
    }

    private File file(Uri uri) throws FileNotFoundException {
        try {
            List<String> segments = uri.getPathSegments();
            if (!"content".equals(uri.getScheme()) || !(getContext().getPackageName() + ".shared").equals(uri.getAuthority())
                    || segments.size() != 2) throw new FileNotFoundException("Invalid URI");
            File root = new File(getContext().getCacheDir(), "shared").getCanonicalFile();
            File file = new File(new File(root, segments.get(0)), segments.get(1)).getCanonicalFile();
            if (!file.toPath().startsWith(root.toPath()) || !file.isFile()) throw new FileNotFoundException("File unavailable");
            return file;
        } catch (IOException e) { throw new FileNotFoundException(e.getMessage()); }
    }

    @Override public boolean onCreate() { return true; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read only");
        return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public String getType(Uri uri) {
        String name = uri.getLastPathSegment();
        int dot = name == null ? -1 : name.lastIndexOf('.');
        String type = dot < 0 ? null : MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substring(dot + 1).toLowerCase(Locale.ROOT));
        return type == null ? "application/octet-stream" : type;
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] arguments, String order) {
        try {
            File file = file(uri);
            if (projection == null) projection = new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
            MatrixCursor cursor = new MatrixCursor(projection, 1);
            Object[] row = new Object[projection.length];
            for (int i = 0; i < projection.length; i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(projection[i])) row[i] = file.getName();
                else if (OpenableColumns.SIZE.equals(projection[i])) row[i] = file.length();
            }
            cursor.addRow(row);
            return cursor;
        } catch (FileNotFoundException e) { throw new IllegalArgumentException(e); }
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException("Read only"); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] arguments) { throw new UnsupportedOperationException("Read only"); }
    @Override public int delete(Uri uri, String selection, String[] arguments) { throw new UnsupportedOperationException("Read only"); }
}
