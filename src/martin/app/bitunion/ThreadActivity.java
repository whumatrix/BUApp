package martin.app.bitunion;

import java.util.ArrayList;

import org.json.JSONObject;

import martin.app.bitunion.fragment.ThreadFragment;
import martin.app.bitunion.fragment.UserInfoDialogFragment;
import martin.app.bitunion.martin.app.bitunion.widget.ObservableWebView;
import martin.app.bitunion.util.BUApiHelper;
import martin.app.bitunion.util.BUAppUtils;
import martin.app.bitunion.model.BUPost;
import martin.app.bitunion.util.CommonIntents;
import martin.app.bitunion.util.BUAppUtils.Result;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.PagerTitleStrip;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.View.OnTouchListener;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.android.volley.Response;
import com.android.volley.VolleyError;

public class ThreadActivity extends ActionBarActivity implements View.OnClickListener {

    private ThreadPagerAdapter mThreadAdapter;

    private ViewPager mViewPager;
    private PagerTitleStrip mPagerTitleStrip;
    private LinearLayout replyContainer = null;
    private EditText replyMessage = null;
    private View mReplyBtn;

    private int threadId;
    private String threadName;

    private ArrayList<Integer> postList = new ArrayList<Integer>(); // 所有回复列表
    private int lastpage, replies; // 当前帖子总页数，总回复数
    private int currentpage = 0; // 当前所在页数

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_thread);

        Intent intent = getIntent();
        threadId = intent.getIntExtra(CommonIntents.EXTRA_TID, 0);
        threadName = intent.getStringExtra(CommonIntents.EXTRA_THREAD_NAME);
        replies = Integer.parseInt(intent.getStringExtra(CommonIntents.EXTRA_REPIES)) + 1;

        if (savedInstanceState != null && !savedInstanceState.isEmpty()) {
            threadId = savedInstanceState.getInt("tid");
            threadName = savedInstanceState.getString("subject");
            replies = savedInstanceState.getInt("replies");
        }

        if (replies % BUAppUtils.POSTS_PER_PAGE == 0)
            lastpage = replies / BUAppUtils.POSTS_PER_PAGE - 1;
        else
            lastpage = replies / BUAppUtils.POSTS_PER_PAGE;
        Log.v("ThreadActivity", "lastpage>>>>>" + lastpage);

        // Setup the action bar.
        getSupportActionBar().setTitle(threadName);
        getSupportActionBar().setDisplayShowHomeEnabled(false);

        // Get reply View container and hide for current
        replyContainer = (LinearLayout) findViewById(R.id.reply_layout);
        replyContainer.setVisibility(View.GONE);
        replyMessage = (EditText) replyContainer.findViewById(R.id.reply_message);
        // Button calls reply window to front
        ImageButton replySubmit = (ImageButton) replyContainer.findViewById(R.id.reply_submit);
        replySubmit.setOnClickListener(new MyReplySubmitListener());
        mReplyBtn = findViewById(R.id.imgVw_reply_btn);
        mReplyBtn.setOnClickListener(this);
        ImageButton advreply = (ImageButton) replyContainer.findViewById(R.id.reply_advanced);
        advreply.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ThreadActivity.this, NewthreadActivity.class);
                intent.putExtra(CommonIntents.EXTRA_ACTION, NewthreadActivity.ACTION_NEW_POST);
                intent.putExtra(CommonIntents.EXTRA_TID, threadId);
                intent.putExtra(CommonIntents.EXTRA_MESSAGE, replyMessage.getText().toString());
                startActivity(intent);

            }
        });

        postList.add(0);
        if (postList.size() <= lastpage)
            postList.add(0);

        // Create the adapter that will return a fragment for each of the three
        // primary sections of the application.
        mThreadAdapter = new ThreadPagerAdapter(getSupportFragmentManager());

        // Setup the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.viewpager_thread);
        mPagerTitleStrip = (PagerTitleStrip) findViewById(R.id.pager_title_strip_thread);
        mPagerTitleStrip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        mViewPager.setAdapter(mThreadAdapter);
        mViewPager.setOnPageChangeListener(new MyOnPageChangeListener());
        mViewPager.setOnTouchListener(new ScrollListener());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // TODO Data needs to be stored
        outState.putInt("tid", threadId);
        outState.putString("subject", threadName);
        outState.putInt("replies", replies);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.thread, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.action_refresh:
                break;
            case R.id.action_sharelink:
                StringBuilder sb = new StringBuilder(threadName);
                sb.append('\n');
                sb.append(BUApiHelper.getRootUrl());
                sb.append("/viewthread.php?tid=");
                sb.append(threadId);

                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData.Item cliptext = new ClipData.Item(sb.toString());
                String[] mime = new String[1];
                mime[0] = "text/plain";
                ClipData clip = new ClipData("帖子链接", mime, cliptext);
                clipboard.setPrimaryClip(clip);
                showToast(R.string.thread_url_copied);
                return true;
        }
        return false;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.imgVw_reply_btn:
                mReplyBtn.setEnabled(false);
                break;
        }
    }

    public void setQuoteText(BUPost quotePost) {
        if (!replyContainer.isShown())
            replyContainer.setVisibility(View.VISIBLE);
        replyMessage.setText(replyMessage.getText().toString() + quotePost.toQuote());
        replyMessage.setSelection(replyMessage.getText().toString().length());	// 设置光标到文本末尾
    }

    public void displayUserInfo(int uid) {
        UserInfoDialogFragment infoDialog = new UserInfoDialogFragment();
        Bundle args = new Bundle();
        args.putInt("uid", uid);
        infoDialog.setArguments(args);
        infoDialog.show(getSupportFragmentManager(), "Userinfo-" + uid);
    }

    @Override
    public void onBackPressed() {
        if (replyContainer.getVisibility() == View.VISIBLE) {
            replyContainer.setVisibility(View.GONE);
            return;
        } else
            super.onBackPressed();
    }

    /**
     * A {@link FragmentStatePagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class ThreadPagerAdapter extends FragmentStatePagerAdapter {

        private SparseArray<ThreadFragment> registeredFragments = new SparseArray<ThreadFragment>();

        public ThreadPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            ThreadFragment frag = registeredFragments.get(position);
            if (frag == null) {
                frag = new ThreadFragment();
                Bundle args = new Bundle();
                args.putInt(ThreadFragment.ARG_THREAD_ID, threadId);
                args.putInt(ThreadFragment.ARG_PAGE_NUMBER, position);
                frag.setArguments(args);
                registeredFragments.put(position, frag);
                Log.d("PagerAdapter", "Fragment create>>" + position);
            } else if (!frag.isUpdating()){
                frag.onRefresh();
            }
            return frag;
        }

        @Override
        public int getCount() {
            return postList.size();
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return Integer.toString(position + 1);
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            ThreadFragment fragment = (ThreadFragment) super.instantiateItem(
                    container, position);
            registeredFragments.put(position, fragment);
            return fragment;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
			registeredFragments.remove(position);
            super.destroyItem(container, position, object);
        }

    }

    /**
     * Listener to swipe activity. Double one-direction swiping will kill
     * current activity thus lead to upper level of this application.
     */
    private class ScrollListener extends GestureDetector.SimpleOnGestureListener implements View.OnTouchListener {
        private long lastswipetime = 0;
        private int TRIGGER = 50;

        private ScrollListener() {
            TRIGGER = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24f,
                    BUApplication.getInstance().getResources().getDisplayMetrics());
        }
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (distanceX > TRIGGER && currentpage == 0) {
                if ((System.currentTimeMillis() - lastswipetime) >= BUAppUtils.EXIT_WAIT_TIME) {
                    showToast("再次右滑返回");
                    lastswipetime = System.currentTimeMillis();
                } else
                    finish();
            }
            return false;
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            return onTouchEvent(event);
        }
    }

    /**
     * Listen to the {@link ViewPager}, pre-load pages will be useful next to
     * current view.
     */
    private class MyOnPageChangeListener implements OnPageChangeListener {

        @Override
        public void onPageScrollStateChanged(int state) {
        }

        @Override
        public void onPageScrolled(int pos, float per, int arg2) {
        }

        @Override
        public void onPageSelected(int pos) {
            currentpage = pos;
            while (pos > postList.size() - 2 && postList.size() <= lastpage)
                postList.add(0);
            mThreadAdapter.notifyDataSetChanged();
        }
    }

    private class MyReplySubmitListener implements OnClickListener {

        @Override
        public void onClick(View v) {
            final String message = replyMessage.getText().toString();
            if (message != null && !message.isEmpty()) {
                showToast(R.string.message_sending);
                final DialogInterface.OnClickListener clickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == DialogInterface.BUTTON_POSITIVE) {
                            Log.i("MyReplySubmitListener", "Reply submitted>>" + message);
                            StringBuilder finalMsg = new StringBuilder(message);
                            if (BUApplication.settings.showsigature)
                                finalMsg.append(getString(R.string.buapp_client_postfix));
                            BUApiHelper.postNewPost(threadId, finalMsg.toString(), new Response.Listener<JSONObject>() {
                                @Override
                                public void onResponse(JSONObject jsonObject) {
                                    if (BUApiHelper.getResult(jsonObject) == Result.SUCCESS) {
                                        replyMessage.setText("");
                                    } else {
                                        // TODO need to handle error
                                    }
                                }
                            }, new Response.ErrorListener() {
                                @Override
                                public void onErrorResponse(VolleyError volleyError) {
                                    showToast(R.string.network_unknown);
                                }
                            });
                        } else {

                        }
                    }
                };
                new AlertDialog.Builder(ThreadActivity.this)
                        .setTitle(R.string.send_message_title)
                        .setMessage(R.string.send_message_message)
                        .setPositiveButton(R.string.dialog_button_confirm, clickListener)
                        .setNegativeButton(R.string.dialog_button_cancel, clickListener)
                        .create().show();
            } else
                showToast("回复不能为空");
        }
    }

    private Toast toast = null;
    private void showToast(String text) {
        if (toast != null)
            toast.cancel();
        toast = Toast.makeText(ThreadActivity.this, text, Toast.LENGTH_SHORT);
        toast.show();
    }

    private void showToast(int resId) {
        showToast(getString(resId));
    }

}
