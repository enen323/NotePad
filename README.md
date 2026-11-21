# NotePad

基于 Android 官方早期 NotePad 示例（ContentProvider + SQLite）扩展而来的记事本应用。
原始示例使用的 `ListActivity` / `managedQuery` 等 API 已被废弃，本项目在保留数据逻辑的前提下做了工程现代化改造。

## 功能对照

| 要求 | 实现情况 | 代码位置 |
| --- | --- | --- |
| **基本 1**：笔记列表显示时间戳 | ✅ 相对时间（刚刚 / N 分钟前 / 今天 HH:mm / 昨天 HH:mm / M月d日） | `util/TimeUtils.java`、`item_note.xml` |
| **基本 2**：按标题或内容查询 | ✅ 顶部搜索框实时模糊查询（`title LIKE ? OR content LIKE ?`） | `NotesFragment.java`、`NoteRepository.listNotes()` |
| **附加 1**：多类型笔记（图片 / 语音 / 视频） | ✅ 相册选图、相册选视频、拍照、录音四种入口 | `NoteEditorActivity.java`、`RecordDialog.java` |
| **附加 2**：待办功能 | ✅ 新建 / 编辑 / 勾选完成 / 删除（可撤销）/ 截止日期 / 状态筛选 | `TodoFragment.java`、`TodoAdapter.java` |
| 附赠：UI 美化 | ✅ Material 3、深色模式、卡片式列表、空状态页、底部导航角标 | `values/themes.xml`、`values-night/` |
| 附赠：笔记排序 / 置顶 | ✅ 按最近修改、创建时间、标题排序；长按条目置顶 | `menu_notes.xml`、`NotesFragment.java` |

## 技术栈

- Java 17，AGP 8.13.2，Gradle 8.13，`compileSdk 37` / `minSdk 24` / `targetSdk 36`
- AndroidX AppCompat + Material Components 1.12 + RecyclerView
- 本地存储：`SQLiteOpenHelper`（不依赖 Room，方便对照原始 NotePad 的 SQLite 用法）
- 依赖仓库使用阿里云镜像（国内网络下直连 `dl.google.com` 往往不可用）

## 数据库设计

```
notes(_id, title, content, created_at, updated_at, pinned)
attachments(_id, note_id, type, path, mime, duration, size, created_at)
    type: 0 图片 / 1 语音 / 2 视频
    note_id 外键 → notes(_id)，ON DELETE CASCADE
todos(_id, title, detail, due_at, done, done_at, created_at)
```

附件文件统一复制到 `files/media/`，数据库中只保存文件路径。
这样处理是因为相册返回的 `content://` Uri 授权会过期，直接存 Uri 会导致笔记过一段时间后附件打不开。

## 目录结构

```
app/src/main/java/com/example/android/notepad/
├── MainActivity.java              底部导航（笔记 / 待办）+ 未完成角标
├── data/
│   ├── NoteDbHelper.java          建表、外键、索引
│   └── NoteRepository.java        笔记 / 附件 / 待办的增删改查
├── model/
│   ├── Note.java
│   ├── Attachment.java
│   └── Todo.java
├── ui/
│   ├── NotesFragment.java         列表 + 搜索 + 排序 + 置顶/删除
│   ├── NoteEditorActivity.java    编辑正文与附件
│   ├── TodoFragment.java          待办页与编辑对话框
│   ├── RecordDialog.java          MediaRecorder 录音
│   └── adapter/                   NoteAdapter / AttachmentAdapter / TodoAdapter
└── util/
    ├── TimeUtils.java             时间戳、时长、截止日期格式化
    ├── MediaStoreUtil.java        Uri 导入、拍照文件、FileProvider、时长读取
    ├── ThumbnailLoader.java       图片采样解码 + 视频首帧 + LruCache
    ├── AudioPlayer.java           语音播放（单实例 MediaPlayer）
    └── UiUtils.java               全面屏 / 输入法 insets 适配
```

## 构建与运行

```bash
# 命令行构建（Windows 下用 gradlew.bat）
./gradlew assembleDebug

# 产物
app/build/outputs/apk/debug/app-debug.apk
```

在 Android Studio 中直接打开根目录运行即可。首次构建需要联网下载依赖。

### 权限

- `RECORD_AUDIO`：录音时动态申请
- 拍照、选图、选视频通过系统相机与 Photo Picker 完成，不需要额外存储权限

## 与原示例的差异

| 原实现 | 现实现 | 原因 |
| --- | --- | --- |
| `ListActivity` + `managedQuery` | `AppCompatActivity` + `RecyclerView` | 相关 API 早已废弃，新 SDK 上不可用 |
| `SimpleCursorAdapter` 单行 TextView | 自定义 item 布局（标题/摘要/时间/附件图标） | 需要展示时间戳与附件类型 |
| `ContentProvider` + `NotesLiveFolder` | 直接通过 Repository 访问 SQLite | 课程场景不需要跨应用共享数据，去掉 Provider 可显著降低复杂度 |
| `compileSdk 23` / `jcenter` | `compileSdk 37` / 阿里云 Maven 镜像 | 本机只安装了 API 37，jcenter 已停止服务 |

## 可以继续做的方向

- 笔记分类 / 标签
- 导出为 Markdown 或纯文本、云备份与恢复
- 语音转文字、OCR 扫描
- 使用 Room + LiveData 替换手写 SQLite，并配合 ViewModel 做数据驱动
