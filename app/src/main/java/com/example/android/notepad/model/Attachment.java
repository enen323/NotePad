package com.example.android.notepad.model;

/**
 * 笔记附件：图片 / 语音 / 视频。
 * path 指向应用私有目录（files/media）中的文件副本，避免外部 Uri 失效。
 */
public class Attachment {

    public static final int TYPE_IMAGE = 0;
    public static final int TYPE_AUDIO = 1;
    public static final int TYPE_VIDEO = 2;

    public long id;
    public long noteId;
    public int type;
    public String path = "";
    public String mime = "";
    /** 音视频时长，单位毫秒；图片为 0 */
    public long duration;
    /** 文件大小，单位字节 */
    public long size;
    public long createdAt;
}
