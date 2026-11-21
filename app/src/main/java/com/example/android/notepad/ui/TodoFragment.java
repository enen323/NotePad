package com.example.android.notepad.ui;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.android.notepad.MainActivity;
import com.example.android.notepad.R;
import com.example.android.notepad.data.NoteRepository;
import com.example.android.notepad.model.Todo;
import com.example.android.notepad.ui.adapter.TodoAdapter;
import com.example.android.notepad.util.TimeUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Calendar;
import java.util.List;

/**
 * 待办页：新增 / 编辑 / 勾选完成 / 删除（支持撤销），顶部按状态筛选。
 */
public class TodoFragment extends Fragment {

    private static final String STATE_FILTER = "state_filter";

    private NoteRepository repository;
    private TodoAdapter adapter;
    private RecyclerView recycler;
    private View emptyState;

    private int filter = NoteRepository.FILTER_ALL;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_todo, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = NoteRepository.get(requireContext());
        if (savedInstanceState != null) {
            filter = savedInstanceState.getInt(STATE_FILTER, NoteRepository.FILTER_ALL);
        }

        recycler = view.findViewById(R.id.recycler_todo);
        emptyState = view.findViewById(R.id.empty_state);

        adapter = new TodoAdapter(new TodoAdapter.Listener() {
            @Override
            public void onToggle(Todo todo, boolean done) {
                repository.setTodoDone(todo.id, done);
                reload();
            }

            @Override
            public void onEdit(Todo todo) {
                showTodoDialog(todo);
            }

            @Override
            public void onDelete(Todo todo) {
                deleteTodo(todo);
            }
        });
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);

        view.findViewById(R.id.fab_add_todo).setOnClickListener(v -> showTodoDialog(null));

        ChipGroup chipGroup = view.findViewById(R.id.chip_group);
        if (filter == NoteRepository.FILTER_ACTIVE) {
            chipGroup.check(R.id.chip_active);
        } else if (filter == NoteRepository.FILTER_DONE) {
            chipGroup.check(R.id.chip_done);
        } else {
            chipGroup.check(R.id.chip_all);
        }
        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            int checked = checkedIds.isEmpty() ? R.id.chip_all : checkedIds.get(0);
            if (checked == R.id.chip_active) {
                filter = NoteRepository.FILTER_ACTIVE;
            } else if (checked == R.id.chip_done) {
                filter = NoteRepository.FILTER_DONE;
            } else {
                filter = NoteRepository.FILTER_ALL;
            }
            reload();
        });
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_FILTER, filter);
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        List<Todo> todos = repository.listTodos(filter);
        adapter.setItems(todos);
        emptyState.setVisibility(todos.isEmpty() ? View.VISIBLE : View.GONE);

        if (getActivity() instanceof AppCompatActivity) {
            ActionBar bar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (bar != null) {
                bar.setTitle(R.string.title_todo);
                int active = repository.countActiveTodos();
                bar.setSubtitle(getString(R.string.todo_count, active));
            }
        }
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).refreshTodoBadge();
        }
    }

    private void showTodoDialog(@Nullable final Todo existing) {
        View content = getLayoutInflater().inflate(R.layout.dialog_todo, null);
        TextInputLayout titleLayout = content.findViewById(R.id.layout_todo_title);
        TextInputEditText titleInput = content.findViewById(R.id.edit_todo_title);
        TextInputEditText detailInput = content.findViewById(R.id.edit_todo_detail);
        MaterialButton dueButton = content.findViewById(R.id.button_due);
        View clearDue = content.findViewById(R.id.button_clear_due);

        final long[] dueAt = {existing == null ? 0 : existing.dueAt};
        updateDueButton(dueButton, clearDue, dueAt[0]);

        dueButton.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            if (dueAt[0] > 0) {
                calendar.setTimeInMillis(dueAt[0]);
            }
            DatePickerDialog picker = new DatePickerDialog(requireContext(),
                    (view, year, month, dayOfMonth) -> {
                        dueAt[0] = TimeUtils.endOfDay(year, month, dayOfMonth);
                        updateDueButton(dueButton, clearDue, dueAt[0]);
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH));
            picker.show();
        });

        clearDue.setOnClickListener(v -> {
            dueAt[0] = 0;
            updateDueButton(dueButton, clearDue, 0);
        });

        if (existing != null) {
            titleInput.setText(existing.title);
            detailInput.setText(existing.detail);
            if (existing.title != null) {
                titleInput.setSelection(existing.title.length());
            }
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(existing == null ? R.string.todo_dialog_title_new : R.string.todo_dialog_title_edit)
                .setView(content)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.menu_save, null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String title = titleInput.getText() == null ? "" : titleInput.getText().toString().trim();
                    if (title.isEmpty()) {
                        titleLayout.setError(getString(R.string.todo_title_required));
                        return;
                    }
                    titleLayout.setError(null);
                    String detail = detailInput.getText() == null ? "" : detailInput.getText().toString().trim();

                    if (existing == null) {
                        Todo todo = new Todo();
                        todo.title = title;
                        todo.detail = detail;
                        todo.dueAt = dueAt[0];
                        todo.createdAt = System.currentTimeMillis();
                        repository.insertTodo(todo);
                    } else {
                        existing.title = title;
                        existing.detail = detail;
                        existing.dueAt = dueAt[0];
                        repository.updateTodo(existing);
                    }
                    dialog.dismiss();
                    reload();
                }));
        dialog.show();
    }

    private void updateDueButton(MaterialButton button, View clearView, long dueAt) {
        if (dueAt > 0) {
            button.setText(getString(R.string.todo_due_set, TimeUtils.formatDate(dueAt)));
            clearView.setVisibility(View.VISIBLE);
        } else {
            button.setText(R.string.todo_due_none);
            clearView.setVisibility(View.GONE);
        }
    }

    private void deleteTodo(final Todo todo) {
        repository.deleteTodo(todo.id);
        reload();
        Snackbar.make(recycler, R.string.todo_deleted, Snackbar.LENGTH_LONG)
                .setAction(R.string.undo, v -> {
                    repository.restoreTodo(todo);
                    reload();
                })
                .show();
    }
}
