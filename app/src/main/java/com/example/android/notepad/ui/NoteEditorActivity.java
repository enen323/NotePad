package com.example.android.notepad.ui;

import android.Manifest;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.android.notepad.R;
import com.example.android.notepad.data.NoteRepository;
import com.example.android.notepad.model.Attachment;
import com.example.android.notepad.model.Note;
import com.example.android.notepad.ui.adapter.AttachmentAdapter;
import com.example.android.notepad.util.AudioPlayer;
import com.example.android.notepad.util.MediaStoreUtil;
import com.example.android.notepad.util.ThumbnailLoader;
import com.example.android.notepad.util.TimeUtils;
import com.example.android.notepad.util.UiUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 笔记编辑页：标题 + 正文 + 多类型附件（图片 / 语音 / 视频）。
 * 新建时首次添加附件或退出时自动落库；退出自动保存，避免误触返回丢内容。
 */
public class NoteEditorActivity extends AppCompatActivity {

    public static final String EXTRA_NOTE_ID = "extra_note_id";
    private static final String STATE_NOTE_ID = "state_note_id";

    private NoteRepository repository;
    private long noteId = -1L;
    private Note note;
    private boolean isNewNote;
    private boolean deleted;

    private final List<Attachment> attachments = new ArrayList<>();
    private AttachmentAdapter attachmentAdapter;

    private EditText editTitle;
    private EditText editContent;
    private TextView textTime;
    private View attachmentSection;

    private File pendingCameraFile;
    private Uri pendingCameraUri;

    private final ActivityResultLauncher<PickVisualMediaRequest> pickImage =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    importUri(uri);
                }
            });

    private final ActivityResultLauncher<PickVisualMediaRequest> pickVideo =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    importUri(uri);
                }
            });

    private final ActivityResultLauncher<Intent> takePicture =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && pendingCameraFile != null
                        && pendingCameraFile.exists() && pendingCameraFile.length() > 0) {
                    addAttachmentFile(pendingCameraFile, "image/jpeg", 0);
                } else if (pendingCameraFile != null) {
                    //noinspection ResultOfMethodCallIgnored
                    pendingCameraFile.delete();
                }
                pendingCameraFile = null;
                pendingCameraUri = null;
            });

    private final ActivityResultLauncher<String> requestAudioPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    showRecordDialog();
                } else {
                    Toast.makeText(this, R.string.record_permission_denied, Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_note_editor);
        UiUtils.applyInsetsWithIme(findViewById(R.id.root));

        repository = NoteRepository.get(this);
        noteId = getIntent().getLongExtra(EXTRA_NOTE_ID, -1L);
        // 新建笔记在 onStop 落库后，旋屏重建要靠保存的 noteId 恢复，否则会变成一张空白新笔记
        if (savedInstanceState != null) {
            long restored = savedInstanceState.getLong(STATE_NOTE_ID, -1L);
            if (restored > 0) {
                noteId = restored;
            }
        }
        isNewNote = noteId <= 0;

        initViews();

        if (noteId > 0) {
            loadNote();
        } else {
            note = new Note();
            note.createdAt = System.currentTimeMillis();
            updateTimeLabel();
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                saveAndFinish();
            }
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(STATE_NOTE_ID, noteId);
    }

    private void initViews() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> saveAndFinish());

        editTitle = findViewById(R.id.edit_title);
        editContent = findViewById(R.id.edit_content);
        textTime = findViewById(R.id.text_time);
        attachmentSection = findViewById(R.id.attachment_section);

        RecyclerView recycler = findViewById(R.id.recycler_attachments);
        recycler.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        attachmentAdapter = new AttachmentAdapter(new AttachmentAdapter.Listener() {
            @Override
            public void onAttachmentClick(Attachment attachment) {
                handleAttachmentClick(attachment);
            }

            @Override
            public void onAttachmentRemove(Attachment attachment) {
                confirmRemoveAttachment(attachment);
            }
        });
        recycler.setAdapter(attachmentAdapter);

        findViewById(R.id.button_add_attachment).setOnClickListener(v -> showAttachmentSheet());
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 在 onSaveInstanceState 之前落库，保证旋屏重建时 noteId 已经确定
        persist();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AudioPlayer.stop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_editor, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem pin = menu.findItem(R.id.action_pin);
        if (pin != null) {
            pin.setTitle(note != null && note.pinned ? R.string.menu_unpin : R.string.menu_pin);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_save) {
            saveAndFinish();
            return true;
        }
        if (id == R.id.action_pin) {
            togglePin();
            return true;
        }
        if (id == R.id.action_delete_note) {
            confirmDeleteNote();
            return true;
        }
        if (id == android.R.id.home) {
            saveAndFinish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ========================= 数据 =========================

    private void loadNote() {
        note = repository.getNote(noteId);
        if (note == null) {
            Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        editTitle.setText(note.title);
        editContent.setText(note.content);
        editTitle.setSelection(note.title.length());

        attachments.clear();
        attachments.addAll(repository.listAttachments(noteId));
        attachmentAdapter.setItems(attachments);
        updateAttachmentSection();

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.title_editor_edit);
        }
        updateTimeLabel();
        invalidateOptionsMenu();
    }

    /** 保存当前编辑内容。新建且完全空白时不落库。 */
    private void persist() {
        if (deleted || note == null) {
            return;
        }
        String title = editTitle.getText().toString().trim();
        String content = editContent.getText().toString();
        long now = System.currentTimeMillis();

        if (noteId <= 0) {
            if (title.isEmpty() && content.isEmpty() && attachments.isEmpty()) {
                return;
            }
            note.title = title;
            note.content = content;
            if (note.createdAt <= 0) {
                note.createdAt = now;
            }
            note.updatedAt = now;
            noteId = repository.insertNote(note);
            note.id = noteId;
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(R.string.title_editor_edit);
            }
        } else {
            note.title = title;
            note.content = content;
            note.updatedAt = now;
            repository.updateNote(note);
        }
        updateTimeLabel();
    }

    private void saveAndFinish() {
        persist();
        // 新建笔记但内容被清空：不留下空壳记录
        if (isNewNote && noteId > 0 && attachments.isEmpty()
                && editTitle.getText().toString().trim().isEmpty()
                && editContent.getText().toString().trim().isEmpty()) {
            repository.deleteNote(noteId);
            noteId = -1;
        }
        finish();
    }

    /** 附件必须挂在笔记上，新建时先插入一条笔记。 */
    private void ensureNoteSaved() {
        if (noteId > 0) {
            return;
        }
        if (note == null) {
            note = new Note();
        }
        note.title = editTitle.getText().toString().trim();
        note.content = editContent.getText().toString();
        if (note.createdAt <= 0) {
            note.createdAt = System.currentTimeMillis();
        }
        note.updatedAt = System.currentTimeMillis();
        noteId = repository.insertNote(note);
        note.id = noteId;
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.title_editor_edit);
        }
        updateTimeLabel();
        invalidateOptionsMenu();
    }

    private void updateTimeLabel() {
        if (note == null || note.createdAt <= 0) {
            textTime.setText("");
            return;
        }
        StringBuilder sb = new StringBuilder(getString(R.string.time_created,
                TimeUtils.formatFull(note.createdAt)));
        if (note.updatedAt > note.createdAt) {
            sb.append(" · ").append(getString(R.string.time_updated,
                    TimeUtils.formatFull(note.updatedAt)));
        }
        textTime.setText(sb.toString());
    }

    private void togglePin() {
        if (noteId <= 0) {
            ensureNoteSaved();
        }
        if (note == null) {
            return;
        }
        note.pinned = !note.pinned;
        repository.setPinned(noteId, note.pinned);
        Toast.makeText(this, note.pinned ? R.string.pinned : R.string.unpinned,
                Toast.LENGTH_SHORT).show();
        invalidateOptionsMenu();
    }

    private void confirmDeleteNote() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_note_title)
                .setMessage(R.string.delete_note_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    if (noteId > 0) {
                        repository.deleteNote(noteId);
                    }
                    deleted = true;
                    finish();
                })
                .show();
    }

    // ========================= 附件 =========================

    private void showAttachmentSheet() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View content = getLayoutInflater().inflate(R.layout.sheet_add_attachment, null);
        sheet.setContentView(content);

        content.findViewById(R.id.row_image).setOnClickListener(v -> {
            sheet.dismiss();
            pickImage.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        });

        content.findViewById(R.id.row_video).setOnClickListener(v -> {
            sheet.dismiss();
            pickVideo.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.VideoOnly.INSTANCE)
                    .build());
        });

        content.findViewById(R.id.row_camera).setOnClickListener(v -> {
            sheet.dismiss();
            launchCamera();
        });

        content.findViewById(R.id.row_record).setOnClickListener(v -> {
            sheet.dismiss();
            ensureAudioPermission();
        });

        sheet.show();
    }

    private void launchCamera() {
        pendingCameraFile = MediaStoreUtil.newCameraFile(this);
        pendingCameraUri = MediaStoreUtil.uriFor(this, pendingCameraFile);
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraUri);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            takePicture.launch(intent);
        } catch (ActivityNotFoundException e) {
            //noinspection ResultOfMethodCallIgnored
            pendingCameraFile.delete();
            pendingCameraFile = null;
            pendingCameraUri = null;
            Toast.makeText(this, R.string.no_camera, Toast.LENGTH_SHORT).show();
        }
    }

    private void ensureAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            showRecordDialog();
        } else {
            requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    private void showRecordDialog() {
        RecordDialog dialog = new RecordDialog(this, (file, durationMs) ->
                addAttachmentFile(file, MediaStoreUtil.mimeOf(file), durationMs));
        dialog.show();
    }

    private void importUri(Uri uri) {
        try {
            File file = MediaStoreUtil.importUri(this, uri);
            String mime = MediaStoreUtil.mimeOf(file);
            long duration = MediaStoreUtil.typeOf(mime) == Attachment.TYPE_IMAGE
                    ? 0 : MediaStoreUtil.durationOf(this, file);
            addAttachmentFile(file, mime, duration);
        } catch (Exception e) {
            Toast.makeText(this, R.string.attachment_add_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void addAttachmentFile(File file, String mime, long duration) {
        if (file == null || !file.exists()) {
            Toast.makeText(this, R.string.attachment_add_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        ensureNoteSaved();

        Attachment attachment = new Attachment();
        attachment.noteId = noteId;
        attachment.type = MediaStoreUtil.typeOf(mime);
        attachment.path = file.getAbsolutePath();
        attachment.mime = mime;
        attachment.duration = duration;
        attachment.size = file.length();
        attachment.createdAt = System.currentTimeMillis();
        attachment.id = repository.addAttachment(attachment);

        attachments.add(attachment);
        attachmentAdapter.setItems(attachments);
        updateAttachmentSection();

        RecyclerView recycler = findViewById(R.id.recycler_attachments);
        recycler.post(() -> recycler.smoothScrollToPosition(attachments.size() - 1));
    }

    private void updateAttachmentSection() {
        attachmentSection.setVisibility(attachments.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void handleAttachmentClick(Attachment attachment) {
        File file = new File(attachment.path);
        if (!file.exists()) {
            Toast.makeText(this, R.string.attachment_add_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        switch (attachment.type) {
            case Attachment.TYPE_IMAGE:
                showImagePreview(file);
                break;
            case Attachment.TYPE_VIDEO:
                if (!MediaStoreUtil.openExternally(this, file)) {
                    Toast.makeText(this, R.string.no_player, Toast.LENGTH_SHORT).show();
                }
                break;
            case Attachment.TYPE_AUDIO:
            default:
                AudioPlayer.toggle(attachment.path);
                attachmentAdapter.setItems(attachments);
                break;
        }
    }

    private void showImagePreview(File file) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setBackgroundColor(Color.BLACK);
        image.setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(image);
        dialog.show();
        ThumbnailLoader.loadFull(file, image);
    }

    private void confirmRemoveAttachment(final Attachment attachment) {
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.attachment_delete_title)
                .setMessage(R.string.attachment_delete_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, which) -> {
                    repository.deleteAttachment(attachment.id);
                    attachments.remove(attachment);
                    attachmentAdapter.setItems(attachments);
                    updateAttachmentSection();
                })
                .create();
        dialog.show();
    }
}
