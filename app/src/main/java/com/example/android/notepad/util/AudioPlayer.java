package com.example.android.notepad.util;

import android.media.MediaPlayer;

import java.io.File;

/**
 * 简易音频播放器：同一时刻只播放一条语音，重复点击同一条则停止。
 */
public final class AudioPlayer {

    private AudioPlayer() {
    }

    private static MediaPlayer player;
    private static String currentPath;

    public static boolean isPlaying(String path) {
        try {
            return player != null && path != null && path.equals(currentPath) && player.isPlaying();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /** @return true 表示开始播放，false 表示已停止或播放失败 */
    public static boolean toggle(String path) {
        if (isPlaying(path)) {
            stop();
            return false;
        }
        stop();
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setDataSource(path);
            mp.prepare();
            mp.setOnCompletionListener(p -> stop());
            mp.start();
            player = mp;
            currentPath = path;
            return true;
        } catch (Exception e) {
            stop();
            return false;
        }
    }

    public static void stop() {
        if (player != null) {
            try {
                player.stop();
            } catch (IllegalStateException ignored) {
                // 已停止
            }
            player.release();
            player = null;
        }
        currentPath = null;
    }

    public static boolean exists(File file) {
        return file != null && file.exists();
    }
}
