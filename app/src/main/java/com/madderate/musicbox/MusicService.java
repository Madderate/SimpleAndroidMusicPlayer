package com.madderate.musicbox;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.madderate.musicbox.model.MusicInfo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MusicService extends Service {

    public static final String MUSIC_SERVICE_ACTION = "com.madderate.musicbox.MUSIC_SERVICE";
    public static final String MUSIC_SERVICE_NAME = "music_service";
    public static final String LIST_POS = "list_pos";
    public static final String SEEK_POS = "seek_pos";
    public static final String REQUIRE_MUSIC_INFO = "require_music_info";

    public static String CURRENT_MUSIC_NAME = "current_music_name";
    public static String CURRENT_INDEX = "current_index";
    public static String CURRENT_DURATION = "current_duration";
    public static String CURRENT_POS = "current_pos";
    public static String FUNCTION = "function";

    private static MusicManager manager;

    private MusicServiceReceiver receiver;

    private static int function;

    // 控制线程中循环的变量
    // 让广播只发送一遍
    private static boolean isNext;
    private Handler handler = new Handler();
    private Runnable thread = new Runnable() {
        @Override
        public void run() {
            if (manager != null) {
                try {
                    if (manager.isPlaying()) {
                        Intent intent = new Intent(MUSIC_SERVICE_ACTION);
                        intent.setPackage(getPackageName());
                        intent.putExtra(MUSIC_SERVICE_NAME, StatusSigns.CURRENT_POS);
                        sendBroadcast(intent);
//                            Log.d("MusicSvcReceiverThread",
//                                    "current: " + currentPos
//                                            + "\tduration: " + duration
//                                            + "%: " + ((float) currentPos / (float) duration) * 100
//                            );
                        // 线程发送广播不像指导中直接调用updateStatus那样
                        // 广播的发送与接收之间有较大延迟
                        // 因此通过一个isNext来控制广播的单次发送与接收
                        // 避免出现重复发送广播导致跳过部分歌曲的情况
                        if (manager.getCurrentPosition() / 1000 >= manager.getDuration() / 1000 && !isNext) {
                            // 表示已经发送过该广播
                            isNext = true;
                            // 先实现列表顺序播放
                            sendNextStatusBroadcast();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            handler.post(thread);
        }

        private void sendNextStatusBroadcast() {
            Intent intent = new Intent(MUSIC_SERVICE_ACTION);
            intent.setPackage(getPackageName());
            intent.putExtra(MUSIC_SERVICE_NAME, StatusSigns.NEXT);
            sendBroadcast(intent);
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null && intent.getAction().equals("STOP")) {
            stopSelf();
        }
        return START_STICKY;
    }

    // *****************************
    // onCreate部分
    @Override
    public void onCreate() {
        startServiceAsForeground(); // 请求前台服务
        manager = new MusicManager(MusicService.this); // onCreate时将manager初始化
        // 注册register
        // 第一步就能创建MusicManager对象
        receiver = new MusicServiceReceiver(MusicService.this);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(MUSIC_SERVICE_ACTION);
        registerReceiver(receiver, intentFilter);

        // music manager只存在于MusicServiceReceiver中
        // 因此不管是MainActivity还是Service
        // 需要使用MusicManager对象中的内容时
        // 都需要发送一条广播向MusicServiceReceiver申请
        requestSendMusicAmountBroadcast();
        requestSendMusicListBroadcast();

        // 读文件
        readStatusFromSharedPreferences();

        isNext = false;

        if (handler == null) {
            handler = new Handler();
        }
        // 启动线程
        handler.post(thread);
    }

    private void readStatusFromSharedPreferences() {
        SharedPreferences preferences = getSharedPreferences("status", MODE_PRIVATE);
        if(preferences != null) {
            String currentMusicName = preferences.getString(CURRENT_MUSIC_NAME, "");
            int currentIndex = preferences.getInt(CURRENT_INDEX, -1);
            int currentPos = preferences.getInt(CURRENT_POS, 0);
            int currentDuration = preferences.getInt(CURRENT_DURATION, 0);
            function = preferences.getInt(FUNCTION, R.drawable.repeat_all);

            if (currentIndex != -1) {
                manager.setCurrentIndex(currentIndex);
                if (currentMusicName.equals(manager.getMusicInfo(manager.getCurrentIndex()).getMusicName())) {
                    manager.seekTo(currentPos);
                } else {
                    manager.setCurrentIndex(-1);
                }
            }
            if (manager.getCurrentIndex() != -1) {
                notifyRebuildStatusBroadcast(currentPos, currentDuration);
            }
        }
    }

    private void notifyRebuildStatusBroadcast(int currentPos, int duration) {
        Intent intent = new Intent(MUSIC_SERVICE_ACTION);
        intent.setPackage(getPackageName());
        intent.putExtra(MUSIC_SERVICE_NAME, StatusSigns.START);
        intent.putExtra(CURRENT_POS, currentPos);
        intent.putExtra(CURRENT_DURATION, duration);
        sendBroadcast(intent);
    }

    private void startServiceAsForeground() {
        String id = "MusicService";
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String description = "MusicBox Foreground Service";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            String SERVICE_NAME = "music_service";
            NotificationChannel channel =
                    new NotificationChannel(id, SERVICE_NAME, importance);
            channel.setDescription(description);
            try {
                manager.createNotificationChannel(channel);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
        Notification notification = new NotificationCompat.Builder(this, id)
                .setContentTitle("MusicBox")
                .setContentText("Music is running...")
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.drawable.gnote)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.gnote))
                .setContentIntent(pendingIntent)
                .build();
        startForeground(1, notification);
    }

    // 向MusicService的BroadcastReceiver发送一条广播
    // 提示需要向MainActivity提供一些必要数据
    // 以供Activity更新UI

    // 这里是请求提供歌曲数量的信息
    private void requestSendMusicAmountBroadcast() {
        Intent intent = new Intent(MUSIC_SERVICE_ACTION);
        intent.setPackage(getPackageName());
        intent.putExtra(MUSIC_SERVICE_NAME, StatusSigns.SET_LIST_TITLE);
        sendBroadcast(intent);
    }

    // 这里是请求提供歌曲列表
    private void requestSendMusicListBroadcast() {
        Intent intent = new Intent(MUSIC_SERVICE_ACTION);
        intent.setPackage(getPackageName());
        intent.putExtra(MUSIC_SERVICE_NAME, StatusSigns.SET_LIST_VIEW);
        sendBroadcast(intent);
    }
    // *******************************************

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Log.d("MusicService", "onDestroy");
        super.onDestroy();
        manager.Pause();
        storeStatus();
        handler.removeCallbacks(thread);
        unregisterReceiver(receiver);
        manager.Release();
    }

    private void storeStatus() {
        if (manager.getCurrentIndex() != -1) {
            SharedPreferences.Editor editor = getSharedPreferences("status", MODE_PRIVATE).edit();
            editor.putInt(CURRENT_INDEX, manager.getCurrentIndex());
            editor.putInt(CURRENT_POS, manager.getCurrentPosition());
            editor.putInt(CURRENT_DURATION, manager.getDuration());
            editor.putString(CURRENT_MUSIC_NAME, manager.getMusicInfo(manager.getCurrentIndex()).getMusicName());
            editor.putInt(FUNCTION, function);
            editor.apply();
        }
    }



    // Service中的BroadcastReceiver
    static class MusicServiceReceiver extends BroadcastReceiver {
        private final int REPEAT_ALL = R.drawable.repeat_all;
        private final int REPEAT_ONE = R.drawable.repeat_one;
        private final int SHUFFLE = R.drawable.shuffle;

        // 判断是否已经用过shuffle模式
        private boolean isShuffled;

        private Random random;
        private List<Integer> shuffleList;
        private int times;

        public MusicServiceReceiver(Context context) {
            isShuffled = false;
            function = REPEAT_ALL;
            random = new Random(System.currentTimeMillis());
            times = 0;
            InitShuffleList();
            sendFunctionStatusBroadcast(context);
        }

        private void InitShuffleList() {
            shuffleList = new ArrayList<>();
            int totalMusic = manager.getTotalMusic();
            // 外层循环用来生成随机数
            for (int i = 0; i < manager.getTotalMusic(); i++) {
                int ranNum = random.nextInt(totalMusic);
                if (!shuffleList.isEmpty()) {
                    while (shuffleList.contains(ranNum)) {
                        ranNum = random.nextInt(totalMusic);
                    }
                }
                shuffleList.add(ranNum);
            }
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getIntExtra(MUSIC_SERVICE_NAME, -1)) {
                case StatusSigns.SET_LIST_TITLE:
                    sendMusicAmountBroadcast(context);
                    break;

                case StatusSigns.SET_LIST_VIEW:
                    sendMusicListBroadcast(context);
                    break;

                case StatusSigns.START:
                    sendStartStatusBroadcast(context, manager.getCurrentIndex());
                    sendCurrentPosBroadcast(context, intent.getIntExtra(CURRENT_POS, 0), intent.getIntExtra(CURRENT_DURATION, 0));
                    sendFunctionStatusBroadcast(context);
                    break;

                case StatusSigns.PLAY:
                    playStatus(context, intent);
                    break;

                case StatusSigns.PAUSE:
                    manager.Pause();
                    if (intent.getBooleanExtra(REQUIRE_MUSIC_INFO, false))
                        sendStartStatusBroadcast(context, manager.getCurrentIndex());
                    sendPlayStatusBroadcast(context);
                    break;

                case StatusSigns.NEXT:
                    nextStatus(context);
                    isNext = false;
                    break;

                case StatusSigns.PREV:
                    prevStatus(context);
                    break;

                case StatusSigns.CLICK_LIST_ITEM:
                    clickListItemStatus(context, intent);
                    break;

                case StatusSigns.RESET_ACTIVITY_UI:
                    if (manager.isPlaying()) {
                        manager.Pause();
                    }
                    manager.setCurrentIndex(0);
                    manager.Reset();
                    break;

                case StatusSigns.SEEK_TO:
                    try {
                        int seekPos = intent.getIntExtra(SEEK_POS, 0);
                        manager.seekTo(seekPos);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;

                case StatusSigns.CHANGE_PLAY_MODE:
                    changePlayMode(context);
                    break;

                case StatusSigns.RECOVER:
                    sendStartStatusBroadcast(context, manager.getCurrentIndex());
                    sendCurrentPosBroadcast(context,
                            intent.getIntExtra(CURRENT_POS, 0) ,
                            intent.getIntExtra(CURRENT_DURATION, 0));
                    break;

                case StatusSigns.CURRENT_POS:
                    sendCurrentPosBroadcast(context, manager.getCurrentPosition(), manager.getDuration());
                    break;

                default:
                    break;
            }
        }

        private void changePlayMode(Context context) {
            switch (function) {
                case REPEAT_ALL:
                    function = REPEAT_ONE;
                    break;
                case REPEAT_ONE:
                    function = SHUFFLE;
                    break;
                case SHUFFLE:
                    function = REPEAT_ALL;
                    isShuffled = true;
                    times = 0;
                    break;
            }
            if (isShuffled) {
                // 先清空
                shuffleList.clear();
                // 再重新添加
                InitShuffleList();
                isShuffled = false;
            }
            sendFunctionStatusBroadcast(context);
        }


        private void playStatus(Context context, Intent intent) {
            // 首次或者在controller panel没有内容的时候
            if (manager.getCurrentIndex() == -1) {
                switch (function) {
                    case REPEAT_ALL:
                    case REPEAT_ONE:
                        manager.setCurrentIndex(0);
                        break;
                    case SHUFFLE:
                        isShuffled = true;
                        manager.setCurrentIndex(shuffleList.get(0));
                        times = 0;
                        break;
                }
                // 已经有内容在播放的情况
            } else {
                manager.setCurrentIndex(manager.getCurrentIndex());
            }
            manager.Play();
            manager.Resume();

            if (intent.getBooleanExtra(REQUIRE_MUSIC_INFO, false))
                sendStartStatusBroadcast(context, manager.getCurrentIndex());
            sendPlayStatusBroadcast(context);
        }

        private void nextStatus(Context context) {
            // 顺序向下播放，不循环
//            if (manager.isPlaying()) {
//                if (manager.getCurrentIndex() == manager.getMusicList().size() - 1) {
//                    resetMusicManager();
//                    sendResetControllerPanelStatusBroadcast(context);
//                } else {
//                    manager.PlayNext();
//                    sendStartStatusBroadcast(context, manager.getCurrentIndex());
//                }
//            } else {
//                manager.PlayNext();
//                sendStartStatusBroadcast(context, manager.getCurrentIndex());
//            }

            switch (function) {
                case REPEAT_ALL:
                    manager.PlayNext();
                    sendStartStatusBroadcast(context, manager.getCurrentIndex());
                    break;

                case REPEAT_ONE:
                    manager.Reset();
                    sendStartStatusBroadcast(context, manager.getCurrentIndex());
                    break;

                case SHUFFLE:
                    if (times < manager.getTotalMusic() - 1) {
                        times++;
                        manager.PlayNext(shuffleList.get(times));
                        int index = manager.getCurrentIndex();
                        sendStartStatusBroadcast(context, index);
                    } else {
                        times = 0;
                        resetMusicManager();
                        shuffleList.clear();
                        InitShuffleList();
                        sendResetActivityUIStatusBroadcast(context);
                    }
                    break;
            }
        }

        private void prevStatus(Context context) {
            // 顺序向上播放，不循环
//            if (manager.isPlaying()) {
//                if (manager.getCurrentIndex() == 0) {
//                    resetMusicManager();
//                    sendResetControllerPanelStatusBroadcast(context);
//                } else {
//                    manager.PlayPrev();
//                    sendStartStatusBroadcast(context, manager.getCurrentIndex());
//                }
//            } else {
//                manager.PlayPrev();
//                sendStartStatusBroadcast(context, manager.getCurrentIndex());
//            }

            switch (function) {
                case REPEAT_ALL:
                    manager.PlayPrev();
                    sendStartStatusBroadcast(context, manager.getCurrentIndex());
                    break;

                case REPEAT_ONE:
                    manager.Reset();
                    sendStartStatusBroadcast(context, manager.getCurrentIndex());
                    break;

                case SHUFFLE:
                    times--;
                    if (times < 0) {
                        times = 0;
                        resetMusicManager();
                        shuffleList.clear();
                        InitShuffleList();
                        sendResetActivityUIStatusBroadcast(context);
                    } else {
                        manager.PlayNext(shuffleList.get(times));
                        int index = manager.getCurrentIndex();
                        sendStartStatusBroadcast(context, index);
                    }
                    break;
            }
        }

        private void resetMusicManager() {
            manager.Pause();
            manager.Reset();
            manager.setCurrentIndex(-1);
        }

        private void clickListItemStatus(Context context, Intent intent) {
            int position = intent.getIntExtra(MusicService.LIST_POS, -1);
            manager.setCurrentIndex(position);
            manager.Reset();
            manager.Play();
            sendStartStatusBroadcast(context, position);
            sendPlayStatusBroadcast(context);
        }

        private void sendCurrentPosBroadcast(Context context, int currentPos, int duration) {
            Intent intent = new Intent(MainActivity.MAIN_ACTIVITY_ACTION);
            intent.setPackage(context.getPackageName());
            intent.putExtra(MainActivity.MAIN_ACTIVITY_NAME, StatusSigns.SEEK_TO);
            intent.putExtra(MainActivity.SEEK_POS, currentPos);
            intent.putExtra(MainActivity.DURATION, duration);
            context.sendBroadcast(intent);
        }

        private void sendMusicListBroadcast(Context context) {
            Intent intent = new Intent(MainActivity.MAIN_ACTIVITY_ACTION);
            intent.setPackage(context.getPackageName());
            intent.putExtra(MainActivity.MAIN_ACTIVITY_NAME, StatusSigns.SET_LIST_VIEW);
            intent.putExtra(MainActivity.MUSIC_LIST, (Serializable) manager.getMusicList());
            context.sendBroadcast(intent);
        }

        private void sendMusicAmountBroadcast(Context context) {
            Intent intent = new Intent(MainActivity.MAIN_ACTIVITY_ACTION);
            intent.setPackage(context.getPackageName());
            intent.putExtra(MainActivity.MAIN_ACTIVITY_NAME, StatusSigns.SET_LIST_TITLE);
            intent.putExtra(MainActivity.MUSIC_AMOUNT, manager.getTotalMusic());
            context.sendBroadcast(intent);
        }

        private void sendFunctionStatusBroadcast(Context context) {
            Intent intent = new Intent(MainActivity.MAIN_ACTIVITY_ACTION);
            intent.setPackage(context.getPackageName());
            intent.putExtra(MainActivity.MAIN_ACTIVITY_NAME, StatusSigns.CHANGE_PLAY_MODE);
            intent.putExtra(MainActivity.FUNCTION, function);
            context.sendBroadcast(intent);
        }

        private void sendResetActivityUIStatusBroadcast(Context context) {
            Intent intent = new Intent(MainActivity.MAIN_ACTIVITY_ACTION);
            intent.setPackage(context.getPackageName());
            intent.putExtra(MainActivity.MAIN_ACTIVITY_NAME, StatusSigns.RESET_ACTIVITY_UI);
            context.sendBroadcast(intent);
        }

        // 管理播放时的btnPlay的样式
        private void sendPlayStatusBroadcast(Context context) {
            Intent intent = new Intent(MainActivity.MAIN_ACTIVITY_ACTION);
            intent.setPackage(context.getPackageName());
            intent.putExtra(MainActivity.MAIN_ACTIVITY_NAME, StatusSigns.PLAY);
            intent.putExtra(MainActivity.IS_PLAYING, manager.isPlaying());
            context.sendBroadcast(intent);
        }

        // 设定Controller panel样式
        private void sendStartStatusBroadcast(Context context, int position) {
            MusicInfo info = manager.getMusicInfo(position);

            Intent intent = new Intent(MainActivity.MAIN_ACTIVITY_ACTION);
            intent.putExtra(MainActivity.MAIN_ACTIVITY_NAME, StatusSigns.START);
            intent.putExtra(MainActivity.MUSIC_INFO, info);
            intent.putExtra(MainActivity.LIST_POS, position);
            context.sendBroadcast(intent);
        }
    }
}
