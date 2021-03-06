package com.netease.yunxin.nertc.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.blankj.utilcode.constant.PermissionConstants;
import com.blankj.utilcode.util.PermissionUtils;
import com.blankj.utilcode.util.ToastUtils;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.netease.lava.nertc.sdk.NERtc;
import com.netease.lava.nertc.sdk.NERtcConstants;
import com.netease.lava.nertc.sdk.stats.NERtcNetworkQualityInfo;
import com.netease.lava.nertc.sdk.video.NERtcVideoConfig;
import com.netease.lava.nertc.sdk.video.NERtcVideoView;
import com.netease.nimlib.sdk.NIMClient;
import com.netease.nimlib.sdk.RequestCallback;
import com.netease.nimlib.sdk.ResponseCode;
import com.netease.nimlib.sdk.avsignalling.builder.InviteParamBuilder;
import com.netease.nimlib.sdk.avsignalling.constant.ChannelType;
import com.netease.nimlib.sdk.avsignalling.event.InvitedEvent;
import com.netease.nimlib.sdk.avsignalling.model.ChannelFullInfo;
import com.netease.nimlib.sdk.avsignalling.model.MemberInfo;
import com.netease.nimlib.sdk.nos.NosService;
import com.netease.nimlib.sdk.uinfo.UserService;
import com.netease.nimlib.sdk.uinfo.model.NimUserInfo;
import com.netease.yunxin.nertc.model.ProfileManager;
import com.netease.yunxin.nertc.model.UserModel;
import com.netease.yunxin.nertc.nertcvideocall.model.JoinChannelCallBack;
import com.netease.yunxin.nertc.nertcvideocall.model.NERTCCallingDelegate;
import com.netease.yunxin.nertc.nertcvideocall.model.NERTCVideoCall;
import com.netease.yunxin.nertc.nertcvideocall.utils.CallParams;
import com.netease.yunxin.nertc.ui.team.AVChatSoundPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * ???????????????activity
 */
public class NERTCVideoCallActivity extends AppCompatActivity {

    private static final String LOG_TAG = NERTCVideoCallActivity.class.getSimpleName();

    public static final String CALL_OUT_USER = "call_out_user";

    private NERTCVideoCall nertcVideoCall;

    private NERtcVideoView localVideoView;
    private NERtcVideoView remoteVideoView;
    private TextView tvRemoteVideoClose;
    private ImageView ivSwitch;
    private ImageView ivUserIcon;
    private TextView tvCallUser;
    private TextView tvCallComment;
    private ImageView ivMute;
    private ImageView ivVideo;
    private ImageView ivHungUp;
    private LinearLayout llyCancel;
    private LinearLayout llyBingCall;
    private LinearLayout llyDialogOperation;
    private ImageView ivAccept;
    private ImageView ivReject;
    private ImageView ivChangeType;
    private RelativeLayout rlyTopUserInfo;

    private UserModel callOutUser;//????????????
    private boolean callReceived;

    private String inventRequestId;
    private String inventChannelId;
    private String inventFromAccountId;

    private boolean isMute;//????????????
    private boolean isCamOff;//?????????????????????

    private static final int DELAY_TIME = 0;//??????

    private String peerAccid;
    private long peerUid;
    private String inviterNickname;
    /**
     * ???????????? AUDIO(1),
     * VIDEO(2),
     */
    private int callType;

    private NERTCCallingDelegate nertcCallingDelegate = new NERTCCallingDelegate() {
        @Override
        public void onError(int errorCode, String errorMsg, boolean needFinish) {
            if (needFinish) {
                ToastUtils.showLong(errorMsg + " errorCode:" + errorCode);
                AVChatSoundPlayer.instance().stop();
                finish();
            } else {
                Log.i(LOG_TAG, errorMsg + " errorCode:" + errorCode);
            }
        }

        @Override
        public void onInvited(InvitedEvent invitedEvent) {

        }


        @Override
        public void onUserEnter(long uid,String accId) {
            llyCancel.setVisibility(View.GONE);
            llyDialogOperation.setVisibility(View.VISIBLE);
            rlyTopUserInfo.setVisibility(View.GONE);
            llyBingCall.setVisibility(View.GONE);
            if (callType == ChannelType.VIDEO.getValue()) {
                setupLocalVideo();
            } else if (callType == ChannelType.AUDIO.getValue()) {
                setupLocalAudio();
            }
            AVChatSoundPlayer.instance().stop();

            peerAccid = accId;
            peerUid = uid;
            Log.i(LOG_TAG, String.format("onUserEnter uid:%d, accId:%s", uid, accId));
        }

        @Override
        public void onCallEnd(String userId) {
            onCallEnd(userId, "??????????????????");
        }

        @Override
        public void onUserLeave(String accountId) {
            Log.i(LOG_TAG, "onUserLeave:" + accountId);
        }

        @Override
        public void onUserDisconnect(String userId) {
            Log.i(LOG_TAG, "onUserDisconnect:" + userId);
            if (TextUtils.equals(userId, peerAccid)) {
                onCallEnd(userId, "??????????????????");
            }
        }

        private void onCallEnd(String userId, String tip) {
            if (!isDestroyed() && !ProfileManager.getInstance().isCurrentUser(userId)) {
                ToastUtils.showLong(tip);
                handler.postDelayed(() -> finish(), DELAY_TIME);
                AVChatSoundPlayer.instance().stop();
            }
        }

        @Override
        public void onRejectByUserId(String userId) {
            if (!isDestroyed() && !callReceived) {
                ToastUtils.showLong("??????????????????");
                handler.postDelayed(() -> {
                    finish();
                }, DELAY_TIME);
                AVChatSoundPlayer.instance().play(AVChatSoundPlayer.RingerTypeEnum.PEER_REJECT);
            }
        }


        @Override
        public void onUserBusy(String userId) {
            if (!isDestroyed() && !callReceived) {
                ToastUtils.showLong("????????????");
                handler.postDelayed(() -> {
                    finish();
                }, DELAY_TIME);
                AVChatSoundPlayer.instance().play(AVChatSoundPlayer.RingerTypeEnum.PEER_BUSY);
            }
        }

        @Override
        public void onCancelByUserId(String uid) {
            if (!isDestroyed() && callReceived) {
                ToastUtils.showLong("????????????");
                handler.postDelayed(() -> {
                    finish();
                }, DELAY_TIME);
                AVChatSoundPlayer.instance().stop();
            }
        }


        @Override
        public void onCameraAvailable(long userId, boolean isVideoAvailable) {
            if (callType == ChannelType.VIDEO.getValue() && !ProfileManager.getInstance().isCurrentUser(userId)) {
                if (isVideoAvailable) {
                    rlyTopUserInfo.setVisibility(View.GONE);
                    remoteVideoView.setVisibility(View.VISIBLE);
                    tvRemoteVideoClose.setVisibility(View.GONE);
                    setupRemoteVideo(userId);
                } else {
                    remoteVideoView.setVisibility(View.GONE);
                    tvRemoteVideoClose.setVisibility(View.VISIBLE);
                }
            }

        }

        @Override
        public void onAudioAvailable(long userId, boolean isAudioAvailable) {
            if (callType == ChannelType.AUDIO.getValue() && !ProfileManager.getInstance().isCurrentUser(userId)) {
                if (isAudioAvailable) {
                    setDialogViewAsType(callType);
                }
            }
        }

        @Override
        public void onUserNetworkQuality(NERtcNetworkQualityInfo[] stats) {
            /**
             *             0	??????????????????
             *             1	??????????????????
             *             2	?????????????????????????????????????????????????????????????????????
             *             3	?????????????????????
             *             4	???????????????
             *             5	??????????????????
             */
            if (stats == null || stats.length == 0) {
                return;
            }

            for (NERtcNetworkQualityInfo networkQualityInfo : stats) {
                if (networkQualityInfo.userId == peerUid) {
                    if (networkQualityInfo.upStatus >= 4) {
                        Toast.makeText(NERTCVideoCallActivity.this,
                                "?????????????????????",
                                Toast.LENGTH_SHORT).show();
                    } else if (networkQualityInfo.upStatus == 0) {
                        Log.e(LOG_TAG, "network is unKnow");
                    }

                    Log.i(LOG_TAG, String.format("NERtcNetworkQualityInfo: %d", networkQualityInfo.downStatus));
                }
            }
        }

        @Override
        public void onCallTypeChange(ChannelType type) {
            callType = type.getValue();
            setDialogViewAsType(callType);
            setViewAsType(callType);
        }

        @Override
        public void timeOut() {
            if (callReceived) {
                ToastUtils.showLong("???????????????");
            } else {
                ToastUtils.showLong("????????????");
            }
            AVChatSoundPlayer.instance().play(AVChatSoundPlayer.RingerTypeEnum.NO_RESPONSE);
            handler.postDelayed(() -> {
                AVChatSoundPlayer.instance().stop();
                finish();
            }, DELAY_TIME);
        }
    };

    private Handler handler = new Handler();

    public static void startCallOther(Context context, UserModel callOutUser) {
        Intent intent = new Intent(context, NERTCVideoCallActivity.class);
        intent.putExtra(CALL_OUT_USER, callOutUser);
        intent.putExtra(CallParams.INVENT_CHANNEL_TYPE, ChannelType.VIDEO.getValue());
        intent.putExtra(CallParams.INVENT_CALL_RECEIVED, false);
        context.startActivity(intent);
    }

    public static void startAudioCallOther(Context context, UserModel callOutUser) {
        Intent intent = new Intent(context, NERTCVideoCallActivity.class);
        intent.putExtra(CALL_OUT_USER, callOutUser);
        intent.putExtra(CallParams.INVENT_CALL_RECEIVED, false);
        intent.putExtra(CallParams.INVENT_CHANNEL_TYPE, ChannelType.AUDIO.getValue());
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // ?????????????????????????????????????????????
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.video_call_layout);
        initIntent();
        initView();
        initData();
    }

    private void initIntent() {
        inventRequestId = getIntent().getStringExtra(CallParams.INVENT_REQUEST_ID);
        inventChannelId = getIntent().getStringExtra(CallParams.INVENT_CHANNEL_ID);
        inventFromAccountId = getIntent().getStringExtra(CallParams.INVENT_FROM_ACCOUNT_ID);
        callType = getIntent().getIntExtra(CallParams.INVENT_CHANNEL_TYPE, ChannelType.VIDEO.getValue());
        callOutUser = (UserModel) getIntent().getSerializableExtra(CALL_OUT_USER);
        callReceived = getIntent().getBooleanExtra(CallParams.INVENT_CALL_RECEIVED, false);
    }

    /**
     * ??????????????????view
     *
     * @param callType
     */
    private void setDialogViewAsType(int callType) {
        if (callType == ChannelType.AUDIO.getValue()) {
            rlyTopUserInfo.setVisibility(View.VISIBLE);
            localVideoView.setVisibility(View.GONE);
            if (callReceived) {
                tvCallUser.setText(inviterNickname);
            } else {
                tvCallUser.setText(callOutUser.nickname);
            }
            tvCallComment.setText("????????????");
        } else {
            localVideoView.setVisibility(View.VISIBLE);
            rlyTopUserInfo.setVisibility(View.GONE);
        }
    }

    private void initData() {
        setViewAsType(callType);
        nertcVideoCall = NERTCVideoCall.sharedInstance();
        nertcVideoCall.setTimeOut(30 * 1000);
        nertcVideoCall.addDelegate(nertcCallingDelegate);
        ivChangeType.setOnClickListener(v -> {
            nertcVideoCall.switchCallType(ChannelType.AUDIO, new RequestCallback<Void>() {
                @Override
                public void onSuccess(Void param) {
                    callType = ChannelType.AUDIO.getValue();
                    setDialogViewAsType(callType);
                    setViewAsType(callType);
                }

                @Override
                public void onFailed(int code) {

                }

                @Override
                public void onException(Throwable exception) {

                }
            });
        });
        ivSwitch.setOnClickListener(v -> {
            nertcVideoCall.switchCamera();
        });
        ivMute.setOnClickListener(view -> {
            isMute = !isMute;
            nertcVideoCall.muteLocalAudio(isMute);
            if (isMute) {
                Glide.with(NERTCVideoCallActivity.this).load(R.drawable.voice_off).into(ivMute);
            } else {
                Glide.with(NERTCVideoCallActivity.this).load(R.drawable.voice_on).into(ivMute);
            }
        });

        ivHungUp.setOnClickListener(view -> {
            hungUpAndFinish();
        });

        ivVideo.setOnClickListener(view -> {
            isCamOff = !isCamOff;
            nertcVideoCall.enableLocalVideo(!isCamOff);
            if (isCamOff) {
                Glide.with(NERTCVideoCallActivity.this).load(R.drawable.cam_off).into(ivVideo);
            } else {
                Glide.with(NERTCVideoCallActivity.this).load(R.drawable.cam_on).into(ivVideo);
            }
        });
        if (!callReceived && callOutUser != null) {
            tvCallUser.setText("???????????? " + callOutUser.nickname);
            llyCancel.setVisibility(View.VISIBLE);
            llyBingCall.setVisibility(View.GONE);
            llyCancel.setOnClickListener(v -> {
                nertcVideoCall.cancel(new RequestCallback<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        AVChatSoundPlayer.instance().stop();
                        finish();
                    }

                    @Override
                    public void onFailed(int i) {
                        // 10410 ?????????????????????
                        if (i == ResponseCode.RES_INVITE_HAS_ACCEPT) {
                            return;
                        }

                        AVChatSoundPlayer.instance().stop();
                        finish();
                    }

                    @Override
                    public void onException(Throwable throwable) {
                        Log.e("NERTCVideoCallActivity", "cancel Failed", throwable);
                        AVChatSoundPlayer.instance().stop();
                        finish();
                    }
                });
            });
            Glide.with(this).load(callOutUser.avatar).apply(RequestOptions.bitmapTransform(new RoundedCorners(5))).into(ivUserIcon);
        } else if (!TextUtils.isEmpty(inventRequestId) && !TextUtils.isEmpty(inventFromAccountId) && !TextUtils.isEmpty(inventChannelId)) {
            llyCancel.setVisibility(View.GONE);
            llyBingCall.setVisibility(View.VISIBLE);
        }

        PermissionUtils.permission(PermissionConstants.STORAGE, PermissionConstants.CAMERA, PermissionConstants.MICROPHONE)
                .callback(new PermissionUtils.FullCallback() {
                    @Override
                    public void onGranted(@NonNull List<String> granted) {
                        if (callReceived) {
                            callIn();
                        } else {
                            callOUt();
                        }
                    }

                    @Override
                    public void onDenied(@NonNull List<String> deniedForever, @NonNull List<String> denied) {

                    }
                }).request();
    }

    private void setViewAsType(int type) {
        int viewVisible = type == ChannelType.VIDEO.getValue() ? View.VISIBLE : View.GONE;
        ivVideo.setVisibility(viewVisible);
        ivSwitch.setVisibility(viewVisible);
        remoteVideoView.setVisibility(viewVisible);
        ivChangeType.setVisibility(viewVisible);
    }

    private void callOUt() {
        AVChatSoundPlayer.instance().play(AVChatSoundPlayer.RingerTypeEnum.CONNECTING);
        String selfUserId = ProfileManager.getInstance().getUserModel().imAccid;
        ChannelType type = ChannelType.retrieveType(callType);
        nertcVideoCall.call(callOutUser.imAccid, selfUserId, type, new JoinChannelCallBack() {
            @Override
            public void onJoinChannel(ChannelFullInfo channelFullInfo) {
                resetUid(channelFullInfo, selfUserId);
            }

            @Override
            public void onJoinFail(String msg, int code) {
                if (code == ResponseCode.RES_PEER_NIM_OFFLINE) {
                    return;
                }

                finishOnFailed();
            }
        });
        NERtcVideoConfig videoConfig = new NERtcVideoConfig();
        videoConfig.frontCamera = true;
        NERtc.getInstance().setLocalVideoConfig(videoConfig);
    }

    private void resetUid(ChannelFullInfo channelFullInfo,String selfUserId){
        if (channelFullInfo != null) {
            for (MemberInfo member : channelFullInfo.getMembers()) {
                if (TextUtils.equals(member.getAccountId(), selfUserId)) {
                    ProfileManager.getInstance().getUserModel().g2Uid = member.getUid();
                    break;
                }
            }
        }
    }

    private void callIn() {
        AVChatSoundPlayer.instance().play(AVChatSoundPlayer.RingerTypeEnum.RING);
        InviteParamBuilder invitedParam = new InviteParamBuilder(inventChannelId, inventFromAccountId, inventRequestId);
        ivAccept.setOnClickListener(view -> {
            accept(invitedParam);
            AVChatSoundPlayer.instance().stop();
        });

        ivReject.setOnClickListener(view -> {
            nertcVideoCall.reject(invitedParam, new RequestCallback<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                    finish();
                }

                @Override
                public void onFailed(int i) {
                    Log.e(LOG_TAG, "reject failed error code = " + i);

                    if (i == ResponseCode.RES_ETIMEOUT) {
                        ToastUtils.showShort("Reject timeout");
                    } else {
                        Log.e(LOG_TAG,"Reject failed:" + i);
                        if (i == ResponseCode.RES_CHANNEL_NOT_EXISTS || i == ResponseCode.RES_INVITE_NOT_EXISTS ||
                                i == ResponseCode.RES_INVITE_HAS_REJECT  || i == ResponseCode.RES_PEER_NIM_OFFLINE ||
                        i == ResponseCode.RES_PEER_PUSH_OFFLINE) {
                            finishOnFailed();
                        }
                    }
                }

                @Override
                public void onException(Throwable throwable) {
                    Log.e(LOG_TAG, "reject failed onException", throwable);
                    finishOnFailed();
                }
            });
            AVChatSoundPlayer.instance().stop();
        });
        List<String> userAccount = new ArrayList<>();
        userAccount.add(inventFromAccountId);
        NIMClient.getService(UserService.class).fetchUserInfo(userAccount).setCallback(new RequestCallback<List<NimUserInfo>>() {
            @Override
            public void onSuccess(List<NimUserInfo> param) {
                NimUserInfo userInfo = param.get(0);
                inviterNickname = userInfo.getName();
                tvCallUser.setText(userInfo.getName());
                if (callType == ChannelType.VIDEO.getValue()) {
                    tvCallComment.setText("?????????????????????");
                } else if (callType == ChannelType.AUDIO.getValue()) {
                    tvCallComment.setText("?????????????????????");
                }

                NIMClient.getService(NosService.class).getOriginUrlFromShortUrl(userInfo.getAvatar()).setCallback(new RequestCallback<String>() {
                    @Override
                    public void onSuccess(String param) {
                        Glide.with(NERTCVideoCallActivity.this).load(param).apply(RequestOptions.bitmapTransform(new RoundedCorners(5))).into(ivUserIcon);
                    }

                    @Override
                    public void onFailed(int code) {
                        Glide.with(NERTCVideoCallActivity.this).load(userInfo.getAvatar()).apply(RequestOptions.bitmapTransform(new RoundedCorners(5))).into(ivUserIcon);
                    }

                    @Override
                    public void onException(Throwable exception) {
                        Glide.with(NERTCVideoCallActivity.this).load(userInfo.getAvatar()).apply(RequestOptions.bitmapTransform(new RoundedCorners(5))).into(ivUserIcon);
                    }
                });
            }

            @Override
            public void onFailed(int code) {

            }

            @Override
            public void onException(Throwable exception) {

            }
        });

    }


    private void initView() {
        localVideoView = findViewById(R.id.local_video_view);
        remoteVideoView = findViewById(R.id.remote_video_view);
        ivSwitch = findViewById(R.id.iv_camera_switch);
        ivUserIcon = findViewById(R.id.iv_call_user);
        tvCallUser = findViewById(R.id.tv_call_user);
        tvCallComment = findViewById(R.id.tv_call_comment);
        ivMute = findViewById(R.id.iv_audio_control);
        ivVideo = findViewById(R.id.iv_video_control);
        ivHungUp = findViewById(R.id.iv_hangup);
        llyCancel = findViewById(R.id.lly_cancel);
        ivAccept = findViewById(R.id.iv_accept);
        ivReject = findViewById(R.id.iv_reject);
        llyBingCall = findViewById(R.id.lly_invited_operation);
        llyDialogOperation = findViewById(R.id.lly_dialog_operation);
        rlyTopUserInfo = findViewById(R.id.rly_top_user_info);
        tvRemoteVideoClose = findViewById(R.id.tv_remote_video_close);
        ivChangeType = findViewById(R.id.iv_type_change);
    }

    /**
     * ????????????????????????
     */
    private void setupLocalVideo() {
        localVideoView.setVisibility(View.VISIBLE);
        nertcVideoCall.setupLocalView(localVideoView);
    }

    /**
     * ??????????????????
     */
    private void setupLocalAudio() {
        NERtc.getInstance().enableLocalAudio(true);
    }

    private void accept(InviteParamBuilder invitedParam) {
        String selfUserId = String.valueOf(ProfileManager.getInstance().getUserModel().imAccid);
        nertcVideoCall.accept(invitedParam, selfUserId, new JoinChannelCallBack() {
            @Override
            public void onJoinChannel(ChannelFullInfo channelFullInfo) {
                resetUid(channelFullInfo, selfUserId);
            }

            @Override
            public void onJoinFail(String msg, int code) {
                if (code == ResponseCode.RES_ETIMEOUT) {
                    ToastUtils.showShort("Accept timeout normal");
                } else {
                    Log.e(LOG_TAG,"Accept normal failed:" + code);
                    if (code == ResponseCode.RES_CHANNEL_NOT_EXISTS || code == ResponseCode.RES_INVITE_NOT_EXISTS ||
                            code == ResponseCode.RES_INVITE_HAS_REJECT || code == ResponseCode.RES_PEER_NIM_OFFLINE ||
                            code == ResponseCode.RES_PEER_PUSH_OFFLINE) {
                        if(code == ResponseCode.RES_PEER_NIM_OFFLINE|| code == ResponseCode.RES_PEER_PUSH_OFFLINE){
                            ToastUtils.showShort("??????????????????");
                        }
                        handler.postDelayed(() -> {
                            finishOnFailed();
                        }, DELAY_TIME);
                    }
                }
            }
        });
    }

    /**
     * ????????????????????????
     *
     * @param uid ????????????Id
     */
    private void setupRemoteVideo(long uid) {
        remoteVideoView.setScalingType(NERtcConstants.VideoScalingType.SCALE_ASPECT_FIT);
        nertcVideoCall.setupRemoteView(remoteVideoView, uid);
    }

    @Override
    public void onBackPressed() {
        showExitDialog();
    }

    private void showExitDialog() {
        final AlertDialog.Builder confirmDialog =
                new AlertDialog.Builder(NERTCVideoCallActivity.this);
        confirmDialog.setTitle("????????????");
        confirmDialog.setMessage("?????????????????????");
        confirmDialog.setPositiveButton("???",
                (dialog, which) -> {
                    hungUpAndFinish();
                });
        confirmDialog.setNegativeButton("???",
                (dialog, which) -> {

                });
        confirmDialog.show();
    }

    private void hungUpAndFinish() {
        nertcVideoCall.hangup(new RequestCallback<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                handler.postDelayed(() -> {
                    finish();
                }, DELAY_TIME);
            }

            @Override
            public void onFailed(int i) {
                Log.e(LOG_TAG, "error when hangup code = " + i);
                handler.postDelayed(() -> {
                    finish();
                }, DELAY_TIME);
            }

            @Override
            public void onException(Throwable throwable) {
                Log.e(LOG_TAG, "onException when hangup", throwable);
                handler.postDelayed(() -> {
                    finish();
                }, DELAY_TIME);
            }
        });
    }

    private void finishOnFailed() {
        try {
            NERTCVideoCall.sharedInstance().leave(new RequestCallback<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                    Log.e(LOG_TAG, "finishOnFailed leave onSuccess");
                }

                @Override
                public void onFailed(int i) {
                    Log.e(LOG_TAG, "finishOnFailed leave onFailed code = " + i);
                }

                @Override
                public void onException(Throwable throwable) {
                    Log.e(LOG_TAG, "finishOnFailed leave onException", throwable);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }

        finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (nertcCallingDelegate != null && nertcVideoCall != null) {
            nertcVideoCall.removeDelegate(nertcCallingDelegate);
        }
        if (callType == ChannelType.VIDEO.getValue()) {
            remoteVideoView.release();
            localVideoView.release();
        }
        super.onDestroy();
    }

}
