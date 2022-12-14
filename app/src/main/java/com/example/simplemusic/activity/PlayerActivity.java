package com.example.simplemusic.activity;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.example.simplemusic.bean.Music;
import com.example.simplemusic.adapter.PlayingMusicAdapter;
import com.example.simplemusic.R;
import com.example.simplemusic.util.Utils;
import com.example.simplemusic.service.MusicService;

import java.util.List;
import java.util.Objects;

public class PlayerActivity extends AppCompatActivity implements View.OnClickListener{

    private TextView                                    musicTitleView;
    private TextView                                    musicArtistView;
    private ImageView                                   musicImgView;
    private ImageView                                   btnPlayMode;
    private ImageView                                   btnPlayPre;
    private ImageView                                   btnPlayOrPause;
    private ImageView                                   btnPlayNext;
    private ImageView                                   btnPlayingList;
    private TextView                                    nowTimeView;
    private TextView                                    totalTimeView;
    private SeekBar                                     seekBar;
    private com.example.simplemusic.view.RotateAnimator rotateAnimator;
    private MusicService.MusicServiceBinder             serviceBinder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        //?????????
        initActivity();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
    }

    // ????????????
    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.play_mode:
                // ??????????????????
                int mode = serviceBinder.getPlayMode();
                switch (mode){
                    case Utils.TYPE_ORDER:
                        serviceBinder.setPlayMode(Utils.TYPE_SINGLE);
                        Toast.makeText(PlayerActivity.this, "????????????", Toast.LENGTH_SHORT).show();
                        btnPlayMode.setImageResource(R.drawable.ic_singlerecycler);
                        break;
                    case Utils.TYPE_SINGLE:
                        serviceBinder.setPlayMode(Utils.TYPE_RANDOM);
                        Toast.makeText(PlayerActivity.this, "????????????", Toast.LENGTH_SHORT).show();
                        btnPlayMode.setImageResource(R.drawable.ic_random);
                        break;
                    case Utils.TYPE_RANDOM:
                        serviceBinder.setPlayMode(Utils.TYPE_ORDER);
                        Toast.makeText(PlayerActivity.this, "????????????", Toast.LENGTH_SHORT).show();
                        btnPlayMode.setImageResource(R.drawable.ic_playrecycler);
                        break;
                    default:
                }
                break;
            case R.id.play_pre:
                // ?????????
                serviceBinder.playPre();
                break;
            case R.id.play_next:
                // ?????????
                serviceBinder.playNext();
                break;
            case R.id.play_or_pause:
                // ???????????????
                serviceBinder.playOrPause();
                break;
            case R.id.playing_list:
                // ????????????
                showPlayList();
                break;
            default:
        }
    }

    private void initActivity() {
        musicTitleView = findViewById(R.id.title);
        musicArtistView = findViewById(R.id.artist);
        musicImgView = findViewById(R.id.imageView);
        btnPlayMode = findViewById(R.id.play_mode);
        btnPlayOrPause = findViewById(R.id.play_or_pause);
        btnPlayPre = findViewById(R.id.play_pre);
        btnPlayNext = findViewById(R.id.play_next);
        btnPlayingList = findViewById(R.id.playing_list);
        seekBar = findViewById(R.id.seekbar);
        nowTimeView = findViewById(R.id.current_time);
        totalTimeView = findViewById(R.id.total_time);
        ImageView needleView = findViewById(R.id.ivNeedle);

        // ToolBar
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("");
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        setSupportActionBar(toolbar);

        // ????????????
        btnPlayMode.setOnClickListener(this);
        btnPlayOrPause.setOnClickListener(this);
        btnPlayPre.setOnClickListener(this);
        btnPlayNext.setOnClickListener(this);
        btnPlayingList.setOnClickListener(this);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                //??????????????????
                nowTimeView.setText(Utils.formatTime((long) progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                serviceBinder.seekTo(seekBar.getProgress());
            }
        });

        //???????????????
        rotateAnimator = new com.example.simplemusic.view.RotateAnimator(this, musicImgView, needleView);
        rotateAnimator.set_Needle();

        // ??????service
        Intent i = new Intent(this, MusicService.class);
        bindService(i, mServiceConnection, BIND_AUTO_CREATE);
    }

    //?????????????????????????????????
    private void showPlayList(){
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
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
        builder.setCancelable(true);
        builder.create().show();
    }

    //????????????????????????????????????
    private ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            //????????????????????????MusicSercice???????????????
            serviceBinder = (MusicService.MusicServiceBinder) service;

            //???????????????
            serviceBinder.registerOnStateChangeListener(listenr);

            //??????????????????
            Music item = serviceBinder.getCurrentMusic();

            if(item == null) {
                //??????????????????, seekbar????????????
                seekBar.setEnabled(false);
            }
            else if (serviceBinder.isPlaying()){
                //????????????????????????, ????????????
                musicTitleView.setText(item.title);
                musicArtistView.setText(item.artist);
                btnPlayOrPause.setImageResource(R.drawable.ic_pause);
                rotateAnimator.playAnimator();
                if (item.isOnlineMusic){
                    Glide.with(getApplicationContext())
                            .load(item.imgUrl)
                            .placeholder(R.drawable.defult_music_img)
                            .error(R.drawable.defult_music_img)
                            .into(musicImgView);
                }
                else {
                    ContentResolver resolver = getContentResolver();
                    Bitmap img = Utils.getLocalMusicBmp(resolver, item.imgUrl);
                    Glide.with(getApplicationContext())
                            .load(img)
                            .placeholder(R.drawable.defult_music_img)
                            .error(R.drawable.defult_music_img)
                            .into(musicImgView);
                }
            }
            else {
                //???????????????????????????????????????
                musicTitleView.setText(item.title);
                musicArtistView.setText(item.artist);
                btnPlayOrPause.setImageResource(R.drawable.ic_play);
                if (item.isOnlineMusic){
                    Glide.with(getApplicationContext())
                            .load(item.imgUrl)
                            .placeholder(R.drawable.defult_music_img)
                            .error(R.drawable.defult_music_img)
                            .into(musicImgView);
                }
                else {
                    ContentResolver resolver = getContentResolver();
                    Bitmap img = Utils.getLocalMusicBmp(resolver, item.imgUrl);
                    Glide.with(getApplicationContext())
                            .load(img)
                            .placeholder(R.drawable.defult_music_img)
                            .error(R.drawable.defult_music_img)
                            .into(musicImgView);
                }
            }

            // ????????????????????????
            int mode = (serviceBinder.getPlayMode());
            switch (mode){
                case Utils.TYPE_ORDER:
                    btnPlayMode.setImageResource(R.drawable.ic_playrecycler);
                    break;
                case Utils.TYPE_SINGLE:
                    btnPlayMode.setImageResource(R.drawable.ic_singlerecycler);
                    break;
                case Utils.TYPE_RANDOM:
                    btnPlayMode.setImageResource(R.drawable.ic_random);
                    break;
                default:
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            //??????????????????, ???????????????
            serviceBinder.unregisterOnStateChangeListener(listenr);
        }
    };

    //?????????????????????MusicService????????????
    private MusicService.OnStateChangeListenr listenr = new MusicService.OnStateChangeListenr() {

        @Override
        public void onPlayProgressChange(long played, long duration) {
            seekBar.setMax((int) duration);
            totalTimeView.setText(Utils.formatTime(duration));
            nowTimeView.setText(Utils.formatTime(played));
            seekBar.setProgress((int) played);
        }

        @Override
        public void onPlay(final Music item) {
            //?????????????????????
            musicTitleView.setText(item.title);
            musicArtistView.setText(item.artist);
            btnPlayOrPause.setImageResource(R.drawable.ic_pause);
            rotateAnimator.playAnimator();
            if (item.isOnlineMusic){
                Glide.with(getApplicationContext())
                        .load(item.imgUrl)
                        .placeholder(R.drawable.defult_music_img)
                        .error(R.drawable.defult_music_img)
                        .into(musicImgView);
            }
            else {
                ContentResolver resolver = getContentResolver();
                Bitmap img = Utils.getLocalMusicBmp(resolver, item.imgUrl);
                Glide.with(getApplicationContext())
                        .load(img)
                        .placeholder(R.drawable.defult_music_img)
                        .error(R.drawable.defult_music_img)
                        .into(musicImgView);
            }
        }

        @Override
        public void onPause() {
            //?????????????????????
            btnPlayOrPause.setImageResource(R.drawable.ic_play);
            rotateAnimator.pauseAnimator();
        }
    };

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void finish() {
        super.finish();
        //????????????????????????
        overridePendingTransition(R.anim.bottom_silent,R.anim.bottom_out);
    }
}
