package com.example.android.notepad.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.android.notepad.R;
import com.example.android.notepad.model.Note;
import com.example.android.notepad.util.TimeUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 笔记列表适配器：标题、正文摘要、时间戳、置顶标记、附件类型图标。
 */
public class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.NoteHolder> {

    public interface Listener {
        void onNoteClick(Note note);

        void onNoteLongClick(Note note, View anchor);
    }

    private final List<Note> items = new ArrayList<>();
    private final Listener listener;

    public NoteAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<Note> notes) {
        items.clear();
        if (notes != null) {
            items.addAll(notes);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public NoteHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_note, parent, false);
        return new NoteHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteHolder holder, int position) {
        Note note = items.get(position);
        Context context = holder.itemView.getContext();

        holder.title.setText(note.title == null || note.title.trim().isEmpty()
                ? context.getString(R.string.note_untitled)
                : note.title.trim());

        String snippet = note.content == null ? "" : note.content.trim().replaceAll("\\s+", " ");
        if (snippet.isEmpty()) {
            holder.snippet.setVisibility(View.GONE);
        } else {
            holder.snippet.setVisibility(View.VISIBLE);
            holder.snippet.setText(snippet);
        }

        holder.pin.setVisibility(note.pinned ? View.VISIBLE : View.GONE);
        holder.time.setText(TimeUtils.formatListTime(note.updatedAt));

        bindCount(holder.imageIcon, holder.imageCount, note.imageCount);
        bindCount(holder.audioIcon, holder.audioCount, note.audioCount);
        bindCount(holder.videoIcon, holder.videoCount, note.videoCount);

        holder.itemView.setOnClickListener(v -> listener.onNoteClick(note));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onNoteLongClick(note, holder.itemView);
            return true;
        });
    }

    /** 有附件才显示类型图标；同类多于 1 个时补一个数字。 */
    private void bindCount(ImageView icon, TextView countView, int count) {
        if (count > 0) {
            icon.setVisibility(View.VISIBLE);
            if (count > 1) {
                countView.setVisibility(View.VISIBLE);
                countView.setText(String.valueOf(count));
            } else {
                countView.setVisibility(View.GONE);
            }
        } else {
            icon.setVisibility(View.GONE);
            countView.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class NoteHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView snippet;
        final TextView time;
        final ImageView pin;
        final ImageView imageIcon;
        final ImageView audioIcon;
        final ImageView videoIcon;
        final TextView imageCount;
        final TextView audioCount;
        final TextView videoCount;

        NoteHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.text_title);
            snippet = itemView.findViewById(R.id.text_snippet);
            time = itemView.findViewById(R.id.text_time);
            pin = itemView.findViewById(R.id.icon_pin);
            imageIcon = itemView.findViewById(R.id.icon_image);
            audioIcon = itemView.findViewById(R.id.icon_audio);
            videoIcon = itemView.findViewById(R.id.icon_video);
            imageCount = itemView.findViewById(R.id.text_image_count);
            audioCount = itemView.findViewById(R.id.text_audio_count);
            videoCount = itemView.findViewById(R.id.text_video_count);
        }
    }
}
