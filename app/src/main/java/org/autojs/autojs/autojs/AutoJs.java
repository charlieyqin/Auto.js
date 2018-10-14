package org.autojs.autojs.autojs;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Looper;
import android.support.v4.content.LocalBroadcastManager;

import com.stardust.app.GlobalAppContext;
import com.stardust.autojs.core.console.GlobalStardustConsole;
import com.stardust.autojs.runtime.ScriptRuntime;
import com.stardust.autojs.runtime.accessibility.AccessibilityConfig;
import com.stardust.autojs.runtime.exception.ScriptException;
import com.stardust.autojs.runtime.api.Console;
import com.stardust.autojs.runtime.exception.ScriptInterruptedException;

import org.autojs.autojs.App;
import org.autojs.autojs.BuildConfig;
import org.autojs.autojs.Pref;
import org.autojs.autojs.R;
import org.autojs.autojs.pluginclient.DevPluginService;
import org.autojs.autojs.ui.floating.FloatyWindowManger;
import org.autojs.autojs.ui.floating.FullScreenFloatyWindow;
import org.autojs.autojs.ui.floating.layoutinspector.LayoutBoundsFloatyWindow;
import org.autojs.autojs.ui.floating.layoutinspector.LayoutHierarchyFloatyWindow;
import org.autojs.autojs.ui.log.LogActivity_;
import org.autojs.autojs.ui.settings.SettingsActivity;
import org.autojs.autojs.ui.settings.SettingsActivity_;

import com.stardust.concurrent.VolatileBox;
import com.stardust.view.accessibility.AccessibilityService;
import com.stardust.view.accessibility.LayoutInspector;
import com.stardust.view.accessibility.NodeInfo;

import org.autojs.autojs.tool.AccessibilityServiceTool;


/**
 * Created by Stardust on 2017/4/2.
 */

public class AutoJs extends com.stardust.autojs.AutoJs {

    private static AutoJs instance;

    public static AutoJs getInstance() {
        return instance;
    }


    public static void initInstance(Application application) {
        instance = new AutoJs(application);
    }

    private interface LayoutInspectFloatyWindow {
        FullScreenFloatyWindow create(NodeInfo nodeInfo);
    }

    private BroadcastReceiver mLayoutInspectBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ensureAccessibilityServiceEnabled();
            String action = intent.getAction();
            if (LayoutBoundsFloatyWindow.class.getName().equals(action)) {
                capture(LayoutBoundsFloatyWindow::new);
            } else if (LayoutHierarchyFloatyWindow.class.getName().equals(action)) {
                capture(LayoutHierarchyFloatyWindow::new);
            }
        }
    };

    private AutoJs(final Application application) {
        super(application);
        getScriptEngineService().registerGlobalScriptExecutionListener(new ScriptExecutionGlobalListener());
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(LayoutBoundsFloatyWindow.class.getName());
        intentFilter.addAction(LayoutHierarchyFloatyWindow.class.getName());
        LocalBroadcastManager.getInstance(application).registerReceiver(mLayoutInspectBroadcastReceiver, intentFilter);
    }

    private void capture(LayoutInspectFloatyWindow window) {
        LayoutInspector inspector = getLayoutInspector();
        LayoutInspector.CaptureAvailableListener listener = new LayoutInspector.CaptureAvailableListener() {
            @Override
            public void onCaptureAvailable(NodeInfo capture) {
                inspector.removeCaptureAvailableListener(this);
                getUiHandler().post(() ->
                        FloatyWindowManger.addWindow(getApplication().getApplicationContext(), window.create(capture))
                );
            }
        };
        inspector.addCaptureAvailableListener(listener);
        if (!inspector.captureCurrentWindow()) {
            inspector.removeCaptureAvailableListener(listener);
        }
    }

    @Override
    protected GlobalStardustConsole createGlobalConsole() {
        return new GlobalStardustConsole(getUiHandler()) {
            @Override
            public String println(int level, CharSequence charSequence) {
                String log = super.println(level, charSequence);
                DevPluginService.getInstance().log(log);
                return log;
            }
        };
    }

    public void ensureAccessibilityServiceEnabled() {
        if (AccessibilityService.getInstance() != null) {
            return;
        }
        String errorMessage = null;
        if (AccessibilityServiceTool.isAccessibilityServiceEnabled(GlobalAppContext.get())) {
            errorMessage = GlobalAppContext.getString(R.string.text_auto_operate_service_enabled_but_not_running);
        } else {
            if (Pref.shouldEnableAccessibilityServiceByRoot()) {
                if (!AccessibilityServiceTool.enableAccessibilityServiceByRootAndWaitFor(2000)) {
                    errorMessage = GlobalAppContext.getString(R.string.text_enable_accessibility_service_by_root_timeout);
                }
            } else {
                errorMessage = GlobalAppContext.getString(R.string.text_no_accessibility_permission);
            }
        }
        if (errorMessage != null) {
            AccessibilityServiceTool.goToAccessibilitySetting();
            throw new ScriptException(errorMessage);
        }
    }

    @Override
    public void waitForAccessibilityServiceEnabled() {
        if (AccessibilityService.getInstance() != null) {
            return;
        }
        String errorMessage = null;
        if (AccessibilityServiceTool.isAccessibilityServiceEnabled(GlobalAppContext.get())) {
            errorMessage = GlobalAppContext.getString(R.string.text_auto_operate_service_enabled_but_not_running);
        } else {
            if (Pref.shouldEnableAccessibilityServiceByRoot()) {
                if (!AccessibilityServiceTool.enableAccessibilityServiceByRootAndWaitFor(2000)) {
                    errorMessage = GlobalAppContext.getString(R.string.text_enable_accessibility_service_by_root_timeout);
                }
            } else {
                errorMessage = GlobalAppContext.getString(R.string.text_no_accessibility_permission);
            }
        }
        if (errorMessage != null) {
            AccessibilityServiceTool.goToAccessibilitySetting();
            if (!org.autojs.autojs.accessibility.AccessibilityService.waitForEnabled(-1)) {
                throw new ScriptInterruptedException();
            }
        }
    }

    @Override
    protected AccessibilityConfig createAccessibilityConfig() {
        AccessibilityConfig config = super.createAccessibilityConfig();
        if (BuildConfig.CHANNEL.equals("coolapk")) {
            config.addWhiteList("com.coolapk.market");
        }
        return config;
    }

    @Override
    protected ScriptRuntime createRuntime() {
        ScriptRuntime runtime = super.createRuntime();
        runtime.putProperty("class.settings", SettingsActivity_.class);
        runtime.putProperty("class.console", LogActivity_.class);
        runtime.putProperty("broadcast.inspect_layout_bounds", LayoutBoundsFloatyWindow.class.getName());
        runtime.putProperty("broadcast.inspect_layout_hierarchy", LayoutHierarchyFloatyWindow.class.getName());
        return runtime;
    }

}
