package com.madderate.musicbox;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.provider.MediaStore;

import com.madderate.musicbox.model.MusicInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class MusicManager {

    private List<MusicInfo> musicList = new ArrayList<>();

    private MediaPlayer mPlayer;
    private Context context;

    private int seekLength = 0;
    private int currentIndex = -1;
    private int total_music;

    public MusicManager(Context context) {
        this.context = context;
        ResolveMusicToList();
        InitPlayer();
    }

    private void InitPlayer() {
        mPlayer = new MediaPlayer();
        mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
    }

    private void ResolveMusicToList() {
        // 查询条件
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        // 按照显示名排序
        String sortOrder = MediaStore.MediaColumns.DISPLAY_NAME + "";
        // 查询的列名
        String [] projection = {
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DURATION
        };
        // 利用ContentResolver得到用ContentProvider接口封装的开放数据
        ContentResolver contentResolver = context.getContentResolver();
        Cursor cursor = contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
        );
        if (cursor != null) {
            // 得到查询的音乐文件总数
            total_music = cursor.getCount();
            //遍历游标的每一条数据，按照MusicInfo的方式存储，并添加到List中
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                MusicInfo mInfo = new MusicInfo();
                mInfo.setMusicTitle(cursor.getString(0));
                mInfo.setMusicArtist(cursor.getString(1));
                mInfo.setMusicName(cursor.getString(2));
                mInfo.setMusicPath(cursor.getString(3));
                mInfo.setMusicDuration(Integer.parseInt(cursor.getString(4)));
                // 将当前的数据加入List
                musicList.add(mInfo);
            }
        }
    }

    public void Release() {
        mPlayer.reset();
        mPlayer.stop();
        mPlayer.release();
    }

    public void Pause() {
        if (mPlayer.isPlaying()) {
            mPlayer.pause();
            seekLength = mPlayer.getCurrentPosition();
        }
    }

    public void Resume() {
        mPlayer.seekTo(seekLength);
        mPlayer.start();
    }

    public void SetSeekPos(int seekPos) {
        mPlayer.seekTo(seekPos);
    }

    public void Reset() {
        seekLength = 0;
        mPlayer.seekTo(seekLength);
    }

    public void Play() {
        mPlayer.reset();
        Uri path = Uri.parse(musicList.get(currentIndex).getMusicPath());
        try {
            mPlayer.setDataSource(String.valueOf(path));
            mPlayer.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mPlayer.seekTo(seekLength);
        mPlayer.start();
    }

    // 用在随机播放中
    public void PlayNext(int index) {
        currentIndex = index;
        if (currentIndex >= musicList.size()) {
            currentIndex = 0;
        }
        seekLength = 0;
        if (mPlayer.isPlaying()) {
            Play();
        }
    }

    void PlayNext() {
        currentIndex = currentIndex + 1;
        if (currentIndex >= musicList.size()) {
            currentIndex = 0;
        }
        seekLength = 0;
        if (mPlayer.isPlaying()) {
            Play();
        }
    }

    void PlayPrev() {
        currentIndex = currentIndex - 1;
        if (currentIndex < 0) {
            currentIndex = musicList.size() - 1;
        }
        seekLength = 0;
        if (mPlayer.isPlaying()) {
            Play();
        }
    }

    boolean isPlaying() {
        return mPlayer.isPlaying();
    }

    int getDuration() {
        return mPlayer.getDuration();
    }

    int getCurrentPosition() {
        return mPlayer.getCurrentPosition();
    }

    void seekTo(int seekLength) {
        this.seekLength = seekLength;
        mPlayer.seekTo(seekLength);
    }

    public void setCurrentIndex(int index) {
        currentIndex = index;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public List<MusicInfo> getMusicList() {
        return musicList;
    }

    public int getTotalMusic() {
        return total_music;
    }

    MusicInfo getMusicInfo(int index) {
        return musicList.get(index);
    }
}
