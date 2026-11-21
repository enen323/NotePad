package com.example.android.notepad.ui.adapter;

import android.content.Context;
import android.graphics.Paint;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.android.notepad.R;
import com.example.android.notepad.model.Todo;
import com.example.android.notepad.util.TimeUtils;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.util.ArrayList;
import java.util.List;

/**
 * 待办列表：勾选切换完成状态，点击条目编辑，右侧按钮删除。
 */
public class TodoAdapter extends RecyclerView.Adapter<TodoAdapter.TodoHolder> {

    public interface Listener {
        void onToggle(Todo todo, boolean done);

        void onEdit(Todo todo);

        void onDelete(Todo todo);
    }

    private final List<Todo> items = new ArrayList<>();
    private final Listener listener;

    public TodoAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<Todo> todos) {
        items.clear();
        if (todos != null) {
            items.addAll(todos);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TodoHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new TodoHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_todo, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull TodoHolder holder, int position) {
        Todo todo = items.get(position);
        Context context = holder.itemView.getContext();

        // 先清空监听再设置选中状态，避免复用时触发错误的回调
        holder.check.setOnCheckedChangeListener(null);
        holder.check.setChecked(todo.done);
        holder.check.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked != todo.done) {
                listener.onToggle(todo, isChecked);
            }
        });

        holder.title.setText(todo.title);
        if (todo.done) {
            holder.title.setPaintFlags(holder.title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            holder.title.setAlpha(0.45f);
        } else {
            holder.title.setPaintFlags(holder.title.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            holder.title.setAlpha(1f);
        }

        if (TextUtils.isEmpty(todo.detail)) {
            holder.detail.setVisibility(View.GONE);
        } else {
            holder.detail.setVisibility(View.VISIBLE);
            holder.detail.setText(todo.detail);
        }

        if (todo.dueAt > 0) {
            holder.due.setVisibility(View.VISIBLE);
            holder.due.setText(TimeUtils.formatDue(context, todo.dueAt));
            boolean overdue = todo.dueAt < System.currentTimeMillis() && !todo.done;
            holder.due.setTextColor(ContextCompat.getColor(context,
                    overdue ? R.color.danger : R.color.muted));
        } else {
            holder.due.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> listener.onEdit(todo));
        holder.delete.setOnClickListener(v -> listener.onDelete(todo));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class TodoHolder extends RecyclerView.ViewHolder {
        final MaterialCheckBox check;
        final TextView title;
        final TextView detail;
        final TextView due;
        final ImageButton delete;

        TodoHolder(@NonNull View itemView) {
            super(itemView);
            check = itemView.findViewById(R.id.check_todo);
            title = itemView.findViewById(R.id.text_todo_title);
            detail = itemView.findViewById(R.id.text_todo_detail);
            due = itemView.findViewById(R.id.text_todo_due);
            delete = itemView.findViewById(R.id.button_delete_todo);
        }
    }
}
