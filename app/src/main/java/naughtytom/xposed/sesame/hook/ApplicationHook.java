package naughtytom.xposed.sesame.hook;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import androidx.annotation.NonNull;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import naughtytom.xposed.sesame.BuildConfig;
import naughtytom.xposed.sesame.data.Config;
import naughtytom.xposed.sesame.data.RunType;
import naughtytom.xposed.sesame.data.ViewAppInfo;
import naughtytom.xposed.sesame.entity.AlipayVersion;
import naughtytom.xposed.sesame.entity.FriendWatch;
import naughtytom.xposed.sesame.hook.rpc.bridge.NewRpcBridge;
import naughtytom.xposed.sesame.hook.rpc.bridge.OldRpcBridge;
import naughtytom.xposed.sesame.hook.rpc.bridge.RpcBridge;
import naughtytom.xposed.sesame.hook.rpc.bridge.RpcVersion;
import naughtytom.xposed.sesame.hook.rpc.debug.DebugRpc;
import naughtytom.xposed.sesame.hook.rpc.intervallimit.RpcIntervalLimit;
import naughtytom.xposed.sesame.model.BaseModel;
import naughtytom.xposed.sesame.model.Model;
import naughtytom.xposed.sesame.task.BaseTask;
import naughtytom.xposed.sesame.task.ModelTask;
import naughtytom.xposed.sesame.task.TaskCommon;
import naughtytom.xposed.sesame.task.antMember.AntMemberRpcCall;
import naughtytom.xposed.sesame.util.General;
import naughtytom.xposed.sesame.util.Log;
import naughtytom.xposed.sesame.util.Maps.UserMap;
import naughtytom.xposed.sesame.util.Notify;
import naughtytom.xposed.sesame.util.PermissionUtil;
import naughtytom.xposed.sesame.util.StatisticsUtil;
import naughtytom.xposed.sesame.util.StatusUtil;
import naughtytom.xposed.sesame.util.StringUtil;
import naughtytom.xposed.sesame.util.TimeUtil;
import lombok.Getter;
public class ApplicationHook implements IXposedHookLoadPackage {
    static final String TAG = ApplicationHook.class.getSimpleName();
    @Getter
    private static final String modelVersion = BuildConfig.VERSION_NAME;
    private static final Map<Object, Object[]> rpcHookMap = new ConcurrentHashMap<>();
    private static final Map<String, PendingIntent> wakenAtTimeAlarmMap = new ConcurrentHashMap<>();
    @Getter
    private static ClassLoader classLoader = null;
    @Getter
    private static Object microApplicationContextObject = null;
    @Getter
    @SuppressLint("StaticFieldLeak")
    static Context context = null;
    @Getter
    static AlipayVersion alipayVersion = new AlipayVersion("");
    @Getter
    private static volatile boolean hooked = false;
    static volatile boolean init = false;
    static volatile Calendar dayCalendar;
    @Getter
    static LocalDate dayDate;
    @Getter
    static volatile boolean offline = false;
    @Getter
    static final AtomicInteger reLoginCount = new AtomicInteger(0);
    @SuppressLint("StaticFieldLeak")
    static Service service;
    @Getter
    static Handler mainHandler;
    static BaseTask mainTask;
    static RpcBridge rpcBridge;
    @Getter
    private static RpcVersion rpcVersion;
    private static PowerManager.WakeLock wakeLock;
    private static PendingIntent alarm0Pi;
    private static XC_MethodHook.Unhook rpcRequestUnhook;
    private static XC_MethodHook.Unhook rpcResponseUnhook;
    public static void setOffline(boolean offline) {
        ApplicationHook.offline = offline;
    }
    private volatile long lastExecTime = 0; // 添加为类成员变量
    /**
     * 执行检查方法
     *
     * @return true表示检查失败，false表示检查成功
     */
    private boolean executeCheckTask(long lastExecTime) {
        try {
            FutureTask<Boolean> checkTask = new FutureTask<>(AntMemberRpcCall::check);
            Thread checkThread = new Thread(checkTask);
            checkThread.start();
            if (!checkTask.get(10, TimeUnit.SECONDS)) {
                long waitTime = 10000 - System.currentTimeMillis() + lastExecTime;
                if (waitTime > 0) {
                    Thread.sleep(waitTime);
                }
                Log.record("执行失败：检查超时");
                return true;
            }
            reLoginCount.set(0);
            return false;
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            Log.record("执行失败：检查中断");
            return false;
        } catch (Exception e) {
            Log.record("执行失败：检查异常");
            Log.printStackTrace(TAG, e);
            return false;
        }
    }
    /**
     * 调度定时执行
     *
     * @param lastExecTime 上次执行时间
     */
    private void scheduleNextExecution(long lastExecTime) {
        try {
            int checkInterval = BaseModel.getCheckInterval().getValue();
            List<String> execAtTimeList = BaseModel.getExecAtTimeList().getValue();
            try {
                if (execAtTimeList != null) {
                    Calendar lastExecTimeCalendar = TimeUtil.getCalendarByTimeMillis(lastExecTime);
                    Calendar nextExecTimeCalendar = TimeUtil.getCalendarByTimeMillis(lastExecTime + checkInterval);
                    for (String execAtTime : execAtTimeList) {
                        Calendar execAtTimeCalendar = TimeUtil.getTodayCalendarByTimeStr(execAtTime);
                        if (execAtTimeCalendar != null && lastExecTimeCalendar.compareTo(execAtTimeCalendar) < 0 && nextExecTimeCalendar.compareTo(execAtTimeCalendar) > 0) {
                            Log.record("设置定时执行:" + execAtTime);
                            execDelayedHandler(execAtTimeCalendar.getTimeInMillis() - lastExecTime);
                            return;
                        }
                    }
                }
            } catch (Exception e) {
                Log.runtime("execAtTime err:：" + e.getMessage());
                Log.printStackTrace(TAG, e);
            }
            execDelayedHandler(checkInterval);
        } catch (Exception e) {
            Log.runtime(TAG, "scheduleNextExecution：" + e.getMessage());
            Log.printStackTrace(TAG, e);
        }
    }
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if ("naughtytom.xposed.sesame".equals(lpparam.packageName)) {
            try {
                XposedHelpers.callStaticMethod(lpparam.classLoader.loadClass(ViewAppInfo.class.getName()), "setRunTypeByCode", RunType.MODEL.getCode());
            } catch (ClassNotFoundException e) {
                Log.printStackTrace(e);
            }
        } else if (General.PACKAGE_NAME.equals(lpparam.packageName) && General.PACKAGE_NAME.equals(lpparam.processName)) {
            if (hooked) return;
            classLoader = lpparam.classLoader;
            //hook Application类的attach方法
            try {
                // 使用Xposed框架hook Application类的attach方法
                // attach方法是在应用程序启动时，由Android系统调用，用于将应用程序与上下文环境关联起来
                XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class,
                        new XC_MethodHook() {
                            // 重写afterHookedMethod方法，在attach方法执行后执行自定义逻辑
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                // 获取attach方法的第一个参数，即Context对象，并赋值给context变量
                                context = (Context) param.args[0];
                                try {
                                    // 通过Context对象获取支付宝应用的版本信息
                                    // context.getPackageManager().getPackageInfo(context.getPackageName(), 0)用于获取当前应用的包信息
                                    // versionName属性表示应用的版本名称
                                    alipayVersion = new AlipayVersion(context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName);
                                } catch (Exception e) {
                                    // 如果在获取支付宝版本信息时出现异常，记录错误日志
                                    Log.runtime(TAG, "获取支付宝版本信息失败");
                                    Log.printStackTrace(e);
                                }
                                // 调用父类的afterHookedMethod方法，执行一些默认的逻辑（如果有）
                                super.afterHookedMethod(param);
                            }
                        });
            } catch (Throwable t) {
                // 如果在hook attach方法时出现异常，记录错误日志
                Log.runtime(TAG, "hook attach err");
                Log.printStackTrace(TAG, t);
            }
            //hook "com.alipay.mobile.nebulaappproxy.api.rpc.H5AppRpcUpdate" 类的matchVersion方法
            try {
                XposedHelpers.findAndHookMethod("com.alipay.mobile.nebulaappproxy.api.rpc.H5AppRpcUpdate", classLoader, "matchVersion",
                        classLoader.loadClass(General.H5PAGE_NAME), Map.class, String.class,
                        XC_MethodReplacement.returnConstant(false));
                Log.runtime(TAG, "hook matchVersion successfully");
            } catch (Throwable t) {
                Log.runtime(TAG, "hook matchVersion err");
                Log.printStackTrace(TAG, t);
            }
            //hook "com.alipay.mobile.quinox.LauncherActivity" 类的onResume方法
            try {
                XposedHelpers.findAndHookMethod("com.alipay.mobile.quinox.LauncherActivity", classLoader, "onResume",
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                Log.runtime(TAG, "Activity onResume");
                                String targetUid = getUserId();
                                if (targetUid == null) {
                                    Log.record("用户未登录");
                                    Toast.show("用户未登录");
                                    return;
                                }
                                if (!init) {
                                    if (initHandler(true)) {
                                        init = true;
                                    }
                                    return;
                                }
                                String currentUid = UserMap.getCurrentUid();
                                if (!targetUid.equals(currentUid)) {
                                    if (currentUid != null) {
                                        initHandler(true);
                                        Log.record("用户已切换");
                                        Toast.show("用户已切换");
                                        return;
                                    }
                                    UserMap.initUser(targetUid);
                                }
                                if (offline) {
                                    offline = false;
                                    execHandler();
                                    ((Activity) param.thisObject).finish();
                                    Log.runtime(TAG, "Activity reLogin");
                                }
                            }
                        });
                Log.runtime(TAG, "hook login successfully");
            } catch (Throwable t) {
                Log.runtime(TAG, "hook login err");
                Log.printStackTrace(TAG, t);
            }
            //hook "android.app.Service" 类的onCreate方法
            try {
                XposedHelpers.findAndHookMethod("android.app.Service", classLoader, "onCreate",
                        new XC_MethodHook() {
                            @SuppressLint("WakelockTimeout")
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                Service appService = (Service) param.thisObject;
                                if (!General.CURRENT_USING_SERVICE.equals(appService.getClass().getCanonicalName())) {
                                    return;
                                }
                                Log.runtime(TAG, "Service onCreate");
                                context = appService.getApplicationContext();
                                service = appService;
                                mainHandler = new Handler(Looper.getMainLooper());
                                AtomicReference<String> UserId = new AtomicReference<>();
                                ExecutorService executorService = Executors.newSingleThreadExecutor();
                                mainTask = BaseTask.newInstance("MAIN_TASK", () -> executorService.submit(() -> {
                                    try {
                                        if (!init) {
                                            Log.record("️跳过执行-未初始化");
                                            return;
                                        }
                                        Log.record("开始执行");
                                        long currentTime = System.currentTimeMillis();
                                        if (lastExecTime + 2000 > currentTime) {
                                            Log.record("执行间隔较短，跳过执行");
                                            execDelayedHandler(BaseModel.getCheckInterval().getValue());
                                            return;
                                        }
                                        String currentUid = UserMap.getCurrentUid();
                                        String targetUid = getUserId();
                                        if (targetUid == null || !targetUid.equals(currentUid)) {
                                            Log.record("用户切换或为空，重新登录");
                                            reLogin();
                                            return;
                                        }
                                        lastExecTime = currentTime; // 更新最后执行时间
                                        if (executeCheckTask(lastExecTime)) {
                                            reLogin();
                                            return;
                                        }
                                        TaskCommon.update();
                                        ModelTask.startAllTask(false);
                                        scheduleNextExecution(lastExecTime);
                                        UserId.set(targetUid);
                                    } catch (Exception e) {
                                        Log.record(TAG, "执行异常");
                                        Log.printStackTrace(TAG, e);
                                    }
                                }));
                                registerBroadcastReceiver(appService);
                                StatisticsUtil.load();
                                FriendWatch.load(UserId.get());
                                dayCalendar = Calendar.getInstance();
                                if (initHandler(true)) {
                                    init = true;
                                }
                            }
                        });
                Log.runtime(TAG, "hook service onCreate successfully");
            } catch (Throwable t) {
                Log.runtime(TAG, "hook service onCreate err");
                Log.printStackTrace(TAG, t);
            }
            //
            try {
                XposedHelpers.findAndHookMethod("android.app.Service", classLoader, "onDestroy",
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                Service service = (Service) param.thisObject;
                                if (!General.CURRENT_USING_SERVICE.equals(service.getClass().getCanonicalName()))
                                    return;
                                Log.record("支付宝前台服务被销毁");
                                Notify.updateStatusText("支付宝前台服务被销毁");
                                destroyHandler(true);
                                FriendWatch.unload();
                                StatisticsUtil.unload();
                                restartByBroadcast();
                            }
                        });
            } catch (Throwable t) {
                Log.runtime(TAG, "hook service onDestroy err");
                Log.printStackTrace(TAG, t);
            }
            try {
                XposedHelpers.findAndHookMethod("com.alipay.mobile.common.fgbg.FgBgMonitorImpl", classLoader, "isInBackground",
                        XC_MethodReplacement.returnConstant(false));
            } catch (Throwable t) {
                Log.runtime(TAG, "hook FgBgMonitorImpl method 1 err");
                Log.printStackTrace(TAG, t);
            }
            try {
                XposedHelpers.findAndHookMethod("com.alipay.mobile.common.fgbg.FgBgMonitorImpl", classLoader, "isInBackground",
                        boolean.class, XC_MethodReplacement.returnConstant(false));
            } catch (Throwable t) {
                Log.runtime(TAG, "hook FgBgMonitorImpl method 2 err");
                Log.printStackTrace(TAG, t);
            }
            try {
                XposedHelpers.findAndHookMethod("com.alipay.mobile.common.fgbg.FgBgMonitorImpl", classLoader, "isInBackgroundV2",
                        XC_MethodReplacement.returnConstant(false));
            } catch (Throwable t) {
                Log.runtime(TAG, "hook FgBgMonitorImpl method 3 err");
                Log.printStackTrace(TAG, t);
            }
            try {
                XposedHelpers.findAndHookMethod("com.alipay.mobile.common.transport.utils.MiscUtils", classLoader, "isAtFrontDesk",
                        classLoader.loadClass("android.content.Context"), XC_MethodReplacement.returnConstant(true));
                Log.runtime(TAG, "hook MiscUtils successfully");
            } catch (Throwable t) {
                Log.runtime(TAG, "hook MiscUtils err");
                Log.printStackTrace(TAG, t);
            }
            hooked = true;
            Log.runtime(TAG, "load success: " + lpparam.packageName);
        }
    }
    private static void setWakenAtTimeAlarm() {
        try {
            unsetWakenAtTimeAlarm();
            try {
                PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, new Intent("com.eg.android.AlipayGphone.sesame.execute"), getPendingIntentFlag());
                Calendar calendar = Calendar.getInstance();
                calendar.add(Calendar.DAY_OF_MONTH, 1);
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                if (setAlarmTask(calendar.getTimeInMillis(), pendingIntent)) {
                    alarm0Pi = pendingIntent;
                    Log.record("设置定时唤醒:0|000000");
                }
            } catch (Exception e) {
                Log.runtime(TAG, "setWakenAt0 err:");
                Log.printStackTrace(TAG, e);
            }
            List<String> wakenAtTimeList = BaseModel.getWakenAtTimeList().getValue();
            if (wakenAtTimeList != null && !wakenAtTimeList.isEmpty()) {
                Calendar nowCalendar = Calendar.getInstance();
                for (int i = 1, len = wakenAtTimeList.size(); i < len; i++) {
                    try {
                        String wakenAtTime = wakenAtTimeList.get(i);
                        Calendar wakenAtTimeCalendar = TimeUtil.getTodayCalendarByTimeStr(wakenAtTime);
                        if (wakenAtTimeCalendar != null) {
                            if (wakenAtTimeCalendar.compareTo(nowCalendar) > 0) {
                                PendingIntent wakenAtTimePendingIntent = PendingIntent.getBroadcast(context, i, new Intent("com.eg.android.AlipayGphone.sesame.execute"), getPendingIntentFlag());
                                if (setAlarmTask(wakenAtTimeCalendar.getTimeInMillis(), wakenAtTimePendingIntent)) {
                                    String wakenAtTimeKey = i + "|" + wakenAtTime;
                                    wakenAtTimeAlarmMap.put(wakenAtTimeKey, wakenAtTimePendingIntent);
                                    Log.record("设置定时唤醒:" + wakenAtTimeKey);
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.runtime(TAG, "setWakenAtTime err:");
                        Log.printStackTrace(TAG, e);
                    }
                }
            }
        } catch (Exception e) {
            Log.runtime(TAG, "setWakenAtTimeAlarm err:");
            Log.printStackTrace(TAG, e);
        }
    }
    private static void unsetWakenAtTimeAlarm() {
        try {
            for (Map.Entry<String, PendingIntent> entry : wakenAtTimeAlarmMap.entrySet()) {
                try {
                    String wakenAtTimeKey = entry.getKey();
                    PendingIntent wakenAtTimePendingIntent = entry.getValue();
                    if (unsetAlarmTask(wakenAtTimePendingIntent)) {
                        wakenAtTimeAlarmMap.remove(wakenAtTimeKey);
                        Log.record("取消定时唤醒:" + wakenAtTimeKey);
                    }
                } catch (Exception e) {
                    Log.runtime(TAG, "unsetWakenAtTime err:");
                    Log.printStackTrace(TAG, e);
                }
            }
            try {
                if (unsetAlarmTask(alarm0Pi)) {
                    alarm0Pi = null;
                    Log.record("取消定时唤醒:0|000000");
                }
            } catch (Exception e) {
                Log.runtime(TAG, "unsetWakenAt0 err:");
                Log.printStackTrace(TAG, e);
            }
        } catch (Exception e) {
            Log.runtime(TAG, "unsetWakenAtTimeAlarm err:");
            Log.printStackTrace(TAG, e);
        }
    }
    @SuppressLint("WakelockTimeout")
    private synchronized Boolean initHandler(Boolean force) {
        if (service == null) {
            return false;
        }
        destroyHandler(force);
        try {
            if (force) {
                String userId = getUserId();
                if (userId == null) {
                    Log.record("用户未登录");
                    Toast.show("用户未登录");
                    return false;
                }
                UserMap.initUser(userId);
                Model.initAllModel();
                String startMsg = "芝麻粒-TK 开始加载";
                Log.record(startMsg);
                Log.record("⚙️模块版本：" + modelVersion);
                Log.record("📦应用版本：" + alipayVersion.getVersionString());
                Config.load(userId);
                // ！！所有权限申请应该放在加载配置之后
                //闹钟权限申请
                if (!PermissionUtil.checkAlarmPermissions()) {
                    Log.record("支付宝无闹钟权限");
                    mainHandler.postDelayed(
                            () -> {
                                if (!PermissionUtil.checkOrRequestAlarmPermissions(context)) {
                                    Toast.show("请授予支付宝使用闹钟权限");
                                }
                            },
                            2000);
                    return false;
                }
                // 检查并请求后台运行权限
                if (BaseModel.getBatteryPerm().getValue() && !init && !PermissionUtil.checkBatteryPermissions()) {
                    Log.record("支付宝无始终在后台运行权限");
                    mainHandler.postDelayed(
                            () -> {
                                if (!PermissionUtil.checkOrRequestBatteryPermissions(context)) {
                                    Toast.show("请授予支付宝始终在后台运行权限");
                                }
                            },
                            2000);
                }
                Notify.start(service);
                if (!Objects.requireNonNull(Model.getModel(BaseModel.class)).getEnableField().getValue()) {
                    Log.record("芝麻粒已禁用");
                    Toast.show("芝麻粒已禁用");
                    Notify.setStatusTextDisabled();
                    return false;
                }
                // 保持唤醒锁，防止设备休眠
                if (BaseModel.getStayAwake().getValue()) {
                    try {
                        PowerManager pm = (PowerManager) service.getSystemService(Context.POWER_SERVICE);
                        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, service.getClass().getName());
                        wakeLock.acquire(); // 确保唤醒锁在前台服务启动前
                    } catch (Throwable t) {
                        Log.printStackTrace(t);
                    }
                }
                setWakenAtTimeAlarm();
                if (BaseModel.getNewRpc().getValue()) {
                    rpcBridge = new NewRpcBridge();
                } else {
                    rpcBridge = new OldRpcBridge();
                }
                rpcBridge.load();
                rpcVersion = rpcBridge.getVersion();
                if (BaseModel.getNewRpc().getValue() && BaseModel.getDebugMode().getValue()) {
                    try {
                        rpcRequestUnhook = XposedHelpers.findAndHookMethod(
                                "com.alibaba.ariver.commonability.network.rpc.RpcBridgeExtension", classLoader
                                , "rpc"
                                , String.class, boolean.class, boolean.class, String.class, classLoader.loadClass(General.JSON_OBJECT_NAME), String.class, classLoader.loadClass(General.JSON_OBJECT_NAME), boolean.class, boolean.class, int.class, boolean.class, String.class, classLoader.loadClass("com.alibaba" +
                                        ".ariver.app.api.App"), classLoader.loadClass("com.alibaba.ariver.app.api.Page"), classLoader.loadClass("com.alibaba.ariver.engine.api.bridge.model.ApiContext"), classLoader.loadClass("com.alibaba.ariver.engine.api.bridge.extension.BridgeCallback")
                                , new XC_MethodHook() {
                                    @SuppressLint("WakelockTimeout")
                                    @Override
                                    protected void beforeHookedMethod(MethodHookParam param) {
                                        Object[] args = param.args;
                                        Object object = args[15];
                                        Object[] recordArray = new Object[4];
                                        recordArray[0] = System.currentTimeMillis();
                                        recordArray[1] = args[0];
                                        recordArray[2] = args[4];
                                        rpcHookMap.put(object, recordArray);
                                    }
                                    @SuppressLint("WakelockTimeout")
                                    @Override
                                    protected void afterHookedMethod(MethodHookParam param) {
                                        Object object = param.args[15];
                                        Object[] recordArray = rpcHookMap.remove(object);
                                        if (recordArray != null) {
                                            Map<String, Object> HookResponse = new HashMap<>();
                                            String TimeStamp = String.valueOf(recordArray[0]);
                                            String Method = String.valueOf(recordArray[1]);
                                            String Params = String.valueOf(recordArray[2]);
                                            String rawData = String.valueOf(recordArray[3]);
                                            HookResponse.put("TimeStamp", recordArray[0]);
                                            HookResponse.put("Method", recordArray[1]);
                                            HookResponse.put("Params", Params);
                                            HookResponse.put("Data", recordArray[3]);
                                            if (BaseModel.getSendHookData().getValue()) {
                                                HookSender.sendHookData(HookResponse);
                                            }
                                            String logMessage = "\n========================>\n" + "TimeStamp: " + TimeStamp + "\n" + "Method: " + Method + "\n" + "Params: " + Params + "\n" + "Data: " + rawData.replace("\\", "") + "\n<========================\n";
                                            if (!logMessage.trim().isEmpty()) {
                                                Log.capture(logMessage);
                                            }
                                        } else {
                                            Log.capture("delete record ID: " + object.hashCode());
                                        }
                                    }
                                });
                        Log.runtime(TAG, "hook record request successfully");
                    } catch (Throwable t) {
                        Log.runtime(TAG, "hook record request err:");
                        Log.printStackTrace(TAG, t);
                    }
                    try {
                        rpcResponseUnhook = XposedHelpers.findAndHookMethod(
                                "com.alibaba.ariver.engine.common.bridge.internal.DefaultBridgeCallback", classLoader
                                , "sendJSONResponse"
                                , classLoader.loadClass(General.JSON_OBJECT_NAME)
                                , new XC_MethodHook() {
                                    @SuppressLint("WakelockTimeout")
                                    @Override
                                    protected void beforeHookedMethod(MethodHookParam param) {
                                        Object object = param.thisObject;
                                        Object[] recordArray = rpcHookMap.get(object);
                                        if (recordArray != null) {
                                            recordArray[3] = String.valueOf(param.args[0]);
                                        }
                                    }
                                });
                        Log.runtime(TAG, "hook record response successfully");
                    } catch (Throwable t) {
                        Log.runtime(TAG, "hook record response err:");
                        Log.printStackTrace(TAG, t);
                    }
                }
                Model.bootAllModel(classLoader);
                StatusUtil.load();
                updateDay(userId);
                BaseModel.initData();
                String successMsg = "芝麻粒-TK 加载成功🎉";
                Log.record(successMsg);
                Toast.show(successMsg);
            }
            offline = false;
            execHandler();
            return true;
        } catch (Throwable th) {
            Log.runtime(TAG, "startHandler err:");
            Log.printStackTrace(TAG, th);
            Toast.show("芝麻粒加载失败 🎃");
            return false;
        }
    }
    static synchronized void destroyHandler(Boolean force) {
        try {
            if (force) {
                if (service != null) {
                    stopHandler();
                    BaseModel.destroyData();
                    StatusUtil.unload();
                    Notify.stop();
                    RpcIntervalLimit.clearIntervalLimit();
                    Config.unload();
                    Model.destroyAllModel();
                    UserMap.unload();
                }
                if (rpcResponseUnhook != null) {
                    try {
                        rpcResponseUnhook.unhook();
                    } catch (Exception e) {
                        Log.printStackTrace(e);
                    }
                }
                if (rpcRequestUnhook != null) {
                    try {
                        rpcRequestUnhook.unhook();
                    } catch (Exception e) {
                        Log.printStackTrace(e);
                    }
                }
                if (wakeLock != null) {
                    wakeLock.release();
                    wakeLock = null;
                }
                if (rpcBridge != null) {
                    rpcVersion = null;
                    rpcBridge.unload();
                    rpcBridge = null;
                }
            } else {
                ModelTask.stopAllTask();
            }
        } catch (Throwable th) {
            Log.runtime(TAG, "stopHandler err:");
            Log.printStackTrace(TAG, th);
        }
    }
    static void execHandler() {
        mainTask.startTask(false);
    }
    /**
     * 安排主任务在指定的延迟时间后执行，并更新通知中的下次执行时间。
     *
     * @param delayMillis 延迟执行的毫秒数
     */
    static void execDelayedHandler(long delayMillis) {
        mainHandler.postDelayed(() -> mainTask.startTask(false), delayMillis);
        try {
            Notify.updateNextExecText(System.currentTimeMillis() + delayMillis);
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }
    private static void stopHandler() {
        mainTask.stopTask();
        ModelTask.stopAllTask();
    }
    public static void updateDay(String userId) {
        Calendar nowCalendar = Calendar.getInstance();
        try {
            int nowYear = nowCalendar.get(Calendar.YEAR);
            int nowMonth = nowCalendar.get(Calendar.MONTH);
            int nowDay = nowCalendar.get(Calendar.DAY_OF_MONTH);
            if (dayCalendar.get(Calendar.YEAR) != nowYear || dayCalendar.get(Calendar.MONTH) != nowMonth || dayCalendar.get(Calendar.DAY_OF_MONTH) != nowDay) {
                dayCalendar = (Calendar) nowCalendar.clone();
                dayCalendar.set(Calendar.HOUR_OF_DAY, 0);
                dayCalendar.set(Calendar.MINUTE, 0);
                dayCalendar.set(Calendar.SECOND, 0);
                Log.record("日期更新为：" + nowYear + "-" + (nowMonth + 1) + "-" + nowDay);
                setWakenAtTimeAlarm();
            }
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
        try {
            StatisticsUtil.save(Calendar.getInstance());
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
        try {
            StatusUtil.save(nowCalendar);
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
        try {
            FriendWatch.updateDay(userId);
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }
    @SuppressLint({"ScheduleExactAlarm", "ObsoleteSdkInt", "MissingPermission"})
    private static Boolean setAlarmTask(long triggerAtMillis, PendingIntent operation) {
        try {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation);
            }
            Log.runtime("setAlarmTask triggerAtMillis:" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(triggerAtMillis) + " operation:" + operation);
            return true;
        } catch (Throwable th) {
            Log.runtime(TAG, "setAlarmTask err:");
            Log.printStackTrace(TAG, th);
        }
        return false;
    }
    private static Boolean unsetAlarmTask(PendingIntent operation) {
        try {
            if (operation != null) {
                AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                alarmManager.cancel(operation);
            }
            return true;
        } catch (Throwable th) {
            Log.runtime(TAG, "unsetAlarmTask err:");
            Log.printStackTrace(TAG, th);
        }
        return false;
    }
    public static void reLoginByBroadcast() {
        try {
            context.sendBroadcast(new Intent("com.eg.android.AlipayGphone.sesame.reLogin"));
        } catch (Throwable th) {
            Log.runtime(TAG, "sesame sendBroadcast reLogin err:");
            Log.printStackTrace(TAG, th);
        }
    }
    public static void restartByBroadcast() {
        try {
            context.sendBroadcast(new Intent("com.eg.android.AlipayGphone.sesame.restart"));
        } catch (Throwable th) {
            Log.runtime(TAG, "sesame sendBroadcast restart err:");
            Log.printStackTrace(TAG, th);
        }
    }
    @SuppressLint("ObsoleteSdkInt")
    private static int getPendingIntentFlag() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        } else {
            return PendingIntent.FLAG_UPDATE_CURRENT;
        }
    }
    public static Object getMicroApplicationContext() {
        if (microApplicationContextObject == null) {
            try {
                Class<?> alipayApplicationClass = XposedHelpers.findClass(
                        "com.alipay.mobile.framework.AlipayApplication", classLoader
                );
                Object alipayApplicationInstance = XposedHelpers.callStaticMethod(
                        alipayApplicationClass, "getInstance"
                );
                if (alipayApplicationInstance == null) {
                    return null;
                }
                microApplicationContextObject = XposedHelpers.callMethod(
                        alipayApplicationInstance, "getMicroApplicationContext"
                );
            } catch (Throwable t) {
                Log.printStackTrace(t);
            }
        }
        return microApplicationContextObject;
    }
    public static Object getServiceObject(String service) {
        try {
            return XposedHelpers.callMethod(getMicroApplicationContext(), "findServiceByInterface", service);
        } catch (Throwable th) {
            Log.runtime(TAG, "getUserObject err");
            Log.printStackTrace(TAG, th);
        }
        return null;
    }
    public static Object getUserObject() {
        try {
            return XposedHelpers.callMethod(
                    getServiceObject(XposedHelpers.findClass("com.alipay.mobile.personalbase.service.SocialSdkContactService", classLoader).getName()), "getMyAccountInfoModelByLocal");
        } catch (Throwable th) {
            Log.runtime(TAG, "getUserObject err");
            Log.printStackTrace(TAG, th);
        }
        return null;
    }
    public static String getUserId() {
        try {
            Object userObject = getUserObject();
            if (userObject != null) {
                return (String) XposedHelpers.getObjectField(userObject, "userId");
            }
        } catch (Throwable th) {
            Log.runtime(TAG, "getUserId err");
            Log.printStackTrace(TAG, th);
        }
        return null;
    }
    public static void reLogin() {
        mainHandler.post(
                () -> {
                    if (reLoginCount.get() < 5) {
                        execDelayedHandler(reLoginCount.getAndIncrement() * 5000L);
                    } else {
                        execDelayedHandler(Math.max(BaseModel.getCheckInterval().getValue(), 180_000));
                    }
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setClassName(General.PACKAGE_NAME, General.CURRENT_USING_ACTIVITY);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    offline = true;
                    context.startActivity(intent);
                });
    }
    class AlipayBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.runtime("sesame 查看广播:" + action + " intent:" + intent);
            if (action != null) {
                switch (action) {
                    case "com.eg.android.AlipayGphone.sesame.restart":
                        String userId = intent.getStringExtra("userId");
                        if (StringUtil.isEmpty(userId) || Objects.equals(UserMap.getCurrentUid(), userId)) {
                            initHandler(true);
                        }
                        break;
                    case "com.eg.android.AlipayGphone.sesame.execute":
                        initHandler(false);
                        break;
                    case "com.eg.android.AlipayGphone.sesame.reLogin":
                        reLogin();
                        break;
                    case "com.eg.android.AlipayGphone.sesame.status":
                        try {
                            context.sendBroadcast(new Intent("naughtytom.xposed.sesame.status"));
                        } catch (Throwable th) {
                            Log.runtime(TAG, "sesame sendBroadcast status err:");
                            Log.printStackTrace(TAG, th);
                        }
                        break;
                    case "com.eg.android.AlipayGphone.sesame.rpctest":
                        try {
                            String method = intent.getStringExtra("method");
                            String data = intent.getStringExtra("data");
                            String type = intent.getStringExtra("type");
                            DebugRpc rpcInstance = new DebugRpc(); // 创建实例
                            rpcInstance.start(method, data, type); // 通过实例调用非静态方法
                        } catch (Throwable th) {
                            Log.runtime(TAG, "sesame 测试RPC请求失败:");
                            Log.printStackTrace(TAG, th);
                        }
                        break;
                    default:
                        throw new IllegalStateException("Unexpected value: " + action);
                }
            }
        }
    }
    /**
     * 注册广播接收器以监听支付宝相关动作。
     *
     * @param context 应用程序上下文
     */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    // 忽略Lint关于注册广播接收器时未指定导出属性的警告
    void registerBroadcastReceiver(Context context) {
        //创建一个IntentFilter实例，用于过滤出我们需要捕获的广播
        try {
            IntentFilter intentFilter = getIntentFilter();
            // 根据Android SDK版本注册广播接收器
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // 在Android 13及以上版本，注册广播接收器并指定其可以被其他应用发送的广播触发
                context.registerReceiver(new AlipayBroadcastReceiver(), intentFilter, Context.RECEIVER_EXPORTED);
            } else {
                // 在Android 13以下版本，注册广播接收器
                context.registerReceiver(new AlipayBroadcastReceiver(), intentFilter);
            }
            // 记录成功注册广播接收器的日志
            Log.runtime(TAG, "hook registerBroadcastReceiver successfully");
        } catch (Throwable th) {
            // 记录注册广播接收器失败的日志
            Log.runtime(TAG, "hook registerBroadcastReceiver err:");
            // 打印异常堆栈信息
            Log.printStackTrace(TAG, th);
        }
    }
    @NonNull
    private static IntentFilter getIntentFilter() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("com.eg.android.AlipayGphone.sesame.restart"); // 重启支付宝服务的动作
        intentFilter.addAction("com.eg.android.AlipayGphone.sesame.execute"); // 执行特定命令的动作
        intentFilter.addAction("com.eg.android.AlipayGphone.sesame.reLogin"); // 重新登录支付宝的动作
        intentFilter.addAction("com.eg.android.AlipayGphone.sesame.status"); // 查询支付宝状态的动作
        intentFilter.addAction("com.eg.android.AlipayGphone.sesame.rpctest"); // 调试RPC的动作
        return intentFilter;
    }
}
