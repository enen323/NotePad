package com.example.android.notepad.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.android.notepad.R;
import com.example.android.notepad.model.Attachment;
import com.example.android.notepad.util.AudioPlayer;
import com.example.android.notepad.util.ThumbnailLoader;
import com.example.android.notepad.util.TimeUtils;
import com.google.android.material.card.MaterialCardView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 编辑器里的附件横滑列表：图片显示缩略图，视频显示首帧 + 播放标记，语音显示波形入口图标。
 */
public class AttachmentAdapter extends RecyclerView.Adapter<AttachmentAdapter.AttachmentHolder> {

    public interface Listener {
        void onAttachmentClick(Attachment attachment);

        void onAttachmentRemove(Attachment attachment);
    }

    private final List<Attachment> items = new ArrayList<>();
    private final Listener listener;

    public AttachmentAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<Attachment> attachments) {
        items.clear();
        if (attachments != null) {
            items.addAll(attachments);
        }
        notifyDataSetChanged();
    }

    public void add(Attachment attachment) {
        items.add(attachment);
        notifyItemInserted(items.size() - 1);
    }

    public void remove(Attachment attachment) {
        int index = items.indexOf(attachment);
        if (index >= 0) {
            items.remove(index);
            notifyItemRemoved(index);
        }
    }

    public int size() {
        return items.size();
    }

    @NonNull
    @Override
    public AttachmentHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_attachment, parent, false);
        return new AttachmentHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AttachmentHolder holder, int position) {
        Attachment attachment = items.get(position);
        File file = new File(attachment.path);

        holder.thumb.setImageDrawable(null);
        holder.duration.setVisibility(View.GONE);
        holder.typeIcon.setVisibility(View.VISIBLE);

        switch (attachment.type) {
            case Attachment.TYPE_IMAGE:
                holder.typeIcon.setVisibility(View.GONE);
                holder.thumb.setVisibility(View.VISIBLE);
                ThumbnailLoader.load(file, holder.thumb, false, 320);
                break;

            case Attachment.TYPE_VIDEO:
                holder.thumb.setVisibility(View.VISIBLE);
                holder.typeIcon.setImageResource(R.drawable.ic_play);
                ThumbnailLoader.load(file, holder.thumb, true, 320);
                if (attachment.duration > 0) {
                    holder.duration.setVisibility(View.VISIBLE);
                    holder.duration.setText(TimeUtils.formatDuration(attachment.duration));
                }
                break;

            case Attachment.TYPE_AUDIO:
            default:
                holder.thumb.setTag(null);
                holder.thumb.setVisibility(View.INVISIBLE);
                boolean playing = AudioPlayer.isPlaying(attachment.path);
                holder.typeIcon.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_mic);
                holder.duration.setVisibility(View.VISIBLE);
                holder.duration.setText(TimeUtils.formatDuration(attachment.duration));
                break;
        }

        holder.card.setOnClickListener(v -> listener.onAttachmentClick(attachment));
        holder.remove.setOnClickListener(v -> listener.onAttachmentRemove(attachment));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class AttachmentHolder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final ImageView thumb;
        final ImageView typeIcon;
        final ImageView remove;
        final TextView duration;

        AttachmentHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.card_attachment);
            thumb = itemView.findViewById(R.id.image_thumb);
            typeIcon = itemView.findViewById(R.id.image_type_icon);
            remove = itemView.findViewById(R.id.button_remove);
            duration = itemView.findViewById(R.id.text_duration);
        }
    }
}
