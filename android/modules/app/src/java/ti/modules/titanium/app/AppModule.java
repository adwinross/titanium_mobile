/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2013 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.app;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollModule;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.KrollRuntime;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.ITiAppInfo;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiContext;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiPlatformHelper;
import org.appcelerator.titanium.util.TiSensorHelper;

import android.app.Application;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.provider.Settings;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityManagerCompat;
import android.support.v4.view.accessibility.AccessibilityManagerCompat.AccessibilityStateChangeListenerCompat;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

@Kroll.module
public class AppModule extends KrollModule implements SensorEventListener
{
	private static final String TAG = "AppModule";

	@Kroll.constant public static final String EVENT_ACCESSIBILITY_ANNOUNCEMENT = "accessibilityannouncement";
	@Kroll.constant public static final String EVENT_ACCESSIBILITY_CHANGED = "accessibilitychanged";

	private ITiAppInfo appInfo;
	private AccessibilityStateChangeListenerCompat accessibilityStateChangeListener = null;
	private boolean proximitySensorRegistered = false;
	private boolean proximityDetection = false;
	private boolean proximityState;
	private int proximityEventListenerCount = 0;

	public AppModule()
	{
		super("App");

		TiApplication.getInstance().addAppEventProxy(this);
		appInfo = TiApplication.getInstance().getAppInfo();
	}

	public AppModule(TiContext tiContext)
	{
		this();
	}

	public void onDestroy() {
		TiApplication.getInstance().removeAppEventProxy(this);
	}

	@Kroll.getProperty @Kroll.method
	public String getId() {
		return appInfo.getId();
	}

	@Kroll.method
	public String getID() {
		return getId();
	}

	@Kroll.getProperty @Kroll.method
	public String getName() {
		return appInfo.getName();
	}

	@Kroll.getProperty @Kroll.method
	public String getVersion() {
		return appInfo.getVersion();
	}

	@Kroll.getProperty @Kroll.method
	public String getPublisher() {
		return appInfo.getPublisher();
	}

	@Kroll.getProperty @Kroll.method
	public String getUrl() {
		return appInfo.getUrl();
	}

	@Kroll.method
	public String getURL() {
		return getUrl();
	}

	@Kroll.getProperty @Kroll.method
	public String getDescription() {
		return appInfo.getDescription();
	}

	@Kroll.getProperty @Kroll.method
	public String getCopyright() {
		return appInfo.getCopyright();
	}

	@Kroll.getProperty @Kroll.method
	public String getGuid() {
		return appInfo.getGUID();
	}

	@Kroll.method
	public String getGUID() {
		return getGuid();
	}
	
	@Kroll.getProperty @Kroll.method
	public String getDeployType() {
		return TiApplication.getInstance().getDeployType();
	}
	
	@Kroll.getProperty @Kroll.method
	public String getSessionId() {
		return TiPlatformHelper.getInstance().getSessionId();
	}
	
	@Kroll.getProperty @Kroll.method
	public boolean getAnalytics() {
		return appInfo.isAnalyticsEnabled();
	}
	
	@Kroll.method
	public String appURLToPath(String url) {
		return resolveUrl(null, url);
	}

	@Kroll.method @Kroll.getProperty
	public boolean getAccessibilityEnabled()
	{
		AccessibilityManager manager = TiApplication.getInstance().getAccessibilityManager();
		boolean enabled = manager.isEnabled();

		if (!enabled && Build.VERSION.SDK_INT < TiC.API_LEVEL_HONEYCOMB) {
			// Prior to Honeycomb, AccessibilityManager.isEnabled() would sometimes
			// return false erroneously the because manager service would asynchronously set the
			// enabled property in the manager client. So when checking the value, it
			// might not have been set yet. In studying the changes they made for
			// Honeycomb, we can see that they do the following in order to determine
			// if accessibility really is enabled or not:
			enabled = Settings.Secure.getInt(TiApplication.getInstance().getContentResolver(),
				Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1;
		}

		return enabled;
	}

	@Kroll.method(name = "_restart")
	public void restart()
	{
		Application app = (Application) KrollRuntime.getInstance().getKrollApplication();
		Intent i = app.getPackageManager().getLaunchIntentForPackage(app.getPackageName());
		i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		i.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
		i.addCategory(Intent.CATEGORY_LAUNCHER);
		i.setAction(Intent.ACTION_MAIN);
		app.startActivity(i);
	}

	@Kroll.method
	public void fireSystemEvent(String eventName, @Kroll.argument(optional = true) Object arg)
	{
		if (eventName.equals(EVENT_ACCESSIBILITY_ANNOUNCEMENT)) {

			if (!getAccessibilityEnabled()) {
				Log.w(TAG, "Accessibility announcement ignored. Accessibility services are not enabled on this device.");
				return;
			}

			if (arg == null) {
				Log.w(TAG, "Accessibility announcement ignored. No announcement text was provided.");
				return;
			}

			AccessibilityManager accessibilityManager = TiApplication.getInstance().getAccessibilityManager();
			AccessibilityEvent event = AccessibilityEvent.obtain(AccessibilityEventCompat.TYPE_ANNOUNCEMENT);
			event.setEnabled(true);
			event.getText().clear();
			event.getText().add(TiConvert.toString(arg));
			accessibilityManager.sendAccessibilityEvent(event);

		} else {
			Log.w(TAG, "Unknown system event: " + eventName);
		}
	}

	@Override
	public void onHasListenersChanged(String event, boolean hasListeners)
	{
		super.onHasListenersChanged(event, hasListeners);

		// If listening for "accessibilitychanged", we need to register
		// our own listener with the system.
		if (!hasListeners && accessibilityStateChangeListener != null) {
			AccessibilityManagerCompat.removeAccessibilityStateChangeListener(TiApplication.getInstance().getAccessibilityManager(), accessibilityStateChangeListener);
			accessibilityStateChangeListener = null;
		} else if (hasListeners && accessibilityStateChangeListener == null) {
			accessibilityStateChangeListener = new AccessibilityStateChangeListenerCompat() {

				@Override
				public void onAccessibilityStateChanged(boolean enabled)
				{
					KrollDict data = new KrollDict();
					data.put(TiC.PROPERTY_ENABLED, enabled);
					fireEvent(EVENT_ACCESSIBILITY_CHANGED, data);
				}
			};

			AccessibilityManagerCompat.addAccessibilityStateChangeListener(TiApplication.getInstance().getAccessibilityManager(), accessibilityStateChangeListener);
		}
	}
	
	@Kroll.getProperty @Kroll.method
	public boolean getProximityDetection()
	{
		return proximityDetection;
	}

	@Kroll.setProperty @Kroll.method
	public void setProximityDetection(Object value)
	{
		proximityDetection = TiConvert.toBoolean(value);
		if (proximityDetection) {
			if (proximityEventListenerCount > 0) {
				registerProximityListener();
			}
		} else {
			unRegisterProximityListener();
		}
	}

	@Kroll.getProperty @Kroll.method
	public boolean getProximityState()
	{
		return proximityState;
	}

	/**
	 * @see org.appcelerator.kroll.KrollProxy#eventListenerAdded(java.lang.String, int,
	 *      org.appcelerator.kroll.KrollProxy)
	 */
	@Override
	public void eventListenerAdded(String type, int count, final KrollProxy proxy)
	{
		proximityEventListenerCount++;
		if (proximityDetection && TiC.EVENT_PROXIMITY.equals(type)) {
			registerProximityListener();
		}
		super.eventListenerAdded(type, count, proxy);
	}

	/**
	 * @see org.appcelerator.kroll.KrollProxy#eventListenerRemoved(java.lang.String, int,
	 *      org.appcelerator.kroll.KrollProxy)
	 */
	@Override
	protected void eventListenerRemoved(String event, int count, KrollProxy proxy)
	{
		proximityEventListenerCount--;
		if (TiC.EVENT_PROXIMITY.equals(event)) {
			unRegisterProximityListener();
		}

		super.eventListenerRemoved(event, count, proxy);
	}

	private void registerProximityListener()
	{
		if (!proximitySensorRegistered) {
			TiSensorHelper.registerListener(Sensor.TYPE_PROXIMITY, this, SensorManager.SENSOR_DELAY_NORMAL);
			proximitySensorRegistered = true;
		}
	}

	private void unRegisterProximityListener()
	{
		if (proximitySensorRegistered) {
			TiSensorHelper.unregisterListener(Sensor.TYPE_PROXIMITY, this);
			proximitySensorRegistered = false;
		}
	}

	@Override
	public void onAccuracyChanged(Sensor arg0, int arg1)
	{
		// intentionally blank
	}

	@Override
	public void onSensorChanged(SensorEvent event)
	{
		proximityState = false;
		float distance = event.values[0];
		if (distance < event.sensor.getMaximumRange()) {
			proximityState = true;
		}
		KrollDict data = new KrollDict();
		data.put(TiC.EVENT_PROPERTY_TYPE, TiC.EVENT_PROXIMITY);
		data.put(TiC.EVENT_PROPERTY_STATE, proximityState);
		fireEvent(TiC.EVENT_PROXIMITY, data);
	}

	@Override
	public String getApiName()
	{
		return "Ti.App";
	}

}
