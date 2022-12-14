package com.example.simplemusic.activity;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.example.simplemusic.bean.Music;
import com.example.simplemusic.adapter.MusicAdapter;
import com.example.simplemusic.adapter.PlayingMusicAdapter;
import com.example.simplemusic.R;
import com.example.simplemusic.util.Utils;
import com.example.simplemusic.service.MusicService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class OnlineMusicActivity extends AppCompatActivity implements View.OnClickListener{

    private TextView musicCountView;
    private ListView musicListView;
    private TextView playingTitleView;
    private TextView playingArtistView;
    private ImageView playingImgView;
    private ImageView btnPlayOrPause;

    private List<Music> onlinemusic_list;
    private MusicService.MusicServiceBinder serviceBinder;
    private MusicAdapter adapter;

    private OkHttpClient client;
    private Handler mainHanlder;

    @SuppressLint("HandlerLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onlinemusic);

        //?????????
        initActivity();

        mainHanlder = new Handler(){
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                switch (msg.what){
                    case 60:
                        //??????????????????
                        Music music = (Music) msg.obj;
                        onlinemusic_list.add(music);
                        adapter.notifyDataSetChanged();
                        musicCountView.setText("????????????(???" + onlinemusic_list.size() + "???)");
                        break;
                }
            }
        };

        // ?????????????????????
        musicListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Music music = onlinemusic_list.get(position);
                serviceBinder.addPlayList(music);
            }
        });

        //???????????????????????????????????????
        adapter.setOnMoreButtonListener(new MusicAdapter.onMoreButtonListener() {
            @Override
            public void onClick(final int i) {
                final Music music = onlinemusic_list.get(i);
                final String[] items = new String[] {"?????????????????????", "?????????????????????", "??????"};
                AlertDialog.Builder builder = new AlertDialog.Builder(OnlineMusicActivity.this);
                builder.setTitle(music.title+"-"+music.artist);

                builder.setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which){
                            case 0:
                                MainActivity.addMymusic(music);
                                break;
                            case 1:
                                serviceBinder.addPlayList(music);
                                break;
                            case 2:
                                //??????????????????
                                onlinemusic_list.remove(i);
                                adapter.notifyDataSetChanged();
                                musicCountView.setText("????????????(???"+onlinemusic_list.size()+"???)");
                                break;
                        }
                    }
                });
                builder.create().show();
            }
        });

    }

    // ????????????
    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.play_all:
                serviceBinder.addPlayList(onlinemusic_list);
                break;
            case R.id.player:
                Intent intent = new Intent(OnlineMusicActivity.this, PlayerActivity.class);
                startActivity(intent);
                //????????????
                overridePendingTransition(R.anim.bottom_in, R.anim.bottom_silent);
                break;
            case R.id.play_or_pause:
                serviceBinder.playOrPause();
                break;
            case R.id.playing_list:
                showPlayList();
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        onlinemusic_list.clear();
        unbindService(mServiceConnection);
        client.dispatcher().cancelAll();
    }

    // ???????????????
    private void initActivity(){
        //???????????????
        ImageView btn_playAll = this.findViewById(R.id.play_all);
        musicCountView = this.findViewById(R.id.play_all_title);
        musicListView = this.findViewById(R.id.music_list);
        RelativeLayout playerToolView = this.findViewById(R.id.player);
        playingImgView = this.findViewById(R.id.playing_img);
        playingTitleView = this.findViewById(R.id.playing_title);
        playingArtistView = this.findViewById(R.id.playing_artist);
        btnPlayOrPause = this.findViewById(R.id.play_or_pause);
        ImageView btn_playingList = this.findViewById(R.id.playing_list);

        // ????????????
        btn_playAll.setOnClickListener(this);
        playerToolView.setOnClickListener(this);
        btnPlayOrPause.setOnClickListener(this);
        btn_playingList.setOnClickListener(this);

        //??????????????????
        Intent i = new Intent(this, MusicService.class);
        bindService(i, mServiceConnection, BIND_AUTO_CREATE);

        // ??????ToolBar
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("??????????????????");
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        setSupportActionBar(toolbar);

        client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)//????????????????????????
                .readTimeout(10, TimeUnit.SECONDS)//????????????????????????
                .build();

        // ??????????????????
        onlinemusic_list = new ArrayList<>();
        adapter = new MusicAdapter(this, R.layout.music_item, onlinemusic_list);
        musicListView.setAdapter(adapter);
        musicCountView.setText("????????????(???"+onlinemusic_list.size()+"???)");
        getOlineMusic();
    }


    // ?????????????????????????????????
    private void showPlayList(){
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

    // ????????????????????????????????????
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        //?????????????????????
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {

            //????????????????????????MusicSercice???????????????
            serviceBinder = (MusicService.MusicServiceBinder) service;

            //???????????????
            serviceBinder.registerOnStateChangeListener(listenr);

            Music item = serviceBinder.getCurrentMusic();

            if (serviceBinder.isPlaying()){
                //????????????????????????, ?????????????????????
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
            else if (item != null){
                //???????????????????????????????????????
                btnPlayOrPause.setImageResource(R.drawable.bofang);
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
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            //??????????????????????????????
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
            btnPlayOrPause.setEnabled(true);
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
            btnPlayOrPause.setEnabled(true);
        }
    };

    // ??????????????????
    private void getOlineMusic() {

        Request request = new Request.Builder()
                .url("https://api.itooi.cn/netease/songList?id=3778678&format=1")
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(OnlineMusicActivity.this, "????????????", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String result = response.body().string();
                try{
                    JSONObject obj = new JSONObject(result);
                    JSONArray songs = new JSONArray(obj.getString("data"));

                    for(int i=0; i<songs.length(); i++){
                        JSONObject song = songs.getJSONObject(i);

                        String id = song.getString("id");
                        String songurl = "https://api.itooi.cn/netease/url?id=" + id + "&quality=128";
                        String name = song.getString("name");
                        String singer = song.getString("singer");
                        String pic = "https://api.itooi.cn/netease/pic?id=" + id;

                        //????????????????????????????????????????????????
                        Music music = new Music(songurl, name, singer, pic, true);
                        Message message = mainHanlder.obtainMessage();
                        message.what = 60;
                        message.obj = music;
                        mainHanlder.sendMessage(message);
                        Thread.sleep(30);
                    }
                }
                catch (Exception e){}
            }
        });
    }

    // ????????????
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
