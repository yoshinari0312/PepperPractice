package com.example.b3demo;

import android.os.Bundle;
import android.util.Log;

import com.aldebaran.qi.sdk.QiContext;
import com.aldebaran.qi.sdk.QiSDK;
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks;
import com.aldebaran.qi.sdk.builder.GoToBuilder;
import com.aldebaran.qi.sdk.builder.SayBuilder;
import com.aldebaran.qi.sdk.design.activity.RobotActivity;
import com.aldebaran.qi.sdk.object.actuation.Actuation;
import com.aldebaran.qi.sdk.object.actuation.FreeFrame;
import com.aldebaran.qi.sdk.object.actuation.GoTo;
import com.aldebaran.qi.sdk.object.actuation.LookAtMovementPolicy;
import com.aldebaran.qi.sdk.object.actuation.Mapping;
import com.aldebaran.qi.Future;
import com.aldebaran.qi.sdk.builder.LookAtBuilder;
import com.aldebaran.qi.sdk.object.actuation.Frame;
import com.aldebaran.qi.sdk.object.conversation.Say;
import com.aldebaran.qi.sdk.object.geometry.Transform;
import com.aldebaran.qi.sdk.object.geometry.Vector3;
import com.aldebaran.qi.sdk.object.actuation.LookAt;
import com.aldebaran.qi.sdk.builder.TransformBuilder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;


public class MainActivity extends RobotActivity implements RobotLifecycleCallbacks {
    int portNum = 2002;

    private GoTo goTo;
    private LookAt lookAt;
    private Future<Void> lookAtFuture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        QiSDK.register(this, this);
    }

    @Override
    protected void onDestroy() {
        QiSDK.unregister(this, this);
        super.onDestroy();
    }

    @Override
    public void onRobotFocusGained(QiContext qiContext) {
        SayBuilder.with(qiContext).withText("準備完了").build().run();
        System.out.println("準備完了");

        try (ServerSocket server = new ServerSocket(portNum)) {
            while (true) {
                // -----------------------------------------
                // 2.クライアントからの接続を待ち受け（accept）
                // -----------------------------------------
                Socket sc = null;
                BufferedReader reader = null;
                PrintWriter writer = null;
                try {
                    sc = server.accept();
                    Log.d("TCP status", "connected");
                    reader = new BufferedReader(new InputStreamReader(sc.getInputStream()));
                    writer = new PrintWriter(sc.getOutputStream(), true);

                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("say:")) {
                            // 喋るメッセージの読み取り（改行まで）
                            String toSay = line.substring(4);
                            System.out.println("Received toSay: " + toSay);
                            Say say = SayBuilder.with(qiContext)
                                    .withText(toSay)
                                    .build();
                            say.run();
                            System.out.println("finished speaking");
                            // 「Say」アクションが終了したら、クライアントに「話し終わった」ことを通知
                            writer.println("Finished speaking");
                        }else if(line.startsWith("goto:")){
                            // 動作用メッセージの受け取り（改行まで）
                            String toMove = line.substring(5);
                            System.out.println("Received toMove: " + toMove);

                            try {
                                // Pepperのロボットフレームを取得
                                Actuation actuation = qiContext.getActuation();
                                if (actuation == null) {
                                    Log.e("GoTo", "Actuation is null.");
                                    return;
                                }else{
                                    Log.i("GoTo", "Actuation OK");
                                }

                                Frame robotFrame = actuation.robotFrame();
                                if (robotFrame == null) {
                                    Log.e("GoTo", "robotFrame is null.");
                                    return;
                                }else{
                                    Log.i("GoTo", "robotFrame OK");
                                }

                                // Mapping インスタンスの取得
                                Mapping mapping = qiContext.getMapping();
                                if (mapping == null) {
                                    Log.e("GoTo", "Mapping is null. Navigation might be disabled.");
                                    return;
                                }else{
                                    Log.i("GoTo", "Mapping OK");
                                }

                                // 新しいフレームを作成（ターゲット座標を設定）
                                FreeFrame targetFrame = mapping.makeFreeFrame();
                                Transform transform = TransformBuilder.create().fromTranslation(new Vector3(0.3, 0.0, 0.0)); // X方向に1m移動
                                targetFrame.update(robotFrame, transform, 0L);

                                // GoTo アクションをビルド
                                goTo = GoToBuilder.with(qiContext)
                                        .withFrame(targetFrame.frame())
                                        .withMaxSpeed(0.5f)
                                        .build();

                                goTo.addOnStartedListener(() -> Log.i("GoTo", "GoTo action started."));

                                Future<Void> goToFuture = goTo.async().run();

                                goToFuture.thenConsume(future -> {
                                    if (future.isSuccess()) {
                                        Log.i("GoTo", "GoTo action finished successfully.");
                                    } else if (future.hasError()) {
                                        Log.e("GoTo", "GoTo action failed.", future.getError());
                                        future.getError().printStackTrace();
                                    }
                                });
                                writer.println("Finished moving");
                            } catch (Exception e) {
                                Log.e("GoTo", "Error in GoTo execution.", e);
                            }
                        }else if(line.startsWith("look:")){
                            boolean onlyHeadFlag;
                            String onlyHead = line.substring(5,13);  // "onlyHead" or "fullBody"
                            Log.i("Look", onlyHead);
                            if(onlyHead.equals("onlyHead")){
                                onlyHeadFlag = true;
                            }else{
                                onlyHeadFlag = false;
                            }
                            // 視線用メッセージの受け取り（改行まで）
                            String toLook = line.substring(14);  // "right", "left", "center"
                            Log.i("Look", "Direction: " + toLook);

                            // Pepperが見るべき方向をx,y,zで指定
                            double x;
                            double y;
                            double z;

                            if(toLook.equals("right")) {
                                x = 1;  // 前に1メートル
                                y = -1;  // 右に1メートル
                                z = 1;  // 地面から1メートル
                            } else if (toLook.equals("left")) {
                                x = 1;  // 前に1メートル
                                y = 1;  // 左に1メートル
                                z = 1;  // 地面から1メートル
                            } else {
                                x = 1;  // 前に1メートル
                                y = 0;  // まっすぐ
                                z = 1;  // 地面から1メートル
                            }

                            // Pepperのロボットフレームを取得
                            Frame robotFrame = qiContext.getActuation().robotFrame();
                            Transform transform = TransformBuilder.create().fromTranslation(new Vector3(x, y, z));
                            // Get the Mapping service from the QiContext.
                            Mapping mapping = qiContext.getMapping();
                            // Create a FreeFrame with the Mapping service.
                            FreeFrame targetFrame = mapping.makeFreeFrame();
                            // Update the target location relatively to Pepper's current location.
                            targetFrame.update(robotFrame, transform, 0L);

                            // 新しいLookAtアクションを開始する前に、以前のアクションをキャンセルする。
                            if (lookAtFuture != null && !lookAtFuture.isDone()) {
                                // 以前のアクションがまだ実行中の場合、キャンセルをリクエストする。
                                Log.i("LookAtFuture", "Requesting cancellation of the ongoing LookAt action.");
                                lookAtFuture.requestCancellation();
                            }

                            // LookAtアクションをビルド
                            lookAt = LookAtBuilder.with(qiContext)
                                    .withFrame(targetFrame.frame())
                                    .build();
                            // 頭だけを動かす
                            if(onlyHeadFlag) {
                                lookAt.setPolicy(LookAtMovementPolicy.HEAD_ONLY);
                            }

                            // LookAtの実行
                            lookAt.addOnStartedListener(() -> Log.i("MainActivity", "LookAt action started."));
                            lookAtFuture = lookAt.async().run();

                            // ログ表示のためのスレッド
                            new Thread(() -> {
                                try {
                                    // Futureが完了するのを待ちます。この呼び出しはブロッキングされます。
                                    lookAtFuture.get(); // 'get' は結果を返すか、エラーが発生した場合は例外をスローします。

                                    // Futureが成功した場合、結果をログに出力します。
                                    Log.i("LookAtFuture", "LookAt action completed successfully.");
                                } catch (CancellationException e) {
                                    // Futureがキャンセルされた場合の処理
                                    Log.i("LookAtFuture", "LookAt action was cancelled.");
                                } catch (ExecutionException e) {
                                    // Futureの実行中に例外が発生した場合の処理をここに記述します。
                                    Throwable cause = e.getCause(); // 実際の原因を取得
                                    Log.e("LookAtFuture", "Exception in LookAt action: ", cause);
                                }
                            }).start();
                            writer.println("Finished looking");
                        }
                    }
                } catch (Exception e) {
                    System.out.println("Catch error");
                    e.printStackTrace();
                } finally {
                    try {
                        if (writer != null) {
                            writer.close();
                        }
                        if (reader != null) {
                            reader.close();
                        }
                        if (sc != null) {
                            sc.close();
                        }
                        System.out.println("Resources are cleaned up.");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Catch error3");
            e.printStackTrace();
        }
    }

    @Override
    public void onRobotFocusLost() {
        if (goTo != null) {
            goTo.removeAllOnStartedListeners();
        }
        if (lookAt != null) {
            lookAt.removeAllOnStartedListeners();
        }
    }

    @Override
    public void onRobotFocusRefused(String reason) {

    }
}
