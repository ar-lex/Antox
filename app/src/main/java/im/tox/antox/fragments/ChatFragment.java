package im.tox.antox.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.text.ClipboardManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Random;

import im.tox.antox.R;
import im.tox.antox.adapters.ChatMessagesAdapter;
import im.tox.antox.data.AntoxDB;
import im.tox.antox.tox.ToxSingleton;
import im.tox.antox.utils.AntoxFriend;
import im.tox.antox.utils.ChatMessages;
import im.tox.antox.utils.Constants;
import im.tox.antox.utils.FriendInfo;
import im.tox.antox.utils.Message;
import im.tox.jtoxcore.ToxException;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;


/**
 * Created by ollie on 28/02/14.
 */
public class ChatFragment extends Fragment {
    private static String TAG = "im.tox.antox.fragments.ChatFragment";
    public static String ARG_CONTACT_NUMBER = "contact_number";
    private ListView chatListView;

    private ChatMessagesAdapter adapter;
    private EditText messageBox;
    private TextView isTypingBox;
    private TextView statusTextBox;
    ToxSingleton toxSingleton = ToxSingleton.getInstance();
    Subscription messagesSub;
    Subscription titleSub;
    Subscription typingSub;
    private ArrayList<ChatMessages> chatMessages;
    private String activeKey;
    private AntoxDB antoxDB;
    public String photoPath;
    public ChatFragment(String key) {
        this.activeKey = key;
    }

    public ChatFragment() {
        super();
    }

    @Override
    public void onResume(){
        super.onResume();
        Runnable load = new Runnable() {
            @Override
            public void run() {
                toxSingleton.chatActiveSubject.onNext(activeKey);
                messagesSub = toxSingleton.updatedMessagesSubject.observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<Boolean>() {
                    @Override
                    public void call(Boolean aBoolean) {
                        Log.d("ChatFragment", "updatedMessageSubject subscription");
                        updateChat();
                    }
                });

                titleSub = toxSingleton.friendInfoListSubject.observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<ArrayList<FriendInfo>>() {
                    @Override
                    public void call(ArrayList<FriendInfo> fi) {
                        String friendName = "";
                        String friendAlias = "";
                        String friendNote = "";
                        for (FriendInfo f : fi) {
                            if (f.friendKey.equals(activeKey)) {
                                friendName = f.friendName;
                                friendNote = f.personalNote;
                                friendAlias = f.alias;
                                break;
                            }
                        }

                        Typeface robotoBold = Typeface.createFromAsset(getActivity().getAssets(), "fonts/Roboto-Bold.ttf");
                        Typeface robotoThin = Typeface.createFromAsset(getActivity().getAssets(), "fonts/Roboto-Thin.ttf");
                        Typeface robotoRegular = Typeface.createFromAsset(getActivity().getAssets(), "fonts/Roboto-Regular.ttf");

                        TextView chatName = (TextView) getActivity().findViewById(R.id.chatActiveName);
                        if (!friendAlias.equals(""))
                            chatName.setText(friendAlias);
                        else
                            chatName.setText(friendName);

                        TextView statusText = (TextView) getActivity().findViewById(R.id.chatActiveStatus);
                        statusText.setText(friendNote);

                        chatName.setTypeface(robotoBold);
                        statusText.setTypeface(robotoRegular);
                    }
                });
                typingSub = toxSingleton.typingSubject.observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<Boolean>() {
                    @Override
                    public void call(Boolean x) {
                        if (toxSingleton.typingMap.containsKey(activeKey)) {
                            boolean isTyping = toxSingleton.typingMap.get(activeKey);
                            if (isTyping) {
                                isTypingBox.setVisibility(View.VISIBLE);
                                statusTextBox.setVisibility(View.GONE);
                            } else {
                                isTypingBox.setVisibility(View.GONE);
                                statusTextBox.setVisibility(View.VISIBLE);
                            }
                        } else {
                            isTypingBox.setVisibility(View.GONE);
                            statusTextBox.setVisibility(View.VISIBLE);
                        }
                    }
                });
            }
        };
        new Thread(load).start();
    }

    @Override
    public void onPause(){
        super.onPause();
        toxSingleton.chatActiveSubject.onNext("");
        messagesSub.unsubscribe();
        titleSub.unsubscribe();
        typingSub.unsubscribe();
    }

    public void sendMessage() {
        Log.d("ChatFragment","sendMessage");
        if (messageBox.getText() != null && messageBox.getText().toString().length() == 0) {
            return;
        }
        final String msg;
        if (messageBox.getText() != null ) {
            msg = messageBox.getText().toString();
        } else {
            msg = "";
        }
        final String key = activeKey;
        messageBox.setText("");
        Observable<Boolean> send = Observable.create(
                new Observable.OnSubscribe<Boolean>() {
                     @Override
                         public void call(Subscriber<? super Boolean> subscriber) {
                            try {
                                /* Send message */
                                AntoxFriend friend = null;
                                Random generator = new Random();
                                int id = generator.nextInt();
                                try {
                                    friend = toxSingleton.getAntoxFriend(key);
                                } catch (Exception e) {
                                    Log.d(TAG, e.toString());
                                }
                                if (friend != null) {
                                    boolean sendingSucceeded = true;
                                    try {
                                        // NB: substring includes from start up to but not including the end position
                                        // Max message length in tox is 1368 bytes
                                        // jToxCore seems to append a null byte so split around 1367
                                        final byte[] utf8Bytes = msg.getBytes("UTF-8");
                                        int numOfMessages = (utf8Bytes.length/1367) + 1;

                                        if(numOfMessages > 1) {

                                            final int OneByte = 0xFFFFFF80;
                                            final int TwoByte = 0xFFFFF800;
                                            final int ThreeByte = 0xFFFF0000;

                                            int total = 0;
                                            int previous = 0;
                                            int numberOfMessagesSent = 0;

                                            for(int i = 0; i < msg.length(); i++) {
                                                if((msg.charAt(i) & OneByte) == 0)
                                                    total += 1;
                                                else if((msg.charAt(i) & TwoByte) == 0)
                                                    total += 2;
                                                else if((msg.charAt(i) & ThreeByte) == 0)
                                                    total += 3;
                                                else
                                                    total += 4;

                                                if(numberOfMessagesSent == numOfMessages-1) {
                                                    toxSingleton.jTox.sendMessage(friend, msg.substring(previous), id);
                                                    break;
                                                } else if(total >= 1366) {
                                                    toxSingleton.jTox.sendMessage(friend, msg.substring(previous, i), id);
                                                    numberOfMessagesSent++;
                                                    previous = i;
                                                    total = 0;
                                                }
                                            }

                                        } else {
                                            toxSingleton.jTox.sendMessage(friend, msg, id);
                                        }

                                    } catch (ToxException e) {
                                        Log.d(TAG, e.toString());
                                        e.printStackTrace();
                                        sendingSucceeded = false;
                                    }
                                    AntoxDB db = new AntoxDB(getActivity());
                                    /* Add message to chatlog */
                                    db.addMessage(id, key, msg, false, false, sendingSucceeded, 1);
                                    db.close();
                                    /* update UI */
                                    toxSingleton.updateMessages(getActivity());
                                }
                                subscriber.onCompleted();
                            } catch (Exception e) {
                                Log.e("ChatFragment", "Subscriber error: " + e.getMessage());
                                subscriber.onError(e);

                            }
                         }
                     });
        send.subscribeOn(Schedulers.io()).subscribe();
    }

    private Cursor getCursor() {
        antoxDB.close();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        Cursor cursor = this.antoxDB.getMessageCursor(activeKey, preferences.getBoolean("action_messages", true));
        return cursor;
    }

    public void updateChat() {
        adapter.changeCursor(getCursor());
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d("ChatFragment ImageResult resultCode", Integer.toString(resultCode));
        Log.d("ChatFragment ImageResult requestCode", Integer.toString(requestCode));
        if (requestCode == Constants.IMAGE_RESULT  && resultCode == Activity.RESULT_OK) {
            Uri uri = data.getData();
            String path = null;
            String[] filePathColumn = {MediaStore.Images.Media.DATA,
                    MediaStore.Images.Media.DISPLAY_NAME};
            String filePath = null;
            String fileName = null;
            CursorLoader loader = new CursorLoader(getActivity(), uri, filePathColumn, null, null, null);
            Cursor cursor = loader.loadInBackground();
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    int columnIndex = cursor.getColumnIndexOrThrow(filePathColumn[0]);
                    filePath = cursor.getString(columnIndex);
                    int fileNameIndex = cursor.getColumnIndexOrThrow(filePathColumn[1]);
                    fileName = cursor.getString(fileNameIndex);
                }
            }
            try {
                path = filePath;
            } catch (Exception e) {
                Log.d("onActivityResult", e.toString());
            }
            if (path != null) {
                toxSingleton.sendFileSendRequest(path, activeKey, getActivity());
            }
        }
        if(requestCode==Constants.PHOTO_RESULT && resultCode==Activity.RESULT_OK){

            if(photoPath!=null) {
                toxSingleton.sendFileSendRequest(photoPath, activeKey, getActivity());
                photoPath=null;
            }

        }
    }




    @SuppressWarnings("deprecation")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        Runnable load = new Runnable() {
            @Override
            public void run() {

            }
        };

        View rootView = inflater.inflate(R.layout.fragment_chat, container, false);
        chatMessages = new ArrayList<ChatMessages>();
        this.antoxDB = new AntoxDB(getActivity());
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        Cursor cursor = this.antoxDB.getMessageCursor(activeKey, preferences.getBoolean("action_messages", true));
        adapter = new ChatMessagesAdapter(getActivity(), cursor, antoxDB.getMessageIds(activeKey, preferences.getBoolean("action_messages", true)));
        chatListView = (ListView) rootView.findViewById(R.id.chatMessages);
        chatListView.setTranscriptMode(ListView.TRANSCRIPT_MODE_NORMAL);
        chatListView.setStackFromBottom(true);
        chatListView.setAdapter(adapter);
        chatListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {

            public boolean onItemLongClick(AdapterView<?> arg0, View v,
                                           int index, long arg3) {
                if (chatMessages.get(index).getType() == 1 || chatMessages.get(index).getType() == 2) {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                    CharSequence[] items = new CharSequence[]{
                            getString(R.string.message_copy),
                            getString(R.string.message_delete)
                    };
                    final int i = index;
                    builder.setCancelable(true)
                            .setItems(items, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int index) {
                                    switch (index) {
                                        case 0: //Copy
                                            String msg = chatMessages.get(i).message;
                                            ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(getActivity().CLIPBOARD_SERVICE);
                                            clipboard.setText(msg);
                                            break;
                                        case 1: //Delete
                                            class DeleteMessage extends AsyncTask<Void, Void, Void> {
                                                @Override
                                                protected Void doInBackground(Void... params) {
                                                    AntoxDB antoxDB = new AntoxDB(getActivity().getApplicationContext());
                                                    antoxDB.deleteMessage(chatMessages.get(i).id);
                                                    antoxDB.close();
                                                    return null;
                                                }

                                                @Override
                                                protected void onPostExecute(Void result) {
                                                    toxSingleton.updateMessages(getActivity());
                                                }

                                            }
                                            new DeleteMessage().execute();

                                            break;
                                    }
                                }
                            });
                    AlertDialog alert = builder.create();
                    alert.show();
                    } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                    CharSequence[] items = new CharSequence[]{
                            getString(R.string.message_delete)
                    };
                    final int i = index;
                    builder.setCancelable(true)
                            .setItems(items, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int index) {
                                    switch (index) {
                                        case 0: //Delete
                                            class DeleteMessage extends AsyncTask<Void, Void, Void> {
                                                @Override
                                                protected Void doInBackground(Void... params) {
                                                    AntoxDB antoxDB = new AntoxDB(getActivity().getApplicationContext());
                                                    antoxDB.deleteMessage(chatMessages.get(i).id);
                                                    antoxDB.close();
                                                    return null;
                                                }

                                                @Override
                                                protected void onPostExecute(Void result) {
                                                    toxSingleton.updateMessages(getActivity());
                                                }

                                            }
                                            new DeleteMessage().execute();

                                            break;
                                    }
                                }
                            });
                    AlertDialog alert = builder.create();
                    alert.show();
                }
                    return true;
                }
        });

        isTypingBox = (TextView) rootView.findViewById(R.id.isTyping);
        statusTextBox = (TextView) rootView.findViewById(R.id.chatActiveStatus);

        messageBox = (EditText) rootView.findViewById(R.id.yourMessage);
        messageBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                boolean isTyping;
                if(i3 > 0) {
                    isTyping = true;
                } else {
                    isTyping = false;
                }
                AntoxFriend friend = toxSingleton.getAntoxFriend(activeKey);
                if (friend != null) {
                    try {
                        toxSingleton.jTox.sendIsTyping(friend.getFriendnumber(), isTyping);
                    } catch (ToxException ex) {

                    }
                }
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        messageBox.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                //chatListView.setSelection(adapter.getCount() - 1);
            }
        });

        View b = (View) rootView.findViewById(R.id.sendMessageButton);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessage();
                AntoxFriend friend = toxSingleton.getAntoxFriend(activeKey);
                if (friend != null) {
                    try {
                        toxSingleton.jTox.sendIsTyping(friend.getFriendnumber(), false);
                    } catch (ToxException ex) {

                    }
                }
            }
        });

        View backButton = (View) rootView.findViewById(R.id.backButton);

        backButton.setOnClickListener(new View.OnClickListener() {
                                          @Override
                                          public void onClick(View v) {
                                              toxSingleton.doClosePaneSubject.onNext(false);
                                          }
                                      });
        View attachmentButton = (View) rootView.findViewById(R.id.attachmentButton);

        attachmentButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                final CharSequence items[];
                items = new CharSequence[] {
                        getResources().getString(R.string.attachment_photo),
                        getResources().getString(R.string.attachment_takephoto)
                };
                builder.setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        switch (i) {
                            case 0:
                                Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                                startActivityForResult(intent, Constants.IMAGE_RESULT);
                                break;
                            case 1:
                                Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                                String image_name = "Antoxpic" + new Date().toString();
                                File storageDir = Environment.getExternalStoragePublicDirectory(
                                        Environment.DIRECTORY_PICTURES);
                                File file = null;
                                try {
                                    file = File.createTempFile(
                                            image_name,  /* prefix */
                                            ".jpg",         /* suffix */
                                            storageDir      /* directory */
                                    );
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                if (file != null) {
                                    Uri imageUri = Uri.fromFile(file);
                                    cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
                                    photoPath = file.getAbsolutePath();
                                }
                                startActivityForResult(cameraIntent, Constants.PHOTO_RESULT);
                        }
                    }
                });
                builder.create().show();
            }
        });
        return rootView;
    }


}
