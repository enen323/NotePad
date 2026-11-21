package com.example.android.notepad.model;

/**
 * 一条笔记。正文与标题都可为空，附件单独存放在 attachments 表中。
 */
public class Note {

    public long id;
    public String title = "";
    public String content = "";
    public long createdAt;
    public long updatedAt;
    public boolean pinned;

    // 列表页展示用的附件统计，由查询时聚合得到，不落库
    public int imageCount;
    public int audioCount;
    public int videoCount;

    public boolean hasAttachment() {
        return imageCount + audioCount + videoCount > 0;
    }

    public int attachmentTotal() {
        return imageCount + audioCount + videoCount;
    }
}
