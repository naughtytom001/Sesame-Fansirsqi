package naughtytom.xposed.sesame.model;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import naughtytom.xposed.sesame.model.modelFieldExt.BooleanModelField;
import naughtytom.xposed.sesame.model.modelFieldExt.ChoiceModelField;
import naughtytom.xposed.sesame.model.modelFieldExt.IntegerModelField;
import naughtytom.xposed.sesame.model.modelFieldExt.ListModelField;
import naughtytom.xposed.sesame.model.modelFieldExt.StringModelField;
import naughtytom.xposed.sesame.task.antOcean.AntOceanRpcCall;
import naughtytom.xposed.sesame.task.reserve.ReserveRpcCall;
import naughtytom.xposed.sesame.util.ListUtil;
import naughtytom.xposed.sesame.util.Log;
import naughtytom.xposed.sesame.util.Maps.BeachMap;
import naughtytom.xposed.sesame.util.Maps.IdMapManager;
import naughtytom.xposed.sesame.util.Maps.ReserveaMap;
import naughtytom.xposed.sesame.util.RandomUtil;
import naughtytom.xposed.sesame.util.ThreadUtil;
import lombok.Getter;
/**
 * 基础配置模块
 */
public class BaseModel extends Model {
    /**
     * 是否保持唤醒状态
     */
    @Getter
    private static final BooleanModelField stayAwake = new BooleanModelField("stayAwake", "保持唤醒", true);
    /**
     * 执行间隔时间（分钟）
     */
    @Getter
    private static final IntegerModelField.MultiplyIntegerModelField checkInterval =
            new IntegerModelField.MultiplyIntegerModelField("checkInterval", "执行间隔(分钟)", 50, 1, 12 * 60, 60_000);//此处调整至30分钟执行一次，可能会比平常耗电一点。。
    /**
     * 定时执行的时间点列表
     */
    @Getter
    private static final ListModelField.ListJoinCommaToStringModelField execAtTimeList =
            new ListModelField.ListJoinCommaToStringModelField("execAtTimeList", "定时执行(关闭:-1)", ListUtil.newArrayList(
                    "0700", "0730", "1200", "1230", "1700", "1730", "2000", "2030", "2359"
            ));
    /**
     * 定时唤醒的时间点列表
     */
    @Getter
    private static final ListModelField.ListJoinCommaToStringModelField wakenAtTimeList =
            new ListModelField.ListJoinCommaToStringModelField("wakenAtTimeList", "定时唤醒(关闭:-1)", ListUtil.newArrayList(
                    "0650", "2350"
            ));
    /**
     * 能量收集的时间范围
     */
    @Getter
    private static final ListModelField.ListJoinCommaToStringModelField energyTime =
            new ListModelField.ListJoinCommaToStringModelField("energyTime", "只收能量时间(范围)", ListUtil.newArrayList("0700-0730"));
    @Getter
    private static final ListModelField.ListJoinCommaToStringModelField modelSleepTime =
            new ListModelField.ListJoinCommaToStringModelField("modelSleepTime", "模块休眠时间(范围)", ListUtil.newArrayList("0100-0540"));
    /**
     * 定时任务模式选择
     */
    @Getter
    private static final ChoiceModelField timedTaskModel = new ChoiceModelField("timedTaskModel", "定时任务模式", TimedTaskModel.SYSTEM, TimedTaskModel.nickNames);
    /**
     * 超时是否重启
     */
    @Getter
    private static final BooleanModelField timeoutRestart = new BooleanModelField("timeoutRestart", "超时重启", true);
    /**
     * 异常发生时的等待时间（分钟）
     */
    @Getter
    private static final IntegerModelField.MultiplyIntegerModelField waitWhenException =
            new IntegerModelField.MultiplyIntegerModelField("waitWhenException", "异常等待时间(分钟)", 60, 0, 24 * 60, 60_000);
    /**
     * 异常通知开关
     */
    @Getter
    private static final BooleanModelField errNotify = new BooleanModelField("errNotify", "开启异常通知", false);
    /**
     * 是否启用新接口（最低支持版本 v10.3.96.8100）
     */
    @Getter
    private static final BooleanModelField newRpc = new BooleanModelField("newRpc", "使用新接口(最低支持v10.3.96.8100)", true);
    /**
     * 是否开启抓包调试模式
     */
    @Getter
    private static final BooleanModelField debugMode = new BooleanModelField("debugMode", "开启抓包(基于新接口)", false);
    /**
     * 是否申请支付宝的后台运行权限
     */
    @Getter
    private static final BooleanModelField batteryPerm = new BooleanModelField("batteryPerm", "为支付宝申请后台运行权限", true);
    /**
     * 是否记录日志
     */
    @Getter
    private static final BooleanModelField recordLog = new BooleanModelField("recordLog", "全部 | 记录日志", true);
    /**
     * 是否显示气泡提示
     */
    @Getter
    private static final BooleanModelField showToast = new BooleanModelField("showToast", "气泡提示", true);
    /**
     * 气泡提示的纵向偏移量
     */
    @Getter
    private static final IntegerModelField toastOffsetY = new IntegerModelField("toastOffsetY", "气泡纵向偏移", 99);
    /**
     * 只显示中文并设置时区
     */
    @Getter
    private static final BooleanModelField languageSimplifiedChinese = new BooleanModelField("languageSimplifiedChinese", "只显示中文并设置时区", true);
    /**
     * 是否开启状态栏禁删
     */
    @Getter
    private static final BooleanModelField enableOnGoing = new BooleanModelField("enableOnGoing", "开启状态栏禁删", false);
    /**
     * 是否开启任务运行进度显示
     */
    @Getter
    private static final BooleanModelField enableProgress = new BooleanModelField("enableProgress", "开启任务运行进度显示", false);

    @Getter
    private static final BooleanModelField sendHookData = new BooleanModelField("sendHookData", "启用Hook数据转发", false);
    @Getter
    static final StringModelField sendHookDataUrl = new StringModelField("sendHookDataUrl", "Hook数据转发地址", "http://127.0.0.1:9527/hook");
    @Override
    public String getName() {
        return "基础";
    }
    @Override
    public ModelGroup getGroup() {
        return ModelGroup.BASE;
    }
    @Override
    public String getIcon() {
        return "BaseModel.png";
    }
    @Override
    public String getEnableFieldName() {
        return "启用模块";
    }
    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(stayAwake);//是否保持唤醒状态
        modelFields.addField(checkInterval);//执行间隔时间
        modelFields.addField(modelSleepTime);//模块休眠时间范围
        modelFields.addField(execAtTimeList);//定时执行的时间点列表
        modelFields.addField(wakenAtTimeList);//定时唤醒的时间点列表
        modelFields.addField(energyTime);//能量收集的时间范围
        modelFields.addField(timedTaskModel);//定时任务模式选择
        modelFields.addField(timeoutRestart);//超时是否重启
        modelFields.addField(waitWhenException);//异常发生时的等待时间
        modelFields.addField(errNotify);//异常通知开关
        modelFields.addField(newRpc);//是否启用新接口
        modelFields.addField(debugMode);//是否开启抓包调试模式
        modelFields.addField(sendHookData);//启用Hook数据转发
        modelFields.addField(sendHookDataUrl);//Hook数据转发地址
        modelFields.addField(batteryPerm);//是否申请支付宝的后台运行权限
        modelFields.addField(recordLog);//是否记录日志
        modelFields.addField(showToast);//是否显示气泡提示
        modelFields.addField(enableOnGoing);//是否开启状态栏禁删
        modelFields.addField(enableProgress);//是否开启任务运行进度显示
        modelFields.addField(languageSimplifiedChinese);//是否只显示中文并设置时区
        modelFields.addField(toastOffsetY);//气泡提示的纵向偏移量
        return modelFields;
    }
    /**
     * 初始化数据，通过异步线程加载初始化 Reserve 和 Beach 任务数据。
     */
    public static void initData() {
        new Thread(
                () -> {
                    try {
                        Log.runtime("🍼初始化海洋，保护地数据");
                        ThreadUtil.sleep(RandomUtil.nextInt(4500, 6000));
                        initReserve();
                        initBeach();
                    } catch (Exception e) {
                        Log.printStackTrace(e);
                    }
                })
                .start();
    }
    /**
     * 清理数据，在模块销毁时调用，清空 Reserve 和 Beach 数据。
     */
    public static void destroyData() {
        try {
            Log.runtime("🧹清理海洋，保护地数据");
            IdMapManager.getInstance(ReserveaMap.class).clear();
            IdMapManager.getInstance(BeachMap.class).clear();
            //其他也可以清理清理
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }
    /**
     * 初始化保护地任务。通过 ReserveRpc 接口查询可兑换的树项目，将符合条件的保护地任务存入 ReserveIdMapUtil。 条件：项目类型为 "RESERVE" 且状态为 "AVAILABLE"。若调用失败则加载备份的 ReserveIdMapUtil。
     */
    private static void initReserve() {
        try {
            // 调用 ReserveRpc 接口，查询可兑换的树项目列表
            String response = ReserveRpcCall.queryTreeItemsForExchange();
            // 若首次调用结果为空，进行延迟后再次调用
            if (response == null) {
                ThreadUtil.sleep(RandomUtil.delay());
                response = ReserveRpcCall.queryTreeItemsForExchange();
            }
            JSONObject jsonResponse = new JSONObject(response);
            // 检查接口调用是否成功，resultCode 为 SUCCESS 表示成功
            if ("SUCCESS".equals(jsonResponse.optString("resultCode", ""))) {
                JSONArray treeItems = jsonResponse.optJSONArray("treeItems");
                // 遍历所有树项目，筛选符合条件的保护地项目
                if (treeItems != null) {
                    for (int i = 0; i < treeItems.length(); i++) {
                        JSONObject item = treeItems.getJSONObject(i);
                        // 跳过未定义 projectType 字段的项目
                        if (!item.has("projectType")) {
                            continue;
                        }
                        // 过滤出 projectType 为 "RESERVE" 且 applyAction 为 "AVAILABLE" 的项目
                        if ("RESERVE".equals(item.getString("projectType")) && "AVAILABLE".equals(item.getString("applyAction"))) {
                            // 将符合条件的项目添加到 ReserveIdMapUtil
                            String itemId = item.getString("itemId");
                            String itemName = item.getString("itemName");
                            int energy = item.getInt("energy");
                            IdMapManager.getInstance(ReserveaMap.class).add(itemId, itemName + "(" + energy + "g)");
                        }
                    }
                    Log.runtime("初始化保护地任务成功。");
                }
                // 将筛选结果保存到 ReserveIdMapUtil
                IdMapManager.getInstance(ReserveaMap.class).save();
            } else {
                // 若 resultCode 不为 SUCCESS，记录错误描述
                Log.runtime(jsonResponse.optString("resultDesc", "未知错误"));
            }
        } catch (JSONException e) {
            // 捕获 JSON 解析错误并记录日志
            Log.runtime("JSON 解析错误：" + e.getMessage());
            Log.printStackTrace(e);
            IdMapManager.getInstance(ReserveaMap.class).load(); // 若出现异常则加载保存的 ReserveIdMapUtil 备份
        } catch (Exception e) {
            // 捕获所有其他异常并记录
            Log.runtime("初始化保护地任务时出错：" + e.getMessage());
            Log.printStackTrace(e);
            IdMapManager.getInstance(ReserveaMap.class).load(); // 加载备份的 ReserveIdMapUtil
        }
    }
    /**
     * 初始化沙滩任务。
     * 通过调用 AntOceanRpc 接口查询养成列表，
     * 并将符合条件的任务加入 BeachMap。
     * 条件：养成项目的类型必须为 BEACH、COOPERATE_SEA_TREE 或 SEA_ANIMAL，
     * 并且其状态为 AVAILABLE。最后将符合条件的任务保存到 BeachMap 中。
     */
    private static void initBeach() {
        try {
            String response = AntOceanRpcCall.queryCultivationList();
            JSONObject jsonResponse = new JSONObject(response);
            if ("SUCCESS".equals(jsonResponse.optString("resultCode", ""))) {
                // 获取 cultivationItemVOList 列表，包含所有养成项目
                JSONArray cultivationList = jsonResponse.optJSONArray("cultivationItemVOList");
                if (cultivationList != null) {
                    for (int i = 0; i < cultivationList.length(); i++) {
                        JSONObject item = cultivationList.getJSONObject(i);
                        // 跳过未定义 templateSubType 字段的项目
                        if (!item.has("templateSubType")) {
                            continue;
                        }
                        // 检查 templateSubType 是否符合指定类型
                        String templateSubType = item.getString("templateSubType");
                        if (!"BEACH".equals(templateSubType) && !"COOPERATE_SEA_TREE".equals(templateSubType) && !"SEA_ANIMAL".equals(templateSubType)) {
                            continue;
                        }
                        // 检查 applyAction 是否为 AVAILABLE
                        if (!"AVAILABLE".equals(item.getString("applyAction"))) {
                            continue;
                        }
                        // 将符合条件的项目添加到 BeachMap
                        String templateCode = item.getString("templateCode");
                        String cultivationName = item.getString("cultivationName");
                        int energy = item.getInt("energy");
                        IdMapManager.getInstance(BeachMap.class).add(templateCode, cultivationName + "(" + energy + "g)");
                    }
                    Log.runtime("初始化沙滩数据成功。");
                }
                // 将所有筛选结果保存到 BeachMap
                IdMapManager.getInstance(BeachMap.class).save();
            } else {
                // 若 resultCode 不为 SUCCESS，记录错误描述
                Log.runtime(jsonResponse.optString("resultDesc", "未知错误"));
            }
        } catch (JSONException e) {
            // 记录 JSON 解析过程中的异常
            Log.runtime("JSON 解析错误：" + e.getMessage());
            Log.printStackTrace(e);
            IdMapManager.getInstance(BeachMap.class).load(); // 若出现异常则加载保存的 BeachMap 备份
        } catch (Exception e) {
            // 捕获所有其他异常并记录
            Log.runtime("初始化沙滩任务时出错：" + e.getMessage());
            Log.printStackTrace(e);
            IdMapManager.getInstance(BeachMap.class).load(); // 加载保存的 BeachMap 备份
        }
    }
    public interface TimedTaskModel {
        int SYSTEM = 0;
        int PROGRAM = 1;
        String[] nickNames = {"系统计时", "程序计时"};
    }
}
