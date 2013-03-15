package net.sf.briar.android.groups;

import static android.view.Gravity.CENTER_VERTICAL;
import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import net.sf.briar.R;
import net.sf.briar.android.BriarActivity;
import net.sf.briar.android.BriarService;
import net.sf.briar.android.BriarService.BriarServiceConnection;
import net.sf.briar.android.widgets.CommonLayoutParams;
import net.sf.briar.android.widgets.HorizontalSpace;
import net.sf.briar.api.android.BundleEncrypter;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DatabaseExecutor;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.messaging.Group;
import net.sf.briar.api.messaging.GroupId;
import net.sf.briar.api.messaging.Message;
import net.sf.briar.api.messaging.MessageFactory;
import net.sf.briar.api.messaging.MessageId;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.inject.Inject;

public class WriteGroupMessageActivity extends BriarActivity
implements OnClickListener, OnItemSelectedListener {

	private static final Logger LOG =
			Logger.getLogger(WriteGroupMessageActivity.class.getName());

	private final BriarServiceConnection serviceConnection =
			new BriarServiceConnection();

	@Inject private BundleEncrypter bundleEncrypter;
	private GroupNameSpinnerAdapter adapter = null;
	private Spinner spinner = null;
	private ImageButton sendButton = null;
	private EditText content = null;

	// Fields that are accessed from DB threads must be volatile
	@Inject private volatile DatabaseComponent db;
	@Inject @DatabaseExecutor private volatile Executor dbExecutor;
	@Inject private volatile MessageFactory messageFactory;
	private volatile Group group = null;
	private volatile GroupId groupId = null;
	private volatile MessageId parentId = null;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(null);

		Intent i = getIntent();
		byte[] id = i.getByteArrayExtra("net.sf.briar.GROUP_ID");
		if(id != null) groupId = new GroupId(id);
		id = i.getByteArrayExtra("net.sf.briar.PARENT_ID");
		if(id != null) parentId = new MessageId(id);

		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(CommonLayoutParams.MATCH_WRAP);
		layout.setOrientation(VERTICAL);

		LinearLayout actionBar = new LinearLayout(this);
		actionBar.setLayoutParams(CommonLayoutParams.MATCH_WRAP);
		actionBar.setOrientation(HORIZONTAL);
		actionBar.setGravity(CENTER_VERTICAL);

		TextView to = new TextView(this);
		to.setTextSize(18);
		to.setPadding(10, 10, 10, 10);
		to.setText(R.string.to);
		actionBar.addView(to);

		adapter = new GroupNameSpinnerAdapter(this);
		spinner = new Spinner(this);
		spinner.setAdapter(adapter);
		spinner.setOnItemSelectedListener(this);
		loadGroupList();
		actionBar.addView(spinner);

		actionBar.addView(new HorizontalSpace(this));

		sendButton = new ImageButton(this);
		sendButton.setBackgroundResource(0);
		sendButton.setImageResource(R.drawable.social_send_now);
		sendButton.setEnabled(false);
		sendButton.setOnClickListener(this);
		actionBar.addView(sendButton);
		layout.addView(actionBar);

		content = new EditText(this);
		content.setPadding(10, 10, 10, 10);
		if(state != null && bundleEncrypter.decrypt(state)) {
			Parcelable p = state.getParcelable("net.sf.briar.CONTENT");
			if(p != null) content.onRestoreInstanceState(p);
		}
		layout.addView(content);

		setContentView(layout);

		// Bind to the service so we can wait for the DB to be opened
		bindService(new Intent(BriarService.class.getName()),
				serviceConnection, 0);
	}

	private void loadGroupList() {
		dbExecutor.execute(new Runnable() {
			public void run() {
				try {
					serviceConnection.waitForStartup();
					updateGroupList(db.getSubscriptions());
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				} catch(InterruptedException e) {
					LOG.info("Interrupted while waiting for service");
					Thread.currentThread().interrupt();
				}
			}
		});
	}

	private void updateGroupList(final Collection<Group> groups) {
		runOnUiThread(new Runnable() {
			public void run() {
				for(Group g : groups) {
					if(g.getId().equals(groupId)) {
						group = g;
						spinner.setSelection(adapter.getCount());
					}
					adapter.add(g);
				}
			}
		});
	}

	@Override
	public void onSaveInstanceState(Bundle state) {
		Parcelable p = content.onSaveInstanceState();
		state.putParcelable("net.sf.briar.CONTENT", p);
		bundleEncrypter.encrypt(state);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		unbindService(serviceConnection);
	}

	public void onClick(View view) {
		if(group == null) throw new IllegalStateException();
		try {
			storeMessage(content.getText().toString().getBytes("UTF-8"));
		} catch(UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
		finish();
	}

	private void storeMessage(final byte[] body) {
		dbExecutor.execute(new Runnable() {
			public void run() {
				try {
					serviceConnection.waitForStartup();
					Message m = messageFactory.createAnonymousMessage(parentId,
							group, "text/plain", body);
					db.addLocalGroupMessage(m);
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				} catch(GeneralSecurityException e) {
					throw new RuntimeException(e);
				} catch(InterruptedException e) {
					if(LOG.isLoggable(INFO))
						LOG.info("Interrupted while waiting for service");
					Thread.currentThread().interrupt();
				} catch(IOException e) {
					throw new RuntimeException(e);
				}
			}
		});
	}

	public void onItemSelected(AdapterView<?> parent, View view, int position,
			long id) {
		group = adapter.getItem(position);
		groupId = group.getId();
		sendButton.setEnabled(true);
	}

	public void onNothingSelected(AdapterView<?> parent) {
		group = null;
		groupId = null;
		sendButton.setEnabled(false);
	}
}