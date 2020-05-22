package com.madderate.musicbox;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.madderate.musicbox.model.MusicInfo;

import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    // 和MusicAmount有关的
    public static final String MAIN_ACTIVITY_ACTION = "com.madderate.musicbox.MAIN_ACTIVITY";
    public static final String MAIN_ACTIVITY_NAME = "main_activity";
    public static final String MUSIC_AMOUNT = "music_amount";
    public static final String MUSIC_LIST = "music_list";
    public static final String MUSIC_INFO = "music_info";
    public static final String IS_PLAYING = "is_playing";
    public static final String LIST_POS = "list_pos";
    public static final String SEEK_POS = "seek_pos";
    public static final String DURATION = "duration";
    public static final String FUNCTION = "function";

    private Intent musicServiceIntent;

    // main body
    private static ListView mListView;
    private static TextView listTitle;

    // controller
    private ImageView btnPrevious;
    private ImageView btnNext;
    private static ImageView btnPlay;
    private static TextView playingName;
    private static TextView playingArtist;
    private static SeekBar musicSeekBar;
    private static ImageView btnFunc;

    // broadcast receiver
    private MainActivityReceiver receiver;

    // variables
    private static boolean music_playing = false;
    private static boolean isStatusSaved = false;
    private int pos;

    // ********************************************
    // onCreate部分
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 获取权限
        initPermissions();

        // 注册receiver
        receiver = new MainActivityReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(MAIN_ACTIVITY_ACTION);
        registerReceiver(receiver, filter);

        // 绑定视图
        setContentView(R.layout.activity_main);

        // 启动服务
        musicServiceIntent = new Intent(MainActivity.this, MusicService.class);
        startService(musicServiceIntent);

        // 初始化组件
        initWidgets();

        setListViewOnItemClickListener();
        setButtonsOnClickListener();
        setMusicSeekBarListener();
    }

    private void initPermissions() {
        // 请求读外存权限
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_DENIED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        1
                );
            }
        }
    }

    private void initWidgets() {
        // 绑定布局中的widgets
        listTitle = findViewById(R.id.music_list_title);
        playingName = findViewById(R.id.music_name);
        playingArtist = findViewById(R.id.artist_name);
        musicSeekBar = findViewById(R.id.music_seek_bar);

        btnPrevious = findViewById(R.id.btn_previous);
        btnNext = findViewById(R.id.btn_next);
        btnPlay = findViewById(R.id.btn_play);
        mListView = findViewById(R.id.music_list);
        btnFunc = findViewById(R.id.btn_func);
    }

    private void setListViewOnItemClickListener() {
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                pos = position; // 记录position 方便更新曲名与歌手的TextView

                // 这里操作之后，歌曲将会暂停，待播放的歌曲下标将会更新
                Intent intent = new Intent(MusicService.MUSIC_SERVICE_ACTION);
                intent.setPackage(getPackageName());
                intent.putExtra(MusicService.MUSIC_SERVICE_NAME, StatusSigns.CLICK_LIST_ITEM);
                // 将点击的位置包装到Intent中，通过广播传给Service
                intent.putExtra(MusicService.LIST_POS, position);
                sendBroadcast(intent);
            }
        });
    }

    private void setButtonsOnClickListener() {
        btnPrevious.setOnClickListener(this);
        btnNext.setOnClickListener(this);
        btnPlay.setOnClickListener(this);
        btnFunc.setOnClickListener(this);
    }

    // 这部分处理SeekBar的滑块移动过程
    // 当发生onStopTrackingTouch的时候，发送广播通知
    private void setMusicSeekBarListener() {
        musicSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Intent intent = new Intent(MusicService.MUSIC_SERVICE_ACTION);
                intent.setPackage(getPackageName());
                intent.putExtra(MusicService.MUSIC_SERVICE_NAME, StatusSigns.SEEK_TO);
                intent.putExtra(MusicService.SEEK_POS, seekBar.getProgress());
                sendBroadcast(intent);
            }
        });
    }
    // ***************************************

    // ***************************************
    // View.OnClickListener的onClick方法实现
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_play:
                sendPlayOrPauseStatusBroadcast(music_playing);
                break;
            case R.id.btn_next:
                sendNextOrPrevStatusBroadcast(R.id.btn_next);
                break;
            case R.id.btn_previous:
                sendNextOrPrevStatusBroadcast(R.id.btn_previous);
                break;
            case R.id.btn_func:
                Intent intent = new Intent(MusicService.MUSIC_SERVICE_ACTION);
                intent.setPackage(getPackageName());
                intent.putExtra(MusicService.MUSIC_SERVICE_NAME, StatusSigns.CHANGE_PLAY_MODE);
                sendBroadcast(intent);
                break;
            default:
                break;
        }
    }

    // 发送播放或暂停的广播给Service
    // 因为只有一个按钮控制播放和暂停
    // 播放和暂停依据传入的boolean值判断
    private void sendPlayOrPauseStatusBroadcast(boolean isPlaying) {
        Intent intent = new Intent(MusicService.MUSIC_SERVICE_ACTION);
        intent.setPackage(getPackageName());
        if (isPlaying) {
            // 发pause
            intent.putExtra(MusicService.MUSIC_SERVICE_NAME, StatusSigns.PAUSE);
        } else {
            // 发play
            intent.putExtra(MusicService.MUSIC_SERVICE_NAME, StatusSigns.PLAY);
        }
        // 第一次或从空开始播放音乐需要获取MusicInfo
        // 而其他情况的播放就不需要了
        if (playingName.getText().equals("") || playingArtist.getText().equals("")) {
            intent.putExtra(MusicService.REQUIRE_MUSIC_INFO, true);
        } else {
            intent.putExtra(MusicService.REQUIRE_MUSIC_INFO, false);
        }
        sendBroadcast(intent);
    }

    // 向Service发送下一首或上一首的广播消息
    // 根据传入的widget id判断
    private void sendNextOrPrevStatusBroadcast(int id) {
        Intent intent = new Intent(MusicService.MUSIC_SERVICE_ACTION);
        intent.setPackage(getPackageName());
        if (id == R.id.btn_next) {
            // 发next的广播
            intent.putExtra(MusicService.MUSIC_SERVICE_NAME, StatusSigns.NEXT);
        } else if (id == R.id.btn_previous) {
            // 发prev的广播
            intent.putExtra(MusicService.MUSIC_SERVICE_NAME, StatusSigns.PREV);
        }
        sendBroadcast(intent);
    }
    // *****************************************


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            moveTaskToBack(true);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        Log.d("MainActivity", "onDestroy");
        super.onDestroy();
        music_playing = false;
        unregisterReceiver(receiver);
        stopService(musicServiceIntent);
    }



    // 用Broadcast Receiver从Service获得数据，修改UI
    static class MainActivityReceiver extends BroadcastReceiver {
        private ListAdapter adapter;

        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getIntExtra(MAIN_ACTIVITY_NAME, -1)) {
                case StatusSigns.SET_LIST_TITLE:
                    int musicAmount = intent.getIntExtra(MUSIC_AMOUNT, 0);
                    String title = "本地音乐（总数：" +musicAmount + "）";
                    listTitle.setText(title);
                    break;

                case StatusSigns.SET_LIST_VIEW:
                    setListView(context, intent);
                    break;

                case StatusSigns.PLAY:
                    music_playing = intent.getBooleanExtra(IS_PLAYING, false);
                    updatePlayBtnResource(music_playing);
                    break;

                case StatusSigns.START:
                    updateControllerPanelAndListFocusedItem(intent);
                    break;

                case StatusSigns.RESET_ACTIVITY_UI:
                    resetActivityUI();
                    break;

                case StatusSigns.SEEK_TO:
                    musicSeekBar.setProgress(intent.getIntExtra(SEEK_POS, 0));
                    musicSeekBar.setMax(intent.getIntExtra(DURATION, 0));
                    break;

                case StatusSigns.CHANGE_PLAY_MODE:
                    btnFunc.setImageResource(intent.getIntExtra(FUNCTION, R.drawable.repeat_all));
                    break;

//                case StatusSigns.STORE_STATUS:
//                    isStatusSaved = true;
//                    break;
            }
        }

        private void resetActivityUI() {
            music_playing = false;
            playingName.setText("");
            playingArtist.setText("");
            musicSeekBar.setMax(0);
            musicSeekBar.setProgress(0);
            updatePlayBtnResource(music_playing);

            if (adapter == null) {
                adapter = (ListAdapter)mListView.getAdapter();
            }
            adapter.setFocusItemPos(-1);
        }

        private void setListView(Context context,Intent intent) {
            try {
                if (intent.getSerializableExtra(MUSIC_LIST) != null) {
                    List<MusicInfo> musicList = (List<MusicInfo>) intent.getSerializableExtra(MUSIC_LIST);
                    adapter = new ListAdapter(context, musicList);
                    mListView.setAdapter(adapter);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void updateControllerPanelAndListFocusedItem(Intent intent) {
            MusicInfo info = (MusicInfo) intent.getSerializableExtra(MUSIC_INFO);
            if (info != null) {
                playingName.setText(info.getMusicTitle());
                playingArtist.setText(info.getMusicArtist());
                musicSeekBar.setMax(info.getMusicDuration());
                musicSeekBar.setProgress(0);
            } else {
                playingName.setText("");
                playingArtist.setText("");
                musicSeekBar.setMax(0);
                musicSeekBar.setProgress(0);
            }

            int position = intent.getIntExtra(LIST_POS, 0);
            if (adapter == null) {
                adapter = (ListAdapter)mListView.getAdapter();
            }
            adapter.setFocusItemPos(position);
        }

        private void updatePlayBtnResource(boolean isPlaying) {
            if (isPlaying) {
                btnPlay.setImageResource(R.drawable.pause);
            } else {
                btnPlay.setImageResource(R.drawable.play);
            }
        }
    }
}
