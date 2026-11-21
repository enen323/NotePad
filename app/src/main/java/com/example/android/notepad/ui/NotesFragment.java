package com.example.android.notepad.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.SearchView;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.android.notepad.MainActivity;
import com.example.android.notepad.R;
import com.example.android.notepad.data.NoteRepository;
import com.example.android.notepad.model.Note;
import com.example.android.notepad.ui.adapter.NoteAdapter;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

/**
 * 笔记列表页：展示时间戳、按标题/内容实时搜索、长按置顶或删除。
 */
public class NotesFragment extends Fragment {

    private static final String STATE_QUERY = "state_query";
    private static final String STATE_SORT = "state_sort";

    private NoteRepository repository;
    private NoteAdapter adapter;
    private RecyclerView recycler;
    private View emptyState;
    private TextView emptyTitle;
    private TextView emptyHint;

    private String query = "";
    private int sortMode = NoteRepository.SORT_UPDATED;

    private final MenuProvider menuProvider = new MenuProvider() {
        @Override
        public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
            inflater.inflate(R.menu.menu_notes, menu);
            setupSearch(menu);
            checkSortItem(menu);
        }

        @Override
        public boolean onMenuItemSelected(@NonNull MenuItem item) {
            int id = item.getItemId();
            if (id == R.id.sort_updated) {
                sortMode = NoteRepository.SORT_UPDATED;
                reload();
                return true;
            }
            if (id == R.id.sort_created) {
                sortMode = NoteRepository.SORT_CREATED;
                reload();
                return true;
            }
            if (id == R.id.sort_title) {
                sortMode = NoteRepository.SORT_TITLE;
                reload();
                return true;
            }
            return false;
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_notes, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = NoteRepository.get(requireContext());
        if (savedInstanceState != null) {
            query = savedInstanceState.getString(STATE_QUERY, "");
            sortMode = savedInstanceState.getInt(STATE_SORT, NoteRepository.SORT_UPDATED);
        }

        recycler = view.findViewById(R.id.recycler_notes);
        emptyState = view.findViewById(R.id.empty_state);
        emptyTitle = view.findViewById(R.id.empty_title);
        emptyHint = view.findViewById(R.id.empty_hint);

        adapter = new NoteAdapter(new NoteAdapter.Listener() {
            @Override
            public void onNoteClick(Note note) {
                openEditor(note.id);
            }

            @Override
            public void onNoteLongClick(Note note, View anchor) {
                showItemMenu(note, anchor);
            }
        });
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);

        view.findViewById(R.id.fab_add_note).setOnClickListener(v -> openEditor(-1));

        requireActivity().addMenuProvider(menuProvider, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_QUERY, query);
        outState.putInt(STATE_SORT, sortMode);
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        List<Note> notes = repository.listNotes(query, sortMode);
        adapter.setItems(notes);

        boolean empty = notes.isEmpty();
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            boolean searching = !query.trim().isEmpty();
            emptyTitle.setText(searching ? R.string.note_empty_search : R.string.note_empty_title);
            emptyHint.setText(R.string.note_empty_hint);
            emptyHint.setVisibility(searching ? View.GONE : View.VISIBLE);
        }

        if (getActivity() instanceof AppCompatActivity) {
            ActionBar bar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (bar != null) {
                bar.setTitle(R.string.title_notes);
                bar.setSubtitle(getString(R.string.note_count, notes.size()));
            }
        }
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).refreshTodoBadge();
        }
    }

    private void setupSearch(Menu menu) {
        MenuItem item = menu.findItem(R.id.action_search);
        if (item == null) {
            return;
        }
        SearchView searchView = (SearchView) item.getActionView();
        if (searchView == null) {
            return;
        }
        searchView.setQueryHint(getString(R.string.note_search_hint));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String text) {
                query = text == null ? "" : text;
                reload();
                searchView.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String text) {
                query = text == null ? "" : text;
                reload();
                return true;
            }
        });
        item.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem menuItem) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem menuItem) {
                query = "";
                reload();
                return true;
            }
        });

        // 排序切换会重建菜单，这里恢复搜索框状态
        if (!query.isEmpty()) {
            item.expandActionView();
            searchView.setQuery(query, false);
        }
    }

    private void checkSortItem(Menu menu) {
        int id = sortMode == NoteRepository.SORT_CREATED ? R.id.sort_created
                : sortMode == NoteRepository.SORT_TITLE ? R.id.sort_title : R.id.sort_updated;
        MenuItem item = menu.findItem(id);
        if (item != null && !item.isChecked()) {
            item.setChecked(true);
        }
    }

    private void openEditor(long noteId) {
        Intent intent = new Intent(requireContext(), NoteEditorActivity.class);
        if (noteId > 0) {
            intent.putExtra(NoteEditorActivity.EXTRA_NOTE_ID, noteId);
        }
        startActivity(intent);
    }

    private void showItemMenu(Note note, View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.inflate(R.menu.menu_note_item);
        MenuItem pinItem = popup.getMenu().findItem(R.id.action_pin);
        if (pinItem != null) {
            pinItem.setTitle(note.pinned ? R.string.menu_unpin : R.string.menu_pin);
        }
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_pin) {
                repository.setPinned(note.id, !note.pinned);
                Toast.makeText(requireContext(),
                        note.pinned ? R.string.unpinned : R.string.pinned, Toast.LENGTH_SHORT).show();
                reload();
                return true;
            }
            if (id == R.id.action_delete_note) {
                confirmDelete(note);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void confirmDelete(Note note) {
        String title = note.title == null || note.title.trim().isEmpty()
                ? getString(R.string.note_untitled) : note.title.trim();
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete_note_title)
                .setMessage(getString(R.string.delete_note_message, title))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    repository.deleteNote(note.id);
                    reload();
                })
                .show();
    }
}
