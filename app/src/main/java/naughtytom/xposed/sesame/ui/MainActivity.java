package fansirsqi.xposed.sesame.ui;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import fansirsqi.xposed.sesame.R;
import fansirsqi.xposed.sesame.data.RunType;
import fansirsqi.xposed.sesame.data.UIConfig;
import fansirsqi.xposed.sesame.data.ViewAppInfo;
import fansirsqi.xposed.sesame.entity.FriendWatch;
import fansirsqi.xposed.sesame.entity.UserEntity;
import fansirsqi.xposed.sesame.model.SelectModelFieldFunc;
import fansirsqi.xposed.sesame.util.FansirsqiUtil;
import fansirsqi.xposed.sesame.util.Files;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.Maps.UserMap;
import fansirsqi.xposed.sesame.util.PermissionUtil;
import fansirsqi.xposed.sesame.util.StatisticsUtil;
import fansirsqi.xposed.sesame.util.ThreadUtil;
import fansirsqi.xposed.sesame.util.ToastUtil;
public class MainActivity extends BaseActivity {
    private boolean hasPermissions = false;
    private boolean isClick = false;
    private TextView tvStatistics;
    private final Handler viewHandler = new Handler(Looper.getMainLooper());
    private Runnable titleRunner;
    private String[] userNameArray = {"默认"};
    private UserEntity[] userEntityArray = {null};
    @SuppressLint({"UnspecifiedRegisterReceiverFlag", "SetTextI18n"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ToastUtil.init(this); // 初始化全局 Context
        hasPermissions = PermissionUtil.checkOrRequestFilePermissions(this);
        if (!hasPermissions) {
            Toast.makeText(this, "未获取文件读写权限", Toast.LENGTH_LONG).show();
            finish(); // 如果权限未获取，终止当前 Activity
            return;
        }
        setContentView(R.layout.activity_main);
        View mainImage = findViewById(R.id.main_image);
        tvStatistics = findViewById(R.id.tv_statistics);
        TextView buildVersion = findViewById(R.id.bulid_version);
        TextView buildTarget = findViewById(R.id.bulid_target);
        TextView oneWord = findViewById(R.id.one_word);
        // 获取并设置一言句子
        ViewAppInfo.checkRunType();
        updateSubTitle(ViewAppInfo.getRunType());
//        viewHandler = new Handler(Looper.getMainLooper());
        titleRunner = () -> updateSubTitle(RunType.DISABLE);
        if (mainImage != null) {
            mainImage.setOnLongClickListener(
                    v -> {
                        // 当视图被长按时执行的操作
                        if (v.getId() == R.id.main_image) {
                            String data = "file://" + Files.getDebugLogFile().getAbsolutePath();
                            Intent it = new Intent(MainActivity.this, HtmlViewerActivity.class);
                            it.putExtra("nextLine", false);
                            it.putExtra("canClear", true);
                            it.setData(Uri.parse(data));
                            startActivity(it);
                            return true; // 表示事件已处理
                        }
                        return false; // 如果不是目标视图，返回false
                    });
        }
        BroadcastReceiver broadcastReceiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        String action = intent.getAction();
                        Log.runtime("receive broadcast:" + action + " intent:" + intent);
                        if (action != null) {
                            switch (action) {
                                case "naughtytom.xposed.sesame.status":
                                    if (RunType.DISABLE == ViewAppInfo.getRunType()) {
                                        updateSubTitle(RunType.PACKAGE);
                                    }
                                    viewHandler.removeCallbacks(titleRunner);
                                    if (isClick) {
                                        // 调用 FansirsqiUtil 获取句子
                                        FansirsqiUtil.getOneWord(
                                                new FansirsqiUtil.OneWordCallback() {
                                                    @Override
                                                    public void onSuccess(String result) {
                                                        runOnUiThread(() -> updateOneWord(result, oneWord)); // 在主线程中更新UI
                                                    }
                                                    @Override
                                                    public void onFailure(String error) {
                                                        runOnUiThread(() -> updateOneWord(error, oneWord)); // 在主线程中更新UI
                                                    }
                                                });
                                        Toast.makeText(context, "芝麻粒状态加载正常👌", Toast.LENGTH_SHORT).show();
                                        ThreadUtil.sleep(200); // 别急，等一会儿再说
                                        isClick = false;
                                    }
                                    break;
                                case "naughtytom.xposed.sesame.update":
                                    StatisticsUtil.load();
                                    tvStatistics.setText(StatisticsUtil.getText());
                                    break;
                            }
                        }
                    }
                };
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("naughtytom.xposed.sesame.status");
        intentFilter.addAction("naughtytom.xposed.sesame.update");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(broadcastReceiver, intentFilter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(broadcastReceiver, intentFilter);
        }
        StatisticsUtil.load();
        tvStatistics.setText(StatisticsUtil.getText());
        // 调用 FansirsqiUtil 获取句子
        FansirsqiUtil.getOneWord(
                new FansirsqiUtil.OneWordCallback() {
                    @Override
                    public void onSuccess(String result) {
                        runOnUiThread(() -> oneWord.setText(result)); // 在主线程中更新UI
                    }
                    @Override
                    public void onFailure(String error) {
                        runOnUiThread(() -> oneWord.setText(error)); // 在主线程中更新UI
                    }
                });
        buildVersion.setText("Build Version: " + ViewAppInfo.getAppVersion()); // 版本信息
        buildTarget.setText("Build Target: " + ViewAppInfo.getAppBuildTarget()); // 编译日期信息
        StringDialog.showAlertDialog(this, "提示", getString(R.string.start_message), "我知道了");
    }
    private void updateOneWord(String str, TextView oneWord) {
        oneWord.setText(str);
    }
    @Override
    protected void onResume() {
        super.onResume();
        if (hasPermissions) {
            if (RunType.DISABLE == ViewAppInfo.getRunType()) {
                viewHandler.postDelayed(titleRunner, 3000);
                try {
                    sendBroadcast(new Intent("com.eg.android.AlipayGphone.sesame.status"));
                } catch (Throwable th) {
                    Log.runtime("view sendBroadcast status err:");
                    Log.printStackTrace(th);
                }
            }
            try {//打开设置前需要确认设置了哪个UI
                UIConfig.load();
            } catch (Exception e) {
                Log.printStackTrace(e);
            }
            try {
                List<String> userNameList = new ArrayList<>();
                List<UserEntity> userEntityList = new ArrayList<>();
                java.io.File[] configFiles = Files.CONFIG_DIR.listFiles();
                if (configFiles != null) {
                    for (java.io.File configDir : configFiles) {
                        if (configDir.isDirectory()) {
                            String userId = configDir.getName();
                            UserMap.loadSelf(userId);
                            UserEntity userEntity = UserMap.get(userId);
                            String userName;
                            if (userEntity == null) {
                                userName = userId;
                            } else {
                                userName = userEntity.getShowName() + ": " + userEntity.getAccount();
                            }
                            userNameList.add(userName);
                            userEntityList.add(userEntity);
                        }
                    }
                }
                userNameList.add(0, "默认");
                userEntityList.add(0, null);
                userNameArray = userNameList.toArray(new String[0]);
                userEntityArray = userEntityList.toArray(new UserEntity[0]);
            } catch (Exception e) {
                userNameArray = new String[]{"默认"};
                userEntityArray = new UserEntity[]{null};
                Log.printStackTrace(e);
            }
            try {
                StatisticsUtil.load();
                StatisticsUtil.updateDay();
                tvStatistics.setText(StatisticsUtil.getText());
            } catch (Exception e) {
                Log.printStackTrace(e);
            }
        }
    }
    @SuppressLint("NonConstantResourceId")
    public void onClick(View v) {
        if (v.getId() == R.id.main_image) {
            try {
                sendBroadcast(new Intent("com.eg.android.AlipayGphone.sesame.status"));
                isClick = true;
            } catch (Throwable th) {
                Log.runtime("view sendBroadcast status err:");
                Log.printStackTrace(th);
            }
            return;
        }
        String data = "file://";
        int id = v.getId();
        if (id == R.id.btn_forest_log) {
            data += Files.getForestLogFile().getAbsolutePath();
        } else if (id == R.id.btn_farm_log) {
            data += Files.getFarmLogFile().getAbsolutePath();
        } else if (id == R.id.btn_other_log) {
            data += Files.getOtherLogFile().getAbsolutePath();
        } else if (id == R.id.btn_github) {
            data = "https://github.com/Fansirsqi/Sesame-TK";
        } else if (id == R.id.btn_settings) {
            selectSettingUid();
            return;
        } else if (id == R.id.btn_friend_watch) {
            String userId = UserMap.getCurrentUid();
            ListDialog.show(this, getString(R.string.friend_watch), FriendWatch.getList(userId), SelectModelFieldFunc.newMapInstance(), false, ListDialog.ListType.SHOW);
            return;
        }
        Intent it = new Intent(this, HtmlViewerActivity.class);
        it.setData(Uri.parse(data));
        startActivity(it);
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        PackageManager packageManager = getPackageManager();
        String aliasName = getClass().getCanonicalName() + "Alias";
        try {
            int componentEnabledSetting = packageManager.getComponentEnabledSetting(new ComponentName(this, aliasName));
            MenuItem checkable = menu.add(0, 1, 1, R.string.hide_the_application_icon).setCheckable(true);
            checkable.setChecked(componentEnabledSetting > PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
            menu.add(0, 2, 2, R.string.view_error_log_file);
            menu.add(0, 3, 3, R.string.view_all_log_file);
            menu.add(0, 4, 4, R.string.view_runtim_log_file);
            menu.add(0, 5, 5, R.string.export_the_statistic_file);
            menu.add(0, 6, 6, R.string.import_the_statistic_file);
            menu.add(0, 7, 7, R.string.view_capture);
            menu.add(0, 8, 8, R.string.extend);
            menu.add(0, 9, 9, R.string.settings);
        } catch (Exception e) {
            Log.printStackTrace(e);
            ToastUtil.makeText(this, "菜单创建失败，请重试", Toast.LENGTH_SHORT).show();
        }
        return super.onCreateOptionsMenu(menu);
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 1:
                boolean shouldHideIcon = !item.isChecked(); // 是否应隐藏图标
                item.setChecked(shouldHideIcon);
                PackageManager packageManager = getPackageManager();
                String aliasName = getClass().getCanonicalName() + "Alias";
                int newState = shouldHideIcon ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED : PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
                packageManager.setComponentEnabledSetting(new ComponentName(this, aliasName), newState, PackageManager.DONT_KILL_APP);
                break;
            case 2:
                String errorData = "file://";
                errorData += Files.getErrorLogFile().getAbsolutePath();
                Intent errorIt = new Intent(this, HtmlViewerActivity.class);
                errorIt.putExtra("nextLine", false);
                errorIt.putExtra("canClear", true);
                errorIt.setData(Uri.parse(errorData));
                startActivity(errorIt);
                break;
            case 3:
                String RecordData = "file://";
                RecordData += Files.getRecordLogFile().getAbsolutePath();
                Intent otherIt = new Intent(this, HtmlViewerActivity.class);
                otherIt.putExtra("nextLine", false);
                otherIt.putExtra("canClear", true);
                otherIt.setData(Uri.parse(RecordData));
                startActivity(otherIt);
                break;
            case 4:
                String runtimeData = "file://";
                runtimeData += Files.getRuntimeLogFile().getAbsolutePath();
                Intent allIt = new Intent(this, HtmlViewerActivity.class);
                allIt.putExtra("nextLine", false);
                allIt.putExtra("canClear", true);
                allIt.setData(Uri.parse(runtimeData));
                startActivity(allIt);
                break;
            case 5:
                java.io.File statisticsFile = Files.exportFile(Files.getStatisticsFile());
                if (statisticsFile != null) {
                    ToastUtil.makeText(this, "文件已导出到: " + statisticsFile.getPath(), Toast.LENGTH_SHORT).show();
                }
                break;
            case 6:
                if (Files.copyTo(Files.getExportedStatisticsFile(), Files.getStatisticsFile())) {
                    tvStatistics.setText(StatisticsUtil.getText());
                    ToastUtil.makeText(this, "导入成功！", Toast.LENGTH_SHORT).show();
                }
                break;
            case 7:
                String captureData = "file://";
                captureData += Files.getCaptureLogFile().getAbsolutePath();
                Intent captureIt = new Intent(this, HtmlViewerActivity.class);
                captureIt.putExtra("nextLine", false);
                captureIt.putExtra("canClear", true);
                captureIt.setData(Uri.parse(captureData));
                startActivity(captureIt);
                break;
            case 8:
                // 扩展功能
                startActivity(new Intent(this, ExtendActivity.class));
                break;
            case 9:
                selectSettingUid();
                break;
        }
        return super.onOptionsItemSelected(item);
    }
    public void selectSettingUid() {
        AtomicBoolean selected = new AtomicBoolean(false);
        AlertDialog dialog =
                StringDialog.showSelectionDialog(
                        this,
                        "请选择配置",
                        userNameArray,
                        (dialog1, which) -> {
                            selected.set(true);
                            dialog1.dismiss();
                            goSettingActivity(which);
                        },
                        "返回",
                        dialog1 -> {
                            selected.set(true);
                            dialog1.dismiss();
                        });
        int length = userNameArray.length;
        if (length > 0 && length < 3) {
            new Thread(
                    () -> {
                        ThreadUtil.sleep(1000);
                        if (!selected.get()) {
                            goSettingActivity(length - 1);
                            // 在主线程中关闭对话框
                            runOnUiThread(
                                    () -> {
                                        if (dialog.isShowing()) {
                                            dialog.dismiss();
                                        }
                                    });
                        }
                    })
                    .start();
        }
    }
    /**
     * 启动设置活动，根据用户选择的配置项启动不同的设置界面。
     *
     * @param index 选择的用户索引，用于获取用户信息。
     */
    private void goSettingActivity(int index) {
        UserEntity userEntity = userEntityArray[index];
//        Class<?> targetActivity = UIConfig.INSTANCE.getNewUI() ? DemoSettingActivity.class : SettingsActivity.class;
        Class<?> targetActivity = UIConfig.INSTANCE.getNewUI() ? NewSettingsActivity.class : SettingsActivity.class;
        // targetActivity：使用 UIConfig 和 ViewAppInfo 中的信息判断启动 NewSettingsActivity 还是 SettingsActivity，简化条件判断。
        // intent.putExtra：userEntity 不为空时，设置用户的 userId 和 userName；若为空，则仅传递 userName。
        Intent intent = new Intent(this, targetActivity);
        // 设置意图的额外信息：用户 ID 和显示名称
        if (userEntity != null) {
            intent.putExtra("userId", userEntity.getUserId());
            intent.putExtra("userName", userEntity.getShowName());
        } else {
            intent.putExtra("userName", userNameArray[index]);
        }
        startActivity(intent);
    }
    private void updateSubTitle(RunType runType) {
        setBaseTitle(ViewAppInfo.getAppTitle() + "[" + runType.getName() + "]");
        switch (runType) {
            case DISABLE:
                setBaseTitleTextColor(ContextCompat.getColor(this, R.color.textColorRed));
                break;
            case MODEL:
                setBaseTitleTextColor(ContextCompat.getColor(this, R.color.textColorPrimary));
                break;
            case PACKAGE:
                setBaseTitleTextColor(ContextCompat.getColor(this, R.color.textColorPrimary));
                break;
        }
    }
}
