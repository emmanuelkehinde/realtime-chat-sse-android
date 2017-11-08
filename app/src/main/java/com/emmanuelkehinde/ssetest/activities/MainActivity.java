package com.emmanuelkehinde.ssetest.activities;

import android.app.ProgressDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.emmanuelkehinde.ssetest.APIService;
import com.emmanuelkehinde.ssetest.adapters.ChatAdapter;
import com.emmanuelkehinde.ssetest.R;
import com.emmanuelkehinde.ssetest.models.Chat;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.tylerjroach.eventsource.EventSource;
import com.tylerjroach.eventsource.EventSourceHandler;
import com.tylerjroach.eventsource.MessageEvent;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity {

    private static final String BASE_URL = "BASE_URL_HERE";
    private RecyclerView chatRecyc;
    private EventSource eventSource;
    private ProgressDialog progressDialog;
    private ArrayList<Chat> chatList = new ArrayList<>();
    private ChatAdapter chatAdapter;

    private EditText edt_message;
    private Button btn_send;
    private String username;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        username=getIntent().getStringExtra("username");

        chatRecyc=findViewById(R.id.chatList);
        edt_message=findViewById(R.id.edt_message);
        btn_send=findViewById(R.id.btn_send);

        chatAdapter=new ChatAdapter(this);
        chatRecyc.setLayoutManager(new LinearLayoutManager(this,LinearLayoutManager.VERTICAL,false));
        chatRecyc.addItemDecoration(new DividerItemDecoration(this,DividerItemDecoration.VERTICAL));
        chatRecyc.setAdapter(chatAdapter);

        progressDialog=new ProgressDialog(this);
        progressDialog.setMessage("Connecting...");
        progressDialog.setCancelable(false);

        btn_send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (TextUtils.isEmpty(edt_message.getText().toString())){
                    Toast.makeText(MainActivity.this, "Enter a chat message", Toast.LENGTH_SHORT).show();
                }else sendMessage();
            }
        });
    }

    private void sendMessage() {
        progressDialog.setMessage("Sending message...");
        progressDialog.show();
        String message=edt_message.getText().toString();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        APIService apiService=retrofit.create(APIService.class);
        Call<JsonObject> sendCall=apiService.send(message,username);
        sendCall.enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.body()!=null){
                    edt_message.setText("");
                }
                progressDialog.hide();
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                progressDialog.hide();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        startEventSource();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopEventSource();
    }

    private SSEHandler sseHandler = new SSEHandler();

    private void startEventSource() {
        progressDialog.show();
        eventSource = new EventSource.Builder(BASE_URL+ "message/sse")
                .eventHandler(sseHandler)
                .build();
        eventSource.connect();
    }

    private void stopEventSource() {
        if (eventSource != null)
            eventSource.close();
        sseHandler = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main,menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId()==R.id.menu_clear){
            clearMessages();
        }
        return super.onOptionsItemSelected(item);
    }

    private void clearMessages() {
        progressDialog.setMessage("Clearing Messages...");
        progressDialog.show();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        APIService apiService=retrofit.create(APIService.class);
        Call<String> clearCall=apiService.clearMessages();
        clearCall.enqueue(new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                progressDialog.hide();
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
                progressDialog.hide();
            }
        });
    }

    /**
     * All callbacks are currently returned on executor thread.
     * If you want to update the ui from a callback, make sure to post to main thread
     */
    private class SSEHandler implements EventSourceHandler {

        public SSEHandler() {
        }

        @Override
        public void onConnect() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    progressDialog.hide();
                }
            });
            Log.v("SSE Connected", "True");
        }

        @Override
        public void onMessage(String event, final MessageEvent message) throws Exception {
            Log.v("SSE Message", event);
            Log.v("SSE Message: ", message.lastEventId);
            Log.v("SSE Message: ", message.data);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Gson gson=new Gson();
                    TypeToken<List<Chat>> token = new TypeToken<List<Chat>>() {};
                    ArrayList<Chat> chats=gson.fromJson(message.data,token.getType());

                    if (chats.size()!=chatList.size()) {
                        chatList=chats;
                        chatAdapter.setChatList(chatList);
                        chatRecyc.scrollToPosition(chatAdapter.getItemCount()-1);
                    }
                }
            });
        }

        @Override
        public void onComment(String comment) {
            //comments only received if exposeComments turned on
            Log.v("SSE Comment", comment);
        }

        @Override
        public void onError(Throwable t) {
            //ignore ssl NPE on eventSource.close()
        }

        @Override
        public void onClosed(boolean willReconnect) {
            Log.v("SSE Closed", "reconnect? " + willReconnect);
        }
    }

}
