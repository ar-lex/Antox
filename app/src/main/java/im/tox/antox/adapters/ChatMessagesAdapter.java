package im.tox.antox.adapters;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Environment;
import android.support.v4.widget.ResourceCursorAdapter;
import android.text.Layout;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ArrayAdapter;
import android.widget.CursorAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import im.tox.antox.R;
import im.tox.antox.activities.MainActivity;
import im.tox.antox.tox.ToxSingleton;
import im.tox.antox.utils.BitmapManager;
import im.tox.antox.utils.ChatMessages;
import im.tox.antox.utils.Constants;
import im.tox.antox.utils.PrettyTimestamp;

public class ChatMessagesAdapter extends ResourceCursorAdapter {
    Context context;
    int layoutResourceId = R.layout.chat_message_row;
    private int density;
    private int paddingscale = 8;
    private ToxSingleton toxSingleton = ToxSingleton.getInstance();
    private Animation animLeft;
    private Animation animRight;
    private Animation anim;
    private LayoutInflater mInflater;
    private HashSet<Integer> animatedIds;

    public ChatMessagesAdapter(Context context, Cursor c, HashSet<Integer> ids) {
        super(context, R.layout.chat_message_row, c, 0);
        this.context = context;
        this.animLeft = AnimationUtils.loadAnimation(this.context, R.anim.slide_in_left);
        this.animRight = AnimationUtils.loadAnimation(this.context, R.anim.slide_in_right);
        this.anim = AnimationUtils.loadAnimation(this.context, R.anim.abc_slide_in_bottom);
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        this.density = (int) context.getResources().getDisplayMetrics().density;
        this.animatedIds = ids;
    }


    @Override
     public View newView(Context context, Cursor cursor, ViewGroup parent) {
          return mInflater.inflate(this.layoutResourceId, parent, false);
     }
    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        int id = cursor.getInt(0);
        Timestamp time = Timestamp.valueOf(cursor.getString(1));
        int message_id = cursor.getInt(2);
        String k = cursor.getString(3);
        String m = cursor.getString(4);
        boolean received = cursor.getInt(5)>0;
        boolean read = cursor.getInt(6)>0;
        boolean sent = cursor.getInt(7)>0;
        int size = cursor.getInt(8);
        int type = cursor.getInt(9);

        ChatMessages msg = new ChatMessages(id, message_id, m, time, received, sent, size, type);
        ChatMessagesHolder holder = new ChatMessagesHolder();


        holder.message = (TextView) view.findViewById(R.id.message_text);
        holder.layout = (LinearLayout) view.findViewById(R.id.message_text_layout);
        holder.row = (LinearLayout) view.findViewById(R.id.message_row_layout);
        holder.background = (LinearLayout) view.findViewById(R.id.message_text_background);
        holder.time = (TextView) view.findViewById(R.id.message_text_date);
        holder.sent = (ImageView) view.findViewById(R.id.chat_row_sent);
        holder.received = (ImageView) view.findViewById(R.id.chat_row_received);
        holder.title = (TextView) view.findViewById(R.id.message_title);
        holder.progress = (ProgressBar) view.findViewById(R.id.file_transfer_progress);
        holder.imageMessage = (ImageView) view.findViewById(R.id.message_sent_photo);
        holder.imageMessageFrame = (FrameLayout) view.findViewById(R.id.message_sent_photo_frame);
        holder.progressText = (TextView) view.findViewById(R.id.file_transfer_progress_text);
        holder.padding = (View) view.findViewById(R.id.file_transfer_padding);

        Typeface robotoBold = Typeface.createFromAsset(context.getAssets(), "fonts/Roboto-Bold.ttf");
        Typeface robotoThin = Typeface.createFromAsset(context.getAssets(), "fonts/Roboto-Thin.ttf");
        Typeface robotoRegular = Typeface.createFromAsset(context.getAssets(), "fonts/Roboto-Regular.ttf");

        switch(type) {
            case Constants.MESSAGE_TYPE_OWN:
                ownMessage(holder);
                if (msg.sent) {
                    if (msg.received) {
                        holder.sent.setVisibility(View.GONE);
                        holder.received.setVisibility(View.VISIBLE);
                    } else {
                        holder.sent.setVisibility(View.VISIBLE);
                        holder.received.setVisibility(View.GONE);
                    }
                } else {
                    holder.sent.setVisibility(View.GONE);
                    holder.received.setVisibility(View.GONE);
                }
                break;

            case Constants.MESSAGE_TYPE_FRIEND:
                friendMessage(holder);
                holder.sent.setVisibility(View.GONE);
                holder.received.setVisibility(View.GONE);
                break;

            case Constants.MESSAGE_TYPE_FILE_TRANSFER:
            case Constants.MESSAGE_TYPE_FILE_TRANSFER_FRIEND:
                if (type == Constants.MESSAGE_TYPE_FILE_TRANSFER) {
                    ownMessage(holder);
                    String[] split = msg.message.split("/");
                    holder.message.setText(split[split.length - 1]);
                } else {
                    friendMessage(holder);
                    holder.message.setText(msg.message);
                }

                holder.title.setVisibility(View.VISIBLE);
                holder.title.setText(R.string.chat_file_transfer);
                holder.title.setTypeface(robotoBold);
                holder.received.setVisibility(View.GONE);
                holder.sent.setVisibility(View.GONE);

                if (msg.received) {
                    holder.progress.setVisibility(View.GONE);
                    holder.progressText.setText("Finished");
                    holder.progressText.setVisibility(View.VISIBLE);
                } else {
                    if (msg.sent) {
                        if (msg.message_id != -1) {
                            holder.progress.setVisibility(View.VISIBLE);
                            holder.progress.setMax(msg.size);
                            holder.progress.setProgress(toxSingleton.getProgress(msg.id));
                            holder.progressText.setVisibility(View.GONE);
                        } else { //Filesending failed, it's sent, we no longer have a filenumber, but it hasn't been received
                            holder.progress.setVisibility(View.GONE);
                            holder.progressText.setText("Failed");
                            holder.progressText.setVisibility(View.VISIBLE);
                        }
                    } else {
                        if (msg.message_id != -1) {
                            holder.progress.setVisibility(View.GONE);
                            if (msg.isMine()) {
                                holder.progressText.setText("Sent filesending request");
                            } else {
                                holder.progressText.setText("Received filesending request");
                            }
                            holder.progressText.setVisibility(View.VISIBLE);
                        } else { //Filesending request not accepted, it's sent, we no longer have a filenumber, but it hasn't been accepted
                            holder.progress.setVisibility(View.GONE);
                            holder.progressText.setText("Failed");
                            holder.progressText.setVisibility(View.VISIBLE);
                        }
                    }
                }

                if (msg.received || msg.isMine()) {
                    File f = null;
                    if (msg.message.contains("/")) {
                        f = new File(msg.message);
                    } else {
                        f = new File(Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_DOWNLOADS), Constants.DOWNLOAD_DIRECTORY);
                        f = new File(f.getAbsolutePath() + "/" + msg.message);
                    }

                    if (f.exists()) {
                        final File file = f;
                        final String[] okFileExtensions =  new String[] {"jpg", "png", "gif","jpeg"};

                        for (String extension : okFileExtensions) {

                            if (file.getName().toLowerCase().endsWith(extension)) {

                                if (msg.received) {
                                    if (BitmapManager.checkValidImage(file)) {
                                        BitmapManager.loadBitmap(file, file.getPath().hashCode(), holder.imageMessage);
                                    }

                                    holder.imageMessage.setVisibility(View.VISIBLE);
                                    holder.imageMessageFrame.setVisibility(View.VISIBLE);

                                    holder.padding.setVisibility(View.GONE);
                                    holder.progressText.setVisibility(View.GONE);
                                    holder.title.setVisibility(View.GONE);
                                    holder.message.setVisibility(View.GONE);

                                    holder.imageMessage.setOnClickListener(new View.OnClickListener() {
                                        public void onClick(View v) {
                                            Intent i = new Intent();
                                            i.setAction(android.content.Intent.ACTION_VIEW);
                                            i.setDataAndType(Uri.fromFile(file), "image/*");
                                            ChatMessagesAdapter.this.context.startActivity(i);
                                        }
                                    });

                                } else {
                                    holder.padding.setVisibility(View.VISIBLE);
                                }

                            }

                            break; // break for loop
                        }
                    }
                }

                break;

            case Constants.MESSAGE_TYPE_ACTION:

                holder.time.setGravity(Gravity.CENTER);
                holder.layout.setGravity(Gravity.CENTER);
                holder.sent.setVisibility(View.GONE);
                holder.received.setVisibility(View.GONE);
                holder.message.setTextColor(context.getResources().getColor(R.color.gray_darker));
                holder.row.setGravity(Gravity.CENTER);
                holder.background.setBackgroundColor(context.getResources().getColor(R.color.white_absolute));
                holder.message.setTextSize(12);

                break;
        }

        if(type != Constants.MESSAGE_TYPE_FILE_TRANSFER && type != Constants.MESSAGE_TYPE_FILE_TRANSFER_FRIEND) {
            holder.title.setVisibility(View.GONE);
            holder.message.setText(msg.message);
            holder.progress.setVisibility(View.GONE);
            holder.imageMessage.setVisibility(View.GONE);
            holder.imageMessageFrame.setVisibility(View.GONE);
            holder.message.setVisibility(View.VISIBLE);
        }

        holder.time.setText(PrettyTimestamp.prettyTimestamp(msg.time, true));

        holder.message.setTypeface(robotoRegular);
        holder.time.setTypeface(robotoRegular);

        if (!animatedIds.contains(id)) {
            /*
            if (msg.isMine()) {
                holder.row.startAnimation(animRight);
            } else {
                holder.row.startAnimation(animLeft);
            }*/
            holder.row.startAnimation(anim);
            animatedIds.add(id);
        }

    }

    @Override
    public int getViewTypeCount() {
        return 4;
    }

    private void ownMessage(ChatMessagesHolder holder) {
        holder.time.setGravity(Gravity.RIGHT);
        holder.layout.setGravity(Gravity.RIGHT);
        holder.message.setTextColor(context.getResources().getColor(R.color.white_absolute));
        holder.row.setGravity(Gravity.RIGHT);
        holder.background.setBackgroundDrawable(context.getResources().getDrawable(R.drawable.chatright));
        holder.background.setPadding(1*density*paddingscale, 1*density*paddingscale, 6*density + 1*density*paddingscale, 1*density*paddingscale);
    }

    private void friendMessage(ChatMessagesHolder holder) {
        holder.message.setTextColor(context.getResources().getColor(R.color.black));
        holder.background.setBackgroundDrawable(context.getResources().getDrawable(R.drawable.chatleft));
        holder.background.setPadding(6 * density + 1 * density * paddingscale, 1 * density * paddingscale, 1 * density * paddingscale, 1 * density * paddingscale);
        holder.time.setGravity(Gravity.LEFT);
        holder.layout.setGravity(Gravity.LEFT);
        holder.row.setGravity(Gravity.LEFT);
    }

    static class ChatMessagesHolder {
        LinearLayout row;
        LinearLayout layout;
        LinearLayout background;
        TextView message;
        TextView time;
        ImageView sent;
        ImageView received;
        ImageView imageMessage;
        FrameLayout imageMessageFrame;
        TextView title;
        ProgressBar progress;
        TextView progressText;
        View padding;
    }

}
