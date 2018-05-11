package org.briarproject.briar.android.splash;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.preference.PreferenceManager;
import android.transition.Fade;

import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.bramble.util.AndroidUtils;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BaseActivity;
import org.briarproject.briar.android.controller.ConfigController;
import org.briarproject.briar.android.login.OpenDatabaseActivity;
import org.briarproject.briar.android.login.SetupActivity;

import java.util.logging.Logger;

import javax.inject.Inject;

import static org.briarproject.briar.android.BriarApplication.EXPIRY_DATE;

public class SplashScreenActivity extends BaseActivity {

	private static final Logger LOG =
			Logger.getLogger(SplashScreenActivity.class.getName());

	@Inject
	protected ConfigController configController;
	@Inject
	protected AndroidExecutor androidExecutor;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		if (Build.VERSION.SDK_INT >= 21) {
			getWindow().setExitTransition(new Fade());
		}

		setPreferencesDefaults();

		setContentView(R.layout.splash);

		if (configController.accountSignedIn()) {
			LOG.info("Already signed in, not showing splash screen");
			startActivity(new Intent(this, OpenDatabaseActivity.class));
			finish();
		} else {
			LOG.info("Showing splash screen");
			new Handler().postDelayed(() -> {
				startNextActivity();
				supportFinishAfterTransition();
			}, 500);
		}
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	protected void startNextActivity() {
		if (System.currentTimeMillis() >= EXPIRY_DATE) {
			LOG.info("Expired");
			startActivity(new Intent(this, ExpiredActivity.class));
		} else {
			AndroidUtils.logDataDirContents(this);
			if (configController.accountExists()) {
				LOG.info("Account exists");
				startActivity(new Intent(this, OpenDatabaseActivity.class));
			} else {
				LOG.info("Account does not exist");
				configController.deleteAccount(this);
				startActivity(new Intent(this, SetupActivity.class));
			}
		}
	}

	private void setPreferencesDefaults() {
		androidExecutor.runOnBackgroundThread(() -> {
 			PreferenceManager.setDefaultValues(SplashScreenActivity.this,
					R.xml.panic_preferences, false);
			LOG.info("Finished setting panic preference defaults");
		});
	}
}
