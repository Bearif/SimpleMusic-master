package com.example.simplemusic.activity;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.example.simplemusic.bean.Music;
import com.example.simplemusic.adapter.MusicAdapter;
import com.example.simplemusic.adapter.PlayingMusicAdapter;
import com.example.simplemusic.R;
import com.example.simplemusic.util.Utils;
import com.example.simplemusic.service.MusicService;
import com.example.simplemusic.db.MyMusic;
import com.jaeger.library.StatusBarUtil;

import org.litepal.LitePal;

import java.util.ArrayList;
import java.util.List;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    private Toolbar toolbar;
    private ListView musicListView;
    private TextView playingTitleView;
    private TextView playingArtistView;
    private ImageView playingImgView;
    private ImageView btnPlayOrPause;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private TextView musicCountView;

    private static List<Music> musicList;
    private MusicAdapter musicAdapter;
    private SharedPreferences spf;
    private MusicService.MusicServiceBinder serviceBinder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ???????????????
        initActivity();

        // ?????????????????????
        initMusicList();

        // ???????????????
        initsettings();

        // ???????????????????????????
        musicListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Music music = musicList.get(position);
                serviceBinder.addPlayList(music);
            }
        });

        // ???????????????????????????????????????
        musicAdapter.setOnMoreButtonListener(new MusicAdapter.onMoreButtonListener() {
            @Override
            public void onClick(final int i) {
                final Music music = musicList.get(i);

                //?????????????????????
                final String[] items = new String[] {"?????????????????????", "??????"};
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle(music.title+"-"+music.artist);

                builder.setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which){
                            case 0:
                                serviceBinder.addPlayList(music);
                                break;
                            case 1:
                                //??????????????????????????????
                                musicList.remove(i);
                                LitePal.deleteAll(MyMusic.class, "title=?", music.title);
                                musicAdapter.notifyDataSetChanged();
                                break;
                        }
                    }
                });
                builder.create().show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        musicCountView.setText("????????????"+Integer.toString(Utils.count)+"???");
        musicAdapter.notifyDataSetChanged(); //????????????
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //????????????
        musicList.clear();
        Glide.with(getApplicationContext()).pauseAllRequests();
        unbindService(serviceConnection);
        saveSettings();
    }

    // ????????????
    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.player:
                // ???????????????
                Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
                startActivity(intent);
                overridePendingTransition(R.anim.bottom_in, R.anim.bottom_silent);
                break;
            case R.id.play_or_pause:
                serviceBinder.playOrPause();
                break;
            case R.id.playing_list:
                // ????????????????????????
                showPlayingList();
                break;
            default:
        }
    }

    // ???????????????
    private void initActivity(){
        // ??????????????????
        ActivityCompat.requestPermissions(MainActivity.this,
                new String[]{WRITE_EXTERNAL_STORAGE}, 1);

        //????????????
        musicListView = this.findViewById(R.id.music_list);
        RelativeLayout playerToolView = this.findViewById(R.id.player);
        playingImgView = this.findViewById(R.id.playing_img);
        playingTitleView = this.findViewById(R.id.playing_title);
        playingArtistView = this.findViewById(R.id.playing_artist);
        btnPlayOrPause = this.findViewById(R.id.play_or_pause);
        ImageView btn_playingList = this.findViewById(R.id.playing_list);
        drawerLayout = this.findViewById(R.id.drawer);
        navigationView = this.findViewById(R.id.nav_view);
        View headerView = navigationView.getHeaderView(0);
        musicCountView = headerView.findViewById(R.id.nav_num);

        // ????????????
        playerToolView.setOnClickListener(this);
        btnPlayOrPause.setOnClickListener(this);
        btn_playingList.setOnClickListener(this);
        
        // ??????ToolBar
        toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("");
        setSupportActionBar(toolbar);

        // ???????????????
        StatusBarUtil.setTransparentForDrawerLayout(this, drawerLayout);

        // ???????????????
        setNavigationView();

        // ??????service?????????
        Intent intent = new Intent(this, MusicService.class);
        startService(intent);
        bindService(intent, serviceConnection, BIND_AUTO_CREATE);
    }

    private void initMusicList(){
        //??????????????????????????????
        musicList = new ArrayList<>();
        List<MyMusic> list = LitePal.findAll(MyMusic.class);
        for (MyMusic s:list){
            Music m = new Music(s.songUrl, s.title, s.artist, s.imgUrl, s.isOnlineMusic);
            musicList.add(m);
        }

        // ???????????????????????????
        musicAdapter = new MusicAdapter(this, R.layout.music_item, musicList);
        musicListView.setAdapter(musicAdapter);
    }

    // ????????????
    private void initsettings(){
        spf = getSharedPreferences("settings", MODE_PRIVATE);
        //??????????????????
        Utils.count = spf.getInt("listen_count", 0);
        musicCountView.setText("????????????"+String.valueOf(Utils.count) +"???");
    }

    // ????????????
    private void saveSettings(){
        SharedPreferences.Editor editor = spf.edit();

        //??????????????????
        editor.putInt("listen_count", Utils.count);
        // ????????????
        int mode = serviceBinder.getPlayMode();
        editor.putInt("play_mode", mode);

        editor.apply();
    }

    // ???????????????
    private void setNavigationView(){

        // ??????toggle?????????????????????:
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawerLayout,toolbar,R.string.app_name,R.string.app_name);
        toggle.syncState();
        drawerLayout.addDrawerListener(toggle);

        //??????????????????????????????
        navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {

                switch (menuItem.getItemId()){
                    case R.id.local:
                        // ??????????????????
                        Intent intent1 = new Intent(MainActivity.this, LocalMusicActivity.class);
                        startActivity(intent1);
                        break;
                    case R.id.online:
                        // ??????????????????
                        Intent intent2 = new Intent(MainActivity.this, OnlineMusicActivity.class);
                        startActivity(intent2);
                        break;
                    case R.id.exit:
                        // ??????
                        unbindService(serviceConnection);
                        Intent intent = new Intent(MainActivity.this, MusicService.class);
                        stopService(intent);
                        finish();
                        break;
                }
                return true;
            }
        });
    }

    // ?????????????????????????????????
    private void showPlayingList(){

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);

        //??????????????????????????????
        builder.setTitle("????????????");
        //??????????????????
        final List<Music> playingList = serviceBinder.getPlayingList();

        if(playingList.size() > 0) {

            //??????????????????????????????????????????
            final PlayingMusicAdapter playingAdapter = new PlayingMusicAdapter(this, R.layout.playinglist_item, playingList);
            builder.setAdapter(playingAdapter, new DialogInterface.OnClickListener() {
                //???????????????????????????
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    serviceBinder.addPlayList(playingList.get(which));
                }
            });
            //???????????????????????????????????????
            playingAdapter.setOnDeleteButtonListener(new PlayingMusicAdapter.onDeleteButtonListener() {
                @Override
                public void onClick(int i) {
                    serviceBinder.removeMusic(i);
                    playingAdapter.notifyDataSetChanged();
                }
            });
        }
        else {
            //?????????????????????????????????????????????
            builder.setMessage("???????????????????????????");
        }

        //????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
        builder.setCancelable(true);
        //????????????????????????
        builder.create().show();
    }

    // ?????????????????????????????????
    private ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            //????????????????????????MusicSercice???????????????
            serviceBinder = (MusicService.MusicServiceBinder) service;

            //???????????????
            serviceBinder.registerOnStateChangeListener(listenr);

            Music item = serviceBinder.getCurrentMusic();

            if(item != null){
                //????????????????????????
                playingTitleView.setText(item.title);
                playingArtistView.setText(item.artist);
                if (item.isOnlineMusic){
                    Glide.with(getApplicationContext())
                            .load(item.imgUrl)
                            .placeholder(R.drawable.defult_music_img)
                            .error(R.drawable.defult_music_img)
                            .into(playingImgView);
                }
                else {
                    ContentResolver resolver = getContentResolver();
                    Bitmap img = Utils.getLocalMusicBmp(resolver, item.imgUrl);
                    Glide.with(getApplicationContext())
                            .load(img)
                            .placeholder(R.drawable.defult_music_img)
                            .error(R.drawable.defult_music_img)
                            .into(playingImgView);
                }
            }

            int mode = spf.getInt("play_mode", Utils.TYPE_ORDER);
            serviceBinder.setPlayMode(mode);
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            //??????????????????, ???????????????
            serviceBinder.unregisterOnStateChangeListener(listenr);
        }
    };

    // ?????????????????????MusicService????????????
    private MusicService.OnStateChangeListenr listenr = new MusicService.OnStateChangeListenr() {

        @Override
        public void onPlayProgressChange(long played, long duration) {}

        @Override
        public void onPlay(Music item) {
            //???????????????????????????
            btnPlayOrPause.setImageResource(R.drawable.zanting);
            playingTitleView.setText(item.title);
            playingArtistView.setText(item.artist);
            if (item.isOnlineMusic){
                Glide.with(getApplicationContext())
                        .load(item.imgUrl)
                        .placeholder(R.drawable.defult_music_img)
                        .error(R.drawable.defult_music_img)
                        .into(playingImgView);
            }
            else {
                ContentResolver resolver = getContentResolver();
                Bitmap img = Utils.getLocalMusicBmp(resolver, item.imgUrl);
                Glide.with(getApplicationContext())
                        .load(img)
                        .placeholder(R.drawable.defult_music_img)
                        .error(R.drawable.defult_music_img)
                        .into(playingImgView);
            }
        }

        @Override
        public void onPause() {
            //???????????????????????????
            btnPlayOrPause.setImageResource(R.drawable.bofang);
        }
    };

    // ????????????, ??????????????????
    public static  void addMymusic(Music item){
        if (musicList.contains(item))
            return;
        //???????????????????????????
        musicList.add(0, item);
        MyMusic myMusic = new MyMusic(item.songUrl, item.title, item.artist, item.imgUrl, item.isOnlineMusic);
        myMusic.save();
    }

//    // ????????????, ??????????????????
//    public static void addMymusic(List<Music> lists){
//
//        for (Music item:lists){
//            addMymusic(item);
//        }
//    }

    // ????????????
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    // ??????????????????
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.local_music:
                Intent intent1 = new Intent(MainActivity.this, LocalMusicActivity.class);
                startActivity(intent1);
                break;
            case R.id.online_music:
                Intent intent2 = new Intent(MainActivity.this, OnlineMusicActivity.class);
                startActivity(intent2);
                break;
            default:
        }
        return true;
    }
}
