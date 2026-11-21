package com.example.android.notepad.model;

/**
 * 待办事项。dueAt 为截止时间戳（取当天 23:59:59），0 表示未设置。
 */
public class Todo {

    public long id;
    public String title = "";
    public String detail = "";
    public long dueAt;
    public boolean done;
    public long doneAt;
    public long createdAt;
}
