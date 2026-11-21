package com.example.android.notepad.data;

import static com.example.android.notepad.data.NoteDbHelper.C_CONTENT;
import static com.example.android.notepad.data.NoteDbHelper.C_CREATED_AT;
import static com.example.android.notepad.data.NoteDbHelper.C_DETAIL;
import static com.example.android.notepad.data.NoteDbHelper.C_DONE;
import static com.example.android.notepad.data.NoteDbHelper.C_DONE_AT;
import static com.example.android.notepad.data.NoteDbHelper.C_DUE_AT;
import static com.example.android.notepad.data.NoteDbHelper.C_DURATION;
import static com.example.android.notepad.data.NoteDbHelper.C_ID;
import static com.example.android.notepad.data.NoteDbHelper.C_MIME;
import static com.example.android.notepad.data.NoteDbHelper.C_NOTE_ID;
import static com.example.android.notepad.data.NoteDbHelper.C_PATH;
import static com.example.android.notepad.data.NoteDbHelper.C_PINNED;
import static com.example.android.notepad.data.NoteDbHelper.C_SIZE;
import static com.example.android.notepad.data.NoteDbHelper.C_TITLE;
import static com.example.android.notepad.data.NoteDbHelper.C_TYPE;
import static com.example.android.notepad.data.NoteDbHelper.C_UPDATED_AT;
import static com.example.android.notepad.data.NoteDbHelper.T_ATTACHMENTS;
import static com.example.android.notepad.data.NoteDbHelper.T_NOTES;
import static com.example.android.notepad.data.NoteDbHelper.T_TODOS;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.android.notepad.model.Attachment;
import com.example.android.notepad.model.Note;
import com.example.android.notepad.model.Todo;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 笔记 / 附件 / 待办 的统一数据访问层。
 * 数据量很小，直接在主线程访问 SQLite，代码更直观；真实项目应放到子线程或使用 Room + LiveData。
 */
public class NoteRepository {

    public static final int SORT_UPDATED = 0;
    public static final int SORT_CREATED = 1;
    public static final int SORT_TITLE = 2;

    public static final int FILTER_ALL = 0;
    public static final int FILTER_ACTIVE = 1;
    public static final int FILTER_DONE = 2;

    private static volatile NoteRepository instance;

    private final NoteDbHelper helper;

    private NoteRepository(Context context) {
        helper = new NoteDbHelper(context.getApplicationContext());
    }

    public static NoteRepository get(Context context) {
        if (instance == null) {
            synchronized (NoteRepository.class) {
                if (instance == null) {
                    instance = new NoteRepository(context);
                }
            }
        }
        return instance;
    }

    // ========================= 笔记 =========================

    private static final String NOTE_SELECT =
            "SELECT n." + C_ID + " AS _id, n." + C_TITLE + ", n." + C_CONTENT + ", " +
                    "n." + C_CREATED_AT + ", n." + C_UPDATED_AT + ", n." + C_PINNED + ", " +
                    "(SELECT COUNT(*) FROM " + T_ATTACHMENTS + " a WHERE a." + C_NOTE_ID + " = n." + C_ID + " AND a." + C_TYPE + " = 0) AS img_count, " +
                    "(SELECT COUNT(*) FROM " + T_ATTACHMENTS + " a WHERE a." + C_NOTE_ID + " = n." + C_ID + " AND a." + C_TYPE + " = 1) AS aud_count, " +
                    "(SELECT COUNT(*) FROM " + T_ATTACHMENTS + " a WHERE a." + C_NOTE_ID + " = n." + C_ID + " AND a." + C_TYPE + " = 2) AS vid_count " +
                    "FROM " + T_NOTES + " n";

    /**
     * 查询笔记列表。query 为空时返回全部，否则按标题或正文模糊匹配。
     */
    public List<Note> listNotes(String query, int sortMode) {
        StringBuilder sql = new StringBuilder(NOTE_SELECT);
        String[] args = null;
        String keyword = query == null ? "" : query.trim();
        if (!keyword.isEmpty()) {
            sql.append(" WHERE n.").append(C_TITLE).append(" LIKE ? OR n.").append(C_CONTENT).append(" LIKE ?");
            String like = "%" + keyword + "%";
            args = new String[]{like, like};
        }
        sql.append(" ORDER BY n.").append(C_PINNED).append(" DESC, ");
        switch (sortMode) {
            case SORT_CREATED:
                sql.append("n.").append(C_CREATED_AT).append(" DESC");
                break;
            case SORT_TITLE:
                sql.append("n.").append(C_TITLE).append(" COLLATE NOCASE ASC");
                break;
            case SORT_UPDATED:
            default:
                sql.append("n.").append(C_UPDATED_AT).append(" DESC");
                break;
        }

        List<Note> result = new ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().rawQuery(sql.toString(), args)) {
            while (c.moveToNext()) {
                Note n = new Note();
                n.id = c.getLong(0);
                n.title = c.getString(1);
                n.content = c.getString(2);
                n.createdAt = c.getLong(3);
                n.updatedAt = c.getLong(4);
                n.pinned = c.getInt(5) == 1;
                n.imageCount = c.getInt(6);
                n.audioCount = c.getInt(7);
                n.videoCount = c.getInt(8);
                result.add(n);
            }
        }
        return result;
    }

    public Note getNote(long id) {
        String sql = NOTE_SELECT + " WHERE n." + C_ID + " = ?";
        try (Cursor c = helper.getReadableDatabase().rawQuery(sql, new String[]{String.valueOf(id)})) {
            if (c.moveToFirst()) {
                Note n = new Note();
                n.id = c.getLong(0);
                n.title = c.getString(1);
                n.content = c.getString(2);
                n.createdAt = c.getLong(3);
                n.updatedAt = c.getLong(4);
                n.pinned = c.getInt(5) == 1;
                n.imageCount = c.getInt(6);
                n.audioCount = c.getInt(7);
                n.videoCount = c.getInt(8);
                return n;
            }
        }
        return null;
    }

    public long insertNote(Note note) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(C_TITLE, note.title == null ? "" : note.title);
        values.put(C_CONTENT, note.content == null ? "" : note.content);
        values.put(C_CREATED_AT, note.createdAt);
        values.put(C_UPDATED_AT, note.updatedAt);
        values.put(C_PINNED, note.pinned ? 1 : 0);
        return db.insert(T_NOTES, null, values);
    }

    public void updateNote(Note note) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(C_TITLE, note.title == null ? "" : note.title);
        values.put(C_CONTENT, note.content == null ? "" : note.content);
        values.put(C_UPDATED_AT, note.updatedAt);
        values.put(C_PINNED, note.pinned ? 1 : 0);
        db.update(T_NOTES, values, C_ID + " = ?", new String[]{String.valueOf(note.id)});
    }

    public void setPinned(long noteId, boolean pinned) {
        ContentValues values = new ContentValues();
        values.put(C_PINNED, pinned ? 1 : 0);
        helper.getWritableDatabase().update(T_NOTES, values, C_ID + " = ?", new String[]{String.valueOf(noteId)});
    }

    /**
     * 删除笔记：先删掉磁盘上的附件文件，再删数据库记录（外键级联删除附件行）。
     */
    public void deleteNote(long noteId) {
        for (Attachment a : listAttachments(noteId)) {
            deleteFileQuietly(a.path);
        }
        helper.getWritableDatabase().delete(T_NOTES, C_ID + " = ?", new String[]{String.valueOf(noteId)});
    }

    // ========================= 附件 =========================

    public List<Attachment> listAttachments(long noteId) {
        List<Attachment> result = new ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().query(
                T_ATTACHMENTS, null, C_NOTE_ID + " = ?", new String[]{String.valueOf(noteId)},
                null, null, C_ID + " ASC")) {
            while (c.moveToNext()) {
                result.add(readAttachment(c));
            }
        }
        return result;
    }

    private Attachment readAttachment(Cursor c) {
        Attachment a = new Attachment();
        a.id = c.getLong(c.getColumnIndexOrThrow(C_ID));
        a.noteId = c.getLong(c.getColumnIndexOrThrow(C_NOTE_ID));
        a.type = c.getInt(c.getColumnIndexOrThrow(C_TYPE));
        a.path = c.getString(c.getColumnIndexOrThrow(C_PATH));
        a.mime = c.getString(c.getColumnIndexOrThrow(C_MIME));
        a.duration = c.getLong(c.getColumnIndexOrThrow(C_DURATION));
        a.size = c.getLong(c.getColumnIndexOrThrow(C_SIZE));
        a.createdAt = c.getLong(c.getColumnIndexOrThrow(C_CREATED_AT));
        return a;
    }

    public long addAttachment(Attachment attachment) {
        ContentValues values = new ContentValues();
        values.put(C_NOTE_ID, attachment.noteId);
        values.put(C_TYPE, attachment.type);
        values.put(C_PATH, attachment.path);
        values.put(C_MIME, attachment.mime);
        values.put(C_DURATION, attachment.duration);
        values.put(C_SIZE, attachment.size);
        values.put(C_CREATED_AT, attachment.createdAt);
        return helper.getWritableDatabase().insert(T_ATTACHMENTS, null, values);
    }

    public void deleteAttachment(long attachmentId) {
        try (Cursor c = helper.getReadableDatabase().query(
                T_ATTACHMENTS, new String[]{C_PATH}, C_ID + " = ?",
                new String[]{String.valueOf(attachmentId)}, null, null, null)) {
            if (c.moveToFirst()) {
                deleteFileQuietly(c.getString(0));
            }
        }
        helper.getWritableDatabase().delete(T_ATTACHMENTS, C_ID + " = ?", new String[]{String.valueOf(attachmentId)});
    }

    private void deleteFileQuietly(String path) {
        if (path == null || path.isEmpty()) {
            return;
        }
        File f = new File(path);
        if (f.exists() && !f.delete()) {
            // 删除失败不影响数据库清理，文件可能在下次清理时回收
            f.deleteOnExit();
        }
    }

    // ========================= 待办 =========================

    public List<Todo> listTodos(int filter) {
        String selection = null;
        if (filter == FILTER_ACTIVE) {
            selection = C_DONE + " = 0";
        } else if (filter == FILTER_DONE) {
            selection = C_DONE + " = 1";
        }
        String order = C_DONE + " ASC, CASE WHEN " + C_DUE_AT + " = 0 THEN 1 ELSE 0 END ASC, " +
                C_DUE_AT + " ASC, " + C_CREATED_AT + " DESC";
        List<Todo> result = new ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().query(
                T_TODOS, null, selection, null, null, null, order)) {
            while (c.moveToNext()) {
                result.add(readTodo(c));
            }
        }
        return result;
    }

    private Todo readTodo(Cursor c) {
        Todo t = new Todo();
        t.id = c.getLong(c.getColumnIndexOrThrow(C_ID));
        t.title = c.getString(c.getColumnIndexOrThrow(C_TITLE));
        t.detail = c.getString(c.getColumnIndexOrThrow(C_DETAIL));
        t.dueAt = c.getLong(c.getColumnIndexOrThrow(C_DUE_AT));
        t.done = c.getInt(c.getColumnIndexOrThrow(C_DONE)) == 1;
        t.doneAt = c.getLong(c.getColumnIndexOrThrow(C_DONE_AT));
        t.createdAt = c.getLong(c.getColumnIndexOrThrow(C_CREATED_AT));
        return t;
    }

    public long insertTodo(Todo todo) {
        ContentValues values = new ContentValues();
        values.put(C_TITLE, todo.title);
        values.put(C_DETAIL, todo.detail);
        values.put(C_DUE_AT, todo.dueAt);
        values.put(C_DONE, todo.done ? 1 : 0);
        values.put(C_DONE_AT, todo.doneAt);
        values.put(C_CREATED_AT, todo.createdAt);
        return helper.getWritableDatabase().insert(T_TODOS, null, values);
    }

    /** 撤销删除时用原 id 写回，保证列表顺序和引用关系不变。 */
    public void restoreTodo(Todo todo) {
        ContentValues values = new ContentValues();
        values.put(C_ID, todo.id);
        values.put(C_TITLE, todo.title);
        values.put(C_DETAIL, todo.detail);
        values.put(C_DUE_AT, todo.dueAt);
        values.put(C_DONE, todo.done ? 1 : 0);
        values.put(C_DONE_AT, todo.doneAt);
        values.put(C_CREATED_AT, todo.createdAt);
        helper.getWritableDatabase().insertWithOnConflict(T_TODOS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void updateTodo(Todo todo) {
        ContentValues values = new ContentValues();
        values.put(C_TITLE, todo.title);
        values.put(C_DETAIL, todo.detail);
        values.put(C_DUE_AT, todo.dueAt);
        values.put(C_DONE, todo.done ? 1 : 0);
        values.put(C_DONE_AT, todo.doneAt);
        helper.getWritableDatabase().update(T_TODOS, values, C_ID + " = ?", new String[]{String.valueOf(todo.id)});
    }

    public void setTodoDone(long id, boolean done) {
        ContentValues values = new ContentValues();
        values.put(C_DONE, done ? 1 : 0);
        values.put(C_DONE_AT, done ? System.currentTimeMillis() : 0);
        helper.getWritableDatabase().update(T_TODOS, values, C_ID + " = ?", new String[]{String.valueOf(id)});
    }

    public void deleteTodo(long id) {
        helper.getWritableDatabase().delete(T_TODOS, C_ID + " = ?", new String[]{String.valueOf(id)});
    }

    public int countActiveTodos() {
        try (Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + T_TODOS + " WHERE " + C_DONE + " = 0", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }
}
