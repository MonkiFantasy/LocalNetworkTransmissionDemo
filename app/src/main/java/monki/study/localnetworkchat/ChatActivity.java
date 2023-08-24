/*
 * Copyright (c) 2023 MonkiFantasy
*/

package monki.study.localnetworkchat;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.ConnectException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

/*
* An activity class with chat and file transmission function.
* @author MonkiFantasy
* @version 1.0
* */
public class ChatActivity extends AppCompatActivity implements View.OnClickListener {

    EditText fromPort, toIp, toPort, receivePort, message;
    TextView tvChat;
    boolean isClicked;

    String readPath;
    String downloadPath;
    private Uri uri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);
        //初始化控件
        fromPort = findViewById(R.id.et_from_port);
        toIp = findViewById(R.id.et_to_ip);
        toPort = findViewById(R.id.et_to_port);
        receivePort = findViewById(R.id.et_receive_port);
        message = findViewById(R.id.et_message);
        tvChat = findViewById(R.id.tv_chat);
        findViewById(R.id.btn_start).setOnClickListener(this);
        findViewById(R.id.btn_send).setOnClickListener(this);
        findViewById(R.id.btn_file).setOnClickListener(this);
        findViewById(R.id.btn_file_send).setOnClickListener(this);
        findViewById(R.id.btn_file_receive).setOnClickListener(this);
    }


    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            //开始聊天按钮
            case R.id.btn_start:
                new Thread(() -> {
                    try {
                        UDPMsgReceive();
                    }catch (NumberFormatException e){
                        runOnUiThread(()->tvChat.append("请输入你的接收端口\n"));
                    }
                }).start();
                break;
            //发送按钮
            case R.id.btn_send:
                isClicked = true;
                new Thread(() -> {
                    UdpMsgSend();
                }).start();
                break;
            //选择文件按钮
            case R.id.btn_file:
                pickFile();
                break;
                //发送文件按钮
            case R.id.btn_file_send:
                String targetIp = toIp.getText().toString();
                if (TextUtils.isEmpty(targetIp)) {
                    Toast.makeText(this, "请填写目标 IP 地址", Toast.LENGTH_SHORT).show();
                    return;
                }
                new Thread(() -> {
                    try {
                        TcpFileClient(uri);
                    }catch (NullPointerException e){
                        runOnUiThread(()->{
                            tvChat.append("请先选择你要发送的文件(NullPointerException)\n");
                        });
                    }
                    catch (ConnectException e){
                        runOnUiThread(()->{
                            tvChat.append("对方尚未开启文件服务，连接失败(ConnectException)\n");
                        });
                    }
                    catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }).start();

                break;
                //开启文件接收服务
            case R.id.btn_file_receive:
                new Thread(() -> {
                    try {
                        TcpFileServer();

                    }catch (BindException e){
                        runOnUiThread(()->{
                            tvChat.append("文件服务已开启，无需重新开启\n");
                        });
                    }
                    catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }).start();
        }
    }

    //udp发送信息
    /*
     *Using class DatagramSocket via UDP protocol realize sending messages.
     * */
    private void UdpMsgSend() {

        try (DatagramSocket socket = new DatagramSocket(Integer.parseInt(fromPort.getText().toString()))) {
                if (isClicked) {
                        String data = message.getText().toString();
                        byte[] datas = data.getBytes();
                        DatagramPacket packet = new DatagramPacket(datas, 0, datas.length,
                                new InetSocketAddress(toIp.getText().toString(), Integer.parseInt(toPort.getText().toString())));
                        //发送包
                        socket.send(packet);
                        runOnUiThread(() -> {
                            tvChat.append("\n" + message.getText().toString() + "\n");
                            message.setText("");
                            Toast.makeText(ChatActivity.this, "已发送", Toast.LENGTH_SHORT).show();
                        });
                }
        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    //udp接收信息
    /*
     *Using class DatagramSocket via UDP protocol realize receiving messages.
     * */
    private void UDPMsgReceive() {
        try (DatagramSocket socket = new DatagramSocket(Integer.parseInt(receivePort.getText().toString()))) {
            while (true) {
                try {
                    //准备接收包裹
                    byte[] container = new byte[1024];
                    DatagramPacket packet = new DatagramPacket(container, 0, container.length);
                    socket.receive(packet);//阻塞式接收包裹
                    byte[] data = packet.getData();
                    //在主线程中执行ui操作
                    runOnUiThread(() -> {
                        //使用packet.getLength()返回获取到包的大小作为字符串大小构造字符串防止构造空字符
                        String receiveData = new String(data, 0, packet.getLength());
                        tvChat.append("对方" + ":" + receiveData);//打印数据
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }


    }

    // 打开系统的文件选择器,通过onActivityResult返回选择文件的Uri
    /*
     *Open the system file picker to Choose a file, obtain relative result by method onActivityResult.
     *{@link #onActivityResult(final int , final int, @Nullable final Intent)}
     * */
    public void pickFile() {
        int REQUEST_CODE = 1;
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_CODE);
    }

    //获取选择文件的Uri
    /*
    * Get the uri of the chosen file, assign it to the global variable uri.
    * */
    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, @Nullable final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (data == null) {
            // 用户未选择任何文件，直接返回
            return;
        }
        // 获取用户选择文件的URI
        uri = data.getData();
        readPath = uri.getPath(); // 获取文件的路径，这里可能不是完整路径，而是文件的 URI
        tvChat.append("选择了"+readPath+"文件\n");
    }


    //tcp传送文件服务端：接收端
    /*
    *Using class ServerSocket by TCP protocol set a file server(receiving end).
    * @throws Exception
    * */
    private void TcpFileServer() throws Exception {
        //1.创建服务
        ServerSocket serverSocket = new ServerSocket(9000);
        runOnUiThread(()->{
            tvChat.append("文件服务已开启...等待接收\n");
        });
        //2.监听客户端的连接
        Socket accept = serverSocket.accept();//阻塞式监听,会一直等待客户端连接

        //3.获取输入流
        InputStream is = accept.getInputStream();

        //4.文件输出
        String filename = "收到.file";
        downloadPath = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).toString() + File.separatorChar + filename;

        FileOutputStream fos = new FileOutputStream(new File(downloadPath));
        byte[] buffer = new byte[1024];
        int len;
        while ((len = is.read(buffer)) != -1) {
            fos.write(buffer, 0, len);
        }
        runOnUiThread(() -> {
            //Toast.makeText(this, "文件接收完毕，保存在" + downloadPath + "路径下", Toast.LENGTH_SHORT).show();
            tvChat.append("文件接收完毕，保存在" + getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).toString() + File.separatorChar  + "\t路径下"+filename+"文件,请修改后缀后使用，文件服务已关闭，下次使用前请重新开启\n");
        });

        //通知客户端我接收完毕了

        //关闭资源
        //os.close();
        fos.close();
        is.close();
        accept.close();
        serverSocket.close();

    }

    //tcp传送文件客户端：发送端
    /*
     *Using class ServerSocket by TCP protocol set a file client(sending end).
     * @params uri - The chosen file's uri.
     * @throws Exception
     * */
    private void TcpFileClient(Uri uri) throws Exception {
        //1.创建一个socket连接对方主机(对方ip:port)
        Socket socket = new Socket(toIp.getText().toString(), 9000);
        //2.创建一个输出流
        OutputStream os = socket.getOutputStream();
        //3.从手机中读取要发送的文件
        InputStream fis = getContentResolver().openInputStream(uri);
        Long start = System.currentTimeMillis();
        //4.传出文件
        byte[] buffer = new byte[1024];
        int len;
        while ((len = fis.read(buffer)) != -1) {
            os.write(buffer, 0, len);
        }
        //弹出文件传输耗时
        runOnUiThread(() -> {
            //Toast.makeText(this, "文件"+readPath+"发送完毕,耗时" + (System.currentTimeMillis() - start) + "ms", Toast.LENGTH_SHORT).show();
            tvChat.append("文件"+readPath+"发送完毕,耗时" + (System.currentTimeMillis() - start) + "ms\n");
        });
        //通知服务器,我已经结束,不然会死锁?
        socket.shutdownOutput();//我已经传输完了

        //确定服务器吗接收完毕,才能断开连接

        //5.关闭资源
        fis.close();
        os.close();
        socket.close();
    }


}