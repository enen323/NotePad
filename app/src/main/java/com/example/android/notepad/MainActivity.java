package com.example.android.notepad;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.android.notepad.data.NoteRepository;
import com.example.android.notepad.ui.NotesFragment;
import com.example.android.notepad.ui.TodoFragment;
import com.example.android.notepad.util.UiUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * 主界面：底部导航在「笔记」和「待办」两个 Fragment 之间切换。
 */
public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;
    private NoteRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        UiUtils.applySystemBarsInsets(findViewById(R.id.root));

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        repository = NoteRepository.get(this);
        bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setOnItemSelectedListener(item -> {
            showTab(item.getItemId());
            return true;
        });

        if (savedInstanceState == null) {
            // 菜单第一项默认选中，这里只负责挂载对应的 Fragment
            showTab(R.id.nav_notes);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshTodoBadge();
    }

    private void showTab(int itemId) {
        Fragment fragment = itemId == R.id.nav_todo ? new TodoFragment() : new NotesFragment();
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment, "tab_" + itemId)
                .commit();
    }

    public void setToolbarSubtitle(CharSequence subtitle) {
        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            bar.setSubtitle(subtitle);
        }
    }

    /** 底部「待办」上的未完成数量角标 */
    public void refreshTodoBadge() {
        if (bottomNav == null) {
            return;
        }
        int count = repository.countActiveTodos();
        if (count > 0) {
            BadgeDrawable badge = bottomNav.getOrCreateBadge(R.id.nav_todo);
            badge.setVisible(true);
            badge.setNumber(count);
            badge.setBackgroundColor(ContextCompat.getColor(this, R.color.accent));
            badge.setBadgeTextColor(ContextCompat.getColor(this, R.color.white));
        } else {
            bottomNav.removeBadge(R.id.nav_todo);
        }
    }
}
