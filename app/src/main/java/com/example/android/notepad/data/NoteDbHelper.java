package com.example.android.notepad.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;

/**
 * 本地 SQLite 数据库。
 *
 * <pre>
 * notes        笔记主体（标题、正文、创建/更新时间、是否置顶）
 * attachments  附件（图片/语音/视频），note_id 外键级联删除
 * todos        待办事项（截止时间、完成状态）
 * </pre>
 */
public class NoteDbHelper extends SQLiteOpenHelper {

    public static final String DB_NAME = "notepad.db";
    public static final int DB_VERSION = 1;

    public static final String T_NOTES = "notes";
    public static final String T_ATTACHMENTS = "attachments";
    public static final String T_TODOS = "todos";

    // notes
    public static final String C_ID = "_id";
    public static final String C_TITLE = "title";
    public static final String C_CONTENT = "content";
    public static final String C_CREATED_AT = "created_at";
    public static final String C_UPDATED_AT = "updated_at";
    public static final String C_PINNED = "pinned";

    // attachments
    public static final String C_NOTE_ID = "note_id";
    public static final String C_TYPE = "type";
    public static final String C_PATH = "path";
    public static final String C_MIME = "mime";
    public static final String C_DURATION = "duration";
    public static final String C_SIZE = "size";

    // todos
    public static final String C_DETAIL = "detail";
    public static final String C_DUE_AT = "due_at";
    public static final String C_DONE = "done";
    public static final String C_DONE_AT = "done_at";

    private static final String SQL_CREATE_NOTES =
            "CREATE TABLE " + T_NOTES + " (" +
                    C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    C_TITLE + " TEXT NOT NULL DEFAULT '', " +
                    C_CONTENT + " TEXT NOT NULL DEFAULT '', " +
                    C_CREATED_AT + " INTEGER NOT NULL, " +
                    C_UPDATED_AT + " INTEGER NOT NULL, " +
                    C_PINNED + " INTEGER NOT NULL DEFAULT 0)";

    private static final String SQL_CREATE_ATTACHMENTS =
            "CREATE TABLE " + T_ATTACHMENTS + " (" +
                    C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    C_NOTE_ID + " INTEGER NOT NULL, " +
                    C_TYPE + " INTEGER NOT NULL, " +
                    C_PATH + " TEXT NOT NULL, " +
                    C_MIME + " TEXT NOT NULL DEFAULT '', " +
                    C_DURATION + " INTEGER NOT NULL DEFAULT 0, " +
                    C_SIZE + " INTEGER NOT NULL DEFAULT 0, " +
                    C_CREATED_AT + " INTEGER NOT NULL, " +
                    "FOREIGN KEY (" + C_NOTE_ID + ") REFERENCES " + T_NOTES + "(" + C_ID + ") ON DELETE CASCADE)";

    private static final String SQL_CREATE_TODOS =
            "CREATE TABLE " + T_TODOS + " (" +
                    C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    C_TITLE + " TEXT NOT NULL, " +
                    C_DETAIL + " TEXT NOT NULL DEFAULT '', " +
                    C_DUE_AT + " INTEGER NOT NULL DEFAULT 0, " +
                    C_DONE + " INTEGER NOT NULL DEFAULT 0, " +
                    C_DONE_AT + " INTEGER NOT NULL DEFAULT 0, " +
                    C_CREATED_AT + " INTEGER NOT NULL)";

    public NoteDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onConfigure(@NonNull SQLiteDatabase db) {
        super.onConfigure(db);
        // 打开外键，删除笔记时自动清理附件记录
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(@NonNull SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_NOTES);
        db.execSQL(SQL_CREATE_ATTACHMENTS);
        db.execSQL(SQL_CREATE_TODOS);
        db.execSQL("CREATE INDEX idx_notes_updated ON " + T_NOTES + "(" + C_UPDATED_AT + ")");
        db.execSQL("CREATE INDEX idx_attach_note ON " + T_ATTACHMENTS + "(" + C_NOTE_ID + ")");
        db.execSQL("CREATE INDEX idx_todo_done ON " + T_TODOS + "(" + C_DONE + ")");
    }

    @Override
    public void onUpgrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
        // 课设作业不需要迁移历史数据，直接重建
        db.execSQL("DROP TABLE IF EXISTS " + T_ATTACHMENTS);
        db.execSQL("DROP TABLE IF EXISTS " + T_TODOS);
        db.execSQL("DROP TABLE IF EXISTS " + T_NOTES);
        onCreate(db);
    }
}
